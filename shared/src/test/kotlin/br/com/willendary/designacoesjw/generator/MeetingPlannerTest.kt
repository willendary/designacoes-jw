package br.com.willendary.designacoesjw.generator

import br.com.willendary.designacoesjw.data.*
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.*

class MeetingPlannerTest {
    private val person = Brother(1, "João", privileges = setOf(1))
    private val other = Brother(2, "João", privileges = setOf(1))
    private val privilege = Privilege(1, "Som", quantity = 2)
    private val part = ProgramItem(section = "Vida", title = "Uma parte", kind = PartKind.PAIR)
    private val meeting = Meeting(10, "07/10/2026", MeetingType.MIDWEEK.label,
        assignments = listOf(Assignment(1, 1)), theme = "Leitura", program = listOf(part))

    @Test fun `completar preserva id programa participantes e ajustes manuais`() {
        val original = meeting.copy(programAssignments = listOf(ProgramAssignment(1, listOf(3))), blockedBrotherIds = setOf(4))
        val result = AssignmentGenerator.generateMonth(YearMonth.of(2026, 10), MeetingSchedule(),
            listOf(person, other, Brother(3, "Ana")), listOf(privilege), listOf(original))
        val updated = result.single { it.id == original.id }
        assertEquals(original.program, updated.program)
        assertEquals(original.programAssignments, updated.programAssignments)
        assertEquals(original.blockedBrotherIds, updated.blockedBrotherIds)
        assertEquals(original.theme, updated.theme)
        assertTrue(Assignment(1, 1) in updated.assignments)
        assertTrue(Assignment(1, 2) in updated.assignments)
        assertEquals(2, updated.assignments.size)
    }

    @Test fun `completar duas vezes e idempotente e mantem reuniao transferida`() {
        val moved = meeting.copy(id = 99, date = "08/10/2026")
        val first = AssignmentGenerator.generateMonth(YearMonth.of(2026, 10), MeetingSchedule(),
            listOf(person, other), listOf(privilege), listOf(moved))
        val second = AssignmentGenerator.generateMonth(YearMonth.of(2026, 10), MeetingSchedule(),
            listOf(person, other), listOf(privilege), first)
        assertEquals(first, second)
        assertEquals(moved, second.single { it.id == 99L })
    }

    @Test fun `tipo segue dias configurados em vez do calendario convencional`() {
        val result = AssignmentGenerator.generateMonth(YearMonth.of(2026, 10), MeetingSchedule(6, 4),
            emptyList(), emptyList(), emptyList())
        assertTrue(result.filter { AssignmentGenerator.parseDate(it.date).dayOfWeek.value == 6 }.all { it.isMidweek })
        assertTrue(result.filter { AssignmentGenerator.parseDate(it.date).dayOfWeek.value == 4 }.none { it.isMidweek })
    }

    @Test fun `recalcular preserva programa e evita designar participante do programa no apoio`() {
        val original = meeting.copy(programAssignments = listOf(ProgramAssignment(1, listOf(1))))
        val result = AssignmentGenerator.generateMonth(YearMonth.of(2026, 10), MeetingSchedule(),
            listOf(person, other), listOf(privilege), listOf(original), recalculate = true).single { it.id == original.id }
        assertEquals(original.programAssignments, result.programAssignments)
        assertEquals(listOf(Assignment(1, 2)), result.assignments)
    }

    @Test fun `ausencia bloqueia nova escolha mas permite remover a antiga`() {
        val absent = person.copy(unavailabilities = listOf(UnavailablePeriod(1, "07/10/2026", "07/10/2026")))
        assertNotNull(MeetingPlanner.programAssignmentError(meeting, 1, 1, listOf(absent)))
        val assigned = meeting.copy(programAssignments = listOf(ProgramAssignment(1, listOf(1))))
        assertNull(MeetingPlanner.programAssignmentError(assigned, 1, 1, listOf(absent)))
        assertTrue(MeetingPlanner.toggleProgramAssignment(assigned, 1, 1).programAssignments.isEmpty())
    }

