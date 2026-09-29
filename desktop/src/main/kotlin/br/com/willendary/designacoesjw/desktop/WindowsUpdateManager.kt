package br.com.willendary.designacoesjw.desktop

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.concurrent.thread

private const val CURRENT_VERSION = "0.1.6"
private const val GITHUB_RELEASES_URL = "https://api.github.com/repos/willendary/designacoes-jw/releases/latest"

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
private data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = ""
)

data class WindowsUpdateInfo(
    val version: String,
    val downloadUrl: String
)

object WindowsUpdateManager {
    private val json = Json { ignoreUnknownKeys = true }

    fun checkForUpdate(): WindowsUpdateInfo? = runCatching {
        val connection = (URI(GITHUB_RELEASES_URL).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Designacoes-JW-Windows/$CURRENT_VERSION")
        }

        connection.use {
            if (it.responseCode !in 200..299) return null
            val release = json.decodeFromString<GitHubRelease>(it.inputStream.bufferedReader().readText())
            val version = release.tagName.removePrefix("v").trim()
            val asset = release.assets.firstOrNull {
                it.name.endsWith(".exe", ignoreCase = true) &&
                    it.browserDownloadUrl.isNotBlank()
            } ?: return null

            if (!isNewer(version, CURRENT_VERSION)) null
            else WindowsUpdateInfo(version, asset.browserDownloadUrl)
        }
    }.getOrNull()

    fun downloadAndInstall(info: WindowsUpdateInfo) {
        thread(isDaemon = true, name = "designacoes-jw-updater") {
            runCatching {
                val currentExe = currentExecutable() ?: return@runCatching
                val currentFile = File(currentExe)
                val newFile = File(currentFile.parentFile, currentFile.nameWithoutExtension + ".new.exe")
                download(info.downloadUrl, newFile)

                if (newFile.length() < 1024) {
                    newFile.delete()
                    return@runCatching
                }

                val script = File.createTempFile("designacoes-jw-update-", ".ps1")
                script.writeText(buildPowerShellScript(
                    ProcessHandle.current().pid(),
                    currentFile.absolutePath,
                    newFile.absolutePath,
                    script.absolutePath
                ))

                ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-WindowStyle", "Hidden",
                    "-File", script.absolutePath
                ).start()

                System.exit(0)
            }
        }
    }

    private fun currentExecutable(): String? {
        val command = ProcessHandle.current().info().command().orElse(null) ?: return null
        if (!command.endsWith(".exe", ignoreCase = true)) return null
        return command
    }

    private fun download(url: String, destination: File) {
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 60000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "Designacoes-JW-Windows/$CURRENT_VERSION")
        }

        connection.use {
            if (it.responseCode !in 200..299) error("Download HTTP ${it.responseCode}")
            destination.parentFile?.mkdirs()
            it.inputStream.use { input ->
                Files.copy(input, destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun buildPowerShellScript(pid: Long, oldPath: String, newPath: String, scriptPath: String): String {
        fun ps(value: String) = "'" + value.replace("'", "''") + "'"

        return """
            $ErrorActionPreference = 'SilentlyContinue'
            $pidToWait = $pid
            $old = ${ps(oldPath)}
            $new = ${ps(newPath)}
            $script = ${ps(scriptPath)}

            try {
                Wait-Process -Id $pidToWait -Timeout 120
            } catch {}

            Start-Sleep -Milliseconds 1000

            for ($i = 0; $i -lt 10; $i++) {
                try {
                    Copy-Item -LiteralPath $new -Destination $old -Force -ErrorAction Stop
                    Start-Process -FilePath $old
                    Remove-Item -LiteralPath $new -Force
                    Remove-Item -LiteralPath $script -Force
                    exit 0
                } catch {
                    Start-Sleep -Seconds 1
                }
            }

            Start-Process -FilePath $old
            Remove-Item -LiteralPath $script -Force
        """.trimIndent()
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
