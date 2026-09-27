package br.com.willendary.designacoesjw.data

data class Brother(val id: Long, val name: String, val phone: String = "", val privileges: Set<Long> = emptySet(), val active: Boolean = true)
data class Privilege(val id: Long, val name: String, val quantity: Int = 1)
data class Assignment(val privilegeId: Long, val brotherId: Long)
data class Meeting(val id: Long, val date: String, val type: String, val assignments: List<Assignment> = emptyList())
