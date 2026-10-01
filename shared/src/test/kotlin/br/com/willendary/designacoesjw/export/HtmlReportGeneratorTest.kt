package br.com.willendary.designacoesjw.export

import br.com.willendary.designacoesjw.data.Assignment
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.Privilege
import br.com.willendary.designacoesjw.data.ProgramItem
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

    // ---- #42: uma pagina por reuniao, em retrato ----

    @Test
    fun testUmaPaginaPorReuniao() {
        val meetings = (1..3).map { i ->
            Meeting(i.toLong(), "0$i/10/2026", "Meio de Semana", emptyList())
        }
        val html = HtmlReportGenerator.generateHtml(
            month = YearMonth.of(2026, 10),
            meetings = meetings,
            brothers = emptyList(),
            privileges = emptyList(),
            hoje = LocalDate.of(2026, 10, 1)
        )
        // Retrato, e uma quebra de pagina entre as reunioes.
        assertTrue(html.contains("size: portrait"), "esperava @page em retrato")
        assertTrue(html.contains("page-break-after"), "esperava quebra entre reunioes")
        // page-break-after: always e page-break-inside: avoid evitam que uma
        // reuniao fique partida entre duas folhas.
        assertTrue(html.contains("break-after: page"))
        assertTrue(html.contains("break-inside: avoid"))
    }

    @Test
    fun testReuniaoSemProgramaNaoQuebra() {
        val html = HtmlReportGenerator.generateHtml(
            month = YearMonth.of(2026, 10),
            meetings = listOf(Meeting(1, "15/10/2026", "Meio de Semana", emptyList())),
            brothers = emptyList(),
            privileges = emptyList(),
            hoje = LocalDate.of(2026, 10, 1)
        )
        assertTrue(html.contains("15/10/2026"))
        assertFalse(html.contains("Programa</h2>"), "nao devia inventar secao de programa")
    }

    @Test
    fun testSecoesDoProgramaViramCabecalhoDeGrupo() {
        val program = listOf(
            ProgramItem(section = "TESOUROS DA PALAVRA DE DEUS", number = 1, title = "Joias espirituais", minutes = 5),
            ProgramItem(section = "TESOUROS DA PALAVRA DE DEUS", number = 2, title = "Examinando as Escrituras", minutes = 10),
            ProgramItem(section = "MINISTERIO", number = 3, title = "Indicacao", minutes = 5)
        )
        val html = HtmlReportGenerator.generateHtml(
            month = YearMonth.of(2026, 10),
            meetings = listOf(Meeting(1, "07/10/2026", "Meio de Semana", emptyList(), program = program)),
            brothers = emptyList(),
            privileges = emptyList(),
            hoje = LocalDate.of(2026, 10, 1)
        )
        assertTrue(html.contains("TESOUROS DA PALAVRA DE DEUS"))
        assertTrue(html.contains("MINISTERIO"))
        assertTrue(html.contains("Examinando as Escrituras"))
        assertTrue(html.contains("10 min"))
    }

    @Test
    fun testParteSemDesignadoMostraVAzio() {
        val program = listOf(ProgramItem(section = "", number = 1, title = "Joias espirituais", minutes = 5))
        val html = HtmlReportGenerator.generateHtml(
            month = YearMonth.of(2026, 10),
            meetings = listOf(Meeting(1, "07/10/2026", "Meio de Semana", emptyList(), program = program)),
            brothers = emptyList(),
            privileges = emptyList(),
            hoje = LocalDate.of(2026, 10, 1)
        )
        // A lacuna precisa ficar visivel: e o que aponta o que falta preencher.
        assertTrue(html.contains("—"))
    }

    @Test
    fun testParteComDesignadoMostraOsNomes() {
        val program = listOf(ProgramItem(section = "MINISTERIO", number = 1, title = "Indicacao", minutes = 5))
        // O elo parte<->privilegio: o privilegio diz a qual item corresponde.
        val privilege = Privilege(1, "Indicacao", quantity = 2, programItem = 1)
        val meeting = Meeting(
            1, "07/10/2026", "Meio de Semana",
            listOf(Assignment(1, 10), Assignment(1, 11)),
            program = program
        )
        val html = HtmlReportGenerator.generateHtml(
            month = YearMonth.of(2026, 10),
            meetings = listOf(meeting),
            brothers = listOf(Brother(10, "Carlos"), Brother(11, "Daniel")),
            privileges = listOf(privilege),
            hoje = LocalDate.of(2026, 10, 1)
        )
        assertTrue(html.contains("Carlos · Daniel"))
    }
}
