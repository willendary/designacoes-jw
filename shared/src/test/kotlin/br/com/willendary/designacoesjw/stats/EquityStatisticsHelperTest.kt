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
}
