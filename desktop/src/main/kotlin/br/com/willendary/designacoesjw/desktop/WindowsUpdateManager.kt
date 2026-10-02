package br.com.willendary.designacoesjw.desktop

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import kotlin.concurrent.thread

const val CURRENT_VERSION = "0.4.1"
private const val GITHUB_RELEASES_URL = "https://api.github.com/repos/willendary/designacoes-jw/releases?per_page=10"

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
private data class GitHubAsset(
    val name: String = "",
    val size: Long = 0L,
    @SerialName("browser_download_url") val browserDownloadUrl: String = ""
)

data class WindowsUpdateInfo(
    val version: String,
    val fileName: String,
    val downloadUrl: String,
    val releasePageUrl: String,
    val sizeBytes: Long = 0L
)

sealed interface UpdateState {
    object Idle : UpdateState
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val percent: Int
    ) : UpdateState
    object Installing : UpdateState
    data class Error(val message: String) : UpdateState
}

object WindowsUpdateManager {
    private val json = Json { ignoreUnknownKeys = true }

    fun checkForUpdate(): WindowsUpdateInfo? = runCatching {
        val connection = (URI(GITHUB_RELEASES_URL).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Designacoes-JW-Windows/$CURRENT_VERSION")
        }

        try {
            if (connection.responseCode !in 200..299) return null
            val releases = json.decodeFromString<List<GitHubRelease>>(connection.inputStream.bufferedReader().readText())
            val release = releases.firstOrNull { release ->
                val tag = release.tagName.removePrefix("v").trim()
                tag.isNotBlank() &&
                    isNewer(tag, CURRENT_VERSION) &&
                    release.assets.any { asset ->
                        (asset.name.endsWith(".exe", ignoreCase = true) || asset.name.endsWith(".msi", ignoreCase = true)) &&
                            asset.browserDownloadUrl.isNotBlank()
                    }
            } ?: return null

            val version = release.tagName.removePrefix("v").trim()
            // Prioriza o instalador .exe, se não houver pega o .msi
            val asset = release.assets.firstOrNull {
                it.name.endsWith(".exe", ignoreCase = true) && it.browserDownloadUrl.isNotBlank()
            } ?: release.assets.first {
                it.name.endsWith(".msi", ignoreCase = true) && it.browserDownloadUrl.isNotBlank()
            }

            WindowsUpdateInfo(
                version = version,
                fileName = asset.name,
                downloadUrl = asset.browserDownloadUrl,
                releasePageUrl = release.htmlUrl.ifBlank { "https://github.com/willendary/designacoes-jw/releases" },
                sizeBytes = asset.size
            )
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    fun downloadAndInstall(
        info: WindowsUpdateInfo,
        onProgress: (bytesDownloaded: Long, totalBytes: Long, percent: Int) -> Unit,
        onInstalling: () -> Unit,
        onError: (String) -> Unit
    ) {
        thread(isDaemon = true, name = "designacoes-jw-updater") {
            try {
                val updatesDir = File(System.getProperty("user.home"), ".designacoes-jw/updates")
                updatesDir.mkdirs()

                val targetFileName = info.fileName.ifBlank { "DesignacoesJW-${info.version}.exe" }
                val targetFile = File(updatesDir, targetFileName)

                downloadFileWithRedirects(
                    initialUrl = info.downloadUrl,
                    destination = targetFile,
                    expectedSize = info.sizeBytes,
                    onProgress = onProgress
                )

                onInstalling()
                launchInstallerAndExit(targetFile)
            } catch (e: Throwable) {
                val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                onError(msg)
            }
        }
    }

    private fun downloadFileWithRedirects(
        initialUrl: String,
        destination: File,
        expectedSize: Long,
        onProgress: (bytesDownloaded: Long, totalBytes: Long, percent: Int) -> Unit
    ) {
        var currentUrl = initialUrl
        var redirects = 0
        val maxRedirects = 10
        var finalConnection: HttpURLConnection? = null

        while (redirects < maxRedirects) {
            val url = URI(currentUrl).toURL()
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 45000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "Designacoes-JW-Windows/$CURRENT_VERSION")
            }

            val code = conn.responseCode
            if (code in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, HttpURLConnection.HTTP_SEE_OTHER, 307, 308)) {
                val location = conn.getHeaderField("Location")
                    ?: throw IllegalStateException("Redirecionamento HTTP $code sem cabeçalho Location.")
                conn.disconnect()
                currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                    location
                } else {
                    URI(currentUrl).resolve(location).toString()
                }
                redirects++
                continue
            }

            if (code !in 200..299) {
                conn.disconnect()
                throw IllegalStateException("Servidor de download retornou HTTP $code: ${conn.responseMessage}")
            }

            finalConnection = conn
            break
        }

        val activeConn = finalConnection
            ?: throw IllegalStateException("Excedido limite de redirecionamentos ao baixar atualização.")

        try {
            val headerLength = activeConn.contentLengthLong
            val totalBytes = if (headerLength > 0) headerLength else expectedSize

            val tempFile = File(destination.parentFile, destination.name + ".part")
            if (tempFile.exists()) tempFile.delete()

            var downloaded = 0L
            val buffer = ByteArray(64 * 1024)
            var lastPercent = -1

            activeConn.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        val percent = if (totalBytes > 0) {
                            ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else 0

                        if (percent != lastPercent || downloaded == totalBytes) {
                            lastPercent = percent
                            onProgress(downloaded, totalBytes, percent)
                        }
                    }
                    output.flush()
                }
            }

            if (downloaded < 100 * 1024) {
                tempFile.delete()
                throw IllegalStateException("Arquivo baixado parece incompleto ou corrompido (${downloaded / 1024} KB).")
            }

            if (destination.exists()) destination.delete()
            if (!tempFile.renameTo(destination)) {
                Files.move(tempFile.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            activeConn.disconnect()
        }
    }

    private fun launchInstallerAndExit(installerFile: File) {
        val path = installerFile.absolutePath

        val started = runCatching {
            if (installerFile.extension.equals("msi", ignoreCase = true)) {
                ProcessBuilder("msiexec.exe", "/i", path).start()
            } else {
                ProcessBuilder("explorer.exe", path).start()
            }
        }.isSuccess

        if (!started) {
            runCatching {
                ProcessBuilder("cmd.exe", "/c", "start", "\"\"", "\"$path\"").start()
            }
        }

        Thread.sleep(1500)
        System.exit(0)
    }

    fun openInBrowser(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
            } else {
                ProcessBuilder("cmd.exe", "/c", "start", "\"\"", url).start()
            }
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb < 1.0) {
            "${bytes / 1024} KB"
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }

    private fun isNewer(remote: String, current: String): Boolean {
        val a = remote.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val b = current.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
