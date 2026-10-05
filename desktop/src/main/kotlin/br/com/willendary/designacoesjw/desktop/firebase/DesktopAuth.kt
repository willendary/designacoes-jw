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

/**
 * A sessão é gravada cifrada com a **DPAPI do Windows**, a proteção de disco do
 * próprio usuário do Windows.
 *
 * **Por que.** O refresh token é de longa duração: com ele, dá para emitir
 * idTokens novos sem pedir a senha. Em texto claro dentro de
 * `~/.designacoes-jw/auth_session.json`, qualquer programa rodando com a mesma
 * conta lia o token — e também qualquer backup, ou uma pasta sincronizada na
 * nuvem, ou o botão "Abrir pasta dos dados" da tela de Configurações, que abre
 * exatamente a pasta onde ele mora. DPAPI não é um esquema do projeto: quem
 * decifra precisa ser o mesmo usuário, no mesmo Windows.
 *
 * **Por que JNA.** O JDK não expõe `CryptProtectData`.
 * `Cipher.getInstance("Windows-ENCRYPTION")` **não** é um provedor dele — é um
 * provedor do JNA, e usá-lo sem declarar a dependência daria `NoSuchProvider`.
 * São 1,8 MB num app de 93 MB.
 *
 * **Por que não há fallback para texto claro.** Se a DPAPI falhar, a gravação
 * falha e o usuário entra de novo no próximo boot. Isso é melhor do que gravar o
 * token sem proteção e fingir que salvou — e o desktop é Windows
 * (`targetFormats(Exe, Msi)`), então não há caminho legítimo onde a DPAPI não
 * esteja.
 *
 * Arquivos antigos em texto claro continuam sendo lidos: [loadSessionFromDisk]
 * reconhece o cabeçalho do formato novo e, sem ele, assume o antigo, lê e regrava
 * cifrado.
 */
internal object SessionCrypt {

    /** Cabeçalho do arquivo cifrado. Sem ele, é o formato antigo em texto claro. */
    private const val CABECALHO = "DESIGNACOESJW-SESSAO-CIFRADA:"

    fun cifrar(texto: String): String {
        val protegido = com.sun.jna.platform.win32.Crypt32Util.cryptProtectData(
            texto.toByteArray(StandardCharsets.UTF_8)
        )
        return CABECALHO + java.util.Base64.getEncoder().encodeToString(protegido)
    }

    fun decifrar(conteudo: String): String {
        val base64 = conteudo.removePrefix(CABECALHO)
        val bytes = java.util.Base64.getDecoder().decode(base64.trim())
        val desprotegido = com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(bytes)
        return String(desprotegido, StandardCharsets.UTF_8)
    }

    fun pareceCifrado(conteudo: String): Boolean = conteudo.trimStart().startsWith(CABECALHO)
}

object DesktopAuthManager {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * Onde a sessão mora. Apontável só para teste.
     *
     * É a única costura do objeto, e existe por um motivo concreto: a migração do
     * formato antigo para o cifrado é o caminho que faz **quem já usa o app**
     * perder o login se estiver errado, e ela não se prova sozinha. Com o
     * arquivo redirecionável, dá para escrever um `auth_session.json` em texto
     * claro, chamar [loadSessionFromDisk] e conferir que leu **e regravou
     * cifrado** — sem isso a migração éfaith em quem a escreve.
     */
    internal var sessionFile: File =
        File(System.getProperty("user.home"), ".designacoes-jw/auth_session.json")

    var currentSession: AuthSession? = null
        private set

    init {
        loadSession()
    }

    /**
     * Só lê o arquivo de sessão. **Nenhuma rede** — seguro para a thread de UI.
     *
     * Separado de [loadSession] de propósito: renovar o token é HTTP com
     * timeout de 10 s, e isso no inicializador do StoreController congelava a
     * janela no boot. O `catch` protegia falha de socket, não o freeze.
     */
    fun loadSessionFromDisk(): AuthSession? {
        if (!sessionFile.exists()) return null
        val conteudo = runCatching { sessionFile.readText() }.getOrNull() ?: return null

        val sessao = runCatching {
            if (SessionCrypt.pareceCifrado(conteudo)) {
                json.decodeFromString<AuthSession>(SessionCrypt.decifrar(conteudo))
            } else {
                // Arquivo no formato antigo, em texto claro. Lê e regrava
                // cifrado: quem tinha a pasta expondo o token deixa de ter.
                val antiga = json.decodeFromString<AuthSession>(conteudo)
                saveSession(antiga)
                antiga
            }
        }.getOrNull()

        if (sessao == null) {
            // Cifrado mas ilegível: DPAPI de outra conta, ou arquivo alterado.
            // Não tenta texto claro — seria aceitar um arquivo forjado.
            System.err.println("[DesktopAuth] sessao ilegivel; va ser preciso entrar de novo")
        }
        return sessao
    }

    /**
     * Renova o token **sem** olhar a validade.
     *
     * Para quando o servidor já respondeu 401: um token ainda "no prazo" pode
     * ter sido revogado (senha trocada, sessões revogadas, app removido do
     * dispositivo). Verificar a validade diria "não precisa renovar" e o push
     * falharia de novo com o mesmo token.
     *
     * **Faz rede** — thread de background, nunca a de UI.
     */
    fun renovarAgora(session: AuthSession?): AuthSession? {
        if (session == null) return null
        return refreshSession(session.refreshToken).getOrNull() ?: session
    }

    /**
     * Renova o token se estiver perto de expirar. **Faz rede** — chamar em
     * thread de background, nunca na de UI.
     */
    fun refreshIfNeeded(session: AuthSession?): AuthSession? {
        if (session == null) {
            currentSession = null
            return null
        }
        if (System.currentTimeMillis() < session.expiresAt - (5 * 60 * 1000L)) {
            currentSession = session
        } else {
            val renewed = refreshSession(session.refreshToken).getOrNull()
            currentSession = renewed ?: session
        }
        return currentSession
    }

    /** Lê do disco e renova. Faz rede: só para fora do boot. */
    fun loadSession(): AuthSession? = refreshIfNeeded(loadSessionFromDisk())

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
        // Falha ao apagar deixa o refresh token em disco. Não é Ideal, mas o
        // usuário pediu para sair e a sessão foi limpa da memória: avisar no
        // log é o suficiente, e a próxima gravação sobrescreve o arquivo.
        runCatching { sessionFile.delete() }
            .onFailure { System.err.println("[DesktopAuth] nao consegui apagar a sessao: ${it.message}") }
    }

    /** Grava a sessão e devolve [false] se o disco recusou. */
    fun saveSessionPublic(session: AuthSession): Boolean = saveSession(session)

    private fun saveSession(session: AuthSession): Boolean {
        currentSession = session
        // Antes era `runCatching` sem tratamento: se o disco recusasse, o
        // resultado sumia e o próximo boot entrava "Offline" sem explicar nada.
        // O usuário logava, o app confirmava o login, e na abertura seguinte era
        // preciso entrar de novo sem saber por quê.
        return runCatching {
            sessionFile.parentFile?.mkdirs()
            sessionFile.writeText(SessionCrypt.cifrar(json.encodeToString(session)))
        }.onFailure {
            System.err.println("[DesktopAuth] nao consegui gravar a sessao: ${it.message}")
        }.isSuccess
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
