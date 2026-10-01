package br.com.willendary.designacoesjw.export

import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.Privilege
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

object HtmlReportGenerator {

    fun generateHtml(
        month: YearMonth,
        meetings: List<Meeting>,
        brothers: List<Brother>,
        privileges: List<Privilege>,
        hoje: LocalDate = LocalDate.now()
    ): String {
        val activePrivileges = privileges.filter { it.active }.sortedBy { it.id }
        val sortedMeetings = meetings.sortedBy { AssignmentGenerator.parseDate(it.date) }
        val monthLabel = month.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
            .uppercase(Locale("pt", "BR")) + " ${month.year}"

        return buildString {
            append("<!DOCTYPE html>\n<html lang=\"pt-BR\">\n<head>\n<meta charset=\"UTF-8\">\n")
            append("<title>Designações — $monthLabel</title>\n")
            append("<style>\n")
            append("@page { size: landscape; margin: 1cm; }\n")
            append("body { font-family: 'Segoe UI', Arial, Helvetica, sans-serif; margin: 0; padding: 20px; color: #1f2937; background: #ffffff; }\n")
            append("h1 { text-align: center; font-size: 22pt; margin-bottom: 20px; color: #1e3a8a; }\n")
            append("table { width: 100%; border-collapse: collapse; table-layout: fixed; margin-top: 10px; }\n")
            append("th, td { border: 1px solid #9ca3af; padding: 8px 6px; text-align: center; vertical-align: middle; font-size: 9.5pt; word-break: break-word; }\n")
            append("th { font-weight: bold; background: #e5e7eb; color: #111827; }\n")
            append("th.date-col, td.date-col { width: 105px; font-weight: 600; background: #f3f4f6; }\n")
            append("tr:nth-child(even) { background-color: #fafafa; }\n")
            append(".badge { display: inline-block; padding: 2px 6px; border-radius: 4px; font-size: 8pt; font-weight: 600; }\n")
            append("@media print { body { padding: 0; } }\n")
            append("</style>\n</head>\n<body>\n")
            append("<h1>DESIGNAÇÕES — $monthLabel</h1>\n")
            append("<table>\n<thead>\n<tr>\n<th class=\"date-col\">Data</th>\n")
            activePrivileges.forEach { privilege ->
                append("<th>${xmlEscape(privilege.name)}</th>\n")
            }
            append("</tr>\n</thead>\n<tbody>\n")

            sortedMeetings.forEach { meeting ->
                val date = AssignmentGenerator.parseDate(meeting.date)
                val weekday = if (date != LocalDate.MIN) {
                    date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
                        .removeSuffix("-feira")
                        .replaceFirstChar { it.uppercase(Locale("pt", "BR")) }
                } else ""

                append("<tr>\n")
                append("<td class=\"date-col\">${xmlEscape(meeting.date)}<br><small style=\"color:#4b5563;\">${xmlEscape(weekday)}</small></td>\n")

                activePrivileges.forEach { privilege ->
                    val names = meeting.assignments
                        .filter { it.privilegeId == privilege.id }
                        .mapNotNull { a -> brothers.find { it.id == a.brotherId }?.name }
                        .map { xmlEscape(it) }

                    val cellContent = if (names.isEmpty()) "—" else names.joinToString("<br>")
                    append("<td>$cellContent</td>\n")
                }
                append("</tr>\n")
            }

            append("</tbody>\n</table>\n")
            append("<footer style=\"margin-top: 24px; text-align: right; font-size: 8pt; color: #6b7280;\">")
            append("Gerado pelo Designações JW em ${hoje.format(AssignmentGenerator.DATE_FORMATTER)}")
            append("</footer>\n")
            append("</body>\n</html>")
        }
    }

    private fun xmlEscape(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
