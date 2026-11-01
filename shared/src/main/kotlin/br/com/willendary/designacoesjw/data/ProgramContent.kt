package br.com.willendary.designacoesjw.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Conteúdo editorial separado da identidade/posição das partes designáveis. */
@Serializable
data class ProgramContent(
    val before: List<ProgramBlock> = emptyList(),
    val body: List<ProgramBlock> = emptyList(),
    val sourceUrl: String = ""
)

@Serializable
data class ProgramBlock(
    // String permite ler futuros tipos sem invalidar a reunião inteira.
    val type: String = "paragraph",
    val runs: List<ProgramText> = emptyList(),
    val imageUrl: String = "",
    val imageDescription: String = ""
) {
    val text: String get() = runs.joinToString("") { it.text }
}

@Serializable
data class ProgramText(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val url: String = ""
)

/** Mesmo contrato no cache Android e nas duas implementações do Firestore. */
object ProgramContentCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun encode(content: ProgramContent): String = json.encodeToString(content)
    fun decode(raw: String?): ProgramContent = raw?.takeIf { it.isNotBlank() }?.let {
        runCatching { json.decodeFromString<ProgramContent>(it) }.getOrNull()
    } ?: ProgramContent()
}
