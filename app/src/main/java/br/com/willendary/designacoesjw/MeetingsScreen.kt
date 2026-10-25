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
import br.com.willendary.designacoesjw.data.AppPermissions
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.ui.MeetingProgramList
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
                        OutlinedIconButton(
                            onClick = { weekStart = weekStart.minusWeeks(1); message = null },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.ChevronLeft, "Semana anterior")

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
                        OutlinedIconButton(
                            onClick = { weekStart = weekStart.plusWeeks(1); message = null },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.ChevronRight, "Próxima semana")
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
                        enabled = !importing && vm.can(AppPermissions.GENERATE_ASSIGNMENTS),
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
                WeeklyMeetingCard(meeting, vm)
            }
        }
    }
}

@Composable
private fun WeeklyMeetingCard(meeting: Meeting, vm: AppViewModel) {
    var expanded by remember(meeting.id) { mutableStateOf(true) }
    MeetingCardView(vm, meeting, expanded, onToggleExpand = { expanded = !expanded })
}
