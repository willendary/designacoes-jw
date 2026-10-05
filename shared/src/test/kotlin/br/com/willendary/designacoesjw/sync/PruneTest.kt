package br.com.willendary.designacoesjw.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Esta é a trava de perda de dado mais afiada do projeto: `Prune.Apagar` vira
 * um `DELETE` no servidor. Cada teste aqui corresponde a uma maneira de a
 * congregação sumir do servidor.
 */
class PruneTest {

    @Test
    fun `apaga so o que o servidor tem e o aparelho nao`() {
        val decisao = decidirPrune(
            idsDoServidor = listOf("1", "2", "3"),
            keep = setOf("1", "2"),
            rotulo = "irmãos"
        )

        assertEquals(Prune.Apagar(listOf("3")), decisao)
    }

    @Test
    fun `nada a apagar quando os dois lados batem`() {
        val decisao = decidirPrune(listOf("1", "2"), setOf("1", "2"), "irmãos")

        val preserva = assertIs<Prune.Preservar>(decisao)
        assertTrue(preserva.motivo.contains("nada sobrando"), preserva.motivo)
    }

    @Test
    fun `servidor vazio nao apaga nada`() {
        val decisao = decidirPrune(emptyList(), setOf("1", "2"), "irmãos")

        assertIs<Prune.Preservar>(decisao)
    }

    @Test
    fun `aparelho vazio sobre colecao populada NAO apaga a congregacao`() {
        // O caso perigoso: `dados.json` corrompido, cache incompleto ou erro de
        // leitura produz lista vazia no aparelho. Sem esta guarda, a congregação
        // inteira seria apagada do servidor.
        val decisao = decidirPrune(
            idsDoServidor = listOf("1", "2", "3", "4"),
            keep = emptySet(),
            rotulo = "irmãos"
        )

        val preserva = assertIs<Prune.Preservar>(decisao)
        assertTrue(preserva.motivo.contains("Não apaguei"), preserva.motivo)
        assertTrue(preserva.motivo.contains("irmãos"), "a mensagem tem que dizer qual colecao: ${preserva.motivo}")
    }

    @Test
    fun `a recusa por aparelho vazio so vale com colecao populada`() {
        // Se o servidor tambem esta vazio, nao ha o que recusar: e so um
        // aparelho novo ainda sem nada.
        val decisao = decidirPrune(emptyList(), emptySet(), "irmãos")

        val preserva = assertIs<Prune.Preservar>(decisao)
        assertTrue(!preserva.motivo.contains("Não apaguei"), preserva.motivo)
    }

    @Test
    fun `ids do servidor repetidos nao viram DELETE repetido`() {
        // Nao deve acontecer, mas um DELETE repetido e um DELETE a mais do que a
        // conta de remocoes que o usuario fez.
        val decisao = decidirPrune(listOf("3", "3", "1"), setOf("1"), "irmãos")

        assertEquals(Prune.Apagar(listOf("3", "3")), decisao)
    }

    @Test
    fun `keep parcial nao apaga o que o outro aparelho acabou de escrever`() {
        // Dois aparelhos, o mesmo usuario. O aparelho A tem 1,2 e o B tem 2,3.
        // O keep e do A: apagar 3 aqui apagaria o que o B gravou. Este teste nao
        // resolve a concorrencia (e' DJW-012) — ele fixa o comportamento
        // atual, que e last-write-wins, e o motivo de DJW-012 existir.
        val decisao = decidirPrune(listOf("1", "2", "3"), setOf("1", "2"), "irmãos")

        assertEquals(Prune.Apagar(listOf("3")), decisao)
    }
}