package br.com.willendary.designacoesjw.export

import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.Privilege
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Relatório A4 de designações.
 *
 * O formato é **uma página por reunião**, em retrato, com as partes em lista.
 * Antes era uma matriz: uma linha por reunião e uma coluna por privilégio, em
 * paisagem. Com uma dúzia de irmãs e quinze privilégios isso dava dezesseis
 * colunas de cinco centímetros com os nomes quebrando no meio das palavras —
 * não é assim que ninguém lê uma lista de designações na congregação.
 */
object HtmlReportGenerator {

    fun generateHtml(
        month: YearMonth,
        meetings: List<Meeting>,
        brothers: List<Brother>,
        privileges: List<Privilege>,
        hoje: LocalDate = LocalDate.now()
    ): String {
        val sortedMeetings = meetings.sortedBy { AssignmentGenerator.parseDate(it.date) }
        val monthLabel = month.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
            .uppercase(Locale("pt", "BR")) + " ${month.year}"

        return buildString {
            append("<!DOCTYPE html>\n<html lang=\"pt-BR\">\n<head>\n<meta charset=\"UTF-8\">\n")
            append("<title>Designações — $monthLabel</title>\n")
            append("<style>\n")
            append("@page { size: portrait; margin: 1.1cm; }\n")
            append("body { font-family: 'Segoe UI', Arial, Helvetica, sans-serif; margin: 0; color: #1f2937; background: #fff; }\n")
            append("h1 { text-align: center; font-size: 16pt; margin: 0 0 2mm; color: #1e3a8a; }\n")
            append(".mes { text-align: center; font-size: 9pt; color: #6b7280; margin-bottom: 4mm; }\n")
            append(".reuniao { page-break-after: always; break-after: page; page-break-inside: avoid; break-inside: avoid; }\n")
            append(".reuniao:last-of-type { page-break-after: auto; break-after: auto; }\n")
            append(".cab { border-bottom: 2px solid #1e3a8a; padding-bottom: 1.5mm; margin-bottom: 3mm; }\n")
            append(".data { font-size: 13pt; font-weight: 700; color: #111827; }\n")
            append(".tipo { font-size: 8.5pt; color: #4b5563; }\n")
            append(".tema { font-size: 9.5pt; color: #1e3a8a; margin-top: 1mm; }\n")
            append(".secao { font-size: 7.5pt; font-weight: 700; color: #1e3a8a; text-transform: uppercase; letter-spacing: .4px; margin: 3mm 0 1.5mm; }\n")
            append("table { width: 100%; border-collapse: collapse; }\n")
            append("td { padding: 1.1mm 1mm; font-size: 9.5pt; vertical-align: top; border-bottom: 1px solid #e5e7eb; }\n")
            append("td.n { width: 7mm; text-align: right; color: #6b7280; font-size: 8.5pt; }\n")
            append("td.p { width: 14mm; text-align: right; color: #9ca3af; font-size: 8pt; white-space: nowrap; }\n")
            append("td.b { width: 48mm; text-align: right; font-size: 9.5pt; color: #1565c0; }\n")
            append("td.vazio { color: #9ca3af; }\n")
            append(".partes { margin-top: 3mm; }\n")
            append(".partes h2 { font-size: 9pt; margin: 0 0 1.5mm; color: #374151; text-transform: uppercase; letter-spacing: .4px; }\n")
            append(".partes td { border-bottom: 1px solid #f3f4f6; }\n")
            append(".rodape { margin-top: 4mm; font-size: 8pt; color: #6b7280; border-top: 1px solid #e5e7eb; padding-top: 1.5mm; }\n")
            append(".assinatura { margin-top: 8mm; font-size: 8.5pt; color: #374151; }\n")
            append("@media print { body { padding: 0; } }\n")
            append("</style>\n</head>\n<body>\n")
            append("<h1>DESIGNAÇÕES</h1>\n<div class=\"mes\">$monthLabel</div>\n")

            if (sortedMeetings.isEmpty()) {
                append("<p>Nenhuma reunião neste mês.</p>\n")
            }

            sortedMeetings.forEachIndexed { idx, meeting ->
                appendMeeting(meeting, brothers, privileges)
                if (idx < sortedMeetings.lastIndex) append("\n")
            }

            append("<div class=\"rodape\">Gerado pelo Designações JW em ${hoje.format(AssignmentGenerator.DATE_FORMATTER)}</div>\n")
            append("</body>\n</html>")
        }
    }

