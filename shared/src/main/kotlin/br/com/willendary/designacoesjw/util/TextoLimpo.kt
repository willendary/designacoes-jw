package br.com.willendary.designacoesjw.util

/**
 * Limpeza de texto que o app mostra fora da tela (#49).
 *
 * ## Caractere de controle
 *
 * Vem de importa\u00e7\u00e3o, de colar e de nuvem antiga. N\u00e3o tem glifo, e o efeito \u00e9
 * vis\u00edvel: o PDF sai com caixa vazia no meio do nome e ningu\u00e9m sabe por qu\u00ea.
 * `\t`, `\n` e `\r` ficam: um tema copiado com quebra de linha continua leg\u00edvel,
 * e cortar isso seria trocar um defeito pequeno por outro.
 *
 * ## Espa\u00e7o n\u00e3o separ\u00e1vel
 *
 * `U+00A0` e `U+2007` s\u00e3o invis\u00edveis ao lado do espa\u00e7o comum e passam por
 * `trim()`. Se ficarem, "Encenacao " deixa de ser igual a "Encenacao" no
 * `==`, a compara\u00e7\u00e3o do item de programa falha, e o app duplica a parte.
 *
 * ## O que n\u00e3o \u00e9 removido
 *
 * **Acento e emoji.** "Jo\u00e3o" e "IRM\u00c3O" s\u00e3o v\u00e1lidos e a pessoa os digitou de prop\u00f3sito.
 * Remover \u00e9 o app decidindo que o texto dela estava errado.
 */
object TextoLimpo {

    /**
     * Caracteres de controle, **exceto** tab, LF e CR.
     *
     * \p{Cc} sozinho pegaria os tres tamb\u00e9m; a exce\u00e7\u00e3o \u00e9 o que os preserva.
     */
    private val CONTROLE = Regex("[\\p{Cc}&&[^\\t\\n\\r]]")

    fun limpar(valor: String): String =
        CONTROLE.replace(valor, " ")
            .replace('\u00A0', ' ')
            .replace('\u2007', ' ')
            .replace('\u202F', ' ')
            .trim()
}
