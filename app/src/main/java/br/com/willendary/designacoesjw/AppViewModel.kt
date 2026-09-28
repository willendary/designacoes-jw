package br.com.willendary.designacoesjw

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import br.com.willendary.designacoesjw.data.*
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
        val activePrivileges = privileges.value.filter { it.active && (it.allowedDays.isEmpty() || date.dayOfWeek.value in it.allowedDays) }
        val activeBrothers = brothers.value.filter { it.active && it.id !in blocked }
        val history = historySource.flatMap { it.assignments }
            .groupingBy { it.brotherId to it.privilegeId }.eachCount()
        val result = mutableListOf<Assignment>()
        val used = mutableSetOf<Long>()

        activePrivileges.sortedBy { it.name.lowercase(Locale.getDefault()) }.forEach { privilege ->
            val candidates = activeBrothers
                .filter { it.id !in used && isBrotherAuthorizedForPrivilege(it, privilege) }
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
        val privilege = privileges.value.firstOrNull { it.id == privilegeId } ?: return emptyList()
        return brothers.value.filter {
            it.active &&
            it.id !in meeting.blockedBrotherIds &&
            it.id !in used &&
            isBrotherAuthorizedForPrivilege(it, privilege) &&
            (privilege.allowedDays.isEmpty() || parseMeetingDay(meeting.date) in privilege.allowedDays)
        }.sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    fun missingAssignments(meeting: Meeting): List<Privilege> =
        privileges.value.filter { p ->
            p.active &&
            (p.allowedDays.isEmpty() || parseMeetingDay(meeting.date) in p.allowedDays) &&
            meeting.assignments.count { it.privilegeId == p.id } < p.quantity
        }

    /** 
     * Regras de capacidade entre privilégios:
     * - Leitor da Sentinela também pode ser Leitor do Livro.
     * - Leitor do Livro não pode, por isso, ser considerado Leitor da Sentinela.
     *
     * A autorização direta continua sendo armazenada no cadastro do irmão.
     * A herança é calculada apenas no momento de verificar a elegibilidade.
     */
    private fun isBrotherAuthorizedForPrivilege(brother: Brother, privilege: Privilege): Boolean {
        if (privilege.id in brother.privileges) return true

        val bookPrivilege = privileges.value.firstOrNull { isBookReaderPrivilege(it) }
        val sentinelPrivilege = privileges.value.firstOrNull { isSentinelReaderPrivilege(it) }

        return bookPrivilege?.id == privilege.id &&
            sentinelPrivilege != null &&
            sentinelPrivilege.id in brother.privileges
    }

    private fun isBookReaderPrivilege(privilege: Privilege): Boolean =
        normalizeName(privilege.name) in setOf(
            "leitor do livro",
            "leitor livro"
        )

    private fun isSentinelReaderPrivilege(privilege: Privilege): Boolean =
        normalizeName(privilege.name) in setOf(
            "leitor da sentinela",
            "leitor sentinela"
        )

    private fun parseMeetingDay(value: String): Int = runCatching {
        LocalDate.parse(value, DateTimeFormatter.ofPattern("dd/MM/yyyy")).dayOfWeek.value
    }.getOrDefault(0)

    private fun lastAssignmentDate(brotherId: Long, privilegeId: Long): java.util.Date? {
        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        return meetings.value
            .filter { it.assignments.any { a -> a.brotherId == brotherId && a.privilegeId == privilegeId } }
            .mapNotNull { formatter.parse(it.date) }
            .maxOrNull()
    }

    private fun normalizeName(value: String): String =
        java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase(Locale.getDefault())

    private fun nextId(): Long = System.currentTimeMillis() * 1000L + Random.nextLong(1000)
}
