package br.com.willendary.designacoesjw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.willendary.designacoesjw.data.*
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import br.com.willendary.designacoesjw.generator.MeetingPlanner

/** Programa editorial e seletor inline, sem popups específicos de desktop no APK. */
@Composable
fun MeetingProgramList(
    meeting: Meeting,
    brothers: List<Brother>,
    privileges: List<Privilege>,
    onToggleAssignment: (position: Int, brotherId: Long) -> Unit = { _, _ -> },
    canAssign: List<Brother> = emptyList(),
    modifier: Modifier = Modifier,
    canEdit: Boolean = canAssign.isNotEmpty()
) {
    if (meeting.program.isEmpty()) {
        Text("Programa ainda não importado.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
        return
    }
    val byPosition = meeting.programAssignments.groupBy { it.item }
    val sections = meeting.program.withIndex().groupBy { it.value.section.trim() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        sections.forEach { (section, parts) ->
            val accent = when {
                section.contains("tesouros", true) -> Color(0xFF467B79)
                section.contains("ministério", true) -> Color(0xFF9A6B23)
                section.contains("vida", true) -> Color(0xFF8E4C57)
                else -> MaterialTheme.colorScheme.primary
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (section.isNotBlank()) {
                    Surface(color = accent, shape = RoundedCornerShape(4.dp)) {
                        Text(section.uppercase(), Modifier.fillMaxWidth().padding(12.dp),
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                parts.forEach { indexed ->
                    val position = indexed.index + 1
                    val part = indexed.value
                    val ids = byPosition[position].orEmpty().flatMap { it.brotherIds }.toSet()
                    key(meeting.id, position) {
                        var editing by remember { mutableStateOf(false) }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                if (part.number > 0) {
                                    Text("${part.number}.", Modifier.width(32.dp), color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(part.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    if (part.minutes > 0) Text("${part.minutes} min", style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(if (ids.isEmpty()) "A designar" else ids.joinToString(" · ") { id ->
                                        brothers.firstOrNull { it.id == id }?.name ?: "Participante removido"
                                    }, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium,
                                        color = if (ids.isEmpty()) JwTheme.colors.alerta else MaterialTheme.colorScheme.primary)
                                }
                                if (canEdit) IconButton(onClick = { editing = !editing }) {
                                    Icon(Icons.Default.Edit, "Editar participantes", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (editing && canEdit) {
                                ProgramParticipantPicker(meeting, position, ids,
                                    (canAssign + ids.map { id -> brothers.firstOrNull { it.id == id }
                                        ?: Brother(id, "Participante removido", active = false) }).distinctBy { it.id },
                                    onToggle = { onToggleAssignment(position, it) }, onClose = { editing = false })
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramParticipantPicker(
    meeting: Meeting,
    position: Int,
    selectedIds: Set<Long>,
    people: List<Brother>,
    onToggle: (Long) -> Unit,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val normalized = AssignmentGenerator.normalizeName(query)
    val filtered = people.filter { AssignmentGenerator.normalizeName(it.name).contains(normalized) }
        .sortedWith(compareBy<Brother> { it.id !in selectedIds }.thenBy { AssignmentGenerator.normalizeName(it.name) })
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text("Buscar participante") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                if (filtered.isEmpty()) Text("Nenhum participante encontrado.", Modifier.padding(8.dp))
                filtered.forEach { person ->
                    val selected = person.id in selectedIds
                    val problem = MeetingPlanner.programAssignmentError(meeting, position, person.id, people)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(selected, { onToggle(person.id) }, enabled = selected || problem == null)
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(person.name, style = MaterialTheme.typography.bodyMedium)
                            if (problem != null) Text(problem, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (selectedIds.size == 1) "1 selecionado" else "${selectedIds.size} selecionados", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = onClose) { Text("Concluir") }
            }
        }
    }
}
