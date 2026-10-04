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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.willendary.designacoesjw.desktop.StoreController
import br.com.willendary.designacoesjw.stats.EquityStatisticsHelper
import br.com.willendary.designacoesjw.ui.JwCard
import br.com.willendary.designacoesjw.ui.JwCardTitle
import br.com.willendary.designacoesjw.ui.JwTheme
import br.com.willendary.designacoesjw.ui.corDeContorno
import br.com.willendary.designacoesjw.desktop.components.contar
import br.com.willendary.designacoesjw.util.Datas
import java.time.YearMonth

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

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
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

            JwCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedMonth = selectedMonth.minusMonths(1) }) {
                        Icon(Icons.Default.ChevronLeft, "Mês anterior", modifier = Modifier.size(22.dp))
                    }
                    Text(
                        Datas.mesEAno(selectedMonth),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = JwTheme.spacing.sm)
                    )
                    IconButton(onClick = { selectedMonth = selectedMonth.plusMonths(1) }) {
                        Icon(Icons.Default.ChevronRight, "Próximo mês", modifier = Modifier.size(22.dp))
                    }
                }
            }
        }

        // Cards de Indicadores
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            JwCard(modifier = Modifier.weight(1f)) {
                Text("Reuniões no Mês", style = MaterialTheme.typography.labelMedium)
                Text("${report.totalMeetings}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Escalas computadas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            JwCard(modifier = Modifier.weight(1f)) {
                Text("Total de Designações", style = MaterialTheme.typography.labelMedium)
                Text("${report.totalAssignments}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Partes atribuídas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            JwCard(modifier = Modifier.weight(1f)) {
                Text("Irmãos Ativos sem Parte", style = MaterialTheme.typography.labelMedium)
                Text("${report.unassignedActiveBrothers.size}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Precisam de inclusão", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Alerta de Irmãos Não Escalados
        if (report.unassignedActiveBrothers.isNotEmpty()) {
            JwCard(destaque = true) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.md)) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                    Column(Modifier.weight(1f)) {
                        JwCardTitle("Atenção à equidade: irmãos ativos sem nenhuma designação neste mês")
                        Text(
                            report.unassignedActiveBrothers.joinToString(" • ") { it.name },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Ranking
        Text("Participação por Irmão", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        // Lista densa: aqui o cartão por linha só atrapalharia a varredura do
        // olho e custaria uma borda por irmão. Divisória entre as linhas.
        LazyColumn(verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.xs), modifier = Modifier.fillMaxSize()) {
            items(report.ranking, key = { it.brother.id }) { item ->
                val fraction = item.count.toFloat() / maxCount.toFloat()
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = JwTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.xs)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.md)
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

                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.weight(0.35f).height(8.dp),
                            color = when {
                                item.count == 0 -> Color.Gray
                                fraction > 0.8f -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (item.count > 0) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                contar(item.count, "parte", "partes"),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                HorizontalDivider(color = corDeContorno())
            }
        }
    }
}
