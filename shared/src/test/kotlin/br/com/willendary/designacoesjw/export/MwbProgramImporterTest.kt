package br.com.willendary.designacoesjw.export

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MwbProgramImporterTest {

    private val ano = 2026

    /** Rótulos reais da página bimestral do jw.org (sem ano — o ano vem do slug). */
    @Test
    fun `interpreta intervalo de semana nos formatos do jw org`() {
        assertEquals(
            LocalDate.of(2026, 10, 5) to LocalDate.of(2026, 10, 11),
            MwbProgramImporter.parseWeekRange("5-11 de outubro", ano)
        )
        assertEquals(
            LocalDate.of(2026, 9, 28) to LocalDate.of(2026, 10, 4),
            MwbProgramImporter.parseWeekRange("28 de setembro–4 de outubro", ano)
        )
        assertEquals(
            LocalDate.of(2026, 10, 26) to LocalDate.of(2026, 11, 1),
            MwbProgramImporter.parseWeekRange("26 de outubro–1.º de novembro", ano)
        )
        // Vira o ano: bimestre nov/dez com a semana de janeiro já no ano seguinte.
        assertEquals(
            LocalDate.of(2026, 12, 28) to LocalDate.of(2027, 1, 3),
            MwbProgramImporter.parseWeekRange("28 de dezembro–3 de janeiro", ano)
        )
    }

    @Test
    fun `rotulo sem intervalo nao e interpretado`() {
        assertEquals(null, MwbProgramImporter.parseWeekRange("Programas", ano))
        assertEquals(null, MwbProgramImporter.parseWeekRange("5 de outubro", ano))
    }

    @Test
    fun `slug bimestral segue o mes da segunda metade`() {
        assertEquals("setembro-outubro-2026-mwb", MwbProgramImporter.bimestreSlug(LocalDate.of(2026, 10, 5)))
        // Setembro pertence ao bimestre set/out, não a jul/ago.
        assertEquals("setembro-outubro-2026-mwb", MwbProgramImporter.bimestreSlug(LocalDate.of(2026, 9, 7)))
        assertEquals("janeiro-fevereiro-2026-mwb", MwbProgramImporter.bimestreSlug(LocalDate.of(2026, 1, 12)))
    }

    @Test
    fun `slug bimestral respeita a virada de ano`() {
        // 05/01/2026 (segunda): semana toda em janeiro.
        assertEquals("janeiro-fevereiro-2026-mwb", MwbProgramImporter.bimestreSlug(LocalDate.of(2026, 1, 5)))
        // 28/12/2025 (domingo): semana 22-28/12, toda em dezembro.
        assertEquals("novembro-dezembro-2025-mwb", MwbProgramImporter.bimestreSlug(LocalDate.of(2025, 12, 28)))
        // 02/01/2027 (sábado): semana 28/12/2026-03/01/2027, iniciada em dezembro de 2026.
        assertEquals("novembro-dezembro-2026-mwb", MwbProgramImporter.bimestreSlug(LocalDate.of(2027, 1, 2)))
        // Semana normal em setembro.
        assertEquals("setembro-outubro-2026-mwb", MwbProgramImporter.bimestreSlug(LocalDate.of(2026, 9, 15)))
    }

    @Test
    fun `parse extrai tema, secoes e itens numerados`() {
        val html = buildString {
            append("<html><body><h1>5-11 de outubro</h1>")
            append("""<h2 class="x"><a data-targetverses="1-2"><strong>JEREMIAS 40-41</strong></a></h2>""")
            append("<h3>Cântico 33 e oração | Comentários iniciais (1 min)</h3>")
            append("<h2 class=\"du-color--accent\">TESOUROS DA PALAVRA DE DEUS</h2>")
            append("<h3>1. Tenha o ponto de vista correto sobre a proteção de Jeová</h3>")
            append("<h3>2. Joias espirituais</h3>")
            append("<h3>3. Leitura da Bíblia</h3>")
            append("<h2 class=\"du-color--accent\">FAÇA SEU MELHOR NO MINISTÉRIO</h2>")
            append("<h3>4. Iniciando conversas</h3>")
            append("<h3>7. Explicando suas crenças (5 min)</h3>")
            append("</body></html>")
        }

        val program = MwbProgramImporter.parse(
            html, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11), "http://x"
        )

        assertEquals("JEREMIAS 40-41", program.theme)
        // Só os itens numerados viram designação; cânticos/comentários fixos ficam de fora.
        assertEquals(5, program.parts.size, "partes: ${program.parts}")

        val joias = program.parts.first { it.number == 2 }
        assertEquals("Joias espirituais", joias.title)
        assertEquals("TESOUROS DA PALAVRA DE DEUS", joias.section)

        val crencas = program.parts.first { it.number == 7 }
        assertEquals(5, crencas.minutes)
        assertEquals("Explicando suas crenças (5 min)", crencas.label)
        assertEquals("FAÇA SEU MELHOR NO MINISTÉRIO", crencas.section)
        assertEquals(0, program.parts.first { it.number == 4 }.minutes)
    }

    @Test
    fun `cabecalho em caixa alta vira secao e o item nao`() {
        val html = """
            <h1>5-11 de outubro</h1>
            <h2><strong>SALMOS 1-8</strong></h2>
            <h2>NOSSA VIDA CRISTÃ</h2>
            <h3>1. Joias espirituais</h3>
        """.trimIndent()
        val program = MwbProgramImporter.parse(
            html, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11), "http://x"
        )
        assertEquals("NOSSA VIDA CRISTÃ", program.parts.single().section)
    }

    @Test
    fun `link da semana e encontrado pelo intervalo`() {
        val html = """
            <a href="/pt/biblioteca/programas/">Programas</a>
            <a href="/pt/biblioteca/jw-apostila-do-mes/setembro-outubro-2026-mwb/Programa-para-5-11-de-outubro/">5-11 de outubro</a>
            <a href="/pt/biblioteca/jw-apostila-do-mes/setembro-outubro-2026-mwb/Programa-para-12-18-de-outubro/">12-18 de outubro</a>
        """.trimIndent()
        val link = assertNotNull(MwbProgramImporter.findWeekLink(html, LocalDate.of(2026, 10, 14), ano))
        assertTrue(link.path.endsWith("Programa-para-12-18-de-outubro/"), link.path)
        assertEquals(LocalDate.of(2026, 10, 12), link.start)
        assertEquals(LocalDate.of(2026, 10, 18), link.end)
    }
}
