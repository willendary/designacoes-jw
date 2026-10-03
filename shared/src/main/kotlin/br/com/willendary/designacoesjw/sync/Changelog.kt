package br.com.willendary.designacoesjw.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * As novidades de cada versão, por plataforma.
 *
 * ## Por que um arquivo no repositório e não no Firestore
 *
 * A alternativa seria um documento `changelog/{versao}` no Firestore, que o app
 * já sincroniza. Duas razões para não ser:
 *
 * 1. **Semear Firestore exige credencial de administrador.** O arquivo é
 *    commitado, revisável no diff e editável sem buildar.
 * 2. **Regra de produção não se mexe por confortável.** Firestore já tem regra
 *    de ausência e a de `brothers` releases; cada mudança é chance de a nuvem
 *    negar a congregação inteira.
 *
 * ## Por que por plataforma
 *
 * O APK e o `.exe` do mesmo número **não são o mesmo app**. A 0.5.1, por
 * exemplo, só mexeu no login do desktop. Quem está no celular não precisa
 * ler nada daquilo — e ler é o primeiro passo para não atualizar.
 *
 * ## Formato
 *
 * ```json
 * { "0.6.2": { "android": ["..."], "desktop": ["..."] } }
 * ```
 *
 * Lista de texto, não markdown: o app não renderiza markdown, e quem lê isto
 * também é quem escreve no commit — não vale um parser para ganhar negrito.
 */
@Serializable
data class Changelog(
    /**
     * Versão sem o `v` → novidades. `"0.6.2": {...}`.
     *
     * O JSON é **achatado**, sem `{"versoes": ...}` em volta: o arquivo é
     * editado à mão e revisado no diff, e um embrulho só atrapalha quem lê.
     * Daí `lerChangelog` desserializar direto para `Map<String, Versao>`.
     */
    val versoes: Map<String, Versao>
) {
    constructor() : this(emptyMap())

    /**
     * O que mudou entre [versaoInstalada] e a mais recente deste changelog.
     *
     * Devolve da mais antiga para a mais nova: a pessoa quer saber a
     * cronologia, não a soma. E devolve **só a plataforma dela** — o resto não
     * existe para ela.
     */
    fun novidades(versaoInstalada: String, plataforma: Plataforma): List<Novidade> =
        novidades(versaoInstalada, plataforma, versaoMaisRecente())

    /** Como [novidades], mas até uma versão específica. Para teste. */
    fun novidades(versaoInstalada: String, plataforma: Plataforma, ate: String): List<Novidade> {
        // Instalado mais novo ou igual ao limite: nao ha o que mostrar. O
        // sinal importa: negativo e justamente "instalado esta atras", que e o
        // caso que interessa. Invertido, a lista vinha vazia sempre.
        val distancia = compararVersoes(versaoInstalada, ate)
        if (distancia >= 0) return emptyList()

        return versoes.keys
            .filter { compararVersoes(it, versaoInstalada) > 0 && compararVersoes(it, ate) <= 0 }
            .sortedWith(::ordenaPorVersao)
            .mapNotNull { versao ->
                val itens = versoes[versao]?.para(plataforma).orEmpty()
                if (itens.isEmpty()) null else Novidade(versao, itens)
            }
    }

    /** A versão mais alta declarada no arquivo. */
    fun versaoMaisRecente(): String =
        versoes.keys.maxWithOrNull(::ordenaPorVersao) ?: "0.0.0"

    companion object {
        /** Tolera campo desconhecido: um app antigo não deve quebrar com chave nova. */
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}

@Serializable
data class Versao(
    val android: List<String> = emptyList(),
    val desktop: List<String> = emptyList()
) {
    fun para(plataforma: Plataforma): List<String> = when (plataforma) {
        Plataforma.ANDROID -> android
        Plataforma.DESKTOP -> desktop
    }
}

/** Uma versão, com as novidades **daquela plataforma**. */
data class Novidade(val versao: String, val itens: List<String>)

enum class Plataforma { ANDROID, DESKTOP }

private fun ordenaPorVersao(a: String, b: String): Int = compararVersoes(a, b)

/**
 * Compara `"0.10.0"` com `"0.9.0"`.
 *
 * Existe porque comparar versão **como texto** é o erro clássico: `"0.10.0" <
 * "0.9.0"` porque `'1' < '9'`. Uma versão que nunca aparece é pior que uma que
 * aparece trocada, e aqui a consequência seria esconder correção.
 *
 * Segmento que não é número vale 0. **Pré-release não é suportado** — o
 * projeto nunca publicou `0.5.1-rc1`, e inventar regra para um formato que não
 * existe é exatamente o tipo de coisa que depois diverge da realidade.
 */
fun compararVersoes(a: String, b: String): Int {
    val pa = a.trim().removePrefix("v").split(".")
    val pb = b.trim().removePrefix("v").split(".")

    for (i in 0 until maxOf(pa.size, pb.size)) {
        val va = pa.getOrNull(i).orEmpty().toIntOrNull() ?: 0
        val vb = pb.getOrNull(i).orEmpty().toIntOrNull() ?: 0
        if (va != vb) return va.compareTo(vb)
    }
    return 0
}

/**
 * Lê o `changelog.json`.
 *
 * Devolve `null` em vez de lançar: arquivo corrompido, ou versão nova com
 * formato diferente, **não podem** impedir o app de abrir. Changelog ausente é
 * motivo para não mostrar nada, nunca para crashar — é o que o
 * `crash-on-start` da 0.4.2 ensinou.
 */
fun lerChangelog(texto: String): Changelog? = try {
    Changelog(Changelog.json.decodeFromString(MapSerializer(String.serializer(), Versao.serializer()), texto))
} catch (_: Exception) {
    null
}