package br.com.willendary.designacoesjw.desktop.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.willendary.designacoesjw.desktop.StoreController
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import br.com.willendary.designacoesjw.ui.JwCard
import br.com.willendary.designacoesjw.ui.JwTheme
import br.com.willendary.designacoesjw.ui.corDeContorno
import br.com.willendary.designacoesjw.ui.superficieDeCartao
import br.com.willendary.designacoesjw.util.Datas
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun DesktopPrintReportScreen(c: StoreController) {
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    val monthPrefix = remember(selectedMonth) {
        selectedMonth.format(DateTimeFormatter.ofPattern("MM/yyyy"))
    }
    val monthMeetings = c.data.meetings.filter { it.date.endsWith("/$monthPrefix") }
        .sortedBy { AssignmentGenerator.parseDate(it.date) }

    val brothersMap = c.data.brothers.associateBy { it.id }
    val privilegesMap = c.data.privileges.associateBy { it.id }
    val activePrivileges = c.data.privileges.filter { it.active }.sortedBy { it.name }

    val monthFormatted = Datas.mesEAno(selectedMonth)

    Column(verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.md), modifier = Modifier.fillMaxSize()) {
        // Cabeçalho da Tela
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Relatório A4 e Impressão", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Visualização diagramada para impressão e exportação do programa mensal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Ações Rápidas
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        try {
                            val textReport = buildString {
                                appendLine("📋 PROGRAMA DE DESIGNAÇÕES — $monthFormatted")
                                appendLine("==========================================")
                                monthMeetings.forEach { m ->
                                    appendLine("\n📅 ${m.date} (${m.type})")
                                    m.assignments.forEach { a ->
                                        val priv = privilegesMap[a.privilegeId]?.name ?: "Parte"
                                        val name = brothersMap[a.brotherId]?.name ?: "?"
                                        appendLine("  • $priv: $name")
                                    }
                                }
                            }
                            val sel = StringSelection(textReport)
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, null)
                            actionMessage = "Programa copiado para a área de transferência!"
                        } catch (e: Exception) {
                            actionMessage = "Erro ao copiar: ${e.message}"
                        }
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copiar Texto")
                }

                Button(
                    onClick = {
                        try {
                            val file = c.exportHtmlReport(selectedMonth)
                            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                                Desktop.getDesktop().open(file)
                            }
                            actionMessage = "Relatório HTML gerado em: ${file.name}"
                        } catch (e: Exception) {
                            actionMessage = "Erro ao gerar HTML: ${e.message}"
                        }
                    }
                ) {
                    Icon(Icons.Default.Print, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Gerar Página A4 (HTML)")
                }

                FilledTonalButton(
                    onClick = {
                        try {
                            val file = c.exportIcsReport(selectedMonth)
                            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                                Desktop.getDesktop().open(file.parentFile)
                            }
                            actionMessage = "Calendário iCal salvo: ${file.name}"
                        } catch (e: Exception) {
                            actionMessage = "Erro ao exportar iCal: ${e.message}"
                        }
                    }
                ) {
                    Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Exportar iCal (.ics)")
                }
            }
        }

        // Seletor de Mês e Mensagem de Status
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            JwCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedMonth = selectedMonth.minusMonths(1) }) {
                        Icon(Icons.Default.ChevronLeft, "Mês anterior", modifier = Modifier.size(22.dp))
                    }
                    Text(
                        monthFormatted,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(onClick = { selectedMonth = selectedMonth.plusMonths(1) }) {
                        Icon(Icons.Default.ChevronRight, "Próximo mês", modifier = Modifier.size(22.dp))
                    }
                }
            }

            actionMessage?.let { msg ->
                // Aviso de ação, não cartão: precisa do fundo de destaque para
                // ser lido, e `JwCard` não tem essa cor.
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Text(msg, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        IconButton(onClick = { actionMessage = null }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Visualização da Folha Diagramada (Folha A4). A folha é a "unidade"
        // desta tela: mesmo papel e mesmo traço dos `JwCard` de fora, para a
        // janela não parecer feita de dois sistemas de cartão diferentes.
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            color = superficieDeCartao(isSystemInDarkTheme()),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, corDeContorno())
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    // Cabeçalho Oficial
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "PROGRAMA DE DESIGNAÇÕES DA CONGREGAÇÃO",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            monthFormatted.uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Divider(
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            thickness = 2.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    }
                }

                if (monthMeetings.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Nenhuma reunião programada para este mês.\nGere as reuniões na aba 'Início'.",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.outline,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                } else {
                    item {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(scrollState)
                        ) {
                            // Cabeçalho da Tabela
                            Row(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("DATA", fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                                Text("REUNIÃO", fontWeight = FontWeight.Bold, modifier = Modifier.width(160.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                                activePrivileges.forEach { priv ->
                                    Text(priv.name.uppercase(), fontWeight = FontWeight.Bold, modifier = Modifier.width(150.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                                }
                            }

                            HorizontalDivider(thickness = 2.dp, color = corDeContorno())

                            // Linhas das Reuniões. Sem zebra: a linha já tem
                            // divisória, e fundo alternado é o que some na
                            // impressão a laser.
                            monthMeetings.forEach { meeting ->
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        meeting.date,
                                        modifier = Modifier.width(110.dp),
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        meeting.type,
                                        modifier = Modifier.width(160.dp),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    activePrivileges.forEach { priv ->
                                        val assigned = meeting.assignments
                                            .filter { it.privilegeId == priv.id }
                                            .mapNotNull { brothersMap[it.brotherId]?.name }

                                        Text(
                                            assigned.joinToString("\n").ifBlank { "—" },
                                            modifier = Modifier.width(150.dp),
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (assigned.isNotEmpty()) FontWeight.Medium else FontWeight.Normal,
                                            color = if (assigned.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
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
}
