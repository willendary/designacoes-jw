package br.com.willendary.designacoesjw.generator

import br.com.willendary.designacoesjw.data.Assignment
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.MeetingSchedule
import br.com.willendary.designacoesjw.data.Privilege
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
                        brother.role.ordinal >= privilege.minRole.ordinal &&
                        isBrotherAuthorizedForPrivilege(brother, privilege, privileges)
                }
                .sortedWith(
                    // 1. Prioriza quem fez menos esse privilégio no histórico
                    compareBy<Brother> { privilegeHistoryCount[it.id to privilege.id] ?: 0 }
                        // 2. Tenta evitar designação consecutiva da reunião anterior
                        .thenBy { if (it.id in brothersInPreviousMeeting) 1 else 0 }
                        // 3. Prioriza quem tem menos designações no geral
                        .thenBy { totalAssignmentsCount[it.id] ?: 0 }
                        // 4. Prioriza quem está há mais tempo sem fazer esse privilégio
                        .thenBy { lastAssignmentDate(it.id, privilege.id, history)?.toEpochDay() ?: 0L }
                        // 5. Ordem alfabética para consistência
                        .thenBy { normalizeName(it.name) }
                )

            candidates.take(privilege.quantity).forEach { brother ->
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

        return brothers.filter { brother ->
            brother.active &&
                brother.id !in meeting.blockedBrotherIds &&
                brother.id !in used &&
                !isBrotherUnavailableOn(brother, meetingDate) &&
                brother.role.ordinal >= privilege.minRole.ordinal &&
                isBrotherAuthorizedForPrivilege(brother, privilege, privileges)
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
        val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

        if (privilege.allowedDays.isNotEmpty()) {
            return meetingDay in privilege.allowedDays
        }

        // Regra Teocrática JW:
        // Leitor do Livro (Estudo Bíblico de Congregação) ocorre na reunião de meio de semana.
        if (isBookReaderPrivilege(privilege)) {
            return !isWeekend
        }

        // Leitor de A Sentinela ocorre na reunião de fim de semana.
        if (isSentinelReaderPrivilege(privilege)) {
            return isWeekend
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

    fun isBrotherAuthorizedForPrivilege(
        brother: Brother,
        privilege: Privilege,
        allPrivileges: List<Privilege>
    ): Boolean {
        // Regra Teocrática JW: Tarefas congregacionais de reunião (som, leitor, indicador, orações) são para irmãos
        if (privilege.maleOnly && brother.gender == br.com.willendary.designacoesjw.data.Gender.FEMALE) {
            return false
        }

        if (privilege.id in brother.privileges) return true

        // Regra JW: Leitor da Sentinela pode automaticamente ler o livro
        val isBook = isBookReaderPrivilege(privilege)
        if (isBook) {
            val sentinelPrivilege = allPrivileges.firstOrNull { isSentinelReaderPrivilege(it) }
            if (sentinelPrivilege != null && sentinelPrivilege.id in brother.privileges) {
                return true
            }
        }

        return false
    }

    fun isBookReaderPrivilege(privilege: Privilege): Boolean {
        val norm = normalizeName(privilege.name)
        return norm in setOf(
            "leitor do livro",
            "leitor livro",
            "leitor de livro",
            "estudo biblico",
            "estudo biblico de congregacao",
            "leitor do estudo biblico",
            "leitor estudo biblico",
            "leitor ebc"
        ) || (norm.contains("leitor") && norm.contains("livro")) || (norm.contains("leitor") && norm.contains("ebc"))
    }

    fun isSentinelReaderPrivilege(privilege: Privilege): Boolean {
        val norm = normalizeName(privilege.name)
        return norm in setOf(
            "leitor da sentinela",
            "leitor sentinela",
            "leitor de a sentinela",
            "estudo da sentinela",
            "estudo de a sentinela"
        ) || (norm.contains("leitor") && norm.contains("sentinela"))
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
