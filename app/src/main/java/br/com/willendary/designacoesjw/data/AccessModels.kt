package br.com.willendary.designacoesjw.data

data class UserProfile(
    val uid: String,
    val email: String,
    val name: String = "",
    val role: String = "viewer",
    val permissions: Set<String> = emptySet(),
    val active: Boolean = true,
    val invitationId: String = ""
)

data class Invitation(
    val id: String,
    val email: String,
    val name: String = "",
    val permissions: Set<String> = emptySet(),
    val status: String = "pending",
    val createdBy: String = "",
    val createdAt: Long = 0L
)

object AppPermissions {
    const val VIEW_ASSIGNMENTS = "view_assignments"
    const val MANAGE_BROTHERS = "manage_brothers"
    const val MANAGE_PRIVILEGES = "manage_privileges"
    const val GENERATE_ASSIGNMENTS = "generate_assignments"
    const val MANAGE_USERS = "manage_users"
    const val MANAGE_SETTINGS = "manage_settings"
    const val EXPORT_REPORTS = "export_reports"

    val all = linkedSetOf(
        VIEW_ASSIGNMENTS,
        MANAGE_BROTHERS,
        MANAGE_PRIVILEGES,
        GENERATE_ASSIGNMENTS,
        MANAGE_USERS,
        MANAGE_SETTINGS,
        EXPORT_REPORTS
    )

    fun label(permission: String): String = when (permission) {
        VIEW_ASSIGNMENTS -> "Ver designações"
        MANAGE_BROTHERS -> "Cadastrar e editar irmãos"
        MANAGE_PRIVILEGES -> "Gerenciar privilégios"
        GENERATE_ASSIGNMENTS -> "Gerar e alterar designações"
        MANAGE_USERS -> "Gerenciar usuários e convites"
        MANAGE_SETTINGS -> "Alterar configurações"
        EXPORT_REPORTS -> "Exportar PDF/Word"
        else -> permission
    }
}
