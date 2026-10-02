package br.com.willendary.designacoesjw.generator

import br.com.willendary.designacoesjw.data.Assignment
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.PartKind
import br.com.willendary.designacoesjw.data.Privilege
import br.com.willendary.designacoesjw.data.ProgramAssignment
import br.com.willendary.designacoesjw.data.ProgramItem
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A parte do programa não é privilégio (#51).
 *
 * O defeito que motivou: `Privilege.programItem` amarrava um privilégio
 * cadastrado a uma posição do programa, e o gerador tentava tratar "Indicação"
 * como se fosse "Som" — com dono natural, dia da semana e restrição por sexo,
 * coisas que só fazem sentido para o mecânico.
 */
class ProgramPartsTest {

    private val programa = listOf(
        ProgramItem(section = "TESOUROS", number = 1, title = "Joias espirituais", minutes = 5),
        ProgramItem(section = "MINISTERIO", number = 2, title = "Indicacao", minutes = 5, kind = PartKind.PAIR),
        ProgramItem(section = "NOSSA VIDA", number = 3, title = "Encenacao", minutes = 10, kind = PartKind.DEMONSTRATION)
    )

    @Test
    fun `posicao do item e a identidade, nao o numero oficial`() {
        // O numero oficial pode faltar ou mudar; quem aponta e a posicao.
        assertEquals(1, programa[0].positionIn(programa))
        assertEquals(2, programa[1].positionIn(programa))
        assertEquals(3, programa[2].positionIn(programa))

        // Item sem numero oficial continua tendo posicao.
        val semNumero = listOf(ProgramItem(title = "Leitura da Biblia"), ProgramItem(title = "Oracao"))
        assertEquals(1, semNumero[0].positionIn(semNumero))
        assertEquals(2, semNumero[1].positionIn(semNumero))
    }

    @Test
    fun `expectedCountFor reflecte o tipo da parte`() {
        assertEquals(1, AssignmentGenerator.expectedCountFor(programa[0]))
        assertEquals(2, AssignmentGenerator.expectedCountFor(programa[1]))
        assertEquals(2, AssignmentGenerator.expectedCountFor(programa[2]))
    }

    @Test
    fun `avisa quando dois nao qualificados dividem a mesma parte`() {
        val irmaos = listOf(
            Brother(1, "Carlos", baptized = true),
            Brother(2, "Daniel", trainee = true),
            Brother(3, "Eduardo", baptized = false)
        )

        // Encenacao com aprendiz + nao batizado: dois nao qualificados.
        assertNotNull(
            AssignmentGenerator.unqualifiedWarning(programa[2], listOf(2, 3), irmaos),
            "deveria avisar: aprendiz e nao batizado na mesma encenacao"
        )
        // Mesma parte, mas com um qualificado: nao ha o que avisar.
        assertNull(AssignmentGenerator.unqualifiedWarning(programa[2], listOf(1, 2), irmaos))
        // Parte individual: a regra nao se aplica, mesmo com nao qualificado.
        assertNull(AssignmentGenerator.unqualifiedWarning(programa[0], listOf(2, 3), irmaos))
    }

    @Test
    fun `parte do programa e privilege sao coisas separadas`() {
        // Nao existe mais nenhum campo que amarre um ao outro.
        // O que amarra e Meeting.programAssignments, pela posicao.
        val meeting = Meeting(
            id = 1, date = "07/10/2026", type = "Meio de semana",
            // Mecanicos continuam em assignments.
            assignments = listOf(Assignment(privilegeId = 9, brotherId = 1)),
            program = programa,
            // E as partes do programa, em programAssignments.
            programAssignments = listOf(ProgramAssignment(item = 2, brotherIds = listOf(10, 11)))
        )

        assertEquals(1, meeting.assignments.size)
        assertEquals(1, meeting.programAssignments.size)
        // O item 2 do programa (Indicacao) tem duas pessoas.
        assertEquals(listOf(10L, 11L), meeting.programAssignments.first { it.item == 2 }.brotherIds)
        // E nenhuma das duas listas se misturam.
        assertTrue(meeting.assignments.none { it.brotherId in listOf(10L, 11L) })
    }

    @Test
    fun `reuniao antiga sem programAssignments abre com lista vazia`() {
        val antiga = Meeting(
            id = 1, date = "30/09/2026", type = "Meio de semana",
            assignments = listOf(Assignment(1, 1)),
            program = programa
        )
        assertTrue(antiga.programAssignments.isEmpty())
    }

    @Test
    fun `gerador so distribui privilegio mecanico`() {
        // "Indicacao" e parte do programa: nao existe como privilegio, logo o
        // gerador nao tem o que distribuir para ela. O que ele distribui e o
        // mecanico — e so para irmao habilitado para ele, como sempre foi.
        val privilegiados = listOf(Privilege(1, "Som", maleOnly = true))
        val irmaos = listOf(
            Brother(1, "Carlos", privileges = setOf(1L)),
            Brother(2, "Daniel", privileges = setOf(1L))
        )

        val meeting = AssignmentGenerator.generateMeeting(
            date = LocalDate.of(2026, 10, 7),
            blocked = emptySet(),
            brothers = irmaos,
            privileges = privilegiados,
            history = emptyList(),
            previousMeeting = Meeting(1, "30/09/2026", "Meio de semana", emptyList(), program = programa)
        )

        assertEquals(1, meeting.assignments.size, "o privilegio mecanico continua sendo distribuido")
        assertEquals(1L, meeting.assignments.first().privilegeId)
        // E nada de designacao de parte do programa saiu daqui: essa escolha
        // e de quem conduz a reuniao, na tela.
        assertTrue(meeting.programAssignments.isEmpty())
    }
}
