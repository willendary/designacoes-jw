package br.com.willendary.designacoesjw.data

import kotlinx.serialization.Serializable

@Serializable
enum class BrotherRole(val label: String) {
    PUBLISHER("Publicador"),
    MINISTERIAL_SERVANT("Servo Ministerial"),
    ELDER("Ancião")
}

@Serializable
data class UnavailablePeriod(
    val id: Long = 0L,
    val startDate: String, // dd/MM/yyyy
    val endDate: String,   // dd/MM/yyyy
    val reason: String = ""
)

@Serializable
data class Brother(
    val id: Long,
    val name: String,
    val phone: String = "",
    val privileges: Set<Long> = emptySet(),
    val active: Boolean = true,
    val role: BrotherRole = BrotherRole.PUBLISHER,
    val unavailabilities: List<UnavailablePeriod> = emptyList()
)

@Serializable
data class Privilege(
    val id: Long,
    val name: String,
    val quantity: Int = 1,
    val active: Boolean = true,
    val allowedDays: Set<Int> = emptySet(),
    val minRole: BrotherRole = BrotherRole.PUBLISHER
)

@Serializable
data class Assignment(
    val privilegeId: Long,
    val brotherId: Long
)

@Serializable
data class Meeting(
    val id: Long,
    val date: String,
    val type: String,
    val assignments: List<Assignment> = emptyList(),
    val blockedBrotherIds: Set<Long> = emptySet()
)

@Serializable
data class MeetingSchedule(
    val firstDay: Int = 3,
    val secondDay: Int = 6
)

@Serializable
data class Store(
    val brothers: List<Brother> = emptyList(),
    val privileges: List<Privilege> = emptyList(),
    val meetings: List<Meeting> = emptyList(),
    val firstDay: Int = 3,
    val secondDay: Int = 6,
    val whatsappSingleTemplate: String = "",
    val whatsappMeetingTemplate: String = ""
)
