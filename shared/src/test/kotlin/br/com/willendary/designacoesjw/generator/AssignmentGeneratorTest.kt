package br.com.willendary.designacoesjw.generator

import br.com.willendary.designacoesjw.data.*
import br.com.willendary.designacoesjw.export.CsvDataHandler
import br.com.willendary.designacoesjw.util.WhatsAppHelper
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssignmentGeneratorTest {

    @Test
    fun testSentinelReaderInheritsBookReader() {
        val sentinelPrivilege = Privilege(id = 1, name = "Leitor da Sentinela")
        val bookPrivilege = Privilege(id = 2, name = "Leitor do Livro")
        val allPrivileges = listOf(sentinelPrivilege, bookPrivilege)

        val brotherWithSentinel = Brother(id = 10, name = "João Silva", privileges = setOf(1))
        val brotherWithBookOnly = Brother(id = 11, name = "Pedro Santos", privileges = setOf(2))

        assertTrue(
            AssignmentGenerator.isBrotherAuthorizedForPrivilege(brotherWithSentinel, sentinelPrivilege, allPrivileges)
        )
        // Irmão com Sentinela deve automaticamente poder ler o Livro
        assertTrue(
            AssignmentGenerator.isBrotherAuthorizedForPrivilege(brotherWithSentinel, bookPrivilege, allPrivileges)
        )
        // Irmão só com Livro NÃO pode ler Sentinela
        assertFalse(
            AssignmentGenerator.isBrotherAuthorizedForPrivilege(brotherWithBookOnly, sentinelPrivilege, allPrivileges)
        )
    }

    @Test
    fun testReaderPrivilegesByDayOfWeek() {
        val sentinelPrivilege = Privilege(id = 1, name = "Leitor da Sentinela", quantity = 1)
        val bookPrivilege = Privilege(id = 2, name = "Leitor do Livro", quantity = 1)
        val privileges = listOf(sentinelPrivilege, bookPrivilege)

        val quartaDate = LocalDate.of(2026, 10, 7) // Quarta-feira (Meio de semana)
        val sabadoDate = LocalDate.of(2026, 10, 10) // Sábado (Fim de semana)

        // Quarta: Leitor do Livro é aplicável, Sentinela NÃO é aplicável
        assertTrue(AssignmentGenerator.isPrivilegeApplicableToMeeting(bookPrivilege, quartaDate))
        assertFalse(AssignmentGenerator.isPrivilegeApplicableToMeeting(sentinelPrivilege, quartaDate))

        // Sábado: Leitor da Sentinela é aplicável, Livro NÃO é aplicável
        assertTrue(AssignmentGenerator.isPrivilegeApplicableToMeeting(sentinelPrivilege, sabadoDate))
        assertFalse(AssignmentGenerator.isPrivilegeApplicableToMeeting(bookPrivilege, sabadoDate))

        // Irmãos: b1 tem apenas Livro, b2 tem Sentinela
        val b1 = Brother(id = 10, name = "Irmão Livro", privileges = setOf(2))
        val b2 = Brother(id = 20, name = "Irmão Sentinela", privileges = setOf(1))
        val brothers = listOf(b1, b2)

        // Na quarta-feira (meio de semana), ambos são candidatos ao Livro (b2 herda)
        val meetingQuarta = Meeting(id = 1, date = "07/10/2026", type = "Meio de semana")
        val candidatesQuartaLivro = AssignmentGenerator.candidatesFor(meetingQuarta, bookPrivilege.id, 0L, brothers, privileges)
        assertEquals(2, candidatesQuartaLivro.size, "Ambos os irmãos devem ser candidatos para o Livro")

        // No sábado (fim de semana), apenas b2 (Sentinela) é candidato para a Sentinela
        val meetingSabado = Meeting(id = 2, date = "10/10/2026", type = "Fim de semana")
        val candidatesSabadoSentinela = AssignmentGenerator.candidatesFor(meetingSabado, sentinelPrivilege.id, 0L, brothers, privileges)
        assertEquals(1, candidatesSabadoSentinela.size)
        assertEquals(20, candidatesSabadoSentinela[0].id, "Apenas o irmão habilitado para Sentinela deve ler no sábado")
    }

    @Test
    fun testUnavailableBrotherIsExcluded() {
        val privilege = Privilege(id = 1, name = "Indicador", quantity = 1)
        val meetingDate = LocalDate.of(2026, 10, 7) // Quarta-feira

        val brotherOnVacation = Brother(
            id = 10,
            name = "Viajante",
            privileges = setOf(1),
            unavailabilities = listOf(
                UnavailablePeriod(id = 1, startDate = "01/10/2026", endDate = "15/10/2026", reason = "Férias")
            )
        )
        val availableBrother = Brother(
            id = 11,
            name = "Disponível",
            privileges = setOf(1)
        )

        val meeting = AssignmentGenerator.generateMeeting(
            date = meetingDate,
            blocked = emptySet(),
            brothers = listOf(brotherOnVacation, availableBrother),
            privileges = listOf(privilege),
            history = emptyList()
        )

        assertEquals(1, meeting.assignments.size)
        assertEquals(11, meeting.assignments[0].brotherId, "Irmão em férias não deveria ser designado")
    }

    @Test
    fun testBlockedBrotherIsExcluded() {
        val privilege = Privilege(id = 1, name = "Microfone", quantity = 1)
        val meetingDate = LocalDate.of(2026, 10, 7)

        val b1 = Brother(id = 10, name = "Irmão 1", privileges = setOf(1))
        val b2 = Brother(id = 20, name = "Irmão 2", privileges = setOf(1))

        val meeting = AssignmentGenerator.generateMeeting(
            date = meetingDate,
            blocked = setOf(10), // Bloqueado pontualmente para esta reunião
            brothers = listOf(b1, b2),
            privileges = listOf(privilege),
            history = emptyList()
        )

        assertEquals(1, meeting.assignments.size)
        assertEquals(20, meeting.assignments[0].brotherId)
    }

    @Test
    fun testFairDistributionAcrossMeetings() {
        val privilege = Privilege(id = 1, name = "Som", quantity = 1)
        val meetingDate = LocalDate.of(2026, 10, 7)

        val b1 = Brother(id = 10, name = "Carlos", privileges = setOf(1))
        val b2 = Brother(id = 20, name = "Lucas", privileges = setOf(1))

        // Carlos já fez Som 3 vezes no histórico; Lucas fez apenas 1 vez
        val history = listOf(
            Meeting(1, "01/09/2026", "Meio de semana", listOf(Assignment(1, 10))),
            Meeting(2, "08/09/2026", "Meio de semana", listOf(Assignment(1, 10))),
            Meeting(3, "15/09/2026", "Meio de semana", listOf(Assignment(1, 10))),
            Meeting(4, "22/09/2026", "Meio de semana", listOf(Assignment(1, 20)))
        )

        val meeting = AssignmentGenerator.generateMeeting(
            date = meetingDate,
            blocked = emptySet(),
            brothers = listOf(b1, b2),
            privileges = listOf(privilege),
            history = history
        )

        assertEquals(1, meeting.assignments.size)
        assertEquals(20, meeting.assignments[0].brotherId, "Lucas deve ser priorizado por ter menos designações")
    }

    @Test
    fun testCsvExportAndImport() {
        val privileges = listOf(
            Privilege(1, "Som"),
            Privilege(2, "Indicador")
        )
        val brothers = listOf(
            Brother(1, "Marcos Silva", "11999998888", setOf(1, 2), true, BrotherRole.MINISTERIAL_SERVANT)
        )

        val csv = CsvDataHandler.exportBrothersToCsv(brothers, privileges)
        assertTrue(csv.contains("Marcos Silva"))
        assertTrue(csv.contains("Servo Ministerial"))

        val imported = CsvDataHandler.importBrothersFromCsv(csv)
        assertEquals(1, imported.size)
        assertEquals("Marcos Silva", imported[0].name)
        assertEquals("11999998888", imported[0].phone)
        assertEquals(BrotherRole.MINISTERIAL_SERVANT, imported[0].role)
        assertEquals(listOf("Som", "Indicador"), imported[0].privilegeNames)
    }

    @Test
    fun testWhatsAppHelper() {
        val brother = Brother(1, "Lucas Silva", "5511999998888")
        val privilege = Privilege(1, "Leitor da Sentinela")
        val meeting = Meeting(1, "11/10/2026", "Reunião de fim de semana")

        val message = WhatsAppHelper.buildSingleMessage(
            WhatsAppHelper.DEFAULT_SINGLE_TEMPLATE,
            brother,
            privilege,
            meeting
        )

        assertTrue(message.contains("Lucas Silva"))
        assertTrue(message.contains("Leitor da Sentinela"))
        assertTrue(message.contains("11/10/2026"))
        assertTrue(message.contains("Domingo"))
    }
}
