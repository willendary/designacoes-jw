package br.com.willendary.designacoesjw.generator

import br.com.willendary.designacoesjw.data.Assignment
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.BrotherStatus
import br.com.willendary.designacoesjw.data.Gender
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.MeetingSchedule
import br.com.willendary.designacoesjw.data.MeetingType
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

    /** Quantos tipos de reunião existem. Só para caber no id derivado. */
    private const val TIPOS_DE_REUNIAO = 4

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
            // O chamador tira do mês as reuniões antigas antes de chamar isto, e
            // quem volta são estas — então o programa e as decisões da semana
            // tinham de vir junto. Ver [preservarConteudoDaSemana].
            val jaExistia = history.firstOrNull { it.date == meeting.date }
            generated += if (jaExistia == null) meeting else preservarConteudoDaSemana(meeting, jaExistia)
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
            id = meetingId(date, MeetingType.parse(type)),
            date = date.format(DATE_FORMATTER),
            type = type,
            assignments = result,
            blockedBrotherIds = blocked
        )
    }

    /**
     * Junta a reunião gerada com o que a reunião do mesmo dia já tinha.
     *
     * **Só o que muda a cada semana é regenerado: [Meeting.assignments].** Tudo
     * o que é decisão humana ou vem do jw.org atravessa:
     *
     * - `program` — o programa oficial importado. Uma hora de digitação;
     * - `programAssignments` — quem ficou com cada parte, decidido por quem
     *   designa com a reunião na mão;
     * - `theme` — a leitura do dia ("JEREMIAS 40-41"), que vem do import;
     * - `blockedBrotherIds` — quem foi bloqueado daquela reunião. Bloquear é
     *   decisão, não cálculo.
     *
     * Sem isto, "Gerar" do mês apagava o programa importado e as designações
     * manuais, sem aviso — e o diálogo de confirmação não dizia nada disso, ou
     * seja, confirmava uma pergunta errada.
     *
     * @param gerada a reunião que [generateMeeting] acabou de produzir.
     * @param existente a reunião anterior da mesma data, ou `null` na primeira vez.
     */
    fun preservarConteudoDaSemana(gerada: Meeting, existente: Meeting?): Meeting {
        if (existente == null) return gerada
        return gerada.copy(
            program = existente.program,
            programAssignments = existente.programAssignments,
            theme = existente.theme,
            blockedBrotherIds = existente.blockedBrotherIds
        )
    }

    /**
     * Id de reunião que depende **só de quando e qual** — não de quando o app rodou.
     *
     * Uma congregação não tem duas reuniões do mesmo tipo na mesma data: a
     * semana é um conjunto de dias, e cada dia tem um tipo. Então (data, tipo)
     * identifica a reunião, e o id sai disso.
     *
     * **Por que isso importa.** Com [nextId], "Gerar" de novo o mesmo mês criava
     * reuniões **novas** para as mesmas datas. Como o desktop não propaga
     * exclusão, as duas gerações ficavam no Firestore, o pull trazia as duas e a
     * tela mostrava duas linhas para a mesma data — com o lixo acumulando a cada
     * regeneração. Agora a segunda geração reescreve o mesmo documento.
     *
     * Não colide com id de [nextId]: os dois nascem em faixas diferentes
     * (`epochDay * 4 + ordinal` dá número de seis dígitos; `nextId` dá quinze).
     * E reunião que já existia com id antigo continua válida: o Firestore
     * endereça por `id.toString()`, então o documento novo tem outro endereço
     * até o prune do Android remover o velho.
     */
    fun meetingId(date: LocalDate, tipo: MeetingType): Long =
        date.toEpochDay() * TIPOS_DE_REUNIAO + tipo.ordinal

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
     * designação, o estudo de campo. `GROUP` e `DEMONSTRATION` são livres.
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

    // Sequencial e monotônico **dentro do processo**: o valor inicial deriva do
    // relógio e o incremento atômico garante unicidade mesmo sob concorrência de
    // threads. É o id certo para o que pode legitimamente se repetir (dois
    // irmãos com o mesmo nome, dois discursos na mesma data) e é a chave de
    // documento no Firestore e de `keep` na limpeza do sync.
    //
    // **Reunião não usa mais isto** — usa [meetingId], que é derivado da data.
    // A distinção importa: com id de relógio, o mesmo irmão podia receber dois
    // documentos e o segundo sobrescrevia o primeiro em silêncio.
    private val idCounter = AtomicLong(System.currentTimeMillis() * 1000L)

    fun nextId(): Long = idCounter.incrementAndGet()
}
