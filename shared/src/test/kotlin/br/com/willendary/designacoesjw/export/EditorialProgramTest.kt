package br.com.willendary.designacoesjw.export

import br.com.willendary.designacoesjw.data.*
import br.com.willendary.designacoesjw.generator.MeetingPlanner
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import java.time.LocalDate
import kotlin.test.*

class EditorialProgramTest {
    private val start = LocalDate.of(2026, 10, 5)
    private val source = "https://wol.jw.org/pt/wol/d/r5/lp-t/202026256"
    // Estrutura da publicação de referência, com texto sintético para os testes.
    private val html = """
        <nav><h3>99. Fora do artigo</h3></nav>
        <article><header><h1>5-11 de outubro</h1><h2>SALMOS 1-8</h2></header>
        <div class="bodyTxt">
          <h3><a href="/song">Cântico inicial</a> e oração</h3>
          <div><h2>TESOUROS DA PALAVRA DE DEUS</h2></div>
          <h3><strong>1. Tema de exemplo</strong></h3>
          <div><p>(10 min)</p><p>Texto <strong>importante</strong> com <a href="/reference"><em>referência</em></a>.</p>
          <ul><li><p>Primeira pergunta?</p><div class="gen-field"><p>Sua resposta</p></div></li>
          <li>Segunda pergunta?</li></ul>
          <figure><img src="/photo.jpg" alt="Descrição da ilustração"><figcaption><p>Legenda de exemplo</p></figcaption></figure></div>
          <h2>FAÇA SEU MELHOR NO MINISTÉRIO</h2>
          <h3>2. Conversa de exemplo</h3><p>(2 min) Instrução A.</p>
          <h3>3. Conversa de exemplo</h3><p>(4 min) Instrução B.</p>
          <h2>NOSSA VIDA CRISTÃ</h2><h3>Cântico intermediário</h3>
          <h3>4. Estudo de exemplo</h3><p>(30 min) Capítulo de exemplo.</p>
          <h3>Comentários finais (3 min) | Cântico final</h3>
        </div></article><footer><h3>98. Fora do artigo</h3></footer>
    """.trimIndent()

    private fun parse(text: String = html) = MwbProgramImporter.parse(text, start, start.plusDays(6), source)

    @Test fun `preserva blocos duracoes e interludios sem criar posicoes extras`() {
        val program = parse()
        assertEquals(listOf(1, 2, 3, 4), program.parts.map { it.number })
        assertEquals(listOf(10, 2, 4, 30), program.parts.map { it.minutes })
        val first = program.parts.first().content
        assertEquals(listOf("heading", "section"), first.before.map { it.type })
        assertEquals(listOf("paragraph", "paragraph", "bullet", "bullet", "image", "caption"), first.body.map { it.type })
        assertFalse(first.body.any { it.text.contains("Sua resposta") })
        assertEquals("https://wol.jw.org/photo.jpg", first.body.first { it.type == "image" }.imageUrl)
        assertEquals("Descrição da ilustração", first.body.first { it.type == "image" }.imageDescription)
        val reference = first.body[1].runs.first { it.text == "referência" }
        assertTrue(reference.italic)
        assertEquals("https://wol.jw.org/reference", reference.url)
        assertTrue(first.body[1].runs.first { it.text == "importante" }.bold)
        assertEquals("Cântico intermediário", program.parts.last().content.before.last().text)
        assertTrue(program.parts.last().content.body.last().text.startsWith("Comentários finais"))
    }

    @Test fun `conteudo sobrevive ao cache e ao contrato Firestore`() {
        val content = parse().parts.first().content
        assertEquals(content, ProgramContentCodec.decode(ProgramContentCodec.encode(content)))
        val item = ProgramItem(title = "Parte", content = content)
        assertEquals(item, Json.decodeFromString<ProgramItem>(Json.encodeToString(item)))
        assertEquals(ProgramContent(), Json.decodeFromString<ProgramItem>("""{"title":"Parte antiga"}""").content)
        assertEquals(ProgramContent(), ProgramContentCodec.decode(null))
        assertEquals(ProgramContent(), ProgramContentCodec.decode("inválido"))
    }

    @Test fun `html alterado falha sem retornar programa vazio`() {
        assertFailsWith<IllegalArgumentException> { parse("<article><p>Conteúdo temporariamente indisponível</p></article>") }
        assertEquals(1, parse("<article><div><h3>1. Parte sem descrição</h3></div></article>").parts.size)
        assertEquals("", MwbProgramImporter.safeUrl(source, "javascript:alert(1)"))
        assertEquals("", MwbProgramImporter.safeUrl(source, "file:///tmp/teste"))
    }

    @Test fun `atualizar conteudo preserva participantes de titulos repetidos e privilegios`() {
        val parts = parse().parts.map { ProgramItem(it.section, it.number, it.title, it.minutes, content = it.content) }
        val old = Meeting(1, "05/10/2026", MeetingType.MIDWEEK.label,
            assignments = listOf(Assignment(9, 10)), program = parts.map { it.copy(content = ProgramContent()) },
            programAssignments = listOf(ProgramAssignment(2, listOf(20)), ProgramAssignment(3, listOf(30))))
        val updated = MeetingPlanner.updateProgram(old, "SALMOS 1-8", parts)
        assertEquals(old.assignments, updated.assignments)
        assertEquals(old.programAssignments, updated.programAssignments)
        assertEquals(parts, updated.program)
    }

    @Test fun `pendencias e busca da escala nao incluem partes do programa`() {
        val meeting = Meeting(1, "05/10/2026", MeetingType.MIDWEEK.label,
            assignments = listOf(Assignment(1, 1)), program = listOf(ProgramItem(title = "Parte pendente")))
        val people = listOf(Brother(1, "Pessoa", privileges = setOf(1)))
        val privileges = listOf(Privilege(1, "Microfone"))
        assertTrue(MeetingPlanner.review(meeting, people, privileges).pending)
        assertFalse(MeetingPlanner.reviewSupport(meeting, people, privileges).pending)
        assertFalse(MeetingPlanner.matchesSupport(meeting, "Parte pendente", people, privileges))
        assertTrue(MeetingPlanner.matchesSupport(meeting, "Microfone", people, privileges))
    }

    @Test fun `validacao opcional da pagina real obtida para revisao`() {
        val path = System.getenv("JW_REFERENCE_HTML") ?: return
        val program = parse(java.io.File(path).readText())
        assertEquals(9, program.parts.size)
        assertEquals(listOf(10, 10, 4, 2, 2, 4, 3, 15, 30), program.parts.map { it.minutes })
        assertEquals(2, program.parts.sumOf { p -> p.content.body.count { it.type == "image" } })
        assertTrue(program.parts[7].content.body.count { it.type == "bullet" } >= 3)
    }
}
