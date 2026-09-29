package br.com.willendary.designacoesjw

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import br.com.willendary.designacoesjw.data.*
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import br.com.willendary.designacoesjw.export.CsvDataHandler
import br.com.willendary.designacoesjw.util.WhatsAppHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository(app)
    private val accessRepo = UserAccessRepository()
    private var usersListener: ListenerRegistration? = null
    var currentUserProfile = mutableStateOf<UserProfile?>(null); private set
    var users = mutableStateOf<List<UserProfile>>(emptyList()); private set
    var invitations = mutableStateOf<List<Invitation>>(emptyList()); private set
    var brothers = mutableStateOf(repo.loadBrothers()); private set
    var privileges = mutableStateOf(repo.loadPrivileges()); private set
    var meetings = mutableStateOf(repo.loadMeetings()); private set
    var schedule = mutableStateOf(repo.loadSchedule()); private set

    init {
        FirebaseAuth.getInstance().currentUser?.let { user ->
            accessRepo.observeCurrentUser(
                uid = user.uid,
                email = user.email.orEmpty(),
                onProfile = { profile ->
                    currentUserProfile.value = profile
                    if (profile.permissions.contains(AppPermissions.MANAGE_USERS) || profile.role == "admin") {
                        usersListener?.remove()
                        usersListener = accessRepo.observeAllUsers(
                            onUsers = { users.value = it.sortedBy { u -> u.email.lowercase() } },
                            onError = {}
                        )
                        accessRepo.observeInvitations(
                            onInvitations = { invitations.value = it.sortedByDescending { i -> i.createdAt } },
                            onError = {}
                        )
                    }
                }
            )
        }
        repo.startCloudSync(
            onBrothers = { brothers.value = it },
            onPrivileges = { privileges.value = it },
            onMeetings = { meetings.value = it },
            onSchedule = { schedule.value = it }
        )
    }

    override fun onCleared() {
        repo.closeCloudSync()
        usersListener?.remove()
        accessRepo.close()
        super.onCleared()
    }

    fun can(permission: String): Boolean {
        val profile = currentUserProfile.value ?: return false
        return profile.active && (profile.role == "admin" || permission in profile.permissions)
    }

    fun createInvitation(email: String, name: String, permissions: Set<String>, onResult: (Invitation?, String?) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || !can(AppPermissions.MANAGE_USERS)) {
            onResult(null, "Você não tem permissão para convidar usuários.")
            return
        }
        accessRepo.saveInvitation(email, name, permissions, user.uid, onResult)
    }

    fun updateUser(profile: UserProfile, onResult: (String?) -> Unit) {
        if (!can(AppPermissions.MANAGE_USERS)) {
            onResult("Você não tem permissão para alterar usuários.")
            return
        }
        accessRepo.updateUser(profile, onResult)
    }

    fun setMeetingDays(first: Int, second: Int) {
        if (first == second) return
        schedule.value = MeetingSchedule(first, second)
        repo.saveSchedule(schedule.value)
    }

    fun addBrother(name: String, phone: String): String? {
        val normalized = normalizeName(name)
        if (normalized.isBlank()) return "Informe o nome do irmão."
        if (brothers.value.any { normalizeName(it.name) == normalized }) return "Já existe um irmão cadastrado com esse nome."
        brothers.value = brothers.value + Brother(nextId(), name.trim(), phone.trim())
        repo.saveBrothers(brothers.value)
        return null
    }

    fun updateBrother(id: Long, name: String, phone: String): String? {
        val normalized = normalizeName(name)
        if (normalized.isBlank()) return "Informe o nome do irmão."
        if (brothers.value.any { it.id != id && normalizeName(it.name) == normalized }) return "Já existe outro irmão cadastrado com esse nome."
        brothers.value = brothers.value.map { if (it.id == id) it.copy(name = name.trim(), phone = phone.trim()) else it }
        repo.saveBrothers(brothers.value)
        return null
    }

    fun deleteBrother(id: Long) {
        brothers.value = brothers.value.filterNot { it.id == id }
        repo.saveBrothers(brothers.value)
    }

    fun setBrotherActive(id: Long, active: Boolean) {
        brothers.value = brothers.value.map { if (it.id == id) it.copy(active = active) else it }
        repo.saveBrothers(brothers.value)
    }

    fun addPrivilege(name: String, quantity: Int): String? {
        val normalized = normalizeName(name)
        if (normalized.isBlank()) return "Informe o nome do privilégio."
        if (privileges.value.any { normalizeName(it.name) == normalized }) return "Já existe um privilégio cadastrado com esse nome."
        privileges.value = privileges.value + Privilege(nextId(), name.trim(), quantity.coerceAtLeast(1))
        repo.savePrivileges(privileges.value)
        return null
    }

    fun updatePrivilege(id: Long, name: String, quantity: Int): String? {
        val normalized = normalizeName(name)
        if (normalized.isBlank()) return "Informe o nome do privilégio."
        if (privileges.value.any { it.id != id && normalizeName(it.name) == normalized }) return "Já existe outro privilégio cadastrado com esse nome."
        privileges.value = privileges.value.map { if (it.id == id) it.copy(name = name.trim(), quantity = quantity.coerceAtLeast(1)) else it }
        repo.savePrivileges(privileges.value)
        return null
    }

    fun deletePrivilege(id: Long) {
        privileges.value = privileges.value.filterNot { it.id == id }
        repo.savePrivileges(privileges.value)
        brothers.value = brothers.value.map { it.copy(privileges = it.privileges - id) }
        repo.saveBrothers(brothers.value)
    }

    fun setPrivilegeActive(id: Long, active: Boolean) {
        privileges.value = privileges.value.map { if (it.id == id) it.copy(active = active) else it }
        repo.savePrivileges(privileges.value)
    }

    fun setPrivilegeAllowedDays(id: Long, days: Set<Int>) {
        privileges.value = privileges.value.map { if (it.id == id) it.copy(allowedDays = days) else it }
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
        val monthPrefix = yearMonth.format(DateTimeFormatter.ofPattern("MM/yyyy"))
        val keep = meetings.value.filterNot { it.date.endsWith("/$monthPrefix") }
        val generated = AssignmentGenerator.generateMonth(
            yearMonth = yearMonth,
            schedule = schedule.value,
            brothers = brothers.value,
            privileges = privileges.value,
            existingMeetings = keep
        )
        meetings.value = (keep + generated).sortedBy { AssignmentGenerator.parseDate(it.date) }
        repo.saveMeetings(meetings.value)
        return generated
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

    fun candidatesFor(meeting: Meeting, privilegeId: Long, currentBrotherId: Long): List<Brother> =
        AssignmentGenerator.candidatesFor(meeting, privilegeId, currentBrotherId, brothers.value, privileges.value)

    fun missingAssignments(meeting: Meeting): List<Privilege> =
        AssignmentGenerator.missingAssignments(meeting, privileges.value)

    fun setBrotherRole(id: Long, role: BrotherRole) {
        brothers.value = brothers.value.map { if (it.id == id) it.copy(role = role) else it }
        repo.saveBrothers(brothers.value)
    }

    fun addUnavailability(brotherId: Long, startDate: String, endDate: String, reason: String): String? {
        val start = AssignmentGenerator.parseDate(startDate)
        val end = AssignmentGenerator.parseDate(endDate)
        if (start == LocalDate.MIN || end == LocalDate.MIN) return "Data inválida. Use o formato dd/MM/yyyy."
        if (end.isBefore(start)) return "A data de término não pode ser anterior à data de início."
        val newPeriod = UnavailablePeriod(AssignmentGenerator.nextId(), startDate, endDate, reason.trim())
        brothers.value = brothers.value.map {
            if (it.id == brotherId) it.copy(unavailabilities = it.unavailabilities + newPeriod) else it
        }
        repo.saveBrothers(brothers.value)
        return null
    }

    fun removeUnavailability(brotherId: Long, periodId: Long) {
        brothers.value = brothers.value.map {
            if (it.id == brotherId) it.copy(unavailabilities = it.unavailabilities.filterNot { p -> p.id == periodId }) else it
        }
        repo.saveBrothers(brothers.value)
    }

    fun setPrivilegeMinRole(id: Long, role: BrotherRole) {
        privileges.value = privileges.value.map { if (it.id == id) it.copy(minRole = role) else it }
        repo.savePrivileges(privileges.value)
    }

    fun exportBrothersCsv(): String = CsvDataHandler.exportBrothersToCsv(brothers.value, privileges.value)

    fun exportMeetingsCsv(): String = CsvDataHandler.exportMeetingsToCsv(meetings.value, brothers.value, privileges.value)

    fun importBrothersCsv(csvText: String): Int {
        val imported = CsvDataHandler.importBrothersFromCsv(csvText)
        if (imported.isEmpty()) return 0
        val currentBrothers = brothers.value.toMutableList()
        val currentPrivileges = privileges.value
        var count = 0
        imported.forEach { imp ->
            val normName = AssignmentGenerator.normalizeName(imp.name)
            if (normName.isNotBlank() && currentBrothers.none { AssignmentGenerator.normalizeName(it.name) == normName }) {
                val matchedPrivilegeIds = imp.privilegeNames.mapNotNull { pName ->
                    val normPName = AssignmentGenerator.normalizeName(pName)
                    currentPrivileges.find { AssignmentGenerator.normalizeName(it.name) == normPName }?.id
                }.toSet()
                currentBrothers += Brother(
                    id = AssignmentGenerator.nextId(),
                    name = imp.name,
                    phone = imp.phone,
                    privileges = matchedPrivilegeIds,
                    active = imp.active,
                    role = imp.role
                )
                count++
            }
        }
        if (count > 0) {
            brothers.value = currentBrothers.sortedBy { AssignmentGenerator.normalizeName(it.name) }
            repo.saveBrothers(brothers.value)
        }
        return count
    }

    private fun normalizeName(value: String): String = AssignmentGenerator.normalizeName(value)

    private fun nextId(): Long = AssignmentGenerator.nextId()
}
