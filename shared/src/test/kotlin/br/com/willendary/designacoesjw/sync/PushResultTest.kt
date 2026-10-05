package br.com.willendary.designacoesjw.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O defeito que estes testes cobrem era **silencioso**: o `Result` falhado era
 * descartado e a barra dizia "Sincronizado". O teste que importa é o primeiro —
 * uma recusa tem que virar nome de coleção.
 */
class PushResultTest {

    private fun ok(): Result<Unit> = Result.success(Unit)
    private fun negado(): Result<Unit> = Result.failure(IllegalStateException("PERMISSION_DENIED"))

    @Test
    fun `push recusado vira nome de colecao, nao silencio`() {
        val falhas = falhasDoPush(
            listOf("irmãos" to ok(), "reuniões" to negado(), "privilégios" to ok())
        )

        assertEquals(listOf("reuniões"), falhas)
    }

    @Test
    fun `tudo aceito devolve lista vazia`() {
        val falhas = falhasDoPush(listOf("irmãos" to ok(), "reuniões" to ok()))

        assertEquals(emptyList(), falhas, "push inteiro aceito não é falha")
    }

    @Test
    fun `push parcial lista so o que faltou`() {
        // Push nao e transacional: uma recusa nao desfaz as outras. O que o
        // usuario precisa ver e o que ficou de fora, nao um "deu erro" generico.
        val falhas = falhasDoPush(
            listOf(
                "irmãos" to negado(),
                "privilégios" to ok(),
                "reuniões" to negado(),
                "configurações" to negado()
            )
        )

        assertEquals(listOf("irmãos", "reuniões", "configurações"), falhas)
    }

    @Test
    fun `colecoes repetidas nao somem da lista`() {
        val falhas = falhasDoPush(listOf("irmãos" to negado(), "irmãos" to negado()))

        assertEquals(2, falhas.size)
    }

    @Test
    fun `a mensagem diz que a alteracao ficou so neste computador`() {
        val msg = mensagemDeFalhaNoPush(listOf("irmãos"))

        assertTrue(msg.contains("irmãos"), msg)
        assertTrue(msg.contains("só neste computador"), "precisa dizer onde a alteracao esta: $msg")
        assertTrue(
            !msg.contains("PERMISSION_DENIED") && !msg.contains("Exception"),
            "a mensagem e para o responsavel, nao para quem le log: $msg"
        )
    }

    @Test
    fun `a mensagem de falha nao deixa o status dizer que sincronizou`() {
        val msg = mensagemDeFalhaNoPush(listOf("reuniões"))

        assertTrue(msg.contains("Não consegui enviar"), msg)
        assertTrue(msg.contains("nuvem"), msg)
    }
}