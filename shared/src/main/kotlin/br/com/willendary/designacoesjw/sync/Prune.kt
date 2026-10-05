package br.com.willendary.designacoesjw.sync

/**
 * O que fazer com o que sobrou no servidor e não está mais no aparelho.
 *
 * Push avisa **o que subiu**. Só a leitura da coleção diz **o que ficou lá**.
 * A diferença entre os dois é uma exclusão: quem apaga no app precisa que a
 * remoção vá para a nuvem, senão o pull seguinte traz o irmão, o privilégio ou a
 * reunião de volta e quem apagou fica achando que apagou.
 */
sealed interface Prune {

    /** Apagar estes ids do servidor. */
    data class Apagar(val ids: List<String>) : Prune

    /**
     * Não apagar nada, e [motivo] diz por quê para o usuário.
     *
     * Existe para o caso em que "não tem nada no aparelho" é **falha de
     * sincronização**, não intenção. Apagar o servidor com o aparelho vazio
     * apaga a congregação.
     */
    data class Preservar(val motivo: String) : Prune
}

/**
 * Decide o que apagar do servidor depois de um push.
 *
 * Três regras, nesta ordem:
 *
 * 1. Nada no servidor, nada a fazer.
 * 2. **Aparelho vazio sobre coleção populada** → [Prune.Preservar]. Lista local
 *    vazia é quase sempre cache incompleto ou erro de leitura, não remoção de
 *    todos os irmãos da congregação.
 * 3. Fora disso, apaga o que o servidor tem e o aparelho não tem mais.
 *
 * @param idsDoServidor todos os ids da coleção no servidor. **Tem que ser a
 *   lista completa**: uma lista truncada vira instrução de apagar tudo que não
 *   coube na página.
 * @param keep os ids que o aparelho tem agora — é a chave que o `push*` usou.
 * @param rotulo como chamar a coleção na mensagem de erro, em português.
 */
fun decidirPrune(idsDoServidor: List<String>, keep: Set<String>, rotulo: String): Prune {
    if (idsDoServidor.isEmpty()) return Prune.Preservar("não há nada no servidor")
    if (keep.isEmpty()) {
        return Prune.Preservar(
            "Não apaguei $rotulo do servidor: a lista local está vazia, " +
                "o que normalmente é falha de sincronização. Confira a lista antes de salvar de novo."
        )
    }
    val sobrando = idsDoServidor.filter { it !in keep }
    return if (sobrando.isEmpty()) Prune.Preservar("nada sobrando no servidor")
    else Prune.Apagar(sobrando)
}