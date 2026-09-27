package br.com.willendary.designacoesjw

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import br.com.willendary.designacoesjw.data.*
import kotlin.random.Random

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository(app)
    var brothers = mutableStateOf(repo.loadBrothers()); private set
    var privileges = mutableStateOf(repo.loadPrivileges()); private set
    var meetings = mutableStateOf(repo.loadMeetings()); private set

    fun addBrother(name: String, phone: String) {
        if (name.isBlank()) return
        brothers.value = brothers.value + Brother(nextId(), name.trim(), phone.trim())
        repo.saveBrothers(brothers.value)
    }

    fun addPrivilege(name: String, quantity: Int) {
        if (name.isBlank()) return
        privileges.value = privileges.value + Privilege(nextId(), name.trim(), quantity.coerceAtLeast(1))
        repo.savePrivileges(privileges.value)
    }

    fun togglePrivilege(brotherId: Long, privilegeId: Long) {
        brothers.value = brothers.value.map {
            if (it.id != brotherId) it else it.copy(privileges = it.privileges.toMutableSet().also { s -> if (!s.add(privilegeId)) s.remove(privilegeId) })
        }
        repo.saveBrothers(brothers.value)
    }

    fun generateMeeting(date: String, type: String): Meeting {
        val history = meetings.value.flatMap { it.assignments }.groupingBy { it.brotherId to it.privilegeId }.eachCount()
        val used = mutableSetOf<Long>()
        val result = mutableListOf<Assignment>()
        privileges.value.forEach { p ->
            brothers.value.filter { b -> b.active && p.id in b.privileges && b.id !in used }
                .sortedBy { history[it.id to p.id] ?: 0 }
                .take(p.quantity).forEach { b -> result += Assignment(p.id, b.id); used += b.id }
        }
        val meeting = Meeting(nextId(), date, type, result)
        meetings.value = meetings.value + meeting
        repo.saveMeetings(meetings.value)
        return meeting
    }

    private fun nextId() = System.currentTimeMillis() * 1000L + Random.nextLong(1000)
}
