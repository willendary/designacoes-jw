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
    canEdit: Boolean = canAssign.isNotEmpty(),
    decodeImage: ((ByteArray) -> androidx.compose.ui.graphics.ImageBitmap?)? = null
) {
    if (meeting.program.isEmpty()) {
        Text("Programa ainda não importado.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
        return
    }
    val byPosition = meeting.programAssignments.groupBy { it.item }
    Column(modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val source = meeting.program.firstNotNullOfOrNull { it.content.sourceUrl.takeIf(String::isNotBlank) }
        if (source != null) PublicationLink("Abrir publicação original", source)
        else Text("Programa resumido. Importe novamente para carregar o conteúdo da publicação.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        meeting.program.forEachIndexed { index, part ->
            val position = index + 1
            val ids = byPosition[position].orEmpty().flatMap { it.brotherIds }.toSet()
            key(meeting.id, position) {
                var editing by remember { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PublicationBlocks(part.content.before, decodeImage)
                    if (part.content.before.none { it.type == "section" } && part.section.isNotBlank() &&
                        (index == 0 || meeting.program[index - 1].section != part.section)) {
                        PublicationSection(part.section)
                    }
                    Text((if (part.number > 0) "${part.number}. " else "") + part.title,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = publicationAccent(part.section))
                    if (part.content.body.isEmpty() && part.minutes > 0) Text("${part.minutes} min",
                        style = MaterialTheme.typography.bodyMedium)
                    // As designações continuam ligadas à posição da parte, nunca aos blocos editoriais.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (ids.isEmpty()) "A designar" else ids.joinToString(" · ") { id ->
                            brothers.firstOrNull { it.id == id }?.name ?: "Participante removido"
                        }, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                            color = if (ids.isEmpty()) JwTheme.colors.alerta else MaterialTheme.colorScheme.primary)
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
                    PublicationBlocks(part.content.body, decodeImage)
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
