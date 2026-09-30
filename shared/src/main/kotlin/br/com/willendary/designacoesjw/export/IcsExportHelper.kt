package br.com.willendary.designacoesjw.export

import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.Privilege
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object IcsExportHelper {

    private val inputDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val icsDateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun generateIcs(
        meetings: List<Meeting>,
        brothers: List<Brother>,
        privileges: List<Privilege>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//Designacoes JW//Designacoes JW Multiplataforma//PT")
        sb.appendLine("CALSCALE:GREGORIAN")
        sb.appendLine("METHOD:PUBLISH")
        sb.appendLine("X-WR-CALNAME:Designações de Reunião JW")
        sb.appendLine("X-WR-TIMEZONE:America/Sao_Paulo")

        val sortedMeetings = meetings.sortedBy { m ->
            runCatching { LocalDate.parse(m.date, inputDateFormatter) }.getOrNull() ?: LocalDate.MIN
        }

        for (m in sortedMeetings) {
            val date = runCatching { LocalDate.parse(m.date, inputDateFormatter) }.getOrNull() ?: continue
            val dateStr = date.format(icsDateFormatter)

            val isWeekend = date.dayOfWeek.value in listOf(6, 7)
            val startTime = if (isWeekend) "093000" else "193000"
            val endTime = if (isWeekend) "111500" else "211500"

            val assignmentsText = if (m.assignments.isEmpty()) {
                "Nenhuma designação cadastrada."
            } else {
                m.assignments.joinToString("\\n") { a ->
                    val priv = privileges.find { it.id == a.privilegeId }?.name ?: "Privilégio"
                    val bro = brothers.find { it.id == a.brotherId }?.name ?: "Irmão"
                    "• $priv: $bro"
                }
            }

            val summary = escapeIcsText("${m.type} - Designações")
            val uid = "meeting-${m.id}-${dateStr}@designacoes-jw"

            sb.appendLine("BEGIN:VEVENT")
            sb.appendLine("UID:$uid")
            sb.appendLine("DTSTAMP:${dateStr}T120000Z")
            sb.appendLine("DTSTART:${dateStr}T$startTime")
            sb.appendLine("DTEND:${dateStr}T$endTime")
            sb.appendLine("SUMMARY:$summary")
            sb.appendLine("DESCRIPTION:Designações:\\n$assignmentsText")
            sb.appendLine("LOCATION:Salão do Reino das Testemunhas de Jeová")
            sb.appendLine("STATUS:CONFIRMED")
            sb.appendLine("CATEGORIES:REUNIÃO,DESIGNAÇÕES")
            sb.appendLine("END:VEVENT")
        }

        sb.appendLine("END:VCALENDAR")
        return sb.toString()
    }

    private fun escapeIcsText(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\n", "\\n")
    }
}
