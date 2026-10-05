package br.com.willendary.designacoesjw.sync

import br.com.willendary.designacoesjw.data.Store

/**
 * O que o pull fez com o que estava no aparelho.
 *
 * @param store o que vale aplicar na tela.
 * @param preservadas as coleções que vieram vazias da nuvem e foram mantidas do
 *   aparelho. Vazio é o caminho normal; com elemento, é algo que o usuário
 *   precisa saber.
 */
data class MergeComCloud(
    val store: Store,
    val preservadas: List<String>
)

/**
 * Junta o que veio da nuvem com o que está no aparelho, **coleção por coleção**.
 *
 * O pull do desktop substitui o [Store] inteiro, e é por isso que o vazio da
 * nuvem é perigoso: permissão revogada, token vencido, regra mudada ou coleção
 * recriada chegam como lista vazia, e a tela ficava sem os irmãos sem nenhuma
 * explicação. [CloudGuard] já existia para travar isso, com teste — mas só era
 * usado no Android, sete vezes. No desktop a proteção era um `||` sobre três
 * coleções:
 *
 * ```
 * cloudStore.brothers.isNotEmpty() || cloudStore.meetings.isNotEmpty() || ...
 * ```
 *
 * Com nuvem-populada só em `publicTalks`, a condição passava, o Store inteiro
 * substituía o local, e os irmãos sumiam da tela. Uma coleção vazia decidindo
 * sobre todas.
 *
 * Por coleção: cada lista segue [CloudGuard.deveManterLocal], e as listas não
 * são listas de entidade — são coleções diferentes, cada uma com seu nome em
 * português para a mensagem.
 *
 * @param cloud o que o servidor devolveu.
 * @param local o que está gravado no aparelho agora.
 */
fun mesclarCloudComLocal(cloud: Store, local: Store): MergeComCloud {
    val preservadas = mutableListOf<String>()

    fun <T> colecao(nome: String, daNuvem: List<T>, doAparelho: List<T>): List<T> =
        if (CloudGuard.deveManterLocal(daNuvem, doAparelho)) {
            preservadas += nome
            doAparelho
        } else {
            daNuvem
        }

    val mesclado = cloud.copy(
        brothers = colecao("irmãos", cloud.brothers, local.brothers),
        privileges = colecao("privilégios", cloud.privileges, local.privileges),
        meetings = colecao("reuniões", cloud.meetings, local.meetings),
        publicTalks = colecao("discursos", cloud.publicTalks, local.publicTalks),
        fieldServiceGroups = colecao("grupos de campo", cloud.fieldServiceGroups, local.fieldServiceGroups),
        cleaningSchedules = colecao("escala de limpeza", cloud.cleaningSchedules, local.cleaningSchedules),
        // O tema é escolha de quem está neste aparelho, não estado da congregação:
        // não é dado da congregação e não faz sentido voltar da nuvem.
        themeMode = local.themeMode
    )

    return MergeComCloud(mesclado, preservadas)
}

/** A frase para quando a nuvem voltou vazia e o aparelho foi preservado. */
fun mensagemDeNuvemVazia(preservadas: List<String>): String =
    "A nuvem devolveu ${preservadas.joinToString(", ")} vazio e eu mantive o que está neste computador. " +
        "Se alguém mexeu na congregação em outro aparelho, sincronize de novo; se não, pode ignorar."