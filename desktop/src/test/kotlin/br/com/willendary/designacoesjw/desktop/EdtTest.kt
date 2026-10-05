package br.com.willendary.designacoesjw.desktop

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [Edt] existe porque o defeito era silencioso: estado do Compose escrito de
 * thread crua não dá erro, dá tela inconsistente. Estes testes fixam o contrato —
 * publicar leva para a thread de UI, e não trava quando o chamador já está nela.
 */
class EdtTest {

    @Test
    fun `publicar leva o bloco para a thread de UI`() {
        val rodouNaEdt = java.util.concurrent.atomic.AtomicBoolean(false)
        val pronto = CountDownLatch(1)

        Thread {
            assertFalse(Edt.naEdt(), "o chamador deveria estar fora da EDT")
            Edt.publica {
                rodouNaEdt.set(Edt.naEdt())
                pronto.countDown()
            }
        }.start()

        assertTrue(pronto.await(10, TimeUnit.SECONDS), "o bloco nunca rodou")
        assertTrue(rodouNaEdt.get(), "o estado do Compose foi escrito fora da thread de UI")
    }

    @Test
    fun `o chamador não espera o bloco terminar`() {
        val pronto = CountDownLatch(1)

        // Se publica() rodasse o bloco na hora quando já está na EDT, o código
        // de rede ficaria preso na thread de UI — que é exatamente o que a
        // thread em background existe para evitar.
        Thread {
            Edt.publica {
                Thread.sleep(300)
                pronto.countDown()
            }
        }.start()

        // O start acima já voltou: a chamada não bloqueou.
        assertFalse(pronto.await(150, TimeUnit.MILLISECONDS), "publicar() rodou o bloco na chamada")
        assertTrue(pronto.await(10, TimeUnit.SECONDS))
    }

    @Test
    fun `várias publicações rodam todas, na ordem`() {
        val ordem = mutableListOf<Int>()
        val pronto = CountDownLatch(3)

        Thread {
            repeat(3) { i -> Edt.publica { ordem += i; pronto.countDown() } }
        }.start()

        assertTrue(pronto.await(10, TimeUnit.SECONDS))
        assertTrue(ordem == listOf(0, 1, 2), "publicações se perderam ou embaralharam: $ordem")
    }
}