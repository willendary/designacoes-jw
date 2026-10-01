package br.com.willendary.designacoesjw.util

import java.text.Collator
import java.util.Locale

/**
 * Ordem de exibição de texto em português.
 *
 * A ordem natural de `String` compara pontos de código, então "Áudio" (U+00C1)
 * fica depois de "Z". Numa lista de privilégios ou irmãos, quem lê espera
 * "Áudio" no começo — o mesmo que um dicionário faz. `Collator` com locale
 * pt-BR dá essa ordem.
 *
 * Isto é **exibição**. Para comparar nomes e decidir se são o mesmo irmão, use
 * `AssignmentGenerator.normalizeName`, que remove acentos e usa `Locale.ROOT`
 * de propósito: ali a comparação tem de ser estável entre aparelhos.
 */
object TextOrder {
    private val collator: Collator = Collator.getInstance(Locale("pt", "BR")).apply {
        strength = Collator.SECONDARY // distingue maiúscula de minúscula só no desempate
    }

    private val normalizer: Collator = Collator.getInstance(Locale.ROOT).apply {
        strength = Collator.PRIMARY
    }

    /** Comparador para `sortedWith` em listas que o usuário lê. */
    val ptBr: Comparator<String> = Comparator { a, b -> collator.compare(a, b) }

    /** Ignora acentos e maiúsculas — para agrupar variantes do mesmo nome. */
    val accentInsensitive: Comparator<String> = Comparator { a, b -> normalizer.compare(a, b) }

    fun sort(values: Collection<String>): List<String> = values.sortedWith(ptBr)
}
