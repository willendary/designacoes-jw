package br.com.willendary.designacoesjw.export

import br.com.willendary.designacoesjw.data.Assignment
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.Privilege
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HtmlReportGeneratorTest {

    @Test
    fun testBrotherNameIsEscaped() {
        val brother = Brother(1, "Ana & Bia <teste> \"aspas\" 'apostrofo'")
        val privilege = Privilege(1, "Indicador")
        val meeting = Meeting(1, "15/10/2026", "Meio de Semana", listOf(Assignment(1, 1)))

        val html = HtmlReportGenerator.generateHtml(
            month = YearMonth.of(2026, 10),
            meetings = listOf(meeting),
            brothers = listOf(brother),
            privileges = listOf(privilege)
        )

        // O nome cru não deve aparecer; deve estar escapado.
        assertFalse(html.contains("Ana & Bia <teste>"))
        assertTrue(html.contains("Ana &amp; Bia &lt;teste&gt; &quot;aspas&quot; &apos;apostrofo&apos;"))
    }

    @Test
    fun testGenerateHtmlIsReproducibleWithFixedDate() {
        val brother = Brother(1, "João Silva")
        val privilege = Privilege(1, "Indicador")
        val meeting = Meeting(1, "15/10/2026", "Meio de Semana", listOf(Assignment(1, 1)))
        val hoje = LocalDate.of(2026, 10, 1)

        val html1 = HtmlReportGenerator.generateHtml(
            month = YearMonth.of(2026, 10),
            meetings = listOf(meeting),
            brothers = listOf(brother),
            privileges = listOf(privilege),
            hoje = hoje
        )
        val html2 = HtmlReportGenerator.generateHtml(
            month = YearMonth.of(2026, 10),
            meetings = listOf(meeting),
            brothers = listOf(brother),
            privileges = listOf(privilege),
            hoje = hoje
        )

        assertEquals(html1, html2)
        assertTrue(html1.contains("01/10/2026"))
    }
}
