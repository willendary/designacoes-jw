package br.com.willendary.designacoesjw.stats

import br.com.willendary.designacoesjw.data.Assignment
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.ProgramAssignment
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tempo sem fazer parte do programa (#65).
 *
 * A distinção que o usuário pediu e que o relatório de equidade não cobre:
 * **parte do programa** não é **privilégio mecânico**. "Som" é cargo fixo; parte
 * é o que se distribui para que todos participem, e é aí que o silêncio é
 * sintoma.
 */
class TempoSemParteTest {

    private val hoje = java.time.LocalDate.of(2026, 10, 4)

    private fun reuniao(data: String, vararg nomes: Long) = Meeting(
        id = data.hashCode().toLong(),
        date = data,
        type = "Reunião de Meio de Semana",
        assignments = listOf(Assignment(1, 99L)),  // mecânico: não conta
        programAssignments = listOf(ProgramAssignment(item = 1, brotherIds = nomes.toList()))
    )

    private fun irmao(id: Long, nome: String, ativo: Boolean = true) = Brother(id, nome, active = ativo)

    @AfterTest
    fun soltarHoje() = TempoSemParte.restaurarHoje()

    @Test
    fun `acha a ultima vez que o irmao fez parte`() {
        TempoSemParte.fixarHoje(hoje)
        val s = TempoSemParte.calcular(
            meetings = listOf(
                reuniao("05/08/2026", 1L),
                reuniao("12/09/2026", 1L),
                reuniao("21/09/2026", 1L)
            ),
            brothers = listOf(irmao(1, "Carlos"))
        ).single()

        assertEquals(java.time.LocalDate.of(2026, 9, 21), s.ultimaParte)
        assertEquals(0, s.mesesSemParte)
        assertEquals("semana passada", s.rotulo())
    }

    @Test
    fun `privilegio mecanico NAO conta como parte`() {
        // A distinção do usuário. A reunião tem privilégio do mesmo irmão e
        // nenhuma parte: para esta tela ele não fez parte nenhuma.
        TempoSemParte.fixarHoje(hoje)
        val s = TempoSemParte.calcular(
            meetings = listOf(reuniao("05/10/2026", 1L)),
            brothers = listOf(irmao(1, "Carlos"))
        ).single()

        assertEquals(java.time.LocalDate.of(2026, 10, 5), s.ultimaParte)

        // E a reunião sem parte alguma não move nada:
        val s2 = TempoSemParte.calcular(
            meetings = listOf(
                Meeting(2, "28/09/2026", "Reunião de Fim de Semana",
                    assignments = listOf(Assignment(1, 1L)))
            ),
            brothers = listOf(irmao(1, "Carlos"))
        ).single()
        assertNull(s2.ultimaParte, "só privilégio mecânico não é parte")
    }

    @Test
    fun `nunca fez parte devolve nulo, e nao zero`() {
        // Ausência de dado não é contagem. "0 meses" diria que ele deixou de
        // fazer, e é mentira.
        TempoSemParte.fixarHoje(hoje)
        val s = TempoSemParte.calcular(emptyList(), listOf(irmao(1, "Carlos"))).single()
        assertNull(s.mesesSemParte)
        assertEquals("nunca", s.rotulo())
    }

    @Test
    fun `irmao que entrou este mes nao e acusado de nunca participar`() {
        // Sem a data de entrada, o relatório transformava entrada recente em
        // acusação. Este é o caso que a issue aponta.
        TempoSemParte.fixarHoje(hoje)
        val s = TempoSemParte.calcular(
            meetings = emptyList(),
            brothers = listOf(irmao(1, "Novo")),
            entrouEm = mapOf(1L to java.time.LocalDate.of(2026, 9, 20))
        ).single()

        assertEquals(0, s.mesesSemParte)
        assertEquals("entrou este mês", s.rotulo())
    }

    @Test
    fun `meses conta da ultima parte, nao do fim do mes`() {
        TempoSemParte.fixarHoje(hoje)
        val s = TempoSemParte.calcular(
            meetings = listOf(reuniao("10/07/2026", 1L)),
            brothers = listOf(irmao(1, "Carlos"))
        ).single()

        assertEquals(2, s.mesesSemParte)  // julho -> outubro
        assertEquals("2 meses", s.rotulo())
    }

    @Test
    fun `anos quando passa de doze meses`() {
        TempoSemParte.fixarHoje(hoje)
        val s = TempoSemParte.calcular(
            meetings = listOf(reuniao("10/09/2024", 1L)),
            brothers = listOf(irmao(1, "Carlos"))
        ).single()
        assertEquals("2 anos", s.rotulo())
    }

    @Test
    fun `irmao inativo nao entra`() {
        // Quem saiu da congregação não está "sem fazer parte", está fora.
        TempoSemParte.fixarHoje(hoje)
        val r = TempoSemParte.calcular(
            meetings = emptyList(),
            brothers = listOf(irmao(1, "Carlos", ativo = false))
        )
        assertTrue(r.isEmpty())
    }

    @Test
    fun `duas pessoas na mesma parte contam para as duas`() {
        TempoSemParte.fixarHoje(hoje)
        val r = TempoSemParte.calcular(
            meetings = listOf(reuniao("05/10/2026", 1L, 2L)),
            brothers = listOf(irmao(1, "Carlos"), irmao(2, "Daniel"))
        )
        assertEquals(2, r.count { it.ultimaParte != null })
    }

    @Test
    fun `ordenacao traz quem nunca fez parte primeiro`() {
        // É o caso mais grave, e é o que o zero esconde.
        TempoSemParte.fixarHoje(hoje)
        val r = TempoSemParte.calcular(
            meetings = listOf(
                reuniao("05/10/2026", 2L),   // Daniel: mes passado
                reuniao("05/09/2026", 1L)    // Carlos: ha mais
            ),
            brothers = listOf(irmao(1, "Carlos"), irmao(2, "Daniel"), irmao(3, "Elias"))
        )

        val ordem = TempoSemParte.ordenarPorTempoSemParte(r).map { it.irmao.name }
        assertEquals("Elias", ordem.first(), "quem nunca fez parte vem primeiro")
    }

    @Test
    fun `data invalida nao derruba o calculo`() {
        // O app tem data que vem de importação e de nuvem antiga.
        TempoSemParte.fixarHoje(hoje)
        val r = TempoSemParte.calcular(
            meetings = listOf(
                Meeting(1, "31/02/2026", "Reunião", programAssignments = listOf(ProgramAssignment(1, listOf(1L)))),
                reuniao("05/10/2026", 2L)
            ),
            brothers = listOf(irmao(1, "Carlos"), irmao(2, "Daniel"))
        )
        assertEquals(2, r.size)
        assertNull(r.first { it.irmao.id == 1L }.ultimaParte)
    }

    @Test
    fun `privilegio nao aparece na lista de quem falta`() {
        // Trava de regressão: se alguém voltar a usar `Meeting.assignments`,
        // o Carlos aparece como tendo feito parte e o defeito do #51 volta.
        TempoSemParte.fixarHoje(hoje)
        val comMecanico = TempoSemParte.calcular(
            meetings = listOf(Meeting(1, "05/10/2026", "Reunião", assignments = listOf(Assignment(1, 1L)))),
            brothers = listOf(irmao(1, "Carlos"))
        ).single()
        assertNull(comMecanico.ultimaParte)
    }
}