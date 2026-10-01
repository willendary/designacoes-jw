package br.com.willendary.designacoesjw.export

import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.Privilege
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object IcsExportHelper {

    private val inputDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val icsDateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    private const val TIMEZONE_ID = "America/Sao_Paulo"

    fun generateIcs(
        meetings: List<Meeting>,
        brothers: List<Brother>,
        privileges: List<Privilege>
    ): String {
        val sb = StringBuilder()
        appendLine(sb, "BEGIN:VCALENDAR")
        appendLine(sb, "VERSION:2.0")
        appendLine(sb, "PRODID:-//Designacoes JW//Designacoes JW Multiplataforma//PT")
        appendLine(sb, "CALSCALE:GREGORIAN")
        appendLine(sb, "METHOD:PUBLISH")
        appendLine(sb, "X-WR-CALNAME:Designações de Reunião JW")
        appendLine(sb, "X-WR-TIMEZONE:$TIMEZONE_ID")
        appendVTimezone(sb)

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
                    val priv = escapeIcsText(privileges.find { it.id == a.privilegeId }?.name ?: "Privilégio")
                    val bro = escapeIcsText(brothers.find { it.id == a.brotherId }?.name ?: "Irmão")
                    "• $priv: $bro"
                }
            }

            val summary = escapeIcsText("${m.type} - Designações")
            val description = "Designações:\\n$assignmentsText"
            val uid = "meeting-${m.id}-${dateStr}@designacoes-jw"

            appendLine(sb, "BEGIN:VEVENT")
            appendLine(sb, "UID:$uid")
            appendLine(sb, "DTSTAMP:${dateStr}T120000Z")
            appendLine(sb, "DTSTART;TZID=$TIMEZONE_ID:${dateStr}T$startTime")
            appendLine(sb, "DTEND;TZID=$TIMEZONE_ID:${dateStr}T$endTime")
            appendLine(sb, "SUMMARY:$summary")
            appendLine(sb, "DESCRIPTION:$description")
            appendLine(sb, "LOCATION:Salão do Reino das Testemunhas de Jeová")
            appendLine(sb, "STATUS:CONFIRMED")
            appendLine(sb, "CATEGORIES:REUNIÃO,DESIGNAÇÕES")
            appendLine(sb, "END:VEVENT")
        }

        appendLine(sb, "END:VCALENDAR")
        return sb.toString()
    }

    private fun appendVTimezone(sb: StringBuilder) {
        appendLine(sb, "BEGIN:VTIMEZONE")
        appendLine(sb, "TZID:$TIMEZONE_ID")
        appendLine(sb, "BEGIN:STANDARD")
        appendLine(sb, "DTSTART:19700101T000000")
        appendLine(sb, "TZOFFSETFROM:-0300")
        appendLine(sb, "TZOFFSETTO:-0300")
        appendLine(sb, "TZNAME:-03")
        appendLine(sb, "END:STANDARD")
        appendLine(sb, "END:VTIMEZONE")
    }

    private fun appendLine(sb: StringBuilder, line: String) {
        sb.append(foldLine(line)).append("\r\n")
    }

    private fun escapeIcsText(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\n", "\\n")
    }

    /**
     * Folding RFC 5545: linhas com mais de 75 octetos são quebradas com CRLF
     * seguido de um espaço (caractere de continuação).
     */
    private fun foldLine(line: String): String {
        val maxOctets = 75
        if (line.toByteArray(Charsets.UTF_8).size <= maxOctets) return line
        val sb = StringBuilder()
        var remaining = line
        var first = true
        while (remaining.isNotEmpty()) {
            val limit = if (first) maxOctets else maxOctets - 1
            val chunk = takeOctets(remaining, limit)
            if (!first) sb.append(' ')
            sb.append(chunk)
            remaining = remaining.substring(chunk.length)
            if (remaining.isNotEmpty()) sb.append("\r\n")
            first = false
        }
        return sb.toString()
    }

    private fun takeOctets(s: String, maxOctets: Int): String {
        var bytes = 0
        var i = 0
        while (i < s.length) {
            val charBytes = s.substring(i, i + 1).toByteArray(Charsets.UTF_8).size
            if (bytes + charBytes > maxOctets) break
            bytes += charBytes
            i++
        }
        return s.substring(0, i)
    }
}
