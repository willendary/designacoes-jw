package br.com.willendary.designacoesjw.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import br.com.willendary.designacoesjw.ui.MeetingProgramList
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

/** Programa semanal separado da escala de privilégios. */
@Composable
internal fun DesktopMeetingsScreen(c: StoreController) {
    var week by remember { mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val date = week.plusDays((c.data.firstDay - 1).coerceIn(0, 6).toLong())
    val meetings = c.data.meetings.filter {
        val day = AssignmentGenerator.parseDate(it.date)
        day >= week && day <= week.plusDays(6)
    }.sortedBy { AssignmentGenerator.parseDate(it.date) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Reuniões", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${week.format(AssignmentGenerator.DATE_FORMATTER)} — ${week.plusDays(6).format(AssignmentGenerator.DATE_FORMATTER)}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { week = week.minusWeeks(1); error = null }) { Text("Semana anterior") }
                    OutlinedButton(onClick = { week = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); error = null }) { Text("Esta semana") }
                    OutlinedButton(onClick = { week = week.plusWeeks(1); error = null }) { Text("Próxima semana") }
                }
                Button(enabled = !importing, onClick = {
                    importing = true
                    error = null
                    c.importMwbWeek(date) { importing = false; error = it }
                }) { Text(if (importing) "Importando…" else "Importar programa de ${date.format(AssignmentGenerator.DATE_FORMATTER)}") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        if (meetings.isEmpty()) item { Text("Nenhuma reunião cadastrada nesta semana. Importe o programa para começar.") }
        items(meetings, key = { it.id }) { meeting ->
            Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(meeting.date + " — " + meeting.typeCanonical, style = MaterialTheme.typography.titleLarge)
                if (meeting.theme.isNotBlank()) Text(meeting.theme, style = MaterialTheme.typography.titleMedium)
                if (meeting.isMidweek) MeetingProgramList(meeting, c.data.brothers, emptyList(),
                    canAssign = c.data.brothers.filter { it.active }, canEdit = true,
                    decodeImage = DesktopPublicationImageDecoder,
                    onToggleAssignment = { position, id -> c.toggleProgramAssignment(meeting.id, position, id) })
                else Text("O programa de fim de semana ainda não tem importação automática.")
            }
        }
    }
}
