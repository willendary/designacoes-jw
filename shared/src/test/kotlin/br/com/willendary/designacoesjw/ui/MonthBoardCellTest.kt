package br.com.willendary.designacoesjw.ui

import br.com.willendary.designacoesjw.data.Privilege
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regras de célula do quadro do mês (#52).
 *
 * A regra que merece teste é a distinção entre **célula vazia** e **"sem
 * designação"**. Ela não é cosmetics: vazia diz "o privilégio não vale
 * nesse dia", e "sem designação" diz "valia e ninguém foi designado", que é
 * um buraco. As duas colapsadas num "—", o buraco sumia da parede do salão
 * sem ninguém perceber.
 */
class MonthBoardCellTest {

    // 07/10/2026 é quarta-feira. Dia 3 = quarta (DayOfWeek.WEDNESDAY.value = 3).
    private val quarta = LocalDate.of(2026, 10, 7)

    private val soQuarta = Privilege(
        id = 1, name = "Leitor da Sentinela", allowedDays = setOf(3)
    )
    private val qualquerDia = Privilege(id = 2, name = "Som")

    @Test
    fun `privilegio que nao vale no dia deixa a celula vazia`() {
        val domingo = LocalDate.of(2026, 10, 4)
        assertEquals("", textoDaCelula(soQuarta, domingo, emptyList()))
        assertEquals("", textoDaCelula(soQuarta, domingo, listOf("Carlos")))
        // Vazio e sem destaque: o privilégio não valia naquele dia, então não
        // há buraco a apontar. Destacar aqui seria gritar com quem não fez
        // nada de errado.
        assertFalse(destaqueDaCelula(soQuarta, domingo, emptyList()))
        assertFalse(destaqueDaCelula(soQuarta, domingo, listOf("Carlos")))
    }

    @Test
    fun `privilegio que vale e ficou sem gente diz sem designacao`() {
        assertEquals("sem designação", textoDaCelula(qualquerDia, quarta, emptyList()))
        assertTrue(
            destaqueDaCelula(qualquerDia, quarta, emptyList()),
            "buraco de designacao precisa aparecer, nao sumir"
        )
    }

    @Test
    fun `privilegio com gente mostra os nomes, um por linha`() {
        assertEquals("Carlos", textoDaCelula(qualquerDia, quarta, listOf("Carlos")))
        assertEquals("Carlos\nDaniel", textoDaCelula(qualquerDia, quarta, listOf("Carlos", "Daniel")))
        // Com gente, não é buraco.
        assertFalse(destaqueDaCelula(qualquerDia, quarta, listOf("Carlos")))
    }

    @Test
    fun `data invalida mostra o buraco em vez de esconder`() {
        // Data quebrada não tem dia da semana, então não dá para saber se o
        // privilégio valia. O lado seguro é mostrar a falta.
        assertEquals("sem designação", textoDaCelula(soQuarta, LocalDate.MIN, emptyList()))
        assertTrue(destaqueDaCelula(soQuarta, LocalDate.MIN, emptyList()))
    }

    @Test
    fun `celula vazia nunca e confundida com sem designacao`() {
        val domingo = LocalDate.of(2026, 10, 4)
        val tresCasos = listOf(
            textoDaCelula(soQuarta, domingo, emptyList()),      // não vale
            textoDaCelula(qualquerDia, quarta, emptyList()),   // buraco
            textoDaCelula(qualquerDia, quarta, listOf("Ana"))  // designado
        )
        assertEquals(3, tresCasos.toSet().size, "os três estados precisam ser diferentes entre si: $tresCasos")
    }

}
