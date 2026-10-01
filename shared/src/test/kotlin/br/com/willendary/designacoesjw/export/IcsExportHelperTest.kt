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
        assertTrue(ics.contains("DTSTART;TZID=America/Sao_Paulo:20261015T193000"))
        assertTrue(ics.contains("DTEND;TZID=America/Sao_Paulo:20261015T211500"))
        assertTrue(ics.contains("João Silva"))
        assertTrue(ics.contains("Indicador"))
        assertTrue(ics.contains("END:VEVENT"))
    }

    @Test
    fun testTzidAndVTimezonePresent() {
        val meeting = Meeting(100, "15/10/2026", "Meio de Semana", emptyList())
        val ics = IcsExportHelper.generateIcs(listOf(meeting), emptyList(), emptyList())

        assertTrue(ics.contains("BEGIN:VTIMEZONE"))
        assertTrue(ics.contains("TZID:America/Sao_Paulo"))
        assertTrue(ics.contains("END:VTIMEZONE"))
        assertTrue(ics.contains("DTSTART;TZID=America/Sao_Paulo:20261015T193000"))
        assertTrue(ics.contains("DTEND;TZID=America/Sao_Paulo:20261015T211500"))
    }

    @Test
    fun testDescriptionEscapesSpecialChars() {
        val p1 = Privilege(1, "Indicador")
        val meeting = Meeting(100, "15/10/2026", "Meio de Semana", listOf(Assignment(1, 1)))

        // ; deve ser escapado
        val ics1 = IcsExportHelper.generateIcs(listOf(meeting), listOf(Brother(1, "João; Silva")), listOf(p1))
        assertTrue(ics1.contains("João\\; Silva"))

        // , deve ser escapado
        val ics2 = IcsExportHelper.generateIcs(listOf(meeting), listOf(Brother(1, "João, Silva")), listOf(p1))
        assertTrue(ics2.contains("João\\, Silva"))

        // \ deve ser escapado
        val ics3 = IcsExportHelper.generateIcs(listOf(meeting), listOf(Brother(1, "João\\Silva")), listOf(p1))
        assertTrue(ics3.contains("João\\\\Silva"))

        // quebra de linha deve ser escapada
        val ics4 = IcsExportHelper.generateIcs(listOf(meeting), listOf(Brother(1, "João\nSilva")), listOf(p1))
        assertTrue(ics4.contains("João\\nSilva"))
    }

    @Test
    fun testLongLineIsFolded() {
        val longName = "Irmão com um nome muito longo ".repeat(10)
        val b1 = Brother(1, longName)
        val p1 = Privilege(1, "Indicador")
        val meeting = Meeting(100, "15/10/2026", "Meio de Semana", listOf(Assignment(1, 1)))

        val ics = IcsExportHelper.generateIcs(listOf(meeting), listOf(b1), listOf(p1))

        // Deve haver folding (CRLF + espaço de continuação).
        assertTrue(ics.contains("\r\n "), "esperava folding de linha longa")

        // Nenhuma linha pode exceder 75 octetos (RFC 5545).
        ics.split("\r\n").forEach { line ->
            assertTrue(
                line.toByteArray(Charsets.UTF_8).size <= 75,
                "linha excede 75 octetos: $line"
            )
        }
    }
}