    @Test fun `homonimos sao identificados por id`() {
        val first = MeetingPlanner.toggleProgramAssignment(meeting, 1, person.id)
        val both = MeetingPlanner.toggleProgramAssignment(first, 1, other.id)
        val remaining = MeetingPlanner.toggleProgramAssignment(both, 1, person.id)
        assertEquals(listOf(other.id), remaining.programAssignments.single().brotherIds)
    }

    @Test fun `reimportar reordena participantes por identidade da parte sem deslocamento indevido`() {
        val secondPart = part.copy(title = "Outra parte")
        val old = meeting.copy(program = listOf(part, secondPart), programAssignments = listOf(ProgramAssignment(1, listOf(1))))
        val updated = MeetingPlanner.updateProgram(old, "Nova leitura", listOf(secondPart, part))
        assertEquals(2, updated.programAssignments.single().item)
        val replaced = MeetingPlanner.updateProgram(old, "Nova leitura", listOf(secondPart))
        assertTrue(replaced.programAssignments.isEmpty())
    }

    @Test fun `programa incompleto ausencia e duplicidade aparecem na revisao`() {
        val absent = person.copy(unavailabilities = listOf(UnavailablePeriod(1, "01/10/2026", "10/10/2026")))
        val duplicated = meeting.copy(programAssignments = listOf(ProgramAssignment(1, listOf(1))))
        val review = MeetingPlanner.review(duplicated, listOf(absent), listOf(privilege))
        assertTrue(review.pending)
        assertEquals(0, review.filled)
        assertEquals(4, review.total)
        assertTrue(review.issues.any { it.contains("disponibilidade") })
        assertTrue(review.issues.any { it.contains("mais de uma") })
    }

    @Test fun `busca encontra participante do programa sem acento`() {
        val assigned = meeting.copy(assignments = emptyList(), programAssignments = listOf(ProgramAssignment(1, listOf(1))))
        assertTrue(MeetingPlanner.matches(assigned, "joao", listOf(person), emptyList()))
        assertTrue(MeetingPlanner.matches(assigned, "07/10", emptyList(), emptyList()))
        assertFalse(MeetingPlanner.matches(assigned, "ausente", listOf(person), emptyList()))
    }

    @Test fun `data impossivel nao vira silenciosamente outra data`() {
        assertEquals(LocalDate.MIN, AssignmentGenerator.parseDate("31/02/2026"))
        assertEquals(LocalDate.of(2024, 2, 29), AssignmentGenerator.parseDate("29/02/2024"))
        assertTrue(AssignmentGenerator.candidatesFor(meeting.copy(date = "31/02/2026"), 1, 0, listOf(person), listOf(privilege)).isEmpty())
    }

    @Test fun `substituicao exclui pessoas no programa e dias de reuniao invalidos`() {
        val assigned = meeting.copy(programAssignments = listOf(ProgramAssignment(1, listOf(2))))
        assertTrue(AssignmentGenerator.candidatesFor(assigned, 1, 1, listOf(other), listOf(privilege)).isEmpty())
        assertFailsWith<IllegalArgumentException> {
            AssignmentGenerator.generateMonth(YearMonth.of(2026, 10), MeetingSchedule(3, 3), emptyList(), emptyList(), emptyList())
        }
    }
    @Test fun `revisao sinaliza excesso em parte individual e aprendiz no apoio`() {
        val assigned = meeting.copy(program = listOf(part.copy(kind = PartKind.INDIVIDUAL)),
            programAssignments = listOf(ProgramAssignment(1, listOf(1, 2))))
        val review = MeetingPlanner.review(assigned, listOf(person.copy(trainee = true), other), listOf(privilege))
        assertTrue(review.issues.any { it.contains("mais participantes") })
        assertTrue(review.issues.any { it.contains("Som: faltam 2") })
    }

}