    private fun StringBuilder.appendMeeting(
        meeting: Meeting,
        brothers: List<Brother>,
        privileges: List<Privilege>
    ) {
        val date = AssignmentGenerator.parseDate(meeting.date)
        val weekday = if (date != LocalDate.MIN) {
            date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
                .replaceFirstChar { it.uppercase(Locale("pt", "BR")) }
        } else ""

        append("<div class=\"reuniao\">\n")
        append("<div class=\"cab\">\n")
        append("<div class=\"data\">${xmlEscape(meeting.date)}${if (weekday.isNotBlank()) " — $weekday" else ""}</div>\n")
        append("<div class=\"tipo\">${xmlEscape(meeting.type)}</div>\n")
        if (meeting.theme.isNotBlank()) {
            append("<div class=\"tema\">${xmlEscape(meeting.theme)}</div>\n")
        }
        append("</div>\n")

        appendProgram(meeting, brothers, privileges)
        appendDesignacoes(meeting, brothers, privileges)

        append("<div class=\"assinatura\">Responsável: ________________________________</div>\n")
        append("</div>\n")
    }

    /**
     * Programa com as partes e QUEM faz cada uma.
     *
     * A parte do programa **não** é um privilégio: quem faz vem de
     * `Meeting.programAssignments`, pela posição do item na lista. Sem isso a
     * fica com "—", que é o sinal de que o vínculo não foi definido.
     */
    private fun StringBuilder.appendProgram(
        meeting: Meeting,
        brothers: List<Brother>,
        privileges: List<Privilege>
    ) {
        if (meeting.program.isEmpty()) return
        // A parte do programa não é privilégio: quem faz vem de
        // Meeting.programAssignments, pela POSIÇÃO do item na lista.
        val byPosition: Map<Int, List<Long>> =
            meeting.programAssignments.associate { it.item to it.brotherIds }

        append("<div class=\"partes\">\n<h2>Programa</h2>\n<table>\n")
        meeting.program.groupBy { it.section }.forEach { (section, items) ->
            if (section.isNotBlank()) {
                append("<tr><td colspan=\"4\" class=\"secao\">${xmlEscape(section)}</td></tr>\n")
            }
            items.forEach { item ->
                val index = item.positionIn(meeting.program)
                val names = byPosition[index].orEmpty()
                    .mapNotNull { id -> brothers.firstOrNull { it.id == id }?.name }

                append("<tr>")
                append("<td class=\"n\">${item.number.takeIf { it > 0 } ?: ""}</td>")
                append("<td>${xmlEscape(item.title)}</td>")
                append("<td class=\"p\">${if (item.minutes > 0) "${item.minutes} min" else ""}</td>")
                append(
                    if (names.isEmpty()) {
                        "<td class=\"b vazio\">—</td>"
                    } else {
                        "<td class=\"b\">${xmlEscape(names.joinToString(" · "))}</td>"
                    }
                )
                append("</tr>\n")
            }
        }
        append("</table>\n</div>\n")
    }

    /** Lista de privilégios e designados, para quando não há programa importado. */
    private fun StringBuilder.appendDesignacoes(
        meeting: Meeting,
        brothers: List<Brother>,
        privileges: List<Privilege>
    ) {
        val rows = meeting.assignments.mapNotNull { a ->
            val p = privileges.firstOrNull { it.id == a.privilegeId } ?: return@mapNotNull null
            val name = brothers.firstOrNull { it.id == a.brotherId }?.name ?: return@mapNotNull null
            (p to name)
        }
        if (rows.isEmpty()) return

        append("<div class=\"partes\">\n<h2>Designações</h2>\n<table>\n")
        rows.forEach { (p, name) ->
            append("<tr>")
            append("<td></td>")
            append("<td>${xmlEscape(p.name)}</td>")
            append("<td class=\"p\">${if (p.quantity > 1) "${p.quantity}" else ""}</td>")
            append("<td class=\"b\">${xmlEscape(name)}</td>")
            append("</tr>\n")
        }
        append("</table>\n</div>\n")
    }

    private fun xmlEscape(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
