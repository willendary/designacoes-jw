package br.com.willendary.designacoesjw.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Limpeza de texto fora da tela (#49).
 *
 * Os caracteres especiais entram por `\uXXXX`, e n\u00e3o escritos direto: em
 * arquivo de texto eles se perdem no caminho, e o teste passa a verificar outra
 * coisa sem dar erro -- que \u00e9 como o defeito passou.
 */
class TextoLimpoTest {

    private val NBSP = "\u00A0"
    private val ESPACO_FINO = "\u2007"
    private val ESPACO_ESTREITO = "\u202F"

    @Test
    fun `mantem o texto normal`() {
        assertEquals("Joao Silva", TextoLimpo.limpar("Joao Silva"))
    }

    @Test
    fun `remove caractere de controle`() {
        // 0x07 (bell) e 0x1B (escape) sa\u00edram de alguma importa\u00e7\u00e3o. N\u00e3o t\u00eam
        // glifo: viram caixa vazia no PDF.
        val sujo = "Joao" + chr(0x07) + "Silva" + chr(0x1B)
        assertEquals("Joao Silva", TextoLimpo.limpar(sujo))
    }

    @Test
    fun `preserva tab e quebra de linha`() {
        assertEquals("linha1\nlinha2", TextoLimpo.limpar("linha1\nlinha2"))
        assertEquals("a\tb", TextoLimpo.limpar("a\tb"))
    }

    @Test
    fun `espaco nao separavel vira espaco comum`() {
        // Indistingu\u00edveis do espa\u00e7o na tela, e `==` entre o que foi
        // salvo e o que foi lido falha quando um deles ficou com o outro.
        assertEquals("a b", TextoLimpo.limpar("a" + NBSP + "b"))
        assertEquals("a b", TextoLimpo.limpar("a" + ESPACO_FINO + "b"))
        assertEquals("a b", TextoLimpo.limpar("a" + ESPACO_ESTREITO + "b"))
    }

    @Test
    fun `trim normal e especial`() {
        assertEquals("x", TextoLimpo.limpar(NBSP + "x" + NBSP))
    }

    @Test
    fun `vazio continua vazio`() {
        assertEquals("", TextoLimpo.limpar(""))
        assertEquals("", TextoLimpo.limpar("   "))
    }

    @Test
    fun `nao apaga texto visivel`() {
        // Acento e emoji s\u00e3o v\u00e1lidos e a pessoa digitou de prop\u00f3sito.
        val emoji = "\uD83D\uDCD6"
        assertEquals("IRM" + chr(0x00C3) + "O " + emoji, TextoLimpo.limpar("IRM" + chr(0x00C3) + "O " + emoji))
        assertEquals("Jo" + chr(0x00E3) + "o", TextoLimpo.limpar("Jo" + chr(0x00E3) + "o"))
    }

    private fun chr(c: Int): String = String(Character.toChars(c))
}
