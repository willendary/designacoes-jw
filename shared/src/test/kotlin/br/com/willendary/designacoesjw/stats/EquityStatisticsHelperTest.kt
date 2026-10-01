package br.com.willendary.designacoesjw.stats

import br.com.willendary.designacoesjw.data.Assignment
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.Privilege
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EquityStatisticsHelperTest {

    @Test
    fun testCalculateMonthStats() {
        val month = YearMonth.of(2026, 10)
        val p1 = Privilege(1, "Indicador")
        val p2 = Privilege(2, "Som")
        val privileges = listOf(p1, p2)

        val b1 = Brother(1, "Irmão Ativo 1")
        val b2 = Brother(2, "Irmão Ativo 2")
        val b3 = Brother(3, "Irmão Sem Designação")
        val brothers = listOf(b1, b2, b3)

        val meetings = listOf(
            Meeting(1, "04/10/2026", "Reunião", listOf(Assignment(1, 1), Assignment(2, 2))),
            Meeting(2, "11/10/2026", "Reunião", listOf(Assignment(1, 1)))
        )

        val report = EquityStatisticsHelper.calculateMonthStats(month, meetings, brothers, privileges)

        assertEquals(2, report.totalMeetings)
        assertEquals(3, report.totalAssignments)

        // b1 tem 2 designações, b2 tem 1, b3 tem 0
        assertEquals(1, report.ranking[0].brother.id)
        assertEquals(2, report.ranking[0].count)
        assertEquals(2, report.ranking[1].brother.id)
        assertEquals(1, report.ranking[1].count)

        // b3 deve constar como não designado
        assertEquals(1, report.unassignedActiveBrothers.size)
        assertEquals(3, report.unassignedActiveBrothers[0].id)
    }

    @Test
    fun testZeroActiveBrothers() {
        val month = YearMonth.of(2026, 10)
        val privileges = listOf(Privilege(1, "Indicador"))
        val brothers = listOf(Brother(1, "Inativo", active = false))
        val meetings = emptyList<Meeting>()

        val report = EquityStatisticsHelper.calculateMonthStats(month, meetings, brothers, privileges)

        assertTrue(report.ranking.isEmpty())
        assertEquals(null, report.averageAssignmentsPerAssignedBrother)
    }

    @Test
    fun testAllBrothersUnassigned() {
        val month = YearMonth.of(2026, 10)
        val privileges = listOf(Privilege(1, "Indicador"))
        val brothers = listOf(Brother(1, "Irmão 1"), Brother(2, "Irmão 2"))
        val meetings = emptyList<Meeting>()

        val report = EquityStatisticsHelper.calculateMonthStats(month, meetings, brothers, privileges)

        assertEquals(2, report.ranking.size)
        assertEquals(null, report.averageAssignmentsPerAssignedBrother)
    }

    @Test
    fun testAverageOnlyOverAssignedBrothers() {
        val month = YearMonth.of(2026, 10)
        val p1 = Privilege(1, "Indicador")
        val privileges = listOf(p1)
        val brothers = listOf(
            Brother(1, "Irmão 1"),
            Brother(2, "Irmão 2"),
            Brother(3, "Irmão 3")
        )
        val meetings = listOf(
            Meeting(1, "04/10/2026", "Reunião", listOf(Assignment(1, 1), Assignment(1, 2)))
        )

        val report = EquityStatisticsHelper.calculateMonthStats(month, meetings, brothers, privileges)

        // b1 e b2 têm 1 designação cada; b3 tem 0. Média = 2 / 2 = 1.0 (b3 não dilui).
        assertEquals(1.0, report.averageAssignmentsPerAssignedBrother)
    }

    @Test
    fun `reunioes de outro mes nao entram na estatistica`() {
        val priv = listOf(Privilege(1, "Som"))
        val irmaos = listOf(Brother(1, "Joao"), Brother(2, "Maria"))
        val reunioes = listOf(
            Meeting(1, "04/10/2026", "Meio de semana", listOf(Assignment(1, 1))),
            Meeting(2, "28/09/2026", "Meio de semana", listOf(Assignment(1, 1), Assignment(1, 2)))
        )

        val relatorio = EquityStatisticsHelper.calculateMonthStats(YearMonth.of(2026, 10), reunioes, irmaos, priv)

        assertEquals(1, relatorio.totalMeetings, "só outubro conta")
        assertEquals(1, relatorio.totalAssignments)
        // Maria só apareceu em setembro: fica sem designação no mês.
        assertEquals(listOf(2L), relatorio.unassignedActiveBrothers.map { it.id })
    }

    @Test
    fun `empate de designacoes e desfeito pelo nome normalizado`() {
        val priv = listOf(Privilege(1, "Som"))
        val irmaos = listOf(
            Brother(1, "Zé"),
            Brother(2, "Ana")
        )
        val reunioes = listOf(
            Meeting(1, "04/10/2026", "Meio de semana", listOf(Assignment(1, 1))),
            Meeting(2, "11/10/2026", "Meio de semana", listOf(Assignment(1, 2)))
        )

        val relatorio = EquityStatisticsHelper.calculateMonthStats(YearMonth.of(2026, 10), reunioes, irmaos, priv)

        // Ambos com 1: a ordem é alfabética pelo nome normalizado (ana < zé),
        // e não pela ordem de cadastro.
        assertEquals(listOf(2L, 1L), relatorio.ranking.map { it.brother.id })
    }

    @Test
    fun `totais por privilegio vem ordenados por nome`() {
        val priv = listOf(Privilege(1, "Som"), Privilege(2, "Áudio"), Privilege(3, "Mesa"))
        val irmaos = listOf(Brother(1, "Joao"))
        val reunioes = listOf(
            Meeting(
                1, "04/10/2026", "Meio de semana",
                listOf(Assignment(1, 1), Assignment(2, 1), Assignment(2, 1), Assignment(3, 1))
            )
        )

        val relatorio = EquityStatisticsHelper.calculateMonthStats(YearMonth.of(2026, 10), reunioes, irmaos, priv)

        assertEquals(mapOf("Áudio" to 2, "Mesa" to 1, "Som" to 1), relatorio.privilegeTotals)
        assertEquals(listOf("Áudio", "Mesa", "Som"), relatorio.privilegeTotals.keys.toList())
    }
}
