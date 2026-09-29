package br.com.willendary.designacoesjw.export

import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.BrotherRole
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.Privilege
import br.com.willendary.designacoesjw.generator.AssignmentGenerator

object CsvDataHandler {

    fun exportBrothersToCsv(brothers: List<Brother>, privileges: List<Privilege>): String = buildString {
        append("Nome;WhatsApp;Cargo;Privilégios;Ativo\n")
        brothers.sortedBy { AssignmentGenerator.normalizeName(it.name) }.forEach { b ->
            val privNames = b.privileges.mapNotNull { pid -> privileges.find { it.id == pid }?.name }.joinToString(", ")
            val roleName = b.role.label
            val activeStr = if (b.active) "Sim" else "Não"
            append("${escape(b.name)};${escape(b.phone)};${escape(roleName)};${escape(privNames)};$activeStr\n")
        }
    }

    data class ImportedBrother(
        val name: String,
        val phone: String,
        val role: BrotherRole,
        val privilegeNames: List<String>,
        val active: Boolean
    )

    fun importBrothersFromCsv(csvContent: String): List<ImportedBrother> {
        val lines = csvContent.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val delimiter = if (lines.first().contains(";")) ";" else ","
        val results = mutableListOf<ImportedBrother>()

        val dataLines = if (lines.first().lowercase().contains("nome")) lines.drop(1) else lines

        dataLines.forEach { line ->
            val cols = line.split(delimiter).map { it.trim().trim('"', '\'') }
            if (cols.isNotEmpty() && cols[0].isNotBlank()) {
                val name = cols[0]
                val phone = cols.getOrNull(1)?.filter { it.isDigit() || it == '+' } ?: ""
                val roleStr = cols.getOrNull(2)?.lowercase() ?: ""
                val role = when {
                    "anc" in roleStr -> BrotherRole.ELDER
                    "servo" in roleStr -> BrotherRole.MINISTERIAL_SERVANT
                    else -> BrotherRole.PUBLISHER
                }
                val privNames = cols.getOrNull(3)
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() } ?: emptyList()

                val active = cols.getOrNull(4)?.lowercase()?.let { it != "não" && it != "nao" && it != "false" && it != "0" } ?: true

                results += ImportedBrother(name, phone, role, privNames, active)
            }
        }
        return results
    }

    fun exportMeetingsToCsv(
        meetings: List<Meeting>,
        brothers: List<Brother>,
        privileges: List<Privilege>
    ): String = buildString {
        append("Data;Tipo;Privilégio;Irmão\n")
        meetings.sortedBy { AssignmentGenerator.parseDate(it.date) }.forEach { meeting ->
            meeting.assignments.forEach { a ->
                val p = privileges.find { it.id == a.privilegeId }?.name ?: "Privilégio"
                val b = brothers.find { it.id == a.brotherId }?.name ?: "Irmão"
                append("${meeting.date};${escape(meeting.type)};${escape(p)};${escape(b)}\n")
            }
        }
    }

    private fun escape(value: String): String =
        if (value.contains(";") || value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
