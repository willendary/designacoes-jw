package br.com.willendary.designacoesjw.export

import br.com.willendary.designacoesjw.data.ProgramItem
import br.com.willendary.designacoesjw.data.parseLegacyProgramItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProgramItemMigrationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `string legada reconstrói número, título e minutos`() {
        val item = assertNotNull(parseLegacyProgramItem("1. Joias espirituais"))
        assertEquals(1, item.number)
        assertEquals("Joias espirituais", item.title)
        assertEquals(0, item.minutes)

        val withMinutes = assertNotNull(parseLegacyProgramItem("7. Explicando suas crenças (5 min)"))
        assertEquals(7, withMinutes.number)
        assertEquals("Explicando suas crenças", withMinutes.title)
        assertEquals(5, withMinutes.minutes)
    }

    @Test
    fun `string sem número não é reconhecida`() {
        assertNull(parseLegacyProgramItem("Joias espirituais"))
        assertNull(parseLegacyProgramItem(""))
    }

    @Test
    fun `formato novo lê igual ao legado`() {
        // O formato novo é um objeto com section/number/title/minutes.
        val raw = """{"section":"TESOUROS DA PALAVRA DE DEUS","number":2,"title":"Joias espirituais","minutes":0}"""
        val item = json.decodeFromString<ProgramItem>(raw)
        assertEquals("TESOUROS DA PALAVRA DE DEUS", item.section)
        assertEquals(2, item.number)
        assertEquals("Joias espirituais", item.title)
        assertEquals(0, item.minutes)
    }

    @Test
    fun `gravar e reler faz round-trip sem perder nada`() {
        val original = ProgramItem(section = "NOSSA VIDA CRISTÃ", number = 3, title = "Leitura da Bíblia", minutes = 4)
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ProgramItem>(encoded)
        assertEquals(original, decoded)
    }
}
