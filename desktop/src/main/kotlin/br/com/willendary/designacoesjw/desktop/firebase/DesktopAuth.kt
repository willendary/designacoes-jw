package br.com.willendary.designacoesjw.desktop.firebase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

const val FIREBASE_API_KEY = "AIzaSyDpLo4zAsQ8Tl4V6MJ-lp5hgnQSaaZvD_0"
const val FIREBASE_PROJECT_ID = "designacoes-jw"

@Serializable
data class AuthSession(
    val idToken: String,
    val email: String,
    val refreshToken: String,
    val localId: String,
    val expiresAt: Long = 0L // Epoch millis
)

@Serializable
private data class SignInResponse(
    val idToken: String = "",
    val email: String = "",
    val refreshToken: String = "",
    val expiresIn: String = "3600",
    val localId: String = ""
)

@Serializable
private data class RefreshTokenResponse(
    @SerialName("id_token") val idToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: String = "3600",
    @SerialName("user_id") val userId: String = ""
)

object DesktopAuthManager {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val sessionFile = File(System.getProperty("user.home"), ".designacoes-jw/auth_session.json")

    var currentSession: AuthSession? = null
        private set

    init {
        loadSession()
    }

    fun loadSession(): AuthSession? {
        val session = runCatching {
            if (sessionFile.exists()) {
                json.decodeFromString<AuthSession>(sessionFile.readText())
            } else null
        }.getOrNull()

        if (session != null) {
            // Se falta menos de 5 minutos para expirar, tenta renovar
            if (System.currentTimeMillis() >= session.expiresAt - (5 * 60 * 1000L)) {
                val renewed = refreshSession(session.refreshToken).getOrNull()
                currentSession = renewed ?: session
            } else {
                currentSession = session
            }
        } else {
            currentSession = null
        }
        return currentSession
    }

    fun signInWithEmail(email: String, pass: String): Result<AuthSession> = runCatching {
        val url = URI("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$FIREBASE_API_KEY").toURL()
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connectTimeout = 10000
            readTimeout = 10000
        }

        val payload = """{"email":${json.encodeToString(email.trim())},"password":${json.encodeToString(pass)},"returnSecureToken":true}"""
        conn.outputStream.use { it.write(payload.toByteArray(StandardCharsets.UTF_8)) }

        val code = conn.responseCode
        if (code !in 200..299) {
            val errBody = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val msg = parseFirebaseError(errBody)
            throw RuntimeException(msg)
        }

        val respBody = conn.inputStream.bufferedReader().use { it.readText() }
        val parsed = json.decodeFromString<SignInResponse>(respBody)
        val durationSec = parsed.expiresIn.toLongOrNull() ?: 3600L
        val session = AuthSession(
            idToken = parsed.idToken,
            email = parsed.email,
            refreshToken = parsed.refreshToken,
            localId = parsed.localId,
            expiresAt = System.currentTimeMillis() + (durationSec * 1000L)
        )
        saveSession(session)
        session
    }

    fun refreshSession(refreshToken: String): Result<AuthSession> = runCatching {
        val url = URI("https://securetoken.googleapis.com/v1/token?key=$FIREBASE_API_KEY").toURL()
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connectTimeout = 8000
            readTimeout = 8000
        }

        val body = "grant_type=refresh_token&refresh_token=$refreshToken"
        conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }

        if (conn.responseCode !in 200..299) {
            throw RuntimeException("Falha ao renovar autenticação.")
        }

        val respBody = conn.inputStream.bufferedReader().use { it.readText() }
        val parsed = json.decodeFromString<RefreshTokenResponse>(respBody)
        val durationSec = parsed.expiresIn.toLongOrNull() ?: 3600L
        val old = currentSession
        val session = AuthSession(
            idToken = parsed.idToken,
            email = old?.email ?: "",
            refreshToken = parsed.refreshToken,
            localId = parsed.userId,
            expiresAt = System.currentTimeMillis() + (durationSec * 1000L)
        )
        saveSession(session)
        session
    }

    fun logout() {
        currentSession = null
        runCatching { sessionFile.delete() }
    }

    fun saveSessionPublic(session: AuthSession) = saveSession(session)

    private fun saveSession(session: AuthSession) {
        currentSession = session
        runCatching {
            sessionFile.parentFile?.mkdirs()
            sessionFile.writeText(json.encodeToString(session))
        }
    }

    private fun parseFirebaseError(jsonStr: String): String = runCatching {
        val elem = json.parseToJsonElement(jsonStr) as? JsonObject
        val errorObj = elem?.get("error") as? JsonObject
        val message = errorObj?.get("message")?.jsonPrimitive?.content ?: ""
        when {
            message.contains("EMAIL_NOT_FOUND") -> "E-mail não cadastrado."
            message.contains("INVALID_PASSWORD") || message.contains("INVALID_LOGIN_CREDENTIALS") -> "E-mail ou senha incorretos."
            message.contains("USER_DISABLED") -> "Esta conta foi desativada."
            message.contains("TOO_MANY_ATTEMPTS_TRY_LATER") -> "Muitas tentativas. Tente novamente mais tarde."
            message.isNotBlank() -> "Erro ao entrar: $message"
            else -> "Não foi possível realizar o login."
        }
    }.getOrDefault("Falha na autenticação.")
}
