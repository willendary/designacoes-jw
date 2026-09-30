package br.com.willendary.designacoesjw.desktop.firebase

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Desktop
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * GoogleDesktopAuth implementa o fluxo OAuth 2.0 Authorization Code com PKCE
 * para login com Google no Desktop (Compose for Desktop / JVM).
 *
 * Fluxo:
 *  1. Gera code_verifier + code_challenge (PKCE S256)
 *  2. Abre o navegador com a URL de autorização do Google
 *  3. Sobe um servidor HTTP local na porta 8181 para receber o callback
 *  4. Troca o authorization_code pelo id_token + access_token (Google Token endpoint)
 *  5. Autentica no Firebase com o id_token Google (REST Identity Toolkit)
 *  6. Retorna AuthSession persistida
 */
object GoogleDesktopAuth {

    // Web OAuth Client ID (tipo 3 no google-services.json)
    private const val GOOGLE_CLIENT_ID =
        "859002390487-u74nnqf0f8pg5css83tirh7ibj4ucts9.apps.googleusercontent.com"

    // Client Secret correspondente ao Web Client ID
    // IMPORTANTE: Para apps "instalados" (desktop), o Google aceita um secret pois
    // o Client ID tipo "Web" do Firebase Console não é confidencial desta forma —
    // porém, se o projeto não tiver secret configurado, o flow "installed app" pode
    // ser necessário. Aqui usamos string vazia e o flow PKCE que não exige secret.
    private const val GOOGLE_CLIENT_SECRET = "" // PKCE flow — sem secret necessário

    private const val REDIRECT_URI = "http://localhost:8181/callback"
    private const val SCOPE = "openid email profile"
    private const val CALLBACK_PORT = 8181
    private const val TIMEOUT_SECONDS = 300L // 5 minutos para o usuário autorizar

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class GoogleTokenResponse(
        @SerialName("id_token") val idToken: String = "",
        @SerialName("access_token") val accessToken: String = "",
        @SerialName("refresh_token") val refreshToken: String = "",
        @SerialName("expires_in") val expiresIn: Int = 3600,
        val scope: String = "",
        @SerialName("token_type") val tokenType: String = ""
    )

    @Serializable
    private data class FirebaseGoogleSignInResponse(
        val idToken: String = "",
        val email: String = "",
        val refreshToken: String = "",
        val expiresIn: String = "3600",
        val localId: String = ""
    )

    data class GoogleAuthResult(
        val session: AuthSession? = null,
        val error: String? = null
    )

    /**
     * Inicia o fluxo OAuth do Google.
     * Chame em uma thread de background (não na Main thread do Compose).
     * Retorna [GoogleAuthResult] com a sessão autenticada ou mensagem de erro.
     */
    fun signInWithGoogle(): GoogleAuthResult {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            return GoogleAuthResult(error = "Seu sistema não suporta abertura de navegador.")
        }

        // 1. Gerar PKCE code_verifier e code_challenge
        val (verifier, challenge) = generatePkce()
        val state = generateState()

        // 2. Construir URL de autorização
        val authUrl = buildAuthUrl(challenge, state)

        // 3. Iniciar servidor HTTP local para receber callback
        var authCode: String? = null
        var callbackState: String? = null
        var serverError: String? = null
        val latch = CountDownLatch(1)

        val server = try {
            HttpServer.create(InetSocketAddress(CALLBACK_PORT), 0)
        } catch (e: Exception) {
            return GoogleAuthResult(error = "Não foi possível iniciar o servidor de callback (porta $CALLBACK_PORT em uso?): ${e.message}")
        }

