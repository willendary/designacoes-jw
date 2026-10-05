package br.com.willendary.designacoesjw

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AppUpdate(val versionName: String, val downloadUrl: String, val releaseUrl: String)

enum class UpdateInstallResult {
    STARTED,
    NEED_PERMISSION,
    /** O arquivo não foi assinado pela chave deste app. Não é atualização nossa. */
    NOT_SIGNED,
    FAILED
}

/**
 * `GET_SIGNING_CERTIFICATES` só existe a partir do Android 9 (API 28), e o
 * `minSdk` do app é 26. Nos dois caminhos a informação é a mesma: o
 * `PackageInfo` traz a assinatura por `signingInfo` ou por `signatures`, e o
 * pedido da flag certa é o que faz o sistema incluir os dados.
 */
private fun flagsDeAssinatura(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        @Suppress("DEPRECATION")
        PackageManager.GET_SIGNATURES
    }

/**
 * O APK baixado foi assinado pela mesma chave do app instalado?
 *
 * Compara os certificados byte a byte com [MessageDigest.isEqual], que é
 * comparação em tempo constante — o costume de segurança em comparar material
 * criptográfico. Passar null é falha: sem certificado dos dois lados não há como
 * afirmar que é a mesma chave, e afirmar seria aceitar qualquer assinatura.
 *
 * O app já rodando é a referência. Um APK reempacotado com outra chave tem
 * `packageName` igual e `versionName` igual — só a assinatura denuncia.
 */
private fun mesmaChaveDeAssinatura(instalado: PackageInfo, baixado: PackageInfo): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val esperadas = instalado.signingInfo?.apkContentsSigners ?: return false
        val obtidas = baixado.signingInfo?.apkContentsSigners ?: return false
        if (esperadas.isEmpty() || obtidas.isEmpty()) return false
        return esperadas.any { esperada ->
            obtidas.any { obtida -> MessageDigest.isEqual(esperada.toByteArray(), obtida.toByteArray()) }
        }
    }

    @Suppress("DEPRECATION")
    val esperadas = instalado.signatures ?: return false
    @Suppress("DEPRECATION")
    val obtidas = baixado.signatures ?: return false
    if (esperadas.isEmpty() || obtidas.isEmpty()) return false
    return esperadas.any { esperada ->
        obtidas.any { obtida -> MessageDigest.isEqual(esperada.toByteArray(), obtida.toByteArray()) }
    }
}

object UpdateManager {
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/willendary/designacoes-jw/releases/latest"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    suspend fun check(context: Context): AppUpdate? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                return@withContext null
            }

            val json = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val release = JSONObject(json)
            val remoteVersion = release.optString("tag_name").removePrefix("v").trim()
            val currentVersion = context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName ?: "0.0.0"

            if (remoteVersion.isBlank() || !isNewer(remoteVersion, currentVersion)) {
                return@withContext null
            }

            val assets = release.optJSONArray("assets") ?: return@withContext null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")

                if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                    return@withContext AppUpdate(
                        remoteVersion,
                        url,
                        release.optString("html_url")
                    )
                }
            }

            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun downloadAndInstall(
        context: Context,
        update: AppUpdate
    ): UpdateInstallResult = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                withContext(Dispatchers.Main) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + context.packageName)
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
                return@withContext UpdateInstallResult.NEED_PERMISSION
            }

            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "designacoes-jw-" + update.versionName + ".apk")

            // Sempre baixa novamente para evitar reutilizar um APK incompleto/corrompido.
            if (apkFile.exists()) apkFile.delete()

            val connection = URL(update.downloadUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 60000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", APK_MIME_TYPE)
            connection.connect()

            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                return@withContext UpdateInstallResult.FAILED
            }

            try {
                connection.inputStream.use { input ->
                    apkFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } finally {
                connection.disconnect()
            }

            if (!apkFile.exists() || apkFile.length() < 1024L) {
                apkFile.delete()
                return@withContext UpdateInstallResult.FAILED
            }

            // Confirma antes de abrir o instalador que o arquivo realmente é um APK
            // desta aplicação, contém a versão esperada e foi assinado pela MESMA
            // chave do app instalado.
            //
            // A assinatura é a parte que importa. O `packageName` e o
            // `versionName` são metadados do próprio arquivo: um repositório
            // comprometido os preenche sem dificuldade, e o Android recusaria a
            // instalação — mas o usuário já teria visto "atualizando" e o APK já
            // teria baixado. Comparar com o certificado do app que está rodando
            // é o que impede o arquivo errado de chegar à tela de instalação.
            val installed = context.packageManager.getPackageInfo(
                context.packageName,
                flagsDeAssinatura()
            )
            val downloaded = context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                flagsDeAssinatura()
            )

            if (downloaded == null || downloaded.packageName != context.packageName) {
                apkFile.delete()
                return@withContext UpdateInstallResult.FAILED
            }

            val downloadedVersion = downloaded.versionName ?: ""
            if (downloadedVersion != update.versionName) {
                apkFile.delete()
                return@withContext UpdateInstallResult.FAILED
            }

            if (!mesmaChaveDeAssinatura(installed, downloaded)) {
                // Não é a minha chave. Apaga na hora: deixar o arquivo aqui seria
                // deixar um APK de outra origem no cache do app.
                apkFile.delete()
                return@withContext UpdateInstallResult.NOT_SIGNED
            }

            withContext(Dispatchers.Main) {
                val uri = FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    apkFile
                )

                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, APK_MIME_TYPE)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                )
            }

            UpdateInstallResult.STARTED
        } catch (_: Exception) {
            UpdateInstallResult.FAILED
        }
    }

    private fun isNewer(remote: String, current: String): Boolean {
        val r = remote.split(".").mapNotNull { it.toIntOrNull() }
        val c = current.split(".").mapNotNull { it.toIntOrNull() }
        val size = maxOf(r.size, c.size)

        for (i in 0 until size) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }

        return false
    }
}
