package br.com.willendary.designacoesjw.sync

/**
 * O que a tela mostra sobre a sincronização (#61).
 *
 * ## O problema que isto responde
 *
 * Ninguém sabia dizer se o que estava na tela **estava na nuvem** ou era cache
 * velho. Não é Hypothético: a trava do [CloudGuard] existia justamente porque a
 * nuvem podia vir vazia, e quem abriu o app viu a lista antiga e acreditou que
 * era o estado atual — porque, para a tela, indistinguível.
 *
 * ## Por que "pendente" é separado de "erro"
 *
 * São coisas diferentes e a pessoa precisa saber qual é qual:
 *
 * - **não enviada** — a edição está salva no aparelho, só não subiu. O dado
 *   está seguro; o que falta é chegar aos outros.
 * - **falha** — a nuvem recusou. Só aqui o dado pode estar em risco.
 *
 * Juntar os dois num "erro de sincronização" faz a pessoa criar receio de
 * trabalho que não perdeu.
 */
data class EstadoSincronizacao(
    /** Gravações disparadas e ainda sem confirmação. */
    val pendentes: Int = 0,
    /** Uma gravação está em curso neste momento. */
    val gravando: Boolean = false,
    /** Última falha de **escrita**, com o texto que já vai para o log. */
    val ultimaFalha: String? = null,
    /**
     * A última leitura veio do cache local, não do servidor.
     *
     * É o sinal que responde "é nuvem ou é cache velho", e vem do próprio
     * Firestore (`SnapshotMetadata.isFromCache`) em vez de dedução por relógio.
     */
    val somenteCache: Boolean = false
) {
    val rotulo: String
        get() = when {
            ultimaFalha != null -> "Falha ao enviar"
            // `gravando` **antes** de `pendentes`: enquanto a gravação está em
            // curso o contador já vale 1, e a tela dizia "1 alteração não
            // enviada" para algo que ela está enviando agora. "Sincronizando" é
            // o que está acontecendo.
            gravando -> "Sincronizando"
            // **Escrito por extenso, e não "altera" + plural.** Concatenar
            // quebra o português: "alteração" + "s" dá "alteraçãoes", e o
            // plural correto é "alteraçãoes". O erro passou e a tela mostrava
            // um plural que ninguém escreveu.
            pendentes == 1 -> "1 alteração não enviada"
            pendentes > 0 -> "$pendentes alteraçãoes não enviadas"
            somenteCache -> "Cache local"
            else -> "Sincronizado"
        }

    /** `true` quando a tela precisa mostrar alguma coisa. Silêncio é o estado bom. */
    val mereceAviso: Boolean get() = ultimaFalha != null || pendentes > 0 || gravando || somenteCache
}

/**
 * Contador de gravações à espera de confirmação.
 *
 * Existe porque `Task<Void>` do Firestore **completa assim que a escrita entra
 * no armazenamento local**, mesmo sem rede — então o Task sozinho não distingue
 * "subiu" de "está na fila do aparelho". Confiar nele seria mostrar
 * "Sincronizado" com um mês de alteraçãoes preso no celular.
 */
class ContadorSincronizacao {

    private var estado = EstadoSincronizacao()

    val atual: EstadoSincronizacao get() = estado

    fun emGravacao() {
        estado = estado.copy(pendentes = estado.pendentes + 1, gravando = true, ultimaFalha = null)
    }

    fun confirmada() {
        estado = estado.copy(
            pendentes = (estado.pendentes - 1).coerceAtLeast(0),
            gravando = false
        )
    }

    fun falhou(mensagem: String) {
        estado = estado.copy(
            // A falha **não** volta o contador: a escrita continua não enviada,
            // e é isso que a pessoa precisa saber.
            gravando = false,
            ultimaFalha = mensagem
        )
    }

    /** Leitura confirmada no servidor. */
    fun lidaDoServidor() {
        estado = estado.copy(somenteCache = false)
    }

    /** Leitura servida do cache: o que está na tela pode estar velho. */
    fun lidaDoCache() {
        estado = estado.copy(somenteCache = true)
    }

    /** Conectividade mudou: sem rede, o que está na fila continua na fila. */
    fun semRede() {
        estado = estado.copy(somenteCache = true)
    }
}