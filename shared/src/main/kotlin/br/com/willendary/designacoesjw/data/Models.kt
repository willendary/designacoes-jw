package br.com.willendary.designacoesjw.data

import kotlinx.serialization.Serializable

@Serializable
enum class BrotherRole(val label: String) {
    PUBLISHER("Publicador"),
    MINISTERIAL_SERVANT("Servo Ministerial"),
    ELDER("Ancião")
}

@Serializable
enum class BrotherStatus(val label: String) {
    UNBAPTIZED("Publicador não batizado"),
    BAPTIZED("Publicador")
}

@Serializable
enum class PartKind { INDIVIDUAL, PAIR, DEMONSTRATION, GROUP }

/**
 * Qual habilitação do irmão concede este privilégio.
 *
 * Antes a concessão vinha de casar o *nome* do privilégio ("leitor" e "sentinela"
 * no texto), o que quebrava em silêncio se alguém renomeasse o privilégio. O
 * ganho é dado, não adivinhação.
 */
@Serializable
enum class ReaderGrant { NONE, BOOK, SENTINEL }

@Serializable
enum class Gender(val label: String) {
    MALE("Irmão"),
    FEMALE("Irmã")
}

@Serializable
enum class ThemeMode(val label: String) {
    SYSTEM("Padrão do Sistema"),
    LIGHT("Claro"),
    DARK("Escuro")
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
    val unavailabilities: List<UnavailablePeriod> = emptyList(),
    val gender: Gender = Gender.MALE,
    val groupId: Long? = null,
    val baptized: Boolean = true,
    /** Aprendiz: ainda não designado sozinho. */
    val trainee: Boolean = false,
    val isReader: Boolean = false,          // leitor no salão
    val isSentinelReader: Boolean = false   // leitor de A Sentinela
)

@Serializable
data class Privilege(
    val id: Long,
    val name: String,
    val quantity: Int = 1,
    val active: Boolean = true,
    val allowedDays: Set<Int> = emptySet(),
    val minRole: BrotherRole = BrotherRole.PUBLISHER,
    val maleOnly: Boolean = true,
    val kind: PartKind = PartKind.INDIVIDUAL,
    /** Quem pode fazer esta parte. Vazio = qualquer um. */
    val allowedStatus: Set<BrotherStatus> = BrotherStatus.entries.toSet(),
    /** Habilitação do irmão que concede este privilégio. NONE = só pelo conjunto `privileges`. */
    val readerGrant: ReaderGrant = ReaderGrant.NONE
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
    val blockedBrotherIds: Set<Long> = emptySet(),
    /** Leitura do dia da semana ("JEREMIAS 40-41"), preenchida pelo import do jw.org. */
    val theme: String = "",
    /** Itens do programa oficial, na ordem ("1. Joias espirituais", ...). */
    val program: List<ProgramItem> = emptyList()
)

@Serializable
data class ProgramItem(
    val section: String = "",
    val number: Int = 0,
    val title: String,
    val minutes: Int = 0
) {
    val label: String get() = if (minutes > 0) "$title ($minutes min)" else title
}

/**
 * Reconstrói um [ProgramItem] a partir do formato legado de string
 * ("N. Título (M min)"), usado antes de o programa virar uma lista de objetos.
 * Devolve null quando a string não segue o padrão numerado.
 */
fun parseLegacyProgramItem(text: String): ProgramItem? {
    val trimmed = text.trim()
    val numberMatch = Regex("""^(\d{1,2})\.\s+(.*)$""").matchEntire(trimmed) ?: return null
    val number = numberMatch.groupValues[1].toIntOrNull() ?: return null
    val rest = numberMatch.groupValues[2].trim()
    val minutesMatch = Regex("""^(.*?)\s*\((\d{1,3})\s*min[^)]*\)$""", RegexOption.IGNORE_CASE)
        .matchEntire(rest)
    val (title, minutes) = if (minutesMatch != null) {
        minutesMatch.groupValues[1].trim().trimEnd('.') to (minutesMatch.groupValues[2].toIntOrNull() ?: 0)
    } else {
        rest.trimEnd('.') to 0
    }
    return ProgramItem(section = "", number = number, title = title, minutes = minutes)
}

@Serializable
data class MeetingSchedule(
    val firstDay: Int = 3,
    val secondDay: Int = 6
)

@Serializable
data class PublicTalk(
    val id: Long = 0L,
    val meetingId: Long? = null,
    val date: String = "", // dd/MM/yyyy
    val themeNumber: Int? = null,
    val themeTitle: String = "",
    val speakerName: String = "",
    val speakerCongregation: String = "",
    val speakerPhone: String = "",
    val hospitalityBrotherId: Long? = null,
    val hospitalityNotes: String = "",
    val confirmed: Boolean = false
)

@Serializable
data class FieldServiceGroup(
    val id: Long,
    val number: Int,
    val name: String,
    val overseerBrotherId: Long? = null,
    val assistantBrotherId: Long? = null
)

@Serializable
data class CleaningSchedule(
    val id: Long,
    val weekDate: String, // dd/MM/yyyy
    val groupId: Long? = null,
    val details: String = "",
    val completed: Boolean = false
)

@Serializable
data class Store(
    val brothers: List<Brother> = emptyList(),
    val privileges: List<Privilege> = emptyList(),
    val meetings: List<Meeting> = emptyList(),
    val firstDay: Int = 3,
    val secondDay: Int = 6,
    val whatsappSingleTemplate: String = "",
    val whatsappMeetingTemplate: String = "",
    val publicTalks: List<PublicTalk> = emptyList(),
    val fieldServiceGroups: List<FieldServiceGroup> = emptyList(),
    val cleaningSchedules: List<CleaningSchedule> = emptyList(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)
