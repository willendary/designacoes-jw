package br.com.willendary.designacoesjw.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import br.com.willendary.designacoesjw.AppViewModel
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import br.com.willendary.designacoesjw.ui.JwTheme
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Quadro de avisos do salão, em tela cheia.
 *
 * Três regras que valem mais aqui do que em qualquer outra tela do app:
 *
 * 1. **A paleta é fixa.** Vem de `JwTheme.colors.telao*`, que é o mesmo valor
 *    no modo claro e no escuro: ninguém escolhe tema para um quadro na parede,
 *    e o projector não acerta o branco. Nada aqui segue `colorScheme`.
 * 2. **Nada de cartão.** O bloco é separado por **espaço e por tamanho**, não
 *    por borda: a 3 metros um traço de 1 px some, e o que separa de verdade é o
 *    vão entre um bloco e o outro e o tamanho do texto dentro de cada um.
 * 3. **O designado é o maior texto da tela.** O privilégio é o rótulo; o nome
 *    de quem vai fazer a parte é o que se procura do fundo da sala. Antes o
 *    contrário: o privilégio era `bodyLarge` e o nome `titleMedium`.
 */
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

    val cores = JwTheme.colors

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = cores.telaoFundo,
        contentColor = cores.telaoTexto
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // Cabeçalho do Telão
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Default.Tv, null, modifier = Modifier.size(36.dp), tint = cores.telaoPrimaria)
                    Column {
                        Text("Quadro Teocrático de Avisos", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Designações da Semana", style = MaterialTheme.typography.titleMedium, color = cores.telaoPrimaria)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(currentTime, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = cores.telaoPrimaria)
                    IconButton(onClick = onClose, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.Close, "Sair do modo telão", modifier = Modifier.size(32.dp), tint = cores.telaoTexto)
                    }
                }
            }

            if (currentMeeting == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhuma reunião futura agendada.", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Reunião: o bloco que se lê primeiro. Sem cartão — o vão de
                    // 32 dp e o peso do texto fazem o papel do cartão.
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    currentMeeting.type.uppercase(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = cores.telaoPrimaria
                                )
                                Surface(shape = RoundedCornerShape(10.dp), color = cores.telaoPrimaria) {
                                    Text(
                                        currentMeeting.date,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = cores.telaoFundo,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            // Grid de Designações
                            val brothersMap = vm.brothers.value.associateBy { it.id }
                            val privilegesMap = vm.privileges.value.associateBy { it.id }

                            val grouped = currentMeeting.assignments.groupBy { it.privilegeId }
                            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                grouped.forEach { (privId, assignments) ->
                                    val priv = privilegesMap[privId]
                                    val privName = priv?.name ?: "Designação"
                                    val assignedBrothers = assignments.mapNotNull { brothersMap[it.brotherId]?.name }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            privName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(0.42f)
                                        )
                                        Text(
                                            assignedBrothers.joinToString(" • "),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.weight(0.58f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Próximo Discurso Público
                    if (nextTalk != null) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                rotuloDeBloco("PRÓXIMO DISCURSO PÚBLICO")
                                Text(nextTalk.date, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                                val talkThemeNumber = nextTalk.themeNumber
                                val themeFull = if (talkThemeNumber != null && talkThemeNumber > 0) {
                                    "Nº $talkThemeNumber — ${nextTalk.themeTitle}"
                                } else nextTalk.themeTitle

                                Text(themeFull, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "Orador: ${nextTalk.speakerName} (${nextTalk.speakerCongregation.ifBlank { "Congregação Local" }})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }

                    // Limpeza da Semana
                    if (nextCleaning != null && cleaningGroup != null) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Icon(Icons.Default.CleaningServices, null, tint = cores.telaoPrimaria, modifier = Modifier.size(32.dp))
                                    rotuloDeBloco("LIMPEZA DO SALÃO DO REINO")
                                }
                                Text(
                                    "Grupo ${cleaningGroup.number} — ${cleaningGroup.name}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("Semana de ${nextCleaning.weekDate}", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Rótulo de bloco do quadro: maiúsculo, na cor do quadro, espaçado.
 *
 * Existe aqui e não em `shared/` porque `JwSectionLabel` usa
 * `colorScheme.primary` — que muda com a paleta escolhida. Na parede não há
 * paleta escolhida.
 */
@Composable
private fun rotuloDeBloco(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = JwTheme.colors.telaoPrimaria,
        letterSpacing = 1.sp
    )
}
