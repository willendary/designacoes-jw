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
import androidx.compose.ui.unit.dp
import br.com.willendary.designacoesjw.desktop.StoreController
import br.com.willendary.designacoesjw.stats.EquityStatisticsHelper
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DesktopEquityScreen(c: StoreController) {
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }

    val report = remember(selectedMonth, c.data.meetings, c.data.brothers, c.data.privileges) {
        EquityStatisticsHelper.calculateMonthStats(
            month = selectedMonth,
            meetings = c.data.meetings,
            brothers = c.data.brothers,
            privileges = c.data.privileges
        )
    }

    val maxCount = report.ranking.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1

    Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize()) {
        // Barra Superior com Seletor de Mês
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Estatísticas & Equilíbrio Teocrático", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Acompanhamento da distribuição justa de designações entre os irmãos da congregação",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedMonth = selectedMonth.minusMonths(1) }) {
                        Icon(Icons.Default.ChevronLeft, "Mês anterior")
                    }
                    Text(
                        "${selectedMonth.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR")).replaceFirstChar { it.uppercase() }} de ${selectedMonth.year}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    IconButton(onClick = { selectedMonth = selectedMonth.plusMonths(1) }) {
                        Icon(Icons.Default.ChevronRight, "Próximo mês")
                    }
                }
            }
        }

        // Cards de Indicadores
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Reuniões no Mês", style = MaterialTheme.typography.labelMedium)
                    Text("${report.totalMeetings}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Escalas computadas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                }
            }
            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Total de Designações", style = MaterialTheme.typography.labelMedium)
                    Text("${report.totalAssignments}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Partes atribuídas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                }
            }
            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Irmãos Ativos sem Parte", style = MaterialTheme.typography.labelMedium)
                    Text("${report.unassignedActiveBrothers.size}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Precisam de inclusão", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f))
                }
            }
        }

        // Alerta de Irmãos Não Escalados
        if (report.unassignedActiveBrothers.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Atenção à equidade: Irmãos ativos sem nenhuma designação neste mês", fontWeight = FontWeight.Bold)
                        Text(
                            report.unassignedActiveBrothers.joinToString(" • ") { it.name },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Ranking
        Text("Participação por Irmão", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(report.ranking, key = { it.brother.id }) { item ->
                val fraction = item.count.toFloat() / maxCount.toFloat()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (item.count == 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(0.45f)) {
                            Text(item.brother.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (item.privilegesCount.isNotEmpty()) {
                                Text(
                                    item.privilegesCount.entries.joinToString(" • ") { "${it.key}: ${it.value}" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text("Nenhuma designação neste mês", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }

                        Column(Modifier.weight(0.4f).padding(horizontal = 16.dp)) {
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = when {
                                    item.count == 0 -> Color.Gray
                                    fraction > 0.8f -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (item.count > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                "${item.count} parte(s)",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
