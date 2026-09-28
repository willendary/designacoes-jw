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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ReportGenerator {
    fun sharePdf(context: Context, month: YearMonth, meetings: List<Meeting>, brothers: List<Brother>, privileges: List<Privilege>) =
        share(context, generatePdf(context, month, meetings, brothers, privileges), "application/pdf")

    fun shareDocx(context: Context, month: YearMonth, meetings: List<Meeting>, brothers: List<Brother>, privileges: List<Privilege>) =
        share(context, generateDocx(context, month, meetings, brothers, privileges), "application/vnd.openxmlformats-officedocument.wordprocessingml.document")

    private fun generatePdf(context: Context, month: YearMonth, meetings: List<Meeting>, brothers: List<Brother>, privileges: List<Privilege>): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "designacoes-${month.year}-${month.monthValue.toString().padStart(2, '0')}.pdf")
        val activePrivileges = privileges.filter { it.active }.sortedBy { it.id }
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
            canvas.drawText(meeting.date, x + 5f, y + 21f, cellPaint)
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

    private fun generateDocx(context: Context, month: YearMonth, meetings: List<Meeting>, brothers: List<Brother>, privileges: List<Privilege>): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "designacoes-${month.year}-${month.monthValue.toString().padStart(2, '0')}.docx")
        val activePrivileges = privileges.filter { it.active }.sortedBy { it.id }
        val sortedMeetings = meetings.sortedBy { parseDate(it.date) }
        val widths = buildList { add(1300); repeat(activePrivileges.size) { add(900) } }

        val body = buildString {
            append(paragraph("DESIGNAÇÕES — ${monthLabel(month)}", true, 28))
            append(paragraph("Relatório mensal de designações", false, 20))
            append("<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblLayout w:type=\"fixed\"/></w:tblPr>")
            append("<w:tr>")
            append(cell("Data", widths[0], true))
            activePrivileges.forEachIndexed { index, privilege -> append(cell(privilege.name, widths[index + 1], true)) }
            append("</w:tr>")
            sortedMeetings.forEach { meeting ->
                append("<w:tr>")
                append(cell(meeting.date, widths[0], false))
                activePrivileges.forEachIndexed { index, privilege ->
                    val names = meeting.assignments.filter { it.privilegeId == privilege.id }
                        .mapNotNull { a -> brothers.find { it.id == a.brotherId }?.name }
                    append(cell(if (names.isEmpty()) "—" else names.joinToString("\n"), widths[index + 1], false))
                }
                append("</w:tr>")
            }
            append("</w:tbl>")
        }

        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body
<w:sectPr><w:pgSz w:w="16838" w:h="11906" w:orient="landscape"/><w:pgMar w:top="720" w:right="720" w:bottom="720" w:left="720"/></w:sectPr>
</w:body></w:document>"""
        val styles = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:rPr><w:sz w:val="20"/></w:rPr></w:style></w:styles>"""

        ZipOutputStream(FileOutputStream(file)).use { zip ->
            addEntry(zip, "[Content_Types].xml", contentTypes())
            addEntry(zip, "_rels/.rels", rels())
            addEntry(zip, "word/document.xml", documentXml)
            addEntry(zip, "word/styles.xml", styles)
            addEntry(zip, "word/_rels/document.xml.rels", documentRels())
        }
        return file
    }

    private fun paragraph(text: String, bold: Boolean, size: Int): String {
        val weight = if (bold) "<w:b/>" else ""
        return "<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:rPr>$weight<w:sz w:val=\"$size\"/></w:rPr><w:t>${xmlEscape(text)}</w:t></w:r></w:p>"
    }

    private fun cell(text: String, width: Int, bold: Boolean): String {
        val weight = if (bold) "<w:b/>" else ""
        val paragraphs = text.split("\n").joinToString("") {
            "<w:p><w:r><w:rPr>$weight</w:rPr><w:t xml:space=\"preserve\">${xmlEscape(it)}</w:t></w:r></w:p>"
        }
        return "<w:tc><w:tcPr><w:tcW w:w=\"$width\" w:type=\"dxa\"/></w:tcPr>$paragraphs</w:tc>"
    }

    private fun contentTypes() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/></Types>"""
    private fun rels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
    private fun documentRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"></Relationships>"""

    private fun addEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
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

    private fun parseDate(value: String) = runCatching {
        java.time.LocalDate.parse(value, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }.getOrNull() ?: java.time.LocalDate.MIN

    private fun monthLabel(month: YearMonth): String =
        month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale("pt", "BR")).uppercase(Locale("pt", "BR")) + " ${month.year}"

    private fun xmlEscape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
