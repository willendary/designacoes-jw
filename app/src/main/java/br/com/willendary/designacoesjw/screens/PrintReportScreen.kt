package br.com.willendary.designacoesjw.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.willendary.designacoesjw.AppViewModel
import br.com.willendary.designacoesjw.export.HtmlReportGenerator
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import br.com.willendary.designacoesjw.ui.JwCard
import br.com.willendary.designacoesjw.ui.JwTheme
import br.com.willendary.designacoesjw.ui.corDeContorno
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintReportScreen(
    vm: AppViewModel,
    // Mês controlado de fora: o quadro do mês e a exportação de imagens abrem
    // a partir desta tela e precisam mostrar o mesmo mês. Com o estado aqui
    // dentro, trocar de mês num diálogo deixava o relatório mostrando outro.
    month: YearMonth = YearMonth.now(),
    onMonthChange: (YearMonth) -> Unit = {}
) {
    val context = LocalContext.current
    val selectedMonth = month

    val monthPrefix = remember(selectedMonth) {
        selectedMonth.format(java.time.format.DateTimeFormatter.ofPattern("MM/yyyy"))
    }
    val rotuloMes = remember(selectedMonth) {
        "${selectedMonth.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR")).replaceFirstChar { it.uppercase() }} de ${selectedMonth.year}"
    }
    val monthMeetings = vm.meetings.value.filter { it.date.endsWith("/$monthPrefix") }
        .sortedBy { AssignmentGenerator.parseDate(it.date) }

    val brothersMap = vm.brothers.value.associateBy { it.id }
    val privilegesMap = vm.privileges.value.associateBy { it.id }
    val activePrivileges = vm.privileges.value.filter { it.active }.sortedBy { it.name }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Seletor de Mês e Ações
        item {
            JwCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onMonthChange(selectedMonth.minusMonths(1)) }) {
                        Icon(Icons.Default.ChevronLeft, "Mês anterior")
                    }
                    Text(
                        rotuloMes,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { onMonthChange(selectedMonth.plusMonths(1)) }) {
                        Icon(Icons.Default.ChevronRight, "Próximo mês")
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val textReport = buildString {
                            appendLine("📋 PROGRAMA DE DESIGNAÇÕES — ${selectedMonth.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))} ${selectedMonth.year}")
                            appendLine("==========================================")
                            monthMeetings.forEach { m ->
                                appendLine("\n📅 ${m.date} (${m.type})")
                                m.assignments.forEach { a ->
                                    val priv = privilegesMap[a.privilegeId]?.name ?: "Parte"
                                    val name = brothersMap[a.brotherId]?.name ?: "?"
                                    appendLine("• $priv: $name")
                                }
                            }
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Programa de Reuniões")
                            putExtra(Intent.EXTRA_TEXT, textReport)
                        }
                        context.startActivity(Intent.createChooser(intent, "Compartilhar Programa"))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(JwTheme.spacing.sm))
                    Text("Compartilhar Texto")
                }
            }
        }

        // Simulação da Folha A4
        item {
            // Destaque: a folha é o que vai sair na impressora.
            JwCard(destaque = true) {
                // Cabeçalho da Folha
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "PROGRAMA DE DESIGNAÇÕES DA CONGREGAÇÃO",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        rotuloMes,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (monthMeetings.isEmpty()) {
                    Text(
                        "Nenhuma reunião gerada para este mês.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = JwTheme.spacing.xl),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        "Gere o quadro do mês em 'Quadro do Mês' e volte aqui para imprimir ou compartilhar.",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    // Tabela rolável horizontalmente
                    val scrollState = rememberScrollState()
                    Column(Modifier.horizontalScroll(scrollState)) {
                        // Cabeçalho da Tabela
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                                .padding(vertical = JwTheme.spacing.sm, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Data", fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                            Text("Reunião", fontWeight = FontWeight.Bold, modifier = Modifier.width(130.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                            activePrivileges.forEach { priv ->
                                Text(priv.name, fontWeight = FontWeight.Bold, modifier = Modifier.width(120.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        HorizontalDivider(color = corDeContorno())

                        // Linhas das Reuniões
                        monthMeetings.forEachIndexed { idx, meeting ->
                            val rowBg = if (idx % 2 == 0) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            Row(
                                modifier = Modifier
                                    .background(rowBg)
                                    .padding(vertical = JwTheme.spacing.sm, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(meeting.date, modifier = Modifier.width(90.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                Text(meeting.type, modifier = Modifier.width(130.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                                activePrivileges.forEach { priv ->
                                    val assigned = meeting.assignments
                                        .filter { it.privilegeId == priv.id }
                                        .mapNotNull { brothersMap[it.brotherId]?.name }
                                    Text(
                                        assigned.joinToString("\n").ifBlank { "—" },
                                        modifier = Modifier.width(120.dp),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (assigned.isNotEmpty()) FontWeight.Medium else FontWeight.Normal
                                    )
                                }
                            }
                            HorizontalDivider(color = corDeContorno())
                        }
                    }
                }
            }
        }
    }
}
