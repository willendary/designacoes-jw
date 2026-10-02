package br.com.willendary.designacoesjw.generator

import br.com.willendary.designacoesjw.data.Assignment
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.BrotherStatus
import br.com.willendary.designacoesjw.data.Gender
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.MeetingSchedule
import br.com.willendary.designacoesjw.data.Privilege
import br.com.willendary.designacoesjw.data.PartKind
import br.com.willendary.designacoesjw.data.ProgramItem
import br.com.willendary.designacoesjw.data.ReaderGrant
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

object AssignmentGenerator {
    val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun generateMonth(
        yearMonth: YearMonth,
        schedule: MeetingSchedule,
        brothers: List<Brother>,
        privileges: List<Privilege>,
        existingMeetings: List<Meeting>
    ): List<Meeting> {
        val meetingDays = listOf(schedule.firstDay, schedule.secondDay)
        val dates = (1..yearMonth.lengthOfMonth())
            .map { yearMonth.atDay(it) }
            .filter { it.dayOfWeek.value in meetingDays }
            .sorted()

        val generated = mutableListOf<Meeting>()
        var history = existingMeetings.toList()

        dates.forEach { date ->
            val prevMeeting = history
                .filter { val d = parseDate(it.date); d != LocalDate.MIN && d < date }
                .maxByOrNull { parseDate(it.date) }
            val meeting = generateMeeting(
                date = date,
                blocked = emptySet(),
                brothers = brothers,
                privileges = privileges,
                history = history,
                previousMeeting = prevMeeting
            )
            generated += meeting
            history = history + meeting
        }

        return generated
    }

    fun generateMeeting(
        date: LocalDate,
        blocked: Set<Long>,
        brothers: List<Brother>,
        privileges: List<Privilege>,
        history: List<Meeting>,
        previousMeeting: Meeting? = null
    ): Meeting {
        val activePrivileges = privileges.filter {
            isPrivilegeApplicableToMeeting(it, date)
        }.sortedBy { it.name.lowercase(Locale.ROOT) }

        val activeBrothers = brothers.filter {
            it.active && it.id !in blocked && !isBrotherUnavailableOn(it, date)
        }

        val privilegeHistoryCount = history.flatMap { it.assignments }
            .groupingBy { it.brotherId to it.privilegeId }
            .eachCount()

        val totalAssignmentsCount = history.flatMap { it.assignments }
            .groupingBy { it.brotherId }
            .eachCount()

        val brothersInPreviousMeeting = previousMeeting?.assignments?.map { it.brotherId }?.toSet() ?: emptySet()

        val result = mutableListOf<Assignment>()
        val usedInMeeting = mutableSetOf<Long>()

        activePrivileges.forEach { privilege ->
            val candidates = activeBrothers
                .filter { brother ->
                    brother.id !in usedInMeeting &&
                        !brother.trainee && // aprendiz nunca é automático
                        brother.role.ordinal >= privilege.minRole.ordinal &&
                        isBrotherAuthorizedForPrivilege(brother, privilege, privileges)
                }
                .sortedWith(
                    // 0. Qualificados (batizados e não aprendizes) primeiro
                    compareBy<Brother> { if (it.isUnqualified()) 1 else 0 }
                        // 1. Prioriza quem fez menos esse privilégio no histórico
                        .thenBy { privilegeHistoryCount[it.id to privilege.id] ?: 0 }
                        // 2. Tenta evitar designação consecutiva da reunião anterior
                        .thenBy { if (it.id in brothersInPreviousMeeting) 1 else 0 }
                        // 3. Prioriza quem tem menos designações no geral
                        .thenBy { totalAssignmentsCount[it.id] ?: 0 }
                        // 4. Prioriza quem está há mais tempo sem fazer esse privilégio
                        .thenBy { lastAssignmentDate(it.id, privilege.id, history)?.toEpochDay() ?: 0L }
                        // 5. Ordem alfabética para consistência
                        .thenBy { normalizeName(it.name) }
                )

            // No máximo um não qualificado por parte (quantity >= 2).
            val selected = mutableListOf<Brother>()
            for (candidate in candidates) {
                if (selected.size >= privilege.quantity) break
                if (candidate.isUnqualified() && selected.any { it.isUnqualified() }) continue
                selected += candidate
            }
            selected.forEach { brother ->
                result += Assignment(privilege.id, brother.id)
                usedInMeeting += brother.id
            }
        }

        val type = if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
            "Reunião de fim de semana"
        } else {
            "Reunião do meio de semana"
        }

