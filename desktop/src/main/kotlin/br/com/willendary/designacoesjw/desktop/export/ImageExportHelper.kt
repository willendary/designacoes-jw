package br.com.willendary.designacoesjw.desktop.export

import br.com.willendary.designacoesjw.data.*
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.imageio.ImageIO

object ImageExportHelper {

    fun generateMeetingCard(
        meeting: Meeting,
        brothers: List<Brother>,
        privileges: List<Privilege>,
        publicTalk: PublicTalk? = null,
        cleaningSchedule: CleaningSchedule? = null,
        cleaningGroup: FieldServiceGroup? = null
    ): BufferedImage {
        val width = 900
        val baseHeight = 350
        val itemsCount = meeting.assignments.size
        val hasTalk = publicTalk != null && (publicTalk.themeTitle.isNotBlank() || publicTalk.speakerName.isNotBlank())
        val hasCleaning = cleaningSchedule != null || cleaningGroup != null
        val extraHeight = (itemsCount * 42) + (if (hasTalk) 110 else 0) + (if (hasCleaning) 75 else 0)
        val height = baseHeight + extraHeight

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g2 = image.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        // Fundo
        g2.color = Color(0xF4, 0xF6, 0xF9)
        g2.fillRect(0, 0, width, height)

        // Card com bordas arredondadas e sombra suave
        val margin = 28
        val cardW = width - (margin * 2)
        val cardH = height - (margin * 2)

        g2.color = Color(0xDD, 0xE2, 0xEA)
        g2.fillRoundRect(margin + 3, margin + 5, cardW, cardH, 24, 24)

        g2.color = Color.WHITE
        g2.fillRoundRect(margin, margin, cardW, cardH, 24, 24)

        // Cabeçalho estilizado azul
        val headerH = 110
        val clip = g2.clip
        g2.clip = java.awt.geom.RoundRectangle2D.Float(margin.toFloat(), margin.toFloat(), cardW.toFloat(), headerH.toFloat(), 24f, 24f)
        val grad = GradientPaint(0f, margin.toFloat(), Color(0x15, 0x65, 0xC0), cardW.toFloat(), (margin + headerH).toFloat(), Color(0x0D, 0x47, 0xA1))
        g2.paint = grad
        g2.fillRect(margin, margin, cardW, headerH)
        g2.clip = clip

        // Título no cabeçalho
        g2.color = Color.WHITE
        g2.font = Font("Segoe UI", Font.BOLD, 26)
        g2.drawString("DESIGNAÇÕES DA REUNIÃO", margin + 30, margin + 46)

        val dateObj = AssignmentGenerator.parseDate(meeting.date)
        val weekday = if (dateObj != LocalDate.MIN) {
            dateObj.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
                .removeSuffix("-feira")
                .replaceFirstChar { it.uppercase(Locale("pt", "BR")) }
        } else ""

        g2.font = Font("Segoe UI", Font.PLAIN, 18)
        g2.color = Color(0xBB, 0xDE, 0xFB)
        g2.drawString("📅 ${meeting.date} ($weekday)  •  ${meeting.type}", margin + 30, margin + 82)

        var curY = margin + headerH + 30

        // Discurso Público (se houver)
        if (hasTalk && publicTalk != null) {
            g2.color = Color(0xEB, 0xF3, 0xFB)
            g2.fillRoundRect(margin + 20, curY, cardW - 40, 90, 16, 16)
            g2.color = Color(0x1E, 0x88, 0xE5)
            g2.drawRoundRect(margin + 20, curY, cardW - 40, 90, 16, 16)

            g2.color = Color(0x0D, 0x47, 0xA1)
            g2.font = Font("Segoe UI", Font.BOLD, 17)
            g2.drawString("🎤 DISCURSO PÚBLICO", margin + 36, curY + 28)

            g2.font = Font("Segoe UI", Font.PLAIN, 15)
            g2.color = Color(0x21, 0x21, 0x21)
            val themeStr = if (publicTalk.themeNumber != null) "Nº ${publicTalk.themeNumber} — \"${publicTalk.themeTitle}\"" else "\"${publicTalk.themeTitle}\""
            g2.drawString("Tema: $themeStr", margin + 36, curY + 54)

            val spkCong = if (publicTalk.speakerCongregation.isNotBlank()) " (${publicTalk.speakerCongregation})" else ""
            g2.drawString("Orador: ${publicTalk.speakerName}$spkCong", margin + 36, curY + 76)

            curY += 105
        }

        // Tabela de Designações
        g2.color = Color(0x37, 0x47, 0x4F)
        g2.font = Font("Segoe UI", Font.BOLD, 16)
        g2.drawString("Privilégio", margin + 30, curY)
        g2.drawString("Designado", margin + 380, curY)
        curY += 12

        g2.color = Color(0xCF, 0xD8, 0xDC)
        g2.drawLine(margin + 25, curY, margin + cardW - 25, curY)
        curY += 10

        meeting.assignments.forEachIndexed { idx, assign ->
            val privName = privileges.firstOrNull { it.id == assign.privilegeId }?.name ?: "Privilégio"
            val brothName = brothers.firstOrNull { it.id == assign.brotherId }?.name ?: "—"

            if (idx % 2 == 1) {
                g2.color = Color(0xF8, 0xF9, 0xFA)
                g2.fillRect(margin + 20, curY - 6, cardW - 40, 36)
            }

            g2.font = Font("Segoe UI", Font.BOLD, 15)
            g2.color = Color(0x26, 0x32, 0x38)
            g2.drawString(privName, margin + 30, curY + 18)

            g2.font = Font("Segoe UI", Font.PLAIN, 15)
            g2.color = Color(0x15, 0x65, 0xC0)
            g2.drawString(brothName, margin + 380, curY + 18)

            curY += 38
        }

        // Limpeza do Salão
        if (hasCleaning) {
            curY += 10
            g2.color = Color(0xFA, 0xFA, 0xFA)
            g2.fillRoundRect(margin + 20, curY, cardW - 40, 58, 14, 14)
            g2.color = Color(0xE0, 0xE0, 0xE0)
            g2.drawRoundRect(margin + 20, curY, cardW - 40, 58, 14, 14)

            val gName = cleaningGroup?.name ?: "Grupo da Limpeza"
            val dtl = if (!cleaningSchedule?.details.isNullOrBlank()) " (${cleaningSchedule?.details})" else ""
            g2.font = Font("Segoe UI", Font.BOLD, 15)
            g2.color = Color(0x2E, 0x7D, 0x32)
            g2.drawString("🧹 Limpeza do Salão:", margin + 36, curY + 34)

            g2.font = Font("Segoe UI", Font.PLAIN, 15)
            g2.color = Color(0x1B, 0x5E, 0x20)
            g2.drawString("$gName$dtl", margin + 210, curY + 34)
            curY += 68
        }

        // Rodapé
        g2.font = Font("Segoe UI", Font.PLAIN, 12)
        g2.color = Color(0x9E, 0x9E, 0x9E)
        g2.drawString("Gerado por Designações JW", margin + 30, height - margin - 15)

        g2.dispose()
        return image
    }

    fun saveToPngFile(image: BufferedImage, targetFile: File): File {
        targetFile.parentFile?.mkdirs()
        ImageIO.write(image, "PNG", targetFile)
        return targetFile
    }

    fun copyImageToClipboard(image: BufferedImage) {
        val transferable = TransferableImage(image)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(transferable, null)
    }

    private class TransferableImage(private val image: Image) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor
        override fun getTransferData(flavor: DataFlavor): Any {
            if (flavor != DataFlavor.imageFlavor) throw UnsupportedFlavorException(flavor)
            return image
        }
    }
}
