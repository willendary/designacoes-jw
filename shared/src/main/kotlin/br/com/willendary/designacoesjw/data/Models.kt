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

/**
 * Quem faz uma parte do programa da semana.
 *
 * **Não é um [Assignment].** Um `Assignment` aponta para um [Privilege]
 * cadastrado — que é o privilégio mecânico, o mesmo toda semana (Som,
 * Anunciante, Orações, Leitor de A Sentinela). A parte do programa muda toda
 * semana, vem do jw.org e não se cadastra. Misturar as duas em uma entidade
 * só obrigava a tratar "Indicação" como se fosse "Som": com dono natural,
 * exigido por dia da semana e restrito por sexo.
 *
 * A identidade do item é a **posição** na lista do programa, 1-based, e não o
 * número oficial: o jw.org numera de 1 a 9 mas deixa itens avulsos sem
 * número, e o número pode mudar entre a preview e a semana publicada.
 */
@Serializable
data class ProgramAssignment(
    val item: Int,
    /** Uma parte pode ter várias pessoas — é o caso da encenação. */
    val brotherIds: List<Long> = emptyList()
)

@Serializable
data class Meeting(
    val id: Long,
    val date: String,
    /**
     * Tipo do encontro, em texto.
     *
     * Segue `String` por compatibilidade com os dados já gravados, mas **não se
     * compara por igualdade**: use [isMidweek] ou [tipoReuniao]. Havia duas
     * grafias em produção ("Reunião de Meio de Semana" do import do jw.org e
     * "Reunião do meio de semana" do gerador), e o app distinguia os dois tipos
     * por `contains("meio de semana", ignoreCase = true)` espalhado por quatro
     * arquivos — funcionando por acaso, porque só a capitalização diferia.
     *
     * A leitura normaliza para a grafia de [MeetingType.label], então as duas
     * nunca mais voltam a nascer.
     */
    val type: String,
    /** Só para privilégio **mecânico**. */
    val assignments: List<Assignment> = emptyList(),
    val blockedBrotherIds: Set<Long> = emptySet(),
    /** Leitura do dia da semana ("JEREMIAS 40-41"), preenchida pelo import do jw.org. */
    val theme: String = "",
    /** Itens do programa oficial, na ordem ("1. Joias espirituais", ...). */
    val program: List<ProgramItem> = emptyList(),
    /** Quem faz cada parte do programa. Lado a lado de [assignments], e
     *  deliberadamente separado: um é a parte que muda, o outro é o cargo. */
    val programAssignments: List<ProgramAssignment> = emptyList()
) {
    /** O tipo do encontro, já normalizado. */
    val tipoReuniao: MeetingType get() = MeetingType.parse(type)

    /** Meeting de meio de semana. Use isto, não `type.contains(...)`. */
    val isMidweek: Boolean get() = tipoReuniao == MeetingType.MIDWEEK

    /** A grafia canônica — é o que deve ser gravado e exibido. */
    val typeCanonical: String get() = tipoReuniao.label
}

/**
 * Os dois tipos de reunião.
 *
 * Só existem dois, e o app trava a diferença em vários lugares — por isso
 * virar enum foi tentado e é o caminho certo no modelo **novo**. Aqui o
 * propósito é outro: dar **um** lugar onde a string vira tipo, para que
 * ninguém mais espalhe `contains("meio de semana")` e dependa da
 * capitalização (#48).
 */
enum class MeetingType(val label: String) {
    MIDWEEK("Reunião de Meio de Semana"),
    WEEKEND("Reunião de Fim de Semana");

    companion object {
        /**
         * Reconhece as grafias antigas.
         *
         * Qualquer texto que **não** diga "fim de semana" é reunião de meio de
         * semana. É o mesmo critério que o `contains` espalhado fazia, e é
         * deliberado: um tipo desconhecido não pode virar "fim de semana" e
         * sumir da geração.
         */
        fun parse(raw: String): MeetingType =
            if (raw.contains("fim de semana", ignoreCase = true)) WEEKEND else MIDWEEK
    }
}

@Serializable
data class ProgramItem(
    val section: String = "",
    val number: Int = 0,
    val title: String,
    val minutes: Int = 0,
    /**
     * Tipo da parte. Pertence à parte, não a um privilégio cadastrado: quem
     * define isto é o programa da semana, e o app usa para agrupar na tela e
     * para lembrar quantas pessoas a parte leva.
     */
    val kind: PartKind = PartKind.INDIVIDUAL
) {
    val label: String get() = if (minutes > 0) "$title ($minutes min)" else title

    /** A posição 1-based deste item, dentro da lista do programa da semana. */
    fun positionIn(program: List<ProgramItem>): Int = program.indexOf(this) + 1
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
