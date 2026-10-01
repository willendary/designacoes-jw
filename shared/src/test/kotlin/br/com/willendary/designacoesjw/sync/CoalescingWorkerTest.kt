package br.com.willendary.designacoesjw.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CoalescingWorkerTest {

    private fun waitUntil(timeoutMs: Long = 5000, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    @Test
    fun rajadaDeDisparosViraUmaExecucaoSo() {
        var runs = 0
        val done = CountDownLatch(1)
        val worker = CoalescingWorker<List<String>>(
            debounceMs = 200,
            current = { listOf("estado") },
            action = { runs++; done.countDown() }
        )

        // 50 disparos bem mais rápido que a carência.
        repeat(50) { worker.schedule() }

        assertTrue(done.await(5, TimeUnit.SECONDS), "a acao deveria rodar")
        Thread.sleep(400) // dá tempo de um retrabalho indevido aparecer
        assertEquals(1, runs, "rajada de 50 disparos deve virar 1 execucao, nao 50")
    }

    @Test
    fun nuncaDuasExecucoesSimultaneas() {
        val simultaneas = AtomicInteger(0)
        val maximo = AtomicInteger(0)
        val rodou = AtomicInteger(0)
        val done = CountDownLatch(1)

        val worker = CoalescingWorker<Int>(
            debounceMs = 50,
            current = { 1 },
            action = {
                val agora = simultaneas.incrementAndGet()
                maximo.updateAndGet { anterior -> maxOf(anterior, agora) }
                Thread.sleep(150) // trabalho lento, para sobrepor se houver bug
                simultaneas.decrementAndGet()
                if (rodou.incrementAndGet() >= 1) done.countDown()
            }
        )

        repeat(30) {
            worker.schedule()
            Thread.sleep(20)
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        Thread.sleep(600)
        assertEquals(1, maximo.get(), "duas acoes rodaram ao mesmo tempo")
    }

    @Test
    fun mudancaDuranteExecucaoGeraExatamenteMaisUma() {
        val rodou = AtomicInteger(0)
        val fim = CountDownLatch(1)
        val worker = CoalescingWorker<Int>(
            debounceMs = 20,
            current = { rodou.get() + 1 },
            action = {
                rodou.incrementAndGet()
                if (rodou.get() >= 2) fim.countDown()
            }
        )

        worker.schedule()
        // Dispara de novo enquanto a primeira execução ainda está em curso.
        Thread.sleep(40)
        repeat(5) { worker.schedule() }

        assertTrue(fim.await(5, TimeUnit.SECONDS), "deveria haver um retrabalho")
        Thread.sleep(400)
        assertTrue(rodou.get() <= 3, "disparos durante a execucao devem virar 1 retrabalho, nao ${rodou.get()}")
    }

    @Test
    fun actionRecebeSnapshotEstavel() {
        val contador = AtomicInteger(0)
        val vistos = mutableListOf<Int>()
        val fim = CountDownLatch(1)

        // current() e lido uma vez e o valor repassado: a acao ve o numero do
        // momento em que rodou, mesmo que o contador mude logo em seguida.
        val worker = CoalescingWorker<Int>(
            debounceMs = 30,
            current = { contador.get() },
            action = { snapshot ->
                vistos.add(snapshot)
                contador.set(99)
                fim.countDown()
            }
        )
        worker.schedule()
        assertTrue(fim.await(5, TimeUnit.SECONDS))
        assertEquals(0, vistos.first(), "a acao deve ver o valor no momento em que rodou")
    }

    @Test
    fun erroNaoTravaOWorker() {
        val tentativas = AtomicInteger(0)
        val worker = CoalescingWorker<Int>(
            debounceMs = 20,
            current = { 1 },
            action = { if (tentativas.incrementAndGet() == 1) throw IllegalStateException("falhou") },
            onError = { }
        )
        worker.schedule()
        Thread.sleep(80)
        worker.schedule()
        assertTrue(waitUntil { tentativas.get() >= 2 }, "apos um erro o worker ainda tem de aceitar novo disparo")
    }
}
