package br.com.willendary.designacoesjw.export

import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Privilege
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
}
