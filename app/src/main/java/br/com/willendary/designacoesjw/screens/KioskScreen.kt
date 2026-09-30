package br.com.willendary.designacoesjw.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.willendary.designacoesjw.AppViewModel
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun KioskScreen(vm: AppViewModel, onClose: () -> Unit) {
    var currentTime by remember { mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))) }
    val today = remember { LocalDate.now() }

    LaunchedEffect(Unit) {
        while (true) {
            delay(10000)
            currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        }
    }

    // Identifica próxima reunião
    val nextMeetings = vm.meetings.value.mapNotNull { m ->
        val date = AssignmentGenerator.parseDate(m.date)
        if (date != LocalDate.MIN && !date.isBefore(today)) Pair(m, ChronoUnit.DAYS.between(today, date)) else null
    }.sortedBy { it.second }.map { it.first }

    val currentMeeting = nextMeetings.firstOrNull()
    val nextTalk = vm.publicTalks.value.filter {
        val date = AssignmentGenerator.parseDate(it.date)
        date != LocalDate.MIN && !date.isBefore(today)
    }.minByOrNull { AssignmentGenerator.parseDate(it.date) }

    val nextCleaning = vm.cleaningSchedules.value.filter {
        val date = AssignmentGenerator.parseDate(it.weekDate)
        date != LocalDate.MIN && !date.isBefore(today.minusDays(6))
    }.minByOrNull { AssignmentGenerator.parseDate(it.weekDate) }

    val cleaningGroup = vm.fieldServiceGroups.value.firstOrNull { it.id == nextCleaning?.groupId }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cabeçalho do Telão
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1E293B)) {
                        Icon(Icons.Default.Tv, null, modifier = Modifier.padding(8.dp).size(28.dp), tint = Color(0xFF60A5FA))
                    }
                    Column {
                        Text("Quadro Teocrático de Avisos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Designações da Semana", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(currentTime, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color(0xFF38BDF8))
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Sair do modo telão", tint = Color.White)
                    }
                }
            }

            if (currentMeeting == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhuma reunião futura agendada.", style = MaterialTheme.typography.titleLarge, color = Color(0xFF94A3B8))
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Card Principal da Reunião
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        currentMeeting.type.uppercase(),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8)
                                    )
                                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF0369A1)) {
                                        Text(
                                            currentMeeting.date,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Divider(color = Color(0xFF334155))

                                // Grid de Designações
                                val brothersMap = vm.brothers.value.associateBy { it.id }
                                val privilegesMap = vm.privileges.value.associateBy { it.id }

                                val grouped = currentMeeting.assignments.groupBy { it.privilegeId }
                                grouped.forEach { (privId, assignments) ->
                                    val priv = privilegesMap[privId]
                                    val privName = priv?.name ?: "Designação"
                                    val assignedBrothers = assignments.mapNotNull { brothersMap[it.brotherId]?.name }

                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            privName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Color(0xFFCBD5E1),
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(0.45f)
                                        )
                                        Text(
                                            assignedBrothers.joinToString(" • "),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.weight(0.55f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Card de Discurso Público
                    if (nextTalk != null) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Default.RecordVoiceOver, null, tint = Color(0xFFA78BFA), modifier = Modifier.size(20.dp))
                                            Text("Próximo Discurso Público", fontWeight = FontWeight.Bold, color = Color(0xFFA78BFA))
                                        }
                                        Text(nextTalk.date, style = MaterialTheme.typography.labelMedium, color = Color(0xFFCBD5E1))
                                    }

                                    val talkThemeNumber = nextTalk.themeNumber
                                    val themeFull = if (talkThemeNumber != null && talkThemeNumber > 0) {
                                        "Nº $talkThemeNumber — ${nextTalk.themeTitle}"
                                    } else nextTalk.themeTitle

                                    Text(themeFull, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(
                                        "Orador: ${nextTalk.speakerName} (${nextTalk.speakerCongregation.ifBlank { "Congregação Local" }})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }

                    // Card de Limpeza da Semana
                    if (nextCleaning != null && cleaningGroup != null) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    Modifier.padding(18.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF047857)) {
                                            Icon(Icons.Default.CleaningServices, null, tint = Color.White, modifier = Modifier.padding(8.dp).size(20.dp))
                                        }
                                        Column {
                                            Text("Limpeza do Salão do Reino", style = MaterialTheme.typography.labelMedium, color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                                            Text("Grupo ${cleaningGroup.number} — ${cleaningGroup.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                    Text("Semana de ${nextCleaning.weekDate}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFCBD5E1))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
