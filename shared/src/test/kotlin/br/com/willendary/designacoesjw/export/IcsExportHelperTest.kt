package br.com.willendary.designacoesjw.export

import br.com.willendary.designacoesjw.data.Assignment
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.Privilege
import kotlin.test.Test
import kotlin.test.assertTrue

class IcsExportHelperTest {

    @Test
    fun testGenerateIcs() {
        val b1 = Brother(1, "João Silva")
        val p1 = Privilege(1, "Indicador")
        val meeting = Meeting(
            id = 100,
            date = "15/10/2026",
            type = "Meio de Semana",
            assignments = listOf(Assignment(1, 1))
        )

        val ics = IcsExportHelper.generateIcs(listOf(meeting), listOf(b1), listOf(p1))

        assertTrue(ics.contains("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("END:VCALENDAR"))
        assertTrue(ics.contains("BEGIN:VEVENT"))
        assertTrue(ics.contains("SUMMARY:Meio de Semana - Designações"))
        assertTrue(ics.contains("DTSTART:20261015T193000"))
        assertTrue(ics.contains("DTEND:20261015T211500"))
        assertTrue(ics.contains("João Silva"))
        assertTrue(ics.contains("Indicador"))
        assertTrue(ics.contains("END:VEVENT"))
    }
}
