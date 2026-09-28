package br.com.willendary.designacoesjw

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import br.com.willendary.designacoesjw.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdate(val versionName: String, val downloadUrl: String, val releaseUrl: String)

object UpdateManager {
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/willendary/designacoes-jw/releases/latest"

    suspend fun check(context: Context): AppUpdate? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            if (connection.responseCode !in 200..299) return@withContext null
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val release = JSONObject(json)
            val remoteVersion = release.optString("tag_name").removePrefix("v").trim()
            if (!isNewer(remoteVersion, BuildConfig.VERSION_NAME)) return@withContext null
            val assets = release.optJSONArray("assets") ?: return@withContext null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    return@withContext AppUpdate(remoteVersion, asset.optString("browser_download_url"), release.optString("html_url"))
                }
            }
            null
        } catch (_: Exception) { null }
    }

    suspend fun downloadAndInstall(context: Context, update: AppUpdate): Boolean = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "designacoes-jw-" + update.versionName + ".apk")
            if (!apkFile.exists()) {
                val connection = URL(update.downloadUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = true
                connection.connect()
                if (connection.responseCode !in 200..299) return@withContext false
                connection.inputStream.use { input -> apkFile.outputStream().use { output -> input.copyTo(output) } }
                connection.disconnect()
            }
            withContext(Dispatchers.Main) {
                val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", apkFile)
                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            }
            true
        } catch (_: Exception) { false }
    }

    private fun isNewer(remote: String, current: String): Boolean {
        val r = remote.split(".").mapNotNull { it.toIntOrNull() }
        val c = current.split(".").mapNotNull { it.toIntOrNull() }
        val size = maxOf(r.size, c.size)
        for (i in 0 until size) {
            val rv = r.getOrElse(i) { 0 }; val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }
}