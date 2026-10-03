package br.com.willendary.designacoesjw.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * A paleta é a identidade visual do app (#29).
 *
 * Duas listas de cor — uma no `MainActivity` do Android, outra no `Main.kt` do
 * desktop — já tinham divergido. Estas travas existem para a próxima divergência
 * aparecer no teste, e não um ano depois em print de celular.
 */
class JwPaletteTest {

    @Test
    fun `as cinco paletas continuam sendo cinco`() {
        assertEquals(5, JwPalette.entries.size)
        assertEquals(
            listOf("Azul", "Verde", "Roxo", "Laranja", "Vinho"),
            JwPalette.entries.map { it.nome }
        )
    }

    @Test
    fun `indice fora da faixa nao quebra a tela`() {
        // O índice vem de SharedPreferences. Arquivo editado à mão, ou versão
        // antiga com mais temas, já produziu valor inválido — e indexar direto
        // daria crash na abertura.
        assertEquals(JwPalette.AZUL, JwPalette.porIndice(-1))
        assertEquals(JwPalette.AZUL, JwPalette.porIndice(99))
        assertEquals(JwPalette.AZUL, JwPalette.porIndice(0))
        assertEquals(JwPalette.VINHO, JwPalette.porIndice(4))
    }

    @Test
    fun `o indice preserva a ordem que o usuario escolheu`() {
        // `theme_index` gravado precisa continuar apontando para a mesma cor.
        JwPalette.entries.forEachIndexed { indice, paleta ->
            assertEquals(paleta, JwPalette.porIndice(indice), "ordem mudou na paleta $paleta")
        }
    }

    @Test
    fun `primaria e secundaria nunca sao a mesma cor`() {
        // Cor primária e secundária iguais deixa a hierarquia visual achatada:
        // não há o que distinguir destaque de apoio.
        JwPalette.entries.forEach { paleta ->
            val (primaria, secundaria) = paleta.claro
            assertNotEquals(primaria, secundaria, "paleta ${paleta.nome} no claro")
            val (primariaEsc, secundariaEsc) = paleta.escuro
            assertNotEquals(primariaEsc, secundariaEsc, "paleta ${paleta.nome} no escuro")
        }
    }

    @Test
    fun `a variante escura e mais clara, nao a mesma saturada`() {
        // Cor saturada em fundo escuro brilha e cansa. A variante escura usa o
        // tom claro da rampa; se alguém "simplificar" colocando a mesma, o
        // app fica lavado à noite.
        JwPalette.entries.forEach { paleta ->
            val primaria = paleta.claro.first
            val primariaEsc = paleta.escuro.first
            assertNotEquals(primaria, primariaEsc, "paleta ${paleta.nome}")
        }
    }

    @Test
    fun `nenhuma paleta tem cor transparente`() {
        // Falhar aqui é Primary transparente: tela branca, texto branco,
        // botão que parece não existir.
        JwPalette.entries.forEach { paleta ->
            listOf(paleta.claro.first, paleta.claro.second, paleta.escuro.first, paleta.escuro.second)
                .forEach { cor ->
                    assertEquals(1f, cor.alpha, "paleta ${paleta.nome} tem cor com alpha ${cor.alpha}")
                }
        }
    }
}