package br.com.willendary.designacoesjw

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import br.com.willendary.designacoesjw.data.*
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository(app)
    var brothers = mutableStateOf(repo.loadBrothers()); private set
    var privileges = mutableStateOf(repo.loadPrivileges()); private set
    var meetings = mutableStateOf(repo.loadMeetings()); private set
    var schedule = mutableStateOf(repo.loadSchedule()); private set

    fun setMeetingDays(first: Int, second: Int) {
        if (first == second) return
        schedule.value = MeetingSchedule(first, second)
        repo.saveSchedule(schedule.value)
    }

    fun addBrother(name: String, phone: String) {
        if (name.isBlank()) return
        brothers.value = brothers.value + Brother(nextId(), name.trim(), phone.trim())
        repo.saveBrothers(brothers.value)
    }

    fun updateBrother(id: Long, name: String, phone: String) {
        if (name.isBlank()) return
        brothers.value = brothers.value.map { if (it.id == id) it.copy(name = name.trim(), phone = phone.trim()) else it }
        repo.saveBrothers(brothers.value)
    }

    fun setBrotherActive(id: Long, active: Boolean) {
        brothers.value = brothers.value.map { if (it.id == id) it.copy(active = active) else it }
        repo.saveBrothers(brothers.value)
    }

    fun addPrivilege(name: String, quantity: Int) {
        if (name.isBlank()) return
        privileges.value = privileges.value + Privilege(nextId(), name.trim(), quantity.coerceAtLeast(1))
        repo.savePrivileges(privileges.value)
    }

    fun updatePrivilege(id: Long, name: String, quantity: Int) {
        if (name.isBlank()) return
        privileges.value = privileges.value.map { if (it.id == id) it.copy(name = name.trim(), quantity = quantity.coerceAtLeast(1)) else it }
        repo.savePrivileges(privileges.value)
    }

    fun setPrivilegeActive(id: Long, active: Boolean) {
        privileges.value = privileges.value.map { if (it.id == id) it.copy(active = active) else it }
        repo.savePrivileges(privileges.value)
    }

    fun togglePrivilege(brotherId: Long, privilegeId: Long) {
        brothers.value = brothers.value.map {
            if (it.id != brotherId) it else it.copy(
                privileges = it.privileges.toMutableSet().also { s -> if (!s.add(privilegeId)) s.remove(privilegeId) }
            )
        }
        repo.saveBrothers(brothers.value)
    }

    fun generateMonth(yearMonth: YearMonth): List<Meeting> {
        val days = listOf(schedule.value.firstDay, schedule.value.secondDay)
        val dates = yearMonth.atDay(1).let { first ->
            (0 until yearMonth.lengthOfMonth()).map { first.plusDays(it.toLong()) }
        }.filter { it.dayOfWeek.value in days }.sorted()

        val generated = mutableListOf<Meeting>()
        var historyMeetings = meetings.value
        dates.forEach { date ->
            val meeting = generateMeetingInternal(date, "Reunião", emptySet(), historyMeetings)
            generated += meeting
            historyMeetings = historyMeetings + meeting
        }
        meetings.value = historyMeetings
        repo.saveMeetings(meetings.value)
        return generated
    }

    private fun generateMeetingInternal(
        date: LocalDate,
        type: String,
        blocked: Set<Long>,
        historySource: List<Meeting>
    ): Meeting {
        val activePrivileges = privileges.value.filter { it.active }
        val activeBrothers = brothers.value.filter { it.active && it.id !in blocked }
        val history = historySource.flatMap { it.assignments }
            .groupingBy { it.brotherId to it.privilegeId }.eachCount()
        val result = mutableListOf<Assignment>()
        val used = mutableSetOf<Long>()

        activePrivileges.sortedBy { it.id }.forEach { privilege ->
            val candidates = activeBrothers
                .filter { it.id !in used && privilege.id in it.privileges }
                .sortedWith(
                    compareBy<Brother> { history[it.id to privilege.id] ?: 0 }
                        .thenBy { lastAssignmentDate(it.id, privilege.id)?.time ?: 0L }
                        .thenBy { it.name.lowercase(Locale.getDefault()) }
                )
            candidates.take(privilege.quantity).forEach { brother ->
                result += Assignment(privilege.id, brother.id)
                used += brother.id
            }
        }

        return Meeting(nextId(), date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), type, result, blocked)
    }

    fun replaceAssignment(meetingId: Long, privilegeId: Long, oldBrotherId: Long, newBrotherId: Long) {
        meetings.value = meetings.value.map { meeting ->
            if (meeting.id != meetingId) meeting else meeting.copy(
                assignments = meeting.assignments.map {
                    if (it.privilegeId == privilegeId && it.brotherId == oldBrotherId) it.copy(brotherId = newBrotherId) else it
                }
            )
        }
        repo.saveMeetings(meetings.value)
    }

    fun deleteMeeting(meetingId: Long) {
        meetings.value = meetings.value.filterNot { it.id == meetingId }
        repo.saveMeetings(meetings.value)
    }

    fun deleteMonth(yearMonth: YearMonth) {
        val prefix = yearMonth.format(DateTimeFormatter.ofPattern("MM/yyyy"))
        meetings.value = meetings.value.filterNot {
            it.date.endsWith("/$prefix")
        }
        repo.saveMeetings(meetings.value)
    }

    fun candidatesFor(meeting: Meeting, privilegeId: Long, currentBrotherId: Long): List<Brother> {
        val used = meeting.assignments.filter { it.brotherId != currentBrotherId }.map { it.brotherId }.toSet()
        return brothers.value.filter {
            it.active && it.id !in meeting.blockedBrotherIds && it.id !in used && privilegeId in it.privileges
        }.sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    fun missingAssignments(meeting: Meeting): List<Privilege> =
        privileges.value.filter { p -> p.active && meeting.assignments.count { it.privilegeId == p.id } < p.quantity }

    private fun lastAssignmentDate(brotherId: Long, privilegeId: Long): java.util.Date? {
        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        return meetings.value
            .filter { it.assignments.any { a -> a.brotherId == brotherId && a.privilegeId == privilegeId } }
            .mapNotNull { formatter.parse(it.date) }
            .maxOrNull()
    }

    private fun nextId(): Long = System.currentTimeMillis() * 1000L + Random.nextLong(1000)
}
