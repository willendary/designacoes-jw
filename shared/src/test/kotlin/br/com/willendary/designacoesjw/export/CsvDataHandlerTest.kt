package br.com.willendary.designacoesjw.export

import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.BrotherRole
import br.com.willendary.designacoesjw.data.Privilege
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** BOM que o Excel no Windows grava no começo de um CSV salvo em UTF-8. */
private const val BOM = "\uFEFF"

class CsvDataHandlerTest {

    private val privileges = listOf(Privilege(1, "Som"))

    private fun roundTrip(name: String): String {
        val brothers = listOf(Brother(1, name))
        val csv = CsvDataHandler.exportBrothersToCsv(brothers, privileges)
        val imported = CsvDataHandler.importBrothersFromCsv(csv)
        return imported.single().name
    }

    @Test
    fun testFormulaInjectionIsProtected() {
        val csv = CsvDataHandler.exportBrothersToCsv(
            listOf(
                Brother(1, "=SUM(A1)"),
                Brother(2, "+123"),
                Brother(3, "-foo"),
                Brother(4, "@bar")
            ),
            privileges
        )
        assertTrue(csv.contains("'=SUM(A1)"))
        assertTrue(csv.contains("'+123"))
        assertTrue(csv.contains("'-foo"))
        assertTrue(csv.contains("'@bar"))
    }

    @Test
    fun testRoundTripPreservesSpecialNames() {
        assertEquals("=SUM(A1)", roundTrip("=SUM(A1)"))
        assertEquals("+123", roundTrip("+123"))
        assertEquals("-foo", roundTrip("-foo"))
        assertEquals("@bar", roundTrip("@bar"))
        assertEquals("a\"b", roundTrip("a\"b"))
        assertEquals("a,b", roundTrip("a,b"))
        assertEquals("a\nb", roundTrip("a\nb"))
    }

    // ---- #15: BOM, deteccao do delimitador, header e coluna Ativo ----

    @Test
    fun `BOM do Excel nao entra no nome importado`() {
        // Sem header: o BOM estava grudado no primeiro nome.
        val csv = BOM + "Joao;119;Publicador;Som;Sim\n"
        val imported = CsvDataHandler.importBrothersFromCsv(csv).single()

        assertEquals("Joao", imported.name)
        assertFalse(imported.name.startsWith(BOM), "o BOM não pode sobrar no nome")
    }

    @Test
    fun `BOM no header nao impede a deteccao da linha de cabecalho`() {
        val csv = BOM + "Nome;WhatsApp;Cargo;Privilégios;Ativo\nJoao;119;Publicador;Som;Sim\n"
        val imported = CsvDataHandler.importBrothersFromCsv(csv)

        assertEquals(1, imported.size, "o header não pode virar irmão")
        assertEquals("Joao", imported.single().name)
    }

    @Test
    fun `reimportar o mesmo arquivo nao cria irmaos duplicados`() {
        // normalizeName não remove BOM: com ele no nome a deduplicação por nome
        // normalizado falhava e o mesmo arquivo gerava irmãos duplicados.
        val csv = BOM + "Joao;119;Publicador;Som;Sim\nMaria;118;Publicador;Som;Sim\n"

        val nomes = CsvDataHandler.importBrothersFromCsv(csv)
            .map { AssignmentGenerator.normalizeName(it.name) }

        assertEquals(listOf("joao", "maria"), nomes)
        assertEquals(2, nomes.distinct().size, "nomes normalizados precisam ser distintos")
    }

    @Test
    fun `linha em branco inicial nao troca o delimitador`() {
        // Antes, substringBefore('\n') dava "" e o ';' era lido como ',': o
        // arquivo inteiro virava uma coluna só.
        val csv = "\n\nJoao;119;Publicador;Som;Sim\nMaria;118;Publicador;Som;Sim\n"
        val imported = CsvDataHandler.importBrothersFromCsv(csv)

        assertEquals(2, imported.size)
        assertEquals(listOf("Joao", "Maria"), imported.map { it.name })
        assertEquals(listOf("Som"), imported.first().privilegeNames)
    }

    @Test
    fun `cargo e interpretado a partir do texto`() {
        // Cargo é a 3ª coluna do layout do export.
        val csv = """
            Nome;WhatsApp;Cargo;Privilégios;Ativo
            Joao;119;Ancião;Som;Sim
            Maria;118;Servo Ministerial;Som;Sim
            Jose;117;Publicador;Som;Sim
        """.trimIndent()

        val cargos = CsvDataHandler.importBrothersFromCsv(csv).map { it.role }

        assertEquals(
            listOf(BrotherRole.ELDER, BrotherRole.MINISTERIAL_SERVANT, BrotherRole.PUBLISHER),
            cargos
        )
    }

    @Test
    fun `coluna Ativo distingue sim de nao`() {
        fun linha(nome: String, ativo: String) = "$nome;119;Publicador;Som;$ativo\n"

        val csv = buildString {
            append(linha("A", "Sim"))
            append(linha("B", "Não"))
            append(linha("C", "nao"))
            append(linha("D", "NAO"))
            append(linha("E", "false"))
            append(linha("F", "0"))
            append(linha("G", "Não"))
            append("H;119;Publicador;Som;\n") // vazio
            append(linha("I", "x"))           // desconhecido
        }

        val ativos = CsvDataHandler.importBrothersFromCsv(csv).map { it.active }

        assertEquals(
            listOf(true, false, false, false, false, false, false, true, true),
            ativos,
            "ativos na ordem das linhas: $ativos"
        )
    }

    @Test
    fun `round trip preserva nome com ponto e virgula, virgula, aspas e quebra de linha`() {
        val nomes = listOf("Joao; Silva", "Maria, Souza", "Ana \"Bia\"", "Luzia\nLima")
        val original = nomes.mapIndexed { i, n -> Brother(i + 1L, n) }
        val imported = CsvDataHandler.importBrothersFromCsv(
            CsvDataHandler.exportBrothersToCsv(original, privileges)
        )

        assertEquals(nomes.size, imported.size)
        assertEquals(nomes.toSet(), imported.map { it.name }.toSet())
    }

    @Test
    fun `round trip preserva varios privilegios separados por virgula`() {
        // O export usa ';' como delimitador e ',' entre privilégios: os dois
        // convivem e nenhum nome pode ser cortado.
        val multiplos = listOf(Privilege(1, "Som"), Privilege(2, "Indicador"), Privilege(3, "Leitura"))
        val original = listOf(Brother(1, "Joao", privileges = setOf(1, 2, 3)))
        val imported = CsvDataHandler.importBrothersFromCsv(
            CsvDataHandler.exportBrothersToCsv(original, multiplos)
        ).single()

        assertEquals(listOf("Som", "Indicador", "Leitura"), imported.privilegeNames)
    }

    @Test
    fun `round trip de nome protegido devolve o valor sem apostrofo`() {
        // O export prefixa com ' contra CSV injection; o import tem de tirar.
        listOf("=1+1", "+55", "-1", "@user").forEach { nome ->
            assertEquals(nome, roundTrip(nome), "round trip de '$nome'")
        }
    }
}
