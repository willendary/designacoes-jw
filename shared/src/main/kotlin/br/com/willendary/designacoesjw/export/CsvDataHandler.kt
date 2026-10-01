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
        val delimiter = if (csvContent.substringBefore('\n').contains(";")) ';' else ','
        val rows = parseCsv(csvContent, delimiter)
        if (rows.isEmpty()) return emptyList()

        val results = mutableListOf<ImportedBrother>()
        val dataRows = if (rows.first().firstOrNull()?.lowercase()?.contains("nome") == true) rows.drop(1) else rows

        dataRows.forEach { cols ->
            if (cols.isNotEmpty() && cols[0].isNotBlank()) {
                val name = unprotect(cols[0])
                val phone = cols.getOrNull(1)?.filter { it.isDigit() || it == '+' } ?: ""
                val roleStr = cols.getOrNull(2)?.lowercase() ?: ""
                val role = when {
                    "anc" in roleStr -> BrotherRole.ELDER
                    "servo" in roleStr -> BrotherRole.MINISTERIAL_SERVANT
                    else -> BrotherRole.PUBLISHER
                }
                val privNames = cols.getOrNull(3)
                    ?.split(",")
                    ?.map { unprotect(it.trim()) }
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

    private fun escape(value: String): String {
        // Proteção contra injeção de fórmula (CSV injection): valores que começam
        // com =, +, -, @, TAB ou CR são interpretados como fórmula pelo Excel/LibreOffice.
        // O apóstrofo inicial força o tratamento como texto.
        val protected = if (value.startsWith("=") || value.startsWith("+") ||
            value.startsWith("-") || value.startsWith("@") ||
            value.startsWith("\t") || value.startsWith("\r")) {
            "'" + value
        } else {
            value
        }
        return if (protected.contains(";") || protected.contains(",") ||
            protected.contains("\"") || protected.contains("\n")) {
            "\"" + protected.replace("\"", "\"\"") + "\""
        } else {
            protected
        }
    }

    /** Remove o prefixo de proteção contra injeção de fórmula, se presente. */
    private fun unprotect(value: String): String =
        if (value.length >= 2 && value[0] == '\'' && value[1] in "=+-@\t\r") {
            value.substring(1)
        } else {
            value
        }

    /**
     * Parser CSV que respeita aspas, aspas escapadas (`""`) e delimitador/quebra
     * de linha dentro de campos entre aspas — necessário para o round-trip de
     * nomes com vírgula, aspas ou quebra de linha.
     */
    private fun parseCsv(content: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        var field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < content.length) {
            val c = content[i]
            when {
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < content.length && content[i + 1] == '"') {
                            field.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        field.append(c)
                    }
                }
                c == '"' -> inQuotes = true
                c == delimiter -> {
                    row.add(field.toString().trim())
                    field = StringBuilder()
                }
                c == '\n' || c == '\r' -> {
                    if (c == '\r' && i + 1 < content.length && content[i + 1] == '\n') i++
                    row.add(field.toString().trim())
                    field = StringBuilder()
                    if (row.any { it.isNotBlank() }) rows.add(row)
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        row.add(field.toString().trim())
        if (row.any { it.isNotBlank() }) rows.add(row)
        return rows
    }
}
