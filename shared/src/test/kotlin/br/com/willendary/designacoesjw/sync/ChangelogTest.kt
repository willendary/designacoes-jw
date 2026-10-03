package br.com.willendary.designacoesjw.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * As novidades por versão (#27 do roadmap do usuário).
 *
 * Duas coisas podem dar errado aqui, e nenhuma delas pode aparecer como erro
 * na tela: **comparar versão como texto** (aí `0.10.0` vem antes de `0.9.0` e
 * uma correção some), e **mostrar novidade de outra plataforma** (quem está no
 * celular não precisa ler que o login do desktop foi consertado).
 */
class ChangelogTest {

    private val changelog = Changelog(
        versoes = mapOf(
            "0.6.2" to Versao(android = listOf("celular 6.2"), desktop = emptyList()),
            "0.6.1" to Versao(android = listOf("celular 6.1"), desktop = emptyList()),
            "0.6.0" to Versao(android = listOf("celular 6.0"), desktop = listOf("desktop 6.0")),
            "0.5.1" to Versao(android = emptyList(), desktop = listOf("desktop 5.1")),
            // Versão sem nada para uma plataforma: não aparece, não aparece vazia.
            "0.5.0" to Versao(android = listOf("celular 5.0"), desktop = emptyList())
        )
    )

    @Test
    fun `compara versao como numero, e nao como texto`() {
        // O erro clássico. "0.10.0" < "0.9.0" em texto, porque '1' < '9'.
        assertTrue(compararVersoes("0.10.0", "0.9.0") > 0, "0.10.0 é maior que 0.9.0")
        assertTrue(compararVersoes("0.9.0", "0.10.0") < 0)
        assertTrue(compararVersoes("0.6.2", "0.6.10") < 0)
        assertEquals(0, compararVersoes("0.6.2", "0.6.2"))
        // Prefixo de tag não pode virar segmento.
        assertEquals(0, compararVersoes("v0.6.2", "0.6.2"))
    }

    @Test
    fun `versao com menos segmentos nao e menor que a completa`() {
        // "0.6" e "0.6.0" são a mesma versão; "0.6.1" é maior que as duas.
        assertEquals(0, compararVersoes("0.6", "0.6.0"))
        assertTrue(compararVersoes("0.6.1", "0.6") > 0)
    }

    @Test
    fun `so mostra versao posterior a instalada`() {
        val novidades = changelog.novidades("0.6.1", Plataforma.ANDROID, "0.6.2")
        assertEquals(1, novidades.size)
        assertEquals("0.6.2", novidades.first().versao)
        assertEquals(listOf("celular 6.2"), novidades.first().itens)
    }

    @Test
    fun `mostra tudo que faltou, da mais antiga para a mais nova`() {
        // Quem ficou na 0.5.0 precisa ver 0.5.1 a 0.6.2 em ordem — a
        // cronologia é o que conta, não a soma.
        val novidades = changelog.novidades("0.5.0", Plataforma.ANDROID, "0.6.2")
        assertEquals(
            listOf("0.6.0", "0.6.1", "0.6.2"),
            novidades.map { it.versao }
        )
    }

    @Test
    fun `nunca mostra novidade de outra plataforma`() {
        // A 0.5.1 só mexeu no desktop. Quem está no celular não tem o que ler.
        assertTrue(changelog.novidades("0.5.0", Plataforma.ANDROID, "0.6.2").none { it.versao == "0.5.1" })
        assertEquals(listOf("desktop 5.1"), changelog.novidades("0.5.0", Plataforma.DESKTOP, "0.5.1").first().itens)
    }

    @Test
    fun `versao que nao tem nada para a plataforma simplesmente nao aparece`() {
        // Não aparecer vazio: um item sem texto é pior que item nenhum.
        val novidades = changelog.novidades("0.5.1", Plataforma.DESKTOP, "0.6.2")
        assertTrue(novidades.none { it.itens.isEmpty() })
        assertEquals(listOf("0.6.0"), novidades.map { it.versao })
    }

    @Test
    fun `ja esta na ultima nao mostra nada`() {
        assertTrue(changelog.novidades("0.6.2", Plataforma.ANDROID, "0.6.2").isEmpty())
        // E estar **adiantado** também não mostra nada: é o caso de instalar
        // downgrade, e mostrar "novidades" do nada seria mentira.
        assertTrue(changelog.novidades("0.7.0", Plataforma.ANDROID, "0.6.2").isEmpty())
    }

    @Test
    fun `le o json e ignora chave desconhecida`() {
        // O arquivo é editado à mão e versionado. Um app antigo não pode quebrar
        // porque apareceu uma chave nova.
        val texto = """
            {
              "0.6.2": { "android": ["a"], "desktop": [], "campoNovo": 1 },
              "0.6.1": { "android": ["b"] }
            }
        """.trimIndent()
        val lido = lerChangelog(texto)
        assertEquals(2, lido?.versoes?.size)
        assertEquals(listOf("a"), lido?.novidades("0.6.1", Plataforma.ANDROID)?.first()?.itens)
    }

    @Test
    fun `json invalido devolve nulo em vez de estourar`() {
        // A lição do crash-on-start da 0.4.2: changelog quebrado é motivo para
        // não mostrar nada, nunca para impedir o app de abrir.
        assertNull(lerChangelog("{ isso nao e json"))
        assertNull(lerChangelog(""))
        assertNull(lerChangelog("[]"))
    }

    @Test
    fun `versao mais recente vem do arquivo, nao do nome dela`() {
        // O nome do arquivo pode estar em ordem diferente; o que vale é o maior
        // número, porque é o que decide se há novidade.
        assertEquals("0.6.2", changelog.versaoMaisRecente())

        val embaralhado = Changelog(
            versoes = mapOf(
                "0.6.0" to Versao(),
                "0.10.1" to Versao(),
                "0.9.9" to Versao()
            )
        )
        assertEquals("0.10.1", embaralhado.versaoMaisRecente())
    }

    @Test
    fun `changelog vazio nao quebra`() {
        val vazio = Changelog()
        assertEquals("0.0.0", vazio.versaoMaisRecente())
        assertTrue(vazio.novidades("0.1.0", Plataforma.ANDROID).isEmpty())
    }
}