        return Meeting(
            id = nextId(),
            date = date.format(DATE_FORMATTER),
            type = type,
            assignments = result,
            blockedBrotherIds = blocked
        )
    }

    fun candidatesFor(
        meeting: Meeting,
        privilegeId: Long,
        currentBrotherId: Long,
        brothers: List<Brother>,
        privileges: List<Privilege>
    ): List<Brother> {
        val meetingDate = parseDate(meeting.date)
        val used = meeting.assignments
            .filter { it.brotherId != currentBrotherId }
            .map { it.brotherId }
            .toSet()

        val privilege = privileges.firstOrNull { it.id == privilegeId } ?: return emptyList()
        if (!isPrivilegeApplicableToMeeting(privilege, meetingDate)) return emptyList()

        // Já existe um não qualificado entre os demais designados desta parte?
        // Se sim, a troca manual não pode introduzir um segundo não qualificado.
        val otherBrothersForPrivilege = meeting.assignments
            .filter { it.privilegeId == privilegeId && it.brotherId != currentBrotherId }
            .map { it.brotherId }
            .toSet()
        val hasUnqualifiedOther = otherBrothersForPrivilege.any { id ->
            brothers.firstOrNull { it.id == id }?.isUnqualified() ?: false
        }

        return brothers.filter { brother ->
            brother.active &&
                brother.id !in meeting.blockedBrotherIds &&
                brother.id !in used &&
                !brother.trainee && // aprendiz nunca é automático
                !isBrotherUnavailableOn(brother, meetingDate) &&
                brother.role.ordinal >= privilege.minRole.ordinal &&
                isBrotherAuthorizedForPrivilege(brother, privilege, privileges) &&
                !(hasUnqualifiedOther && brother.isUnqualified())
        }.sortedBy { normalizeName(it.name) }
    }

    fun missingAssignments(meeting: Meeting, privileges: List<Privilege>): List<Privilege> {
        val meetingDate = parseDate(meeting.date)
        return privileges.filter { p ->
            isPrivilegeApplicableToMeeting(p, meetingDate) &&
                meeting.assignments.count { it.privilegeId == p.id } < p.quantity
        }
    }

    fun isPrivilegeApplicableToMeeting(privilege: Privilege, date: LocalDate): Boolean {
        if (!privilege.active) return false
        val meetingDay = date.dayOfWeek.value

        // Dia da semana agora é configurado por allowedDays, não pelo nome do privilégio.
        // "Leitor do Livro" = meio de semana, "Leitor de A Sentinela" = fim de semana:
        // isso é dado (allowedDays), não código. Sem allowedDays, vale qualquer dia.
        if (privilege.allowedDays.isNotEmpty()) {
            return meetingDay in privilege.allowedDays
        }

        return true
    }

    fun isBrotherUnavailableOn(brother: Brother, date: LocalDate): Boolean {
        if (brother.unavailabilities.isEmpty()) return false
        return brother.unavailabilities.any { period ->
            val start = runCatching { LocalDate.parse(period.startDate, DATE_FORMATTER) }.getOrNull()
            val end = runCatching { LocalDate.parse(period.endDate, DATE_FORMATTER) }.getOrNull()
            if (start != null && end != null) {
                !date.isBefore(start) && !date.isAfter(end)
            } else false
        }
    }

    /**
     * Concede este privilégio a este irmão?
     *
     * Fonte única de verdade: o gerador e as telas usam esta função. A regra
     * estava duplicada entre gerador e UI e já divergiu — era isso que
     * permitia a tela dizer que o irmão podia e o gerador recusar.
     */
    fun isAuthorized(brother: Brother, privilege: Privilege): Boolean {
        // Tarefas de som, leitor, indicador e orações são para irmãos.
        if (privilege.maleOnly && brother.gender == Gender.FEMALE) {
            return false
        }

        // allowedStatus: não batizado não pode fazer partes que exigem batismo
        // (Leitor do Livro, Leitor de A Sentinela). Vem da configuração do
        // privilégio, não de código.
        val status = if (brother.baptized) BrotherStatus.BAPTIZED else BrotherStatus.UNBAPTIZED
        if (status !in privilege.allowedStatus) return false

        if (privilege.id in brother.privileges) return true

        // isReader / isSentinelReader concedem a leitura, por dado (readerGrant),
        // não por nome do privilégio. Leitor de A Sentinela também pode ler o
        // livro — herança que antes só funcionava se os dois nomes fossem
        // reconhecidos por string.
        if (privilege.readerGrant == ReaderGrant.SENTINEL && brother.isSentinelReader) return true
        if (privilege.readerGrant == ReaderGrant.BOOK && (brother.isReader || brother.isSentinelReader)) return true

        return false
    }

    fun isBrotherAuthorizedForPrivilege(
        brother: Brother,
        privilege: Privilege,
        allPrivileges: List<Privilege>
    ): Boolean = isAuthorized(brother, privilege)

    private fun Brother.isUnqualified(): Boolean = trainee || !baptized

    /**
     * Quantas pessoas a parte do programa pede.
     *
     * O tipo vem do programa da semana (`ProgramItem.kind`), não de um
     * privilégio cadastrado. `PAIR` são as partes de duas pessoas — a
     * designação, o，研究 de campo. `GROUP` e `DEMONSTRATION` são livres.
     */
    fun expectedCountFor(item: ProgramItem): Int = when (item.kind) {
        PartKind.INDIVIDUAL -> 1
        PartKind.PAIR -> 2
        PartKind.DEMONSTRATION, PartKind.GROUP -> 2
    }

    /**
     * Uma parte do programa pode levar mais de um não qualificado
     * (aprendiz ou não batizado)?
     *
     * **Sim, isso é aviso, não bloqueio.** Quem decide quem faz a parte é o
     * responsável, e a quase totalidade das partes do programa é aberta a
     * qualquer irmão ativo — irmãs fazem leituras, irmãos fazem encenações.
     * A qualificação teocrática de verdade é do privilégio **mecânico**, que
     * continua na lista cadastrada e é checada em [isAuthorized].
     *
     * A regra que existe é a mesma dos privilégios com `quantity >= 2`: dois
     * não qualificados na mesma parte não se aceita numa demonstração. O app
     * avisa para quem conduz corrigir, e não recusa o designar.
     */
    fun unqualifiedWarning(item: ProgramItem, brotherIds: List<Long>, brothers: List<Brother>): String? {
        if (item.kind == PartKind.INDIVIDUAL) return null
        val naoQualificados = brotherIds.count { id ->
            brothers.firstOrNull { it.id == id }?.isUnqualified() ?: false
        }
        if (naoQualificados < 2) return null
        return "Esta parte tem $naoQualificados pessoas ainda não qualificadas " +
            "(aprendiz ou não batizado). Revise se faz sentido."
    }

    fun parseDate(value: String): LocalDate = runCatching {
        LocalDate.parse(value, DATE_FORMATTER)
    }.getOrElse { LocalDate.MIN }

    private fun lastAssignmentDate(brotherId: Long, privilegeId: Long, history: List<Meeting>): LocalDate? =
        history.filter { it.assignments.any { a -> a.brotherId == brotherId && a.privilegeId == privilegeId } }
            .map { parseDate(it.date) }
            .filter { it != LocalDate.MIN }
            .maxOrNull()

    fun normalizeName(value: String): String =
        Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase(Locale.ROOT)

    // Sequencial e monotônico dentro do processo: evita colisão de IDs (chave de
    // documento no Firestore e chave de `keep` na limpeza do sync). O valor inicial
    // deriva do relógio, mas o incremento atômico garante unicidade mesmo sob
    // concorrência entre threads.
    private val idCounter = AtomicLong(System.currentTimeMillis() * 1000L)

    fun nextId(): Long = idCounter.incrementAndGet()
}
