package br.com.willendary.designacoesjw

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.willendary.designacoesjw.data.Meeting
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun MeetingsScreen(vm: AppViewModel) {
    val formatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    var weekStart by remember {
        mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    }
    var importing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }

    val weekEnd = weekStart.plusDays(6)
    val meetingDate = weekStart.plusDays((vm.schedule.value.firstDay - 1).coerceIn(0, 6).toLong())
    val weekMeetings = vm.meetings.value
        .filter { meeting ->
            val d = runCatching {
                br.com.willendary.designacoesjw.generator.AssignmentGenerator.parseDate(meeting.date)
            }.getOrDefault(LocalDate.MIN)
            !d.isBefore(weekStart) && !d.isAfter(weekEnd)
        }
        .sortedBy {
            br.com.willendary.designacoesjw.generator.AssignmentGenerator.parseDate(it.date)
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Reuniões da semana", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "${weekStart.format(formatter)} — ${weekEnd.format(formatter)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Filled.Event, contentDescription = null)
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { weekStart = weekStart.minusWeeks(1); message = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.ChevronLeft, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Anterior")
                        }
                        OutlinedButton(
                            onClick = {
                                weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                                message = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Esta semana")
                        }
                        OutlinedButton(
                            onClick = { weekStart = weekStart.plusWeeks(1); message = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Próxima")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Filled.ChevronRight, null)
                        }
                    }

                    Button(
                        onClick = {
                            importing = true
                            message = null
                            error = false
                            vm.importMwbWeek(meetingDate) { result ->
                                importing = false
                                error = result != null
                                message = result ?: "Programa da semana importado com sucesso."
                            }
                        },
                        enabled = !importing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (importing) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.CloudDownload, null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (importing) "Importando do jw.org..."
                            else "Importar reunião de ${meetingDate.format(formatter)}"
                        )
                    }

                    message?.let {
                        Text(
                            it,
                            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            Text("Reuniões cadastradas nesta semana", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (weekMeetings.isEmpty()) {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Nenhuma reunião cadastrada nesta semana.")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Use o botão acima para importar a reunião de meio de semana.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(weekMeetings, key = { it.id }) { meeting ->
                WeeklyMeetingCard(meeting)
            }
        }
    }
}

@Composable
private fun WeeklyMeetingCard(meeting: Meeting) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(meeting.date, fontWeight = FontWeight.Bold)
                Text(
                    meeting.type,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (meeting.theme.isNotBlank()) {
                Text(meeting.theme, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            if (meeting.program.isEmpty()) {
                Text(
                    "Programa ainda não importado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "${meeting.program.size} itens do programa:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                meeting.program.forEach { item ->
                    Text("• ${item.label}", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (meeting.assignments.isNotEmpty()) {
                Text("${meeting.assignments.size} designações cadastradas.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
