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
}