        server.createContext("/callback") { exchange ->
            try {
                val query = exchange.requestURI.query ?: ""
                val params = parseQuery(query)
                authCode = params["code"]
                callbackState = params["state"]
                serverError = params["error"]

                val htmlResponse = if (authCode != null) {
                    successHtml()
                } else {
                    errorHtml(serverError ?: "Autorização cancelada ou código não recebido.")
                }

                val bytes = htmlResponse.toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "text/html; charset=UTF-8")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } finally {
                latch.countDown()
            }
        }
        server.start()

        // 4. Abrir o navegador
        try {
            Desktop.getDesktop().browse(URI(authUrl))
        } catch (e: Exception) {
            server.stop(0)
            return GoogleAuthResult(error = "Não foi possível abrir o navegador: ${e.message}")
        }

        // 5. Aguardar o callback (timeout de 5 min)
        val received = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        server.stop(0)

        if (!received) {
            return GoogleAuthResult(error = "Tempo esgotado aguardando autorização do Google. Tente novamente.")
        }
        if (serverError != null) {
            return GoogleAuthResult(error = "Google recusou o acesso: $serverError")
        }
        if (authCode == null) {
            return GoogleAuthResult(error = "Código de autorização não recebido.")
        }
        if (callbackState != state) {
            return GoogleAuthResult(error = "Falha de segurança: state inválido. Tente novamente.")
        }

        // 6. Trocar código pelo id_token via Google Token Endpoint
        val googleToken = exchangeCodeForToken(authCode!!, verifier)
            ?: return GoogleAuthResult(error = "Falha ao obter token do Google. Verifique sua conexão.")

        if (googleToken.idToken.isBlank()) {
            return GoogleAuthResult(error = "id_token do Google vazio. Configure 'openid' no escopo do OAuth.")
        }

        // 7. Autenticar no Firebase com o id_token do Google
        return signInWithFirebaseGoogle(googleToken.idToken)
    }

    // ── PKCE ──────────────────────────────────────────────────────────────────

    private fun generatePkce(): Pair<String, String> {
        val bytes = ByteArray(64).also { SecureRandom().nextBytes(it) }
        val verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        return verifier to challenge
    }

    private fun generateState(): String {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    // ── URL de Autorização ───────────────────────────────────────────────────

    private fun buildAuthUrl(codeChallenge: String, state: String): String {
        val params = mapOf(
            "client_id" to GOOGLE_CLIENT_ID,
            "redirect_uri" to REDIRECT_URI,
            "response_type" to "code",
            "scope" to SCOPE,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256",
            "state" to state,
            "access_type" to "offline",
            "prompt" to "select_account"
        )
        val query = params.entries.joinToString("&") { (k, v) -> "$k=${encode(v)}" }
        return "https://accounts.google.com/o/oauth2/v2/auth?$query"
    }

    // ── Troca de Código por Token ─────────────────────────────────────────────

    private fun exchangeCodeForToken(code: String, codeVerifier: String): GoogleTokenResponse? {
        return runCatching {
            val url = URI("https://oauth2.googleapis.com/token").toURL()
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                connectTimeout = 15000
                readTimeout = 15000
            }

            val body = buildString {
                append("code=").append(encode(code))
                append("&client_id=").append(encode(GOOGLE_CLIENT_ID))
                if (GOOGLE_CLIENT_SECRET.isNotBlank()) {
                    append("&client_secret=").append(encode(GOOGLE_CLIENT_SECRET))
                }
                append("&redirect_uri=").append(encode(REDIRECT_URI))
                append("&grant_type=authorization_code")
                append("&code_verifier=").append(encode(codeVerifier))
            }

            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }

            if (conn.responseCode !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                throw RuntimeException("Google token error (${conn.responseCode}): $err")
            }

            val resp = conn.inputStream.bufferedReader().readText()
            json.decodeFromString<GoogleTokenResponse>(resp)
        }.getOrNull()
    }

    // ── Autenticação no Firebase ──────────────────────────────────────────────

    private fun signInWithFirebaseGoogle(googleIdToken: String): GoogleAuthResult {
        return runCatching {
            val url = URI("https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=$FIREBASE_API_KEY").toURL()
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connectTimeout = 15000
                readTimeout = 15000
            }

            val payload = """
                {
                  "postBody": "id_token=${googleIdToken}&providerId=google.com",
                  "requestUri": "http://localhost",
                  "returnIdpCredential": true,
                  "returnSecureToken": true
                }
            """.trimIndent()

            conn.outputStream.use { it.write(payload.toByteArray(StandardCharsets.UTF_8)) }

            if (conn.responseCode !in 200..299) {
                val errBody = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                val msg = parseFirebaseError(errBody)
                return GoogleAuthResult(error = msg)
            }

            val respBody = conn.inputStream.bufferedReader().readText()
            val parsed = json.decodeFromString<FirebaseGoogleSignInResponse>(respBody)
            val durationSec = parsed.expiresIn.toLongOrNull() ?: 3600L

            val session = AuthSession(
                idToken = parsed.idToken,
                email = parsed.email,
                refreshToken = parsed.refreshToken,
                localId = parsed.localId,
                expiresAt = System.currentTimeMillis() + (durationSec * 1000L)
            )

            GoogleAuthResult(session = session)
        }.getOrElse { e ->
            GoogleAuthResult(error = "Erro ao autenticar no Firebase: ${e.message}")
        }
    }

    // ── Utilitários ───────────────────────────────────────────────────────────

    private fun parseQuery(query: String): Map<String, String> =
        query.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) null
            else pair.substring(0, idx) to java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
        }.toMap()

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private fun parseFirebaseError(jsonStr: String): String = runCatching {
        val elem = json.parseToJsonElement(jsonStr) as? JsonObject
        val errorObj = elem?.get("error") as? JsonObject
        val message = errorObj?.get("message")?.jsonPrimitive?.content ?: ""
        when {
            message.contains("INVALID_IDP_RESPONSE") -> "Resposta inválida do Google. Tente novamente."
            message.contains("EMAIL_EXISTS") -> "Este e-mail já está vinculado a outra conta."
            message.contains("OPERATION_NOT_ALLOWED") -> "Login com Google não está ativado no Firebase Console."
            message.isNotBlank() -> "Erro Firebase: $message"
            else -> "Falha na autenticação Firebase."
        }
    }.getOrDefault("Falha na autenticação.")

    // ── Páginas HTML de Retorno ───────────────────────────────────────────────

    private fun successHtml() = """
        <!DOCTYPE html>
        <html lang="pt-BR">
        <head>
          <meta charset="UTF-8">
          <title>Login realizado!</title>
          <style>
            body { font-family: sans-serif; display: flex; align-items: center; justify-content: center;
                   min-height: 100vh; margin: 0; background: #f0f4ff; }
            .card { background: white; border-radius: 16px; padding: 40px 48px; text-align: center;
                    box-shadow: 0 4px 24px rgba(0,0,0,.12); }
            h1 { color: #1565C0; margin-bottom: 8px; }
            p { color: #555; }
            .icon { font-size: 48px; margin-bottom: 16px; }
          </style>
        </head>
        <body>
          <div class="card">
            <div class="icon">✅</div>
            <h1>Login realizado com sucesso!</h1>
            <p>Você pode fechar esta janela e voltar ao <strong>Designações JW</strong>.</p>
          </div>
        </body>
        </html>
    """.trimIndent()

    private fun errorHtml(error: String) = """
        <!DOCTYPE html>
        <html lang="pt-BR">
        <head>
          <meta charset="UTF-8">
          <title>Erro no login</title>
          <style>
            body { font-family: sans-serif; display: flex; align-items: center; justify-content: center;
                   min-height: 100vh; margin: 0; background: #fff0f0; }
            .card { background: white; border-radius: 16px; padding: 40px 48px; text-align: center;
                    box-shadow: 0 4px 24px rgba(0,0,0,.12); }
            h1 { color: #c62828; }
            p { color: #555; }
            .icon { font-size: 48px; margin-bottom: 16px; }
          </style>
        </head>
        <body>
          <div class="card">
            <div class="icon">❌</div>
            <h1>Erro ao fazer login</h1>
            <p>${error}</p>
            <p>Feche esta janela e tente novamente no aplicativo.</p>
          </div>
        </body>
        </html>
    """.trimIndent()
}
