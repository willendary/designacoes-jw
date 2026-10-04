package br.com.willendary.designacoesjw.screens

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
import androidx.compose.ui.unit.dp
import br.com.willendary.designacoesjw.AppViewModel
import br.com.willendary.designacoesjw.stats.EquityStatisticsHelper
import br.com.willendary.designacoesjw.ui.JwCard
import br.com.willendary.designacoesjw.ui.JwCardRail
import br.com.willendary.designacoesjw.ui.JwSectionLabel
import br.com.willendary.designacoesjw.ui.JwTheme
import br.com.willendary.designacoesjw.util.Datas
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquityStatisticsScreen(vm: AppViewModel) {
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }

    val report = remember(selectedMonth, vm.meetings.value, vm.brothers.value, vm.privileges.value) {
        EquityStatisticsHelper.calculateMonthStats(
            month = selectedMonth,
            meetings = vm.meetings.value,
            brothers = vm.brothers.value,
            privileges = vm.privileges.value
        )
    }

    val maxCount = report.ranking.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Seletor de Mês
        item {
            JwCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedMonth = selectedMonth.minusMonths(1) }) {
                        Icon(Icons.Default.ChevronLeft, "Mês anterior")
                    }
                    Text(
                        Datas.mesEAno(selectedMonth),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { selectedMonth = selectedMonth.plusMonths(1) }) {
                        Icon(Icons.Default.ChevronRight, "Próximo mês")
                    }
                }
            }
        }

        // Cards de Indicadores
        item {
            // Destaque: os três números são a resposta que a tela promete.
            JwCard(destaque = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm)
                ) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${report.totalMeetings}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        JwSectionLabel("Reuniões")
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${report.totalAssignments}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        JwSectionLabel("Designações")
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${report.unassignedActiveBrothers.size}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        JwSectionLabel("Sem partes")
                    }
                }
            }
        }

        // Alerta de Irmãos Sem Partes
        if (report.unassignedActiveBrothers.isNotEmpty()) {
            item {
                JwCard {
                    // Trilho em vez de fundo em alpha: alpha não sobrevive à
                    // impressão a laser e no modo escuro vira lama.
                    JwCardRail(JwTheme.colors.perigo)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm)
                    ) {
                        Icon(Icons.Default.Info, null, tint = JwTheme.colors.perigo, modifier = Modifier.size(18.dp))
                        Text("Irmãos ativos sem designação neste mês:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        report.unassignedActiveBrothers.joinToString(" • ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Título do Ranking
        item {
            JwSectionLabel("Distribuição de Designações por Irmão")
        }

        if (report.ranking.isEmpty()) {
            item {
                JwCard {
                    Icon(Icons.Default.Person, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
                    Text("Nenhum irmão cadastrado.", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Cadastre os irmãos em 'Irmãos & Irmãs' para a distribuição do mês aparecer aqui.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        items(report.ranking, key = { it.brother.id }) { item ->
            val fraction = item.count.toFloat() / maxCount.toFloat()
            JwCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.brother.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (item.count > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            if (item.count == 1) "1 parte" else "${item.count} partes",
                            modifier = Modifier.padding(horizontal = JwTheme.spacing.sm, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = when {
                        item.count == 0 -> JwTheme.colors.grade
                        fraction > 0.8f -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )

                if (item.privilegesCount.isNotEmpty()) {
                    Text(
                        item.privilegesCount.entries.joinToString(" • ") { "${it.key}: ${it.value}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
