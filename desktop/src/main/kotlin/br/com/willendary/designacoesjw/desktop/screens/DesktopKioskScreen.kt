package br.com.willendary.designacoesjw.desktop.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import br.com.willendary.designacoesjw.desktop.StoreController
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun DesktopKioskScreen(c: StoreController, onExit: () -> Unit) {
    var currentTime by remember { mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))) }
    val today = remember { LocalDate.now() }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        }
    }

    // Próxima reunião
    val nextMeetings = c.data.meetings.mapNotNull { m ->
        val date = AssignmentGenerator.parseDate(m.date)
        if (date != LocalDate.MIN && !date.isBefore(today)) Pair(m, ChronoUnit.DAYS.between(today, date)) else null
    }.sortedBy { it.second }.map { it.first }

    val currentMeeting = nextMeetings.firstOrNull()
    val nextTalk = c.data.publicTalks.filter {
        val date = AssignmentGenerator.parseDate(it.date)
        date != LocalDate.MIN && !date.isBefore(today)
    }.minByOrNull { AssignmentGenerator.parseDate(it.date) }

    val nextCleaning = c.data.cleaningSchedules.filter {
        val date = AssignmentGenerator.parseDate(it.weekDate)
        date != LocalDate.MIN && !date.isBefore(today.minusDays(6))
    }.minByOrNull { AssignmentGenerator.parseDate(it.weekDate) }

    val cleaningGroup = c.data.fieldServiceGroups.firstOrNull { it.id == nextCleaning?.groupId }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Barra Superior do Telão
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF1E293B)) {
                        Icon(Icons.Default.Tv, null, modifier = Modifier.padding(12.dp).size(36.dp), tint = Color(0xFF38BDF8))
                    }
                    Column {
                        Text(
                            "Quadro Teocrático de Avisos",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "Programação e Designações da Semana",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1E293B)) {
                        Text(
                            currentTime,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                        )
                    }
                    OutlinedButton(onClick = onExit, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sair do Telão")
                    }
                }
            }

            if (currentMeeting == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhuma reunião futura agendada no histórico.", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF94A3B8))
                }
            } else {
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    // Coluna Esquerda: Reunião Principal e Designações (65% largura)
                    Card(
                        modifier = Modifier.weight(0.65f).fillMaxHeight(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        currentMeeting.type.uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8)
                                    )
                                    Text(
                                        "Reunião da Congregação",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF0284C7)) {
                                    Text(
                                        currentMeeting.date,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }

                            Divider(color = Color(0xFF334155))

                            if (currentMeeting.theme.isNotBlank()) {
                                Text(
                                    currentMeeting.theme,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            val brothersMap = c.data.brothers.associateBy { it.id }
                            val privilegesMap = c.data.privileges.associateBy { it.id }

                            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                                val grouped = currentMeeting.assignments.groupBy { it.privilegeId }
                                items(grouped.entries.toList(), key = { it.key }) { (privId, assignments) ->
                                    val privName = privilegesMap[privId]?.name ?: "Designação"
                                    val assignedBrothers = assignments.mapNotNull { brothersMap[it.brotherId]?.name }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF0F172A).copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                privName,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color(0xFF94A3B8),
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(0.4f)
                                            )
                                            Text(
                                                assignedBrothers.joinToString(" • "),
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                textAlign = TextAlign.End,
                                                modifier = Modifier.weight(0.6f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Coluna Direita: Discurso Público e Limpeza (35% largura)
                    Column(modifier = Modifier.weight(0.35f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        // Card Discurso Público
                        Card(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.RecordVoiceOver, null, tint = Color(0xFFA78BFA), modifier = Modifier.size(24.dp))
                                        Text("Discurso Público", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFA78BFA))
                                    }
                                    nextTalk?.let {
                                        Text(it.date, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF94A3B8))
                                    }
                                }

                                Divider(color = Color(0xFF334155))

                                val talk = nextTalk
                                if (talk != null) {
                                    val themeFull = if ((talk.themeNumber ?: 0) > 0) {
                                        "Nº ${talk.themeNumber} — ${talk.themeTitle}"
                                    } else talk.themeTitle

                                    Text(
                                        themeFull,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "Orador Convidado:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFF94A3B8)
                                    )
                                    Text(
                                        talk.speakerName,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8)
                                    )
                                    if (talk.speakerCongregation.isNotBlank()) {
                                        Text(
                                            talk.speakerCongregation,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                } else {
                                    Text("Nenhum orador público agendado para o próximo fim de semana.", color = Color(0xFF94A3B8))
                                }
                            }
                        }

                        // Card Limpeza
                        Card(
                            modifier = Modifier.weight(0.7f).fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.CleaningServices, null, tint = Color(0xFF34D399), modifier = Modifier.size(24.dp))
                                    Text("Limpeza do Salão", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                                }
                                Divider(color = Color(0xFF334155))
                                if (cleaningGroup != null && nextCleaning != null) {
                                    Text(
                                        "Grupo ${cleaningGroup.number} — ${cleaningGroup.name}",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text("Semana de ${nextCleaning.weekDate}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF94A3B8))
                                } else {
                                    Text("Nenhum grupo atribuído para a semana.", color = Color(0xFF94A3B8))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
