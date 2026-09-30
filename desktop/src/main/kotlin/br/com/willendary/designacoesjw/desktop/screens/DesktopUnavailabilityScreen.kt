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
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.desktop.StoreController
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import java.time.LocalDate

@Composable
fun DesktopUnavailabilityScreen(c: StoreController) {
    var showDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val today = remember { LocalDate.now() }

    val allUnavailabilities = c.data.brothers.flatMap { b ->
        b.unavailabilities.map { period -> Pair(b, period) }
    }.sortedByDescending { AssignmentGenerator.parseDate(it.second.startDate) }

    val filtered = allUnavailabilities.filter { (b, _) ->
        searchQuery.isBlank() || b.name.contains(searchQuery, ignoreCase = true)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Cabeçalho da Tela
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Controle de Férias e Ausências", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Irmãos registrados nesta lista não serão escalados automaticamente pelo gerador durante o período informado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Registrar Ausência")
            }
        }

        // Filtro de Busca
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Buscar por nome do irmão") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = if (searchQuery.isNotBlank()) {
                { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null) } }
            } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (filtered.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    Modifier.padding(32.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(48.dp), tint = Color(0xFF2E7D32))
                    Text("Nenhum irmão ausente no momento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Todos os irmãos ativos estão disponíveis para receber designações nas reuniões.", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { "${it.first.id}_${it.second.id}" }) { (brother, period) ->
                    val startDate = AssignmentGenerator.parseDate(period.startDate)
                    val endDate = AssignmentGenerator.parseDate(period.endDate)
                    val isCurrentlyAbsent = !today.isBefore(startDate) && !today.isAfter(endDate)
                    val isPast = today.isAfter(endDate)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isCurrentlyAbsent -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                                isPast -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Row(
                            Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(brother.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = when {
                                            isCurrentlyAbsent -> Color(0xFFFFEBEE)
                                            isPast -> Color(0xFFECEFF1)
                                            else -> Color(0xFFE8F5E9)
                                        }
                                    ) {
                                        Text(
                                            text = when {
                                                isCurrentlyAbsent -> "Ausente agora"
                                                isPast -> "Passada"
                                                else -> "Programada"
                                            },
                                            color = when {
                                                isCurrentlyAbsent -> Color(0xFFC62828)
                                                isPast -> Color(0xFF546E7A)
                                                else -> Color(0xFF2E7D32)
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        if (period.startDate == period.endDate) "Dia ${period.startDate}" else "De ${period.startDate} até ${period.endDate}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (period.reason.isNotBlank()) {
                                        Text("•", color = MaterialTheme.colorScheme.outline)
                                        Text(period.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            IconButton(onClick = { c.removeUnavailability(brother.id, period.id) }) {
                                Icon(Icons.Default.Delete, "Remover ausência", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        DesktopAddUnavailabilityDialog(
            brothers = c.data.brothers.filter { it.active },
            onDismiss = { showDialog = false },
            onAdd = { bId, start, end, reason ->
                c.addUnavailability(bId, start, end, reason)
                showDialog = false
            }
        )
    }
}

@Composable
private fun DesktopAddUnavailabilityDialog(
    brothers: List<Brother>,
    onDismiss: () -> Unit,
    onAdd: (Long, String, String, String) -> Unit
) {
    var selectedBrotherId by remember { mutableStateOf(brothers.firstOrNull()?.id) }
    var startDate by remember { mutableStateOf(LocalDate.now().format(AssignmentGenerator.DATE_FORMATTER)) }
    var endDate by remember { mutableStateOf(LocalDate.now().plusWeeks(1).format(AssignmentGenerator.DATE_FORMATTER)) }
    var reason by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar Ausência / Férias") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.width(420.dp)) {
                Text("Selecione o Irmão", style = MaterialTheme.typography.labelMedium)
                var expanded by remember { mutableStateOf(false) }
                val currentBrother = brothers.firstOrNull { it.id == selectedBrotherId }

                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(currentBrother?.name ?: "Selecione o irmão")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        brothers.forEach { b ->
                            DropdownMenuItem(text = { Text(b.name) }, onClick = { selectedBrotherId = b.id; expanded = false })
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("Data Inicial (dd/MM/yyyy)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it },
                        label = { Text("Data Final (dd/MM/yyyy)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motivo da Ausência (ex: Viagem, Férias, Saúde)") },
                    modifier = Modifier.fillMaxWidth()
                )

                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val bId = selectedBrotherId ?: return@Button
                    val sDate = AssignmentGenerator.parseDate(startDate)
                    val eDate = AssignmentGenerator.parseDate(endDate)
                    if (sDate == LocalDate.MIN || eDate == LocalDate.MIN) {
                        errorMsg = "Data inválida. Use o formato dd/MM/yyyy."
                        return@Button
                    }
                    if (eDate.isBefore(sDate)) {
                        errorMsg = "A data final não pode ser anterior à data inicial."
                        return@Button
                    }
                    onAdd(bId, startDate.trim(), endDate.trim(), reason.trim())
                },
                enabled = selectedBrotherId != null
            ) { Text("Confirmar e Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
