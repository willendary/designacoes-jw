package br.com.willendary.designacoesjw.sync

import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.Privilege
import br.com.willendary.designacoesjw.data.PublicTalk
import br.com.willendary.designacoesjw.data.Store
import br.com.willendary.designacoesjw.data.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O defeito era uma coleção decidindo sobre todas. Cada teste aqui é uma
 * combinação diferente de "a nuvem veio vazia em uma parte" — que era
 * exatamente o que apagava dados da tela sem mensagem.
 */
class MergeCloudTest {

    private val ana = Brother(id = 1, name = "Ana")
    private val privilegio = Privilege(id = 1, name = "Som")
    private val reuniao = Meeting(id = 1, date = "07/10/2026", type = "Reunião de Meio de Semana")
    private val discurso = PublicTalk(id = 1, date = "10/10/2026", speakerName = "Orador")

    @Test
    fun `nuvem so com discursos nao zera os irmaos`() {
        // O caso que o || de três coleções deixava passar.
        val cloud = Store(publicTalks = listOf(discurso))
        val local = Store(brothers = listOf(ana), privileges = listOf(privilegio), meetings = listOf(reuniao))

        val merge = mesclarCloudComLocal(cloud, local)

        assertEquals(listOf(ana), merge.store.brothers, "os irmaos sumiram da tela")
        assertEquals(listOf(privilegio), merge.store.privileges)
        assertEquals(listOf(reuniao), merge.store.meetings)
        assertEquals(listOf(discurso), merge.store.publicTalks, "o que veio da nuvem tem que entrar")
    }

    @Test
    fun `a colecao preservada e dita pelo nome, para o usuario entender`() {
        val cloud = Store(publicTalks = listOf(discurso))
        val local = Store(brothers = listOf(ana))

        val merge = mesclarCloudComLocal(cloud, local)

        assertEquals(listOf("irmãos"), merge.preservadas)
        assertTrue(mensagemDeNuvemVazia(merge.preservadas).contains("irmãos"))
    }

    @Test
    fun `nuvem cheia aceita o que veio`() {
        val cloud = Store(brothers = listOf(Brother(id = 2, name = "Bruno")))
        val local = Store(brothers = listOf(ana))

        val merge = mesclarCloudComLocal(cloud, local)

        assertEquals("Bruno", merge.store.brothers.first().name)
        assertTrue(merge.preservadas.isEmpty(), "nada foi preservado: ${merge.preservadas}")
    }

    @Test
    fun `aparelho novo com nuvem vazia aceita o vazio`() {
        // Nuvem vazia + aparelho vazio = congregação nova. Não é para travar.
        val merge = mesclarCloudComLocal(Store(), Store())

        assertEquals(emptyList(), merge.store.brothers)
        assertTrue(merge.preservadas.isEmpty())
    }

    @Test
    fun `o tema continua sendo escolha deste aparelho`() {
        val cloud = Store(brothers = listOf(ana), themeMode = ThemeMode.DARK)
        val local = Store(themeMode = ThemeMode.LIGHT)

        val merge = mesclarCloudComLocal(cloud, local)

        assertEquals(ThemeMode.LIGHT, merge.store.themeMode, "o tema nao e dado da congregacao")
    }

    @Test
    fun `preserva varias colecoes de uma vez`() {
        val cloud = Store(publicTalks = listOf(discurso))
        val local = Store(brothers = listOf(ana), meetings = listOf(reuniao))

        val merge = mesclarCloudComLocal(cloud, local)

        assertEquals(setOf("irmãos", "reuniões"), merge.preservadas.toSet())
    }
}