package br.com.willendary.designacoesjw.sync

/**
 * Executa uma ação em background, no máximo uma vez por vez, com carência e
 * coalescência.
 *
 * Existe por causa de um bug concreto: cada `save()` abria uma thread nova de
 * push. Editar o dia da semana disparava dezenas de threads, cada uma com 7
 * batches sequenciais lendo o estado vivo. Resultado: rajada de requests,
 * updates aplicados fora de ordem (o mais antigo chegando depois do mais novo) e
 * prunes concorrentes, em que uma thread apaga o que a outra acabou de gravar.
 *
 * Garantias:
 * - **um por vez** — nunca duas ações simultâneas;
 * - **carência** — uma rajada de disparos vira uma execução só;
 * - **um retrabalho, nunca uma fila** — se algo mudou durante a execução, roda
 *   exatamente mais uma vez com o estado mais recente, em vez de acumular;
 * - **snapshot** — [current] é lido uma vez por execução e passado como valor,
 *   então a ação não vê o estado mudar no meio do caminho.
 */
class CoalescingWorker<T>(
    private val debounceMs: Long,
    private val current: () -> T,
    private val action: (T) -> Unit,
    private val onError: (Exception) -> Unit = {}
) {

    private val lock = Any()
    private var running = false
    private var dirty = false

    fun schedule() {
        synchronized(lock) {
            if (running) {
                // Já está rodando: marca e deixa o worker decidir se precisa
                // repetir. Enfileirar um por disparo era justamente o defeito.
                dirty = true
                return
            }
            running = true
            dirty = false
        }
        Thread {
            while (true) {
                try {
                    if (debounceMs > 0) Thread.sleep(debounceMs)
                } catch (e: Exception) {
                    onError(e)
                }
                // Limpa o que chegou DURANTE a carencia: o snapshot que vamos
                // ler agora ja e o mais recente, entao isso nao e retrabalho.
                // Sem esta limpeza, 50 disparos seguidos viravam 2 execucoes em
                // vez de 1 — foi o que o teste pegou.
                synchronized(lock) { dirty = false }
                try {
                    // Lido uma vez e repassado por valor.
                    action(current())
                } catch (e: Exception) {
                    onError(e)
                }
                // Aqui so interessa o que mudou enquanto a acao rodava.
                val repeat = synchronized(lock) {
                    if (dirty) {
                        dirty = false
                        true
                    } else {
                        running = false
                        false
                    }
                }
                if (!repeat) return@Thread
            }
        }.apply {
            isDaemon = true
            name = "coalescing-worker"
            start()
        }
    }
}
