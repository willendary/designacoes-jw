package br.com.willendary.designacoesjw.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Estado de sincronização (#61).
 *
 * O que trava aqui é a **ordem de prioridade** e a separação entre "não
 * enviada" e "falha". Juntar os dois é o erro óbvio: a pessoa passa a temer
 * trabalho que está salvo.
 */
class EstadoSincronizacaoTest {

    @Test
    fun `nada pendente e nada gravando e cache limpo fica em silencio`() {
        val e = EstadoSincronizacao()
        assertEquals("Sincronizado", e.rotulo)
        assertFalse(e.mereceAviso, "estado bom não deve aparecer nada na tela")
    }

    @Test
    fun `gravando mostra sincronizando`() {
        val e = EstadoSincronizacao(pendentes = 1, gravando = true)
        assertEquals("Sincronizando", e.rotulo)
        assertTrue(e.mereceAviso)
    }

    @Test
    fun `pendente conta e singulariza`() {
        assertEquals("1 alteração não enviada", EstadoSincronizacao(pendentes = 1).rotulo)
        assertEquals("3 alteraçãoes não enviadas", EstadoSincronizacao(pendentes = 3).rotulo)
    }

    @Test
    fun `falha ganha de pendente e de gravando`() {
        // Quem falhou precisa saber que falhou, mesmo havendo fila.
        val e = EstadoSincronizacao(pendentes = 2, gravando = true, ultimaFalha = "sem permissão")
        assertEquals("Falha ao enviar", e.rotulo)
    }

    @Test
    fun `cache local avisa sem alarmingar`() {
        // O sintoma original: ninguém sabia se era nuvem ou cache velho.
        val e = EstadoSincronizacao(somenteCache = true)
        assertEquals("Cache local", e.rotulo)
        assertTrue(e.mereceAviso)
    }

    @Test
    fun `contador sobe e desce`() {
        val c = ContadorSincronizacao()
        c.emGravacao()
        c.emGravacao()
        assertEquals(2, c.atual.pendentes)
        c.confirmada()
        c.confirmada()
        assertEquals(0, c.atual.pendentes)
        assertFalse(c.atual.gravando)
    }

    @Test
    fun `confirmar alem do que foi agendado nao fica negativo`() {
        // Números negativos apareciam como "-1 alteração não enviada".
        val c = ContadorSincronizacao()
        c.confirmada()
        assertEquals(0, c.atual.pendentes)
    }

    @Test
    fun `falha NAO devolve a alteracao para o nada`() {
        // A escrita continua não enviada. Se a falha limpasse o contador, a
        // tela diria "Sincronizado" com o dado preso no aparelho — exatamente
        // o defeito que a issue descreve.
        val c = ContadorSincronizacao()
        c.emGravacao()
        c.falhou("sem permissão")
        assertEquals(1, c.atual.pendentes)
        assertEquals("Falha ao enviar", c.atual.rotulo)
    }

    @Test
    fun `nova gravacao limpa a falha anterior`() {
        // Senão a tela ficaria presa em "Falha ao enviar" para sempre, mesmo
        // depois de a nuvem voltar.
        val c = ContadorSincronizacao()
        c.emGravacao()
        c.falhou("sem rede")
        c.emGravacao()
        assertEquals(null, c.atual.ultimaFalha)
    }

    @Test
    fun `leitura do servidor limpa o aviso de cache`() {
        val c = ContadorSincronizacao()
        c.semRede()
        assertTrue(c.atual.somenteCache)
        c.lidaDoServidor()
        assertFalse(c.atual.somenteCache)
        assertEquals("Sincronizado", c.atual.rotulo)
    }

    @Test
    fun `gravacao sobe pendente E marca gravando`() {
        // São dois fatos, e a tela usa os dois: "Sincronizando" vem de um,
        // "não enviada" do outro.
        val c = ContadorSincronizacao()
        c.emGravacao()
        assertTrue(c.atual.gravando)
        assertTrue(c.atual.pendentes > 0)
    }
}