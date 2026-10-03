package br.com.willendary.designacoesjw.sync

/**
 * Regra de segurança da sincronização: **nuvem vazia não apaga o aparelho.**
 *
 * Existe porque o caminho de escrita já era seguro e o de leitura não era.
 * `pushAndPrune` tratava "lista local vazia sobre coleção populada" como cache
 * incompleto; no sentido inverso não havia nada, e a nuvem devolvendo vazio —
 * permissão revogada, token expirado, regra mudada, coleção recriada — levava a
 * `save*Local(emptyList())` e a tela mostrava lista vazia sem explicação. É o
 * inverso do defeito que a #22 já tinha fechado num dos lados.
 *
 * A regra está isolada aqui, e não dentro do repositório, por dois motivos:
 *
 * - é **domínio**, não plataforma — vale igual no Android e no desktop;
 * - é testável. `AppRepository` exige `Context`, e uma trava de perda de dado
 *   que só se prova rodando no aparelho não é prova de nada.
 *
 * ## Por que não oferece "apagar mesmo assim"
 *
 * Seria a saída elegante, e não é necessária: quem quiser mesmo a lista vazia
 * **apaga no app**, e a remoção vai para a nuvem. O que não pode é a nuvem
 * decidir sozinha, porque ela é justamente a parte que pode estar errada.
 * Recusar o vazio não prende ninguém — só remove a decisão de quem não tem como
 * dizer o que aconteceu.
 */
object CloudGuard {

    /**
     * O aparelho deve manter o que tem, em vez de aceitar a lista vazia?
     *
     * @param itensDaNuvem o que o listener recebeu; vazio é o caso perigoso.
     * @param local o que está gravado no aparelho, ou `null` se nem deu para ler.
     */
    fun <T> deveManterLocal(itensDaNuvem: List<T>, local: List<T>?): Boolean =
        itensDaNuvem.isEmpty() && local != null && local.isNotEmpty()
}