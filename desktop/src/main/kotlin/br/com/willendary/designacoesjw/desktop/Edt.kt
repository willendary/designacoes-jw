package br.com.willendary.designacoesjw.desktop

import java.awt.EventQueue

/**
 * Toda escrita de estado do Compose passa por aqui.
 *
 * **Por que existe.** O desktop faz rede em `Thread { }` para a janela abrir na
 * hora — decisão certa. O que estragava era o resto do corpo: `data`,
 * `syncStatus`, `isSyncing` e `reportError` eram atribuídos de thread crua.
 * `mutableStateOf` não avisa ninguém disso, e o efeito é intercalado: a tela às
 * vezes mostra "Sincronizado" e às vezes fica como estava, dependendo de como as
 * threads cresceram. Não há crash, não há aviso — é a classe de defeito mais
 * difícil de achar depois.
 *
 * O projeto já sabia disto: `importMwbProgram` e o login com Google usavam
 * `EventQueue.invokeLater`, e o comentário em `Main.kt:348-349` diz que estado
 * do Compose só recompoe na EDT. A regra estava escrita e não era seguida.
 *
 * Por que um objeto com uma função só: para ter **um** lugar. Se cada thread
 * lembrar deInvokeLater, a regra continua dependendo de memória. Concentrando,
 * o que está fora da EDT é o que não passou por aqui, e dá para testar.
 */
object Edt {

    /**
     * Publica o bloco na thread de UI.
     *
     * Pode ser chamado de qualquer thread. Se já estiver na EDT, o bloco roda
     * no próximo ciclo do event loop em vez de na hora — por isso [naEdt] existe
     * para quem precisar decidir entre os dois.
     */
    fun publica(bloco: () -> Unit) {
        EventQueue.invokeLater(bloco)
    }

    /** Estamos na thread de UI? Para teste, e para o caso que queira saber. */
    fun naEdt(): Boolean = EventQueue.isDispatchThread()
}