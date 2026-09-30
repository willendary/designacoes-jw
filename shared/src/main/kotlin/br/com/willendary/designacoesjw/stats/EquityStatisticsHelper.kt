package br.com.willendary.designacoesjw.stats

import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.Privilege
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import java.time.YearMonth

data class BrotherAssignmentCount(
    val brother: Brother,
    val count: Int,
    val privilegesCount: Map<String, Int>
)

data class MonthEquityReport(
    val month: YearMonth,
    val totalMeetings: Int,
    val totalAssignments: Int,
    val ranking: List<BrotherAssignmentCount>,
    val unassignedActiveBrothers: List<Brother>,
    val privilegeTotals: Map<String, Int>
)

object EquityStatisticsHelper {

    fun calculateMonthStats(
        month: YearMonth,
        meetings: List<Meeting>,
        brothers: List<Brother>,
        privileges: List<Privilege>
    ): MonthEquityReport {
        val monthPrefix = month.format(java.time.format.DateTimeFormatter.ofPattern("MM/yyyy"))
        val monthMeetings = meetings.filter { it.date.endsWith("/$monthPrefix") }

        val assignments = monthMeetings.flatMap { it.assignments }
        val activeBrothers = brothers.filter { it.active }

        val brotherCounts = mutableMapOf<Long, Int>()
        val brotherPrivilegeCounts = mutableMapOf<Long, MutableMap<String, Int>>()
        val privilegeCounts = mutableMapOf<String, Int>()

        assignments.forEach { a ->
            brotherCounts[a.brotherId] = (brotherCounts[a.brotherId] ?: 0) + 1
            val privName = privileges.find { it.id == a.privilegeId }?.name ?: "Privilégio"
            privilegeCounts[privName] = (privilegeCounts[privName] ?: 0) + 1

            val map = brotherPrivilegeCounts.getOrPut(a.brotherId) { mutableMapOf() }
            map[privName] = (map[privName] ?: 0) + 1
        }

        val ranking = activeBrothers.map { b ->
            BrotherAssignmentCount(
                brother = b,
                count = brotherCounts[b.id] ?: 0,
                privilegesCount = brotherPrivilegeCounts[b.id] ?: emptyMap()
            )
        }.sortedWith(compareByDescending<BrotherAssignmentCount> { it.count }.thenBy { AssignmentGenerator.normalizeName(it.brother.name) })

        val unassigned = ranking.filter { it.count == 0 }.map { it.brother }

        return MonthEquityReport(
            month = month,
            totalMeetings = monthMeetings.size,
            totalAssignments = assignments.size,
            ranking = ranking,
            unassignedActiveBrothers = unassigned,
            privilegeTotals = privilegeCounts.toSortedMap()
        )
    }
}
