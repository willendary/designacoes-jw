package br.com.willendary.designacoesjw.desktop.firebase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A sessão guarda o refresh token, que é de longa duração: com ele se emitem
 * idTokens novos sem pedir a senha. Estes testes existem para o arquivo em disco
 * nunca voltar a ser legível, e para o arquivo antigo não travar quem já tem.
 */
class SessionCryptTest {

    private val cabecalho = "DESIGNACOESJW-SESSAO-CIFRADA:"

    @Test
    fun `o token nao aparece no arquivo cifrado`() {
        val cifrado = SessionCrypt.cifrar("""{"refreshToken":"REFRESH_SECRETO_AQUI"}""")

        assertFalse(
            cifrado.contains("REFRESH_SECRETO_AQUI"),
            "o refresh token ficou legivel no arquivo"
        )
        assertTrue(SessionCrypt.pareceCifrado(cifrado))
    }

    @Test
    fun `cifrar e decifrar volta o mesmo texto`() {
        val original = """{"idToken":"abc","email":"joao@exemplo.com","refreshToken":"def"}"""

        assertEquals(original, SessionCrypt.decifrar(SessionCrypt.cifrar(original)))
    }

    @Test
    fun `cada cifragem produce um arquivo diferente`() {
        // A DPAPI usa um IV aleatório. Se duas cifragens fossem iguais, daria
        // para comparar os arquivos e saber que a sessão não mudou.
        val texto = """{"refreshToken":"igual"}"""

        assertTrue(
            SessionCrypt.cifrar(texto) != SessionCrypt.cifrar(texto),
            "a cifragem é determinística, o que vaza informação"
        )
    }

    @Test
    fun `texto com acento e emoji atravessa a cifragem`() {
        val original = """{"name":"João çãé — Reunião ❤️"}"""

        assertEquals(original, SessionCrypt.decifrar(SessionCrypt.cifrar(original)))
    }

    @Test
    fun `o formato antigo nao e confundido com o novo`() {
        // A migração depende disto: sem o cabeçalho, assume texto claro.
        val antigo = """{"idToken":"abc","refreshToken":"def"}"""

        assertFalse(SessionCrypt.pareceCifrado(antigo))
        assertTrue(SessionCrypt.pareceCifrado("  " + SessionCrypt.cifrar(antigo)))
    }

    @Test
    fun `conteudo cifrado invalido falha em vez de virar sessao`() {
        // cryptUnprotectData sobre bytes que não são um blob DPAPI deste usuário
        // tem de falhar. O ponto é que o caminho de erro não pode cair para
        // "tentar ler como texto claro" — foi assim que o arquivo antigo entrou.
        val falso = cabecalho + java.util.Base64.getEncoder()
            .encodeToString("isto nao e um blob dpapi".toByteArray())

        assertTrue(
            runCatching { SessionCrypt.decifrar(falso) }.isFailure,
            "um blob falso foi aceito como sessão"
        )
    }

    /**
     * A migração é o caminho que faz **quem já usa o app** perder o login se
     * estiver errado. Estes três testes escrevem um arquivo de verdade e passam
     * pelo mesmo `loadSessionFromDisk` que o app usa.
     */
    private fun comSessaoEm(pasta: java.io.File, conteudo: String?, bloco: () -> Unit) {
        val original = DesktopAuthManager.sessionFile
        try {
            DesktopAuthManager.sessionFile = java.io.File(pasta, "auth_session.json")
            conteudo?.let { DesktopAuthManager.sessionFile.writeText(it) }
            bloco()
        } finally {
            DesktopAuthManager.sessionFile = original
        }
    }

    @Test
    fun `arquivo antigo em texto claro e lido e regravado cifrado`() {
        val pasta = java.nio.file.Files.createTempDirectory("djw-sessao").toFile()
        val antigo = """{"idToken":"ID","email":"joao@exemplo.com","refreshToken":"REFRESH","localId":"u1","expiresAt":0}"""

        comSessaoEm(pasta, antigo) {
            val lida = DesktopAuthManager.loadSessionFromDisk()

            assertEquals("joao@exemplo.com", lida?.email, "quem ja usava o app perdeu o login")
            assertEquals("REFRESH", lida?.refreshToken)

            val depois = DesktopAuthManager.sessionFile.readText()
            assertTrue(SessionCrypt.pareceCifrado(depois), "o arquivo antigo nao foi regravado cifrado")
            assertFalse(depois.contains("REFRESH"), "o refresh token continua legivel apos migrar")
        }
    }

    @Test
    fun `arquivo cifrado e lido sem ser reescrito`() {
        val pasta = java.nio.file.Files.createTempDirectory("djw-sessao").toFile()
        val conteudo = SessionCrypt.cifrar("""{"idToken":"ID","email":"a@b.c","refreshToken":"R","localId":"u","expiresAt":0}""")

        comSessaoEm(pasta, conteudo) {
            val lida = DesktopAuthManager.loadSessionFromDisk()

            assertEquals("a@b.c", lida?.email)
            assertEquals(conteudo, DesktopAuthManager.sessionFile.readText(), "o arquivo foi reescrito à toa")
        }
    }

    @Test
    fun `arquivo ausente devolve nulo e nao inventa sessao`() {
        val pasta = java.nio.file.Files.createTempDirectory("djw-sessao").toFile()

        comSessaoEm(pasta, null) {
            assertEquals(null, DesktopAuthManager.loadSessionFromDisk())
        }
    }
}