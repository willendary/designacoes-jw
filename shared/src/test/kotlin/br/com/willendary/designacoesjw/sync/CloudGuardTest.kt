package br.com.willendary.designacoesjw.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A trava que impede perda de dado pela sincronização (#47).
 *
 * O caso que motivou: a nuvem devolveu lista vazia — permissão revogada, token
 * expirado, regra mudada — e o `save*Local(emptyList())` sobrescreveu o cache.
 * A tela mostrava lista vazia e a congregação perdia o mês de trabalho sem
 * aviso.
 */
class CloudGuardTest {

    @Test
    fun `nuvem vazia sobre aparelho cheio mantem o que tem`() {
        assertTrue(
            CloudGuard.deveManterLocal(listOf<String>(), listOf("Carlos", "Daniel")),
            "este é o caso que apaga dado: a nuvem veio vazia e o aparelho tem gente"
        )
    }

    @Test
    fun `nuvem com conteudo substitui o aparelho`() {
        // Caminho normal, e tem que continuar funcionando: o app sincroniza
        // entre aparelhos.
        assertFalse(CloudGuard.deveManterLocal(listOf("Carlos"), listOf("Daniel")))
    }

    @Test
    fun `nuvem e aparelho vazios nao e nada a preservar`() {
        assertFalse(CloudGuard.deveManterLocal(listOf<String>(), listOf<String>()))
    }

    @Test
    fun `local ilegivel nao vira silencio`() {
        // `null` = nem deu para ler o aparelho. Não há o que manter, e devolver
        // `true` ali faria a tela mostrar vazio **sem aviso** — que é exatamente
        // o defeito que a trava existe para impedir.
        assertFalse(CloudGuard.deveManterLocal(listOf<String>(), null))
    }

    @Test
    fun `so vazio com cache cheio e perigoso, nas duas direcoes`() {
        // Remove a proteção por engano num listener e este teste acusa.
        val casos: List<Triple<List<String>, List<String>?, Boolean>> = listOf(
            Triple(listOf(), listOf(), false),
            Triple(listOf(), listOf("a"), true),
            Triple(listOf("a"), listOf(), false),
            Triple(listOf("a"), listOf("b"), false),
            Triple(listOf(), null, false)
        )
        casos.forEach { (nuvem, local, esperado) ->
            assertEquals(
                esperado,
                CloudGuard.deveManterLocal(nuvem, local),
                "nuvem=$nuvem local=$local"
            )
        }
    }
}