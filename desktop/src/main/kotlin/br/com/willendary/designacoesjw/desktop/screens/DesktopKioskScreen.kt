package br.com.willendary.designacoesjw.desktop.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.willendary.designacoesjw.desktop.StoreController
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import br.com.willendary.designacoesjw.ui.JwTheme
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Quadro de avisos do salão, em tela cheia, na janela do desktop.
 *
 * Mesmas três regras do `KioskScreen` do Android, e por isso a mesma paleta:
 * `JwTheme.colors.telao*` não muda com o tema escolhido, **não** há cartão com
 * borda fina (a 3 metros o traço some) e o nome do designado é o texto mais
 * grande da tela.
 *
 * Antes eram três `Card` com `Divider` escuro dentro. Uma tela de salão separada
 * por traço é uma tela de salão que some no fundo da sala.
 */
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

    val cores = JwTheme.colors

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = cores.telaoFundo,
        contentColor = cores.telaoTexto
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Barra Superior do Telão
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Default.Tv, null, modifier = Modifier.size(44.dp), tint = cores.telaoPrimaria)
                    Column {
                        Text(
                            "Quadro Teocrático de Avisos",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Programação e Designações da Semana",
                            style = MaterialTheme.typography.titleLarge,
                            color = cores.telaoPrimaria
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(currentTime, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = cores.telaoPrimaria)
                    OutlinedButton(onClick = onExit, colors = ButtonDefaults.outlinedButtonColors(contentColor = cores.telaoTexto)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sair do Telão", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            if (currentMeeting == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhuma reunião futura agendada no histórico.",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    // Coluna Esquerda: Reunião Principal e Designações (65% largura)
                    Column(
                        modifier = Modifier.weight(0.65f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    currentMeeting.type.uppercase(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = cores.telaoPrimaria
                                )
                                Text(
                                    "Reunião da Congregação",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                            Surface(shape = RoundedCornerShape(12.dp), color = cores.telaoPrimaria) {
                                Text(
                                    currentMeeting.date,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = cores.telaoFundo,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                )
                            }
                        }

                        if (currentMeeting.theme.isNotBlank()) {
                            Text(
                                currentMeeting.theme,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val brothersMap = c.data.brothers.associateBy { it.id }
                        val privilegesMap = c.data.privileges.associateBy { it.id }

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxSize()) {
                            val grouped = currentMeeting.assignments.groupBy { it.privilegeId }
                            items(grouped.entries.toList(), key = { it.key }) { (privId, assignments) ->
                                val privName = privilegesMap[privId]?.name ?: "Designação"
                                val assignedBrothers = assignments.mapNotNull { brothersMap[it.brotherId]?.name }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        privName,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(0.38f)
                                    )
                                    Text(
                                        assignedBrothers.joinToString(" • "),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(0.62f)
                                    )
                                }
                            }
                        }
                    }

                    // Coluna Direita: Discurso Público e Limpeza (35% largura)
                    Column(
                        modifier = Modifier.weight(0.35f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(40.dp)
                    ) {
                        // Discurso Público
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.RecordVoiceOver, null, tint = cores.telaoPrimaria, modifier = Modifier.size(30.dp))
                                rotuloDeQuadro("DISCURSO PÚBLICO")
                            }

                            nextTalk?.let {
                                Text(it.date, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }

                            val talk = nextTalk
                            if (talk != null) {
                                val themeFull = if ((talk.themeNumber ?: 0) > 0) {
                                    "Nº ${talk.themeNumber} — ${talk.themeTitle}"
                                } else talk.themeTitle

                                Text(
                                    themeFull,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "Orador Convidado:",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    talk.speakerName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = cores.telaoPrimaria
                                )
                                if (talk.speakerCongregation.isNotBlank()) {
                                    Text(talk.speakerCongregation, style = MaterialTheme.typography.titleLarge)
                                }
                            } else {
                                Text(
                                    "Nenhum orador público agendado para o próximo fim de semana.",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        }

                        // Limpeza
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(0.7f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.CleaningServices, null, tint = cores.telaoPrimaria, modifier = Modifier.size(30.dp))
                                rotuloDeQuadro("LIMPEZA DO SALÃO")
                            }
                            if (cleaningGroup != null && nextCleaning != null) {
                                Text(
                                    "Grupo ${cleaningGroup.number} — ${cleaningGroup.name}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("Semana de ${nextCleaning.weekDate}", style = MaterialTheme.typography.titleLarge)
                            } else {
                                Text("Nenhum grupo atribuído para a semana.", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Rótulo de bloco do quadro: maiúsculo, na cor fixa do quadro, espaçado.
 *
 * Fica aqui e não em `shared/` porque `JwSectionLabel` usa
 * `colorScheme.primary`, que muda com a paleta escolhida. Na parede não há
 * paleta escolhida.
 */
@Composable
private fun rotuloDeQuadro(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = JwTheme.colors.telaoPrimaria,
        letterSpacing = 1.sp
    )
}
