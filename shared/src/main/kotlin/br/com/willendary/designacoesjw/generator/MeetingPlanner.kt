package br.com.willendary.designacoesjw.generator

import br.com.willendary.designacoesjw.data.*
import java.time.LocalDate

data class MeetingReview(val filled: Int, val total: Int, val issues: List<String>) {
    val pending: Boolean get() = issues.isNotEmpty()
}

/** Regras compartilhadas pela agenda, pelo seletor e pelos dois aplicativos. */
object MeetingPlanner {
    fun programAssignmentError(meeting: Meeting, position: Int, brotherId: Long, brothers: List<Brother>): String? {
        if (position !in 1..meeting.program.size) return "Esta parte não existe mais. Reabra o programa."
        // Remover uma designação deve funcionar mesmo após exclusão/inativação da pessoa.
        if (meeting.programAssignments.any { it.item == position && brotherId in it.brotherIds }) return null
        val brother = brothers.firstOrNull { it.id == brotherId } ?: return "Participante não encontrado."
        val date = AssignmentGenerator.parseDate(meeting.date)
        return when {
            date == LocalDate.MIN -> "Corrija a data da reunião antes de designar."
            !brother.active -> "Este participante está inativo."
            brotherId in meeting.blockedBrotherIds || AssignmentGenerator.isBrotherUnavailableOn(brother, date) ->
                "${brother.name} está ausente nesta data."
            else -> null
        }
    }

    fun toggleProgramAssignment(meeting: Meeting, position: Int, brotherId: Long): Meeting {
        val ids = meeting.programAssignments.filter { it.item == position }.flatMap { it.brotherIds }.distinct()
        val next = if (brotherId in ids) ids - brotherId else ids + brotherId
        return meeting.copy(programAssignments = meeting.programAssignments.filterNot { it.item == position } +
            if (next.isEmpty()) emptyList() else listOf(ProgramAssignment(position, next)))
    }

    /** Não transfere uma pessoa para outra parte só porque a posição do programa mudou. */
    fun updateProgram(meeting: Meeting, theme: String, program: List<ProgramItem>): Meeting {
        fun key(item: ProgramItem) = AssignmentGenerator.normalizeName(item.section + "|" + item.title)
        val assignments = meeting.programAssignments.mapNotNull { assignment ->
            val old = meeting.program.getOrNull(assignment.item - 1) ?: return@mapNotNull null
            val matches = program.indices.filter { key(program[it]) == key(old) }
            if (matches.size != 1 || meeting.program.count { key(it) == key(old) } != 1) null
            else assignment.copy(item = matches.single() + 1)
        }
        return meeting.copy(theme = theme, program = program, programAssignments = assignments)
    }

    fun review(meeting: Meeting, brothers: List<Brother>, privileges: List<Privilege>): MeetingReview {
        val date = AssignmentGenerator.parseDate(meeting.date)
        val issues = mutableListOf<String>()
        if (date == LocalDate.MIN) issues += "Data inválida."
        val byId = brothers.associateBy { it.id }
        fun available(id: Long): Boolean {
            val b = byId[id] ?: return false
            return b.active && id !in meeting.blockedBrotherIds && !AssignmentGenerator.isBrotherUnavailableOn(b, date)
        }
        var total = 0
        var filled = 0
        privileges.filter { AssignmentGenerator.isPrivilegeApplicableToMeeting(it, date) }.forEach { p ->
            val assigned = meeting.assignments.filter { it.privilegeId == p.id }.map { it.brotherId }.distinct()
            val valid = assigned.count { id -> available(id) && byId[id]?.let {
                it.role.ordinal >= p.minRole.ordinal && AssignmentGenerator.isAuthorized(it, p)
            } == true }
            val expected = p.quantity.coerceAtLeast(0)
            total += expected
            filled += valid.coerceAtMost(expected)
            if (valid < expected) issues += "${p.name}: faltam ${expected - valid}."
            if (assigned.size > expected) issues += "${p.name}: há mais pessoas que vagas."
        }
        meeting.program.forEachIndexed { index, item ->
            val expected = AssignmentGenerator.expectedCountFor(item)
            val assigned = meeting.programAssignments.filter { it.item == index + 1 }.flatMap { it.brotherIds }.distinct()
            val valid = assigned.count(::available)
            total += expected
            filled += valid.coerceAtMost(expected)
            if (valid < expected) issues += "${item.title}: faltam ${expected - valid}."
        }
        if (meeting.isMidweek && meeting.program.isEmpty()) issues += "Programa da semana ainda não importado."
        if (total == 0 && !meeting.isMidweek) issues += "Nenhuma designação configurada para esta reunião."
        val allIds = meeting.assignments.map { it.brotherId } + meeting.programAssignments.flatMap { it.brotherIds }
        allIds.distinct().filterNot(::available).forEach { id -> issues += "${byId[id]?.name ?: "Participante removido"}: revise a disponibilidade." }
        allIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { id ->
            issues += "${byId[id]?.name ?: "Participante"}: mais de uma designação nesta reunião."
        }
        if (meeting.programAssignments.any { it.item !in 1..meeting.program.size }) issues += "Há designações de partes que não existem mais."
        return MeetingReview(filled, total, issues.distinct())
    }

    fun matches(meeting: Meeting, query: String, brothers: List<Brother>, privileges: List<Privilege>): Boolean {
        val search = AssignmentGenerator.normalizeName(query)
        if (search.isBlank()) return true
        val ids = meeting.assignments.map { it.brotherId }.toSet() + meeting.programAssignments.flatMap { it.brotherIds }
        val texts = listOf(meeting.date, meeting.typeCanonical, meeting.theme) + meeting.program.map { it.title } +
            brothers.filter { it.id in ids }.map { it.name } +
            privileges.filter { p -> meeting.assignments.any { it.privilegeId == p.id } }.map { it.name }
        return texts.any { AssignmentGenerator.normalizeName(it).contains(search) }
    }
}
