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
import kotlin.random.Random

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
            val prevMeeting = history.maxByOrNull { parseDate(it.date) }
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
            it.active && (it.allowedDays.isEmpty() || date.dayOfWeek.value in it.allowedDays)
        }.sortedBy { it.name.lowercase(Locale.getDefault()) }

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
        val meetingDay = meetingDate.dayOfWeek.value
        val used = meeting.assignments
            .filter { it.brotherId != currentBrotherId }
            .map { it.brotherId }
            .toSet()

        val privilege = privileges.firstOrNull { it.id == privilegeId } ?: return emptyList()

        return brothers.filter { brother ->
            brother.active &&
                brother.id !in meeting.blockedBrotherIds &&
                brother.id !in used &&
                !isBrotherUnavailableOn(brother, meetingDate) &&
                brother.role.ordinal >= privilege.minRole.ordinal &&
                isBrotherAuthorizedForPrivilege(brother, privilege, privileges) &&
                (privilege.allowedDays.isEmpty() || meetingDay in privilege.allowedDays)
        }.sortedBy { normalizeName(it.name) }
    }

    fun missingAssignments(meeting: Meeting, privileges: List<Privilege>): List<Privilege> {
        val meetingDay = parseDate(meeting.date).dayOfWeek.value
        return privileges.filter { p ->
            p.active &&
                (p.allowedDays.isEmpty() || meetingDay in p.allowedDays) &&
                meeting.assignments.count { it.privilegeId == p.id } < p.quantity
        }
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
        if (privilege.id in brother.privileges) return true

        // Regra JW: Leitor da Sentinela pode automaticamente ler o livro
        val bookPrivilege = allPrivileges.firstOrNull { isBookReaderPrivilege(it) }
        val sentinelPrivilege = allPrivileges.firstOrNull { isSentinelReaderPrivilege(it) }

        return bookPrivilege?.id == privilege.id &&
            sentinelPrivilege != null &&
            sentinelPrivilege.id in brother.privileges
    }

    fun isBookReaderPrivilege(privilege: Privilege): Boolean =
        normalizeName(privilege.name) in setOf("leitor do livro", "leitor livro")

    fun isSentinelReaderPrivilege(privilege: Privilege): Boolean =
        normalizeName(privilege.name) in setOf("leitor da sentinela", "leitor sentinela")

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
            .lowercase(Locale.getDefault())

    fun nextId(): Long = System.currentTimeMillis() * 1000L + Random.nextLong(1000)
}
