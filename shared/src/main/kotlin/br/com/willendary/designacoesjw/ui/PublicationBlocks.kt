package br.com.willendary.designacoesjw.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import br.com.willendary.designacoesjw.data.ProgramBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

@Composable
internal fun publicationAccent(section: String): Color {
    val dark = MaterialTheme.colorScheme.background.let { it.red + it.green + it.blue < 1.5f }
    return when {
        section.contains("tesouros", true) -> if (dark) Color(0xFF8BC9C5) else Color(0xFF28615E)
        section.contains("ministério", true) -> if (dark) Color(0xFFE3C481) else Color(0xFF805617)
        section.contains("vida", true) -> if (dark) Color(0xFFE7ABB8) else Color(0xFF853E50)
        else -> MaterialTheme.colorScheme.onSurface
    }
}

@Composable
internal fun PublicationSection(title: String) {
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = publicationAccent(title), thickness = 2.dp)
        Text(title.uppercase(), style = MaterialTheme.typography.titleSmall,
            color = publicationAccent(title), fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun PublicationLink(label: String, url: String) {
    val handler = LocalUriHandler.current
    var failed by remember(url) { mutableStateOf(false) }
    TextButton(onClick = { failed = runCatching { handler.openUri(url) }.isFailure }) { Text(label) }
    if (failed) Text("Não foi possível abrir o link: $url", style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun PublicationBlocks(blocks: List<ProgramBlock>, decodeImage: ((ByteArray) -> ImageBitmap?)?) {
    blocks.forEach { block ->
        when (block.type) {
            "section" -> PublicationSection(block.text)
            "image" -> PublicationImage(block, decodeImage)
            else -> PublicationText(block)
        }
    }
}

@Composable
private fun PublicationText(block: ProgramBlock) {
    val handler = LocalUriHandler.current
    val colors = MaterialTheme.colorScheme
    var failed by remember(block) { mutableStateOf(false) }
    val text = buildAnnotatedString {
        if (block.type == "bullet") append("•  ")
        block.runs.forEach { run ->
            val webLink = run.url.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            if (webLink != null) pushStringAnnotation("URL", webLink)
            withStyle(SpanStyle(
                fontWeight = if (run.bold || block.type == "heading") FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (run.italic || block.type == "caption") FontStyle.Italic else FontStyle.Normal,
                color = if (webLink != null) colors.primary else colors.onSurface,
                textDecoration = if (webLink != null) TextDecoration.Underline else TextDecoration.None
            )) { append(run.text) }
            if (webLink != null) pop()
        }
    }
    @Suppress("DEPRECATION")
    ClickableText(text, modifier = Modifier.fillMaxWidth().padding(
        start = if (block.type == "bullet") 8.dp else 0.dp,
        top = if (block.type == "heading") 8.dp else 0.dp),
        style = if (block.type == "caption") MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
        onClick = { offset ->
            text.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                failed = runCatching { handler.openUri(it.item) }.isFailure
            }
        })
    if (failed) Text("Não foi possível abrir a referência. Use a publicação original.",
        color = colors.error, style = MaterialTheme.typography.bodySmall)
}

/** O decoder é fornecido pela plataforma: o APK nunca referencia classes de imagem do desktop. */
@Composable
private fun PublicationImage(block: ProgramBlock, decoder: ((ByteArray) -> ImageBitmap?)?) {
    var complete by remember(block.imageUrl) { mutableStateOf(false) }
    val bitmap by produceState<ImageBitmap?>(null, block.imageUrl, decoder) {
        complete = false
        value = if (decoder == null) null else withContext(Dispatchers.IO) {
            runCatching { downloadPublicationImage(block.imageUrl)?.let(decoder) }.getOrNull()
        }
        complete = true
    }
    val image = bitmap
    if (image != null) Image(image, block.imageDescription.ifBlank { "Ilustração da publicação" },
        Modifier.fillMaxWidth().aspectRatio(image.width.toFloat() / image.height.coerceAtLeast(1)))
    else {
        Text(if (!complete) "Carregando ilustração…" else block.imageDescription.ifBlank { "Ilustração indisponível." },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (complete) PublicationLink("Abrir ilustração", block.imageUrl)
    }
}

private val imageCache = object : LinkedHashMap<String, ByteArray>(8, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean = size > 8
}

private fun downloadPublicationImage(url: String): ByteArray? {
    synchronized(imageCache) { imageCache[url]?.let { return it } }
    val uri = URI(url)
    if (uri.scheme !in listOf("https", "http")) return null
    val connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000
        readTimeout = 15000
    }
    return try {
        if (connection.responseCode !in 200..299) return null
        // Limita o consumo mesmo se o servidor omitir Content-Length.
        val bytes = connection.inputStream.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                if (out.size() + n > 4 * 1024 * 1024) return null
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
        synchronized(imageCache) { imageCache[url] = bytes }
        bytes
    } finally { connection.disconnect() }
}
