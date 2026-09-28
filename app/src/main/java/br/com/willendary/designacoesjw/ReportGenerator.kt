package br.com.willendary.designacoesjw

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.Privilege
import java.io.File
import java.io.FileOutputStream
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object ReportGenerator {
    fun sharePdf(context: Context, month: YearMonth, meetings: List<Meeting>, brothers: List<Brother>, privileges: List<Privilege>) =
        share(context, generatePdf(context, month, meetings, brothers, privileges), "application/pdf")

    fun shareDocx(context: Context, month: YearMonth, meetings: List<Meeting>, brothers: List<Brother>, privileges: List<Privilege>) =
        share(context, generateDoc(context, month, meetings, brothers, privileges), "application/msword")

    private fun generatePdf(context: Context, month: YearMonth, meetings: List<Meeting>, brothers: List<Brother>, privileges: List<Privilege>): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "designacoes-${month.year}-${month.monthValue.toString().padStart(2, '0')}.pdf")
        val activePrivileges = privileges.filter { it.active }.sortedBy { it.name.lowercase(Locale.getDefault()) }
        val sortedMeetings = meetings.sortedBy { parseDate(it.date) }
        val pdf = PdfDocument()
        val pageWidth = 842
        val pageHeight = 595
        val margin = 28f
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.7f }
        val dateWidth = 72f
        val privilegeWidth = if (activePrivileges.isEmpty()) 0f else (pageWidth - 2 * margin - dateWidth) / activePrivileges.size
        val rowHeight = 34f
        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = margin

        fun drawHeader() {
            canvas.drawText("DESIGNAÇÕES — ${monthLabel(month)}", margin, y + 20f, titlePaint)
            y += 48f
            var x = margin
            canvas.drawRect(x, y, x + dateWidth, y + rowHeight, linePaint)
            canvas.drawText("Data", x + 6f, y + 21f, headerPaint)
            x += dateWidth
            activePrivileges.forEach { privilege ->
                canvas.drawRect(x, y, x + privilegeWidth, y + rowHeight, linePaint)
                drawWrapped(canvas, privilege.name, x + 5f, y + 12f, privilegeWidth - 10f, headerPaint, 10f, 2)
                x += privilegeWidth
            }
            y += rowHeight
        }

        drawHeader()
        sortedMeetings.forEach { meeting ->
            if (y + rowHeight > pageHeight - margin) {
                pdf.finishPage(page)
                pageNumber++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = margin
                drawHeader()
            }
            var x = margin
            canvas.drawRect(x, y, x + dateWidth, y + rowHeight, linePaint)
            drawWrapped(canvas, dateWithWeekday(meeting.date), x + 5f, y + 12f, dateWidth - 10f, cellPaint, 9f, 2)
            x += dateWidth
            activePrivileges.forEach { privilege ->
                canvas.drawRect(x, y, x + privilegeWidth, y + rowHeight, linePaint)
                val names = meeting.assignments.filter { it.privilegeId == privilege.id }
                    .mapNotNull { assignment -> brothers.find { it.id == assignment.brotherId }?.name }
                drawWrapped(canvas, if (names.isEmpty()) "—" else names.joinToString("\n"), x + 5f, y + 11f, privilegeWidth - 10f, cellPaint, 9f, 3)
                x += privilegeWidth
            }
            y += rowHeight
        }
        pdf.finishPage(page)
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    private fun generateDoc(context: Context, month: YearMonth, meetings: List<Meeting>, brothers: List<Brother>, privileges: List<Privilege>): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "designacoes-${month.year}-${month.monthValue.toString().padStart(2, '0')}.doc")
        val activePrivileges = privileges.filter { it.active }.sortedBy { it.id }
        val sortedMeetings = meetings.sortedBy { parseDate(it.date) }

        val html = buildString {
            append("<html><head><meta charset='UTF-8'><style>")
            append("@page { size: landscape; margin: 1cm; }")
            append("body { font-family: Arial, sans-serif; }")
            append("h1 { text-align:center; font-size:20pt; }")
            append("table { width:100%; border-collapse:collapse; table-layout:fixed; }")
            append("th,td { border:1px solid #777; padding:6px; text-align:center; vertical-align:middle; font-size:10pt; }")
            append("th { font-weight:bold; background:#eeeeee; }")
            append("th:first-child,td:first-child { width:90px; }")
            append("</style></head><body>")
            append("<h1>DESIGNAÇÕES — ${monthLabel(month)}</h1>")
            append("<table><tr><th>Data</th>")
            activePrivileges.forEach { privilege -> append("<th>${xmlEscape(privilege.name)}</th>") }
            append("</tr>")
            sortedMeetings.forEach { meeting ->
                append("<tr><td>${xmlEscape(dateWithWeekday(meeting.date)).replace("\n", "<br>")}</td>")
                activePrivileges.forEach { privilege ->
                    val names = meeting.assignments.filter { it.privilegeId == privilege.id }
                        .mapNotNull { a -> brothers.find { it.id == a.brotherId }?.name }
                    val value = if (names.isEmpty()) "—" else names.joinToString("<br>")
                    append("<td>$value</td>")
                }
                append("</tr>")
            }
            append("</table></body></html>")
        }
        file.writeText(html, Charsets.UTF_8)
        return file
    }

    private fun drawWrapped(canvas: android.graphics.Canvas, text: String, x: Float, startY: Float, width: Float, paint: Paint, lineHeight: Float, maxLines: Int) {
        var currentY = startY
        text.split("\n").take(maxLines).forEach { paragraph ->
            var line = ""
            paragraph.split(" ").forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(candidate) > width && line.isNotEmpty()) {
                    canvas.drawText(line, x, currentY, paint)
                    currentY += lineHeight
                    line = word
                } else line = candidate
            }
            if (line.isNotEmpty() && currentY <= startY + lineHeight * (maxLines - 1)) {
                canvas.drawText(line, x, currentY, paint)
                currentY += lineHeight
            }
        }
    }

    private fun share(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Compartilhar relatório"))
    }

    private fun dateWithWeekday(value: String): String {
        val date = parseDate(value)
        if (date == java.time.LocalDate.MIN) return value
        val weekday = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale("pt", "BR"))
            .replaceFirstChar { it.uppercase(Locale("pt", "BR")) }
        return value + "\n" + weekday
    }

    private fun parseDate(value: String) = runCatching {
        java.time.LocalDate.parse(value, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }.getOrNull() ?: java.time.LocalDate.MIN

    private fun monthLabel(month: YearMonth): String =
        month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale("pt", "BR")).uppercase(Locale("pt", "BR")) + " ${month.year}"

    private fun xmlEscape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
