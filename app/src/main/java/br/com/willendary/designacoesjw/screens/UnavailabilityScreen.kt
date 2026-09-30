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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.willendary.designacoesjw.AppViewModel
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.UnavailablePeriod
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnavailabilityScreen(vm: AppViewModel) {
    var showDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val today = remember { LocalDate.now() }

    // Lista de pares (Irmão, Período de Ausência)
    val brothers = vm.brothers.value
    val allUnavailabilities = brothers.flatMap { b ->
        b.unavailabilities.map { period -> Pair(b, period) }
    }.sortedByDescending { AssignmentGenerator.parseDate(it.second.startDate) }

    val filteredList = allUnavailabilities.filter { (brother, _) ->
        searchQuery.isBlank() || brother.name.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showDialog = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Registrar Ausência") }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.EventBusy, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Controle de Férias e Ausências", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Irmãos cadastrados aqui NÃO serão escalados automaticamente pelo gerador durante o período informado.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            item {
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
            }

            if (filteredList.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterVertically,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(40.dp), tint = Color(0xFF2E7D32))
                            Text("Nenhuma ausência registrada", fontWeight = FontWeight.SemiBold)
                            Text("Todos os irmãos ativos estão disponíveis para designações.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            items(filteredList, key = { "${it.first.id}_${it.second.id}" }) { (brother, period) ->
                val startDate = AssignmentGenerator.parseDate(period.startDate)
                val endDate = AssignmentGenerator.parseDate(period.endDate)
                val isCurrentlyAbsent = !today.isBefore(startDate) && !today.isAfter(endDate)
                val isPast = today.isAfter(endDate)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                            }

                            if (period.reason.isNotBlank()) {
                                Text("Motivo: ${period.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        IconButton(onClick = { vm.removeUnavailability(brother.id, period.id) }) {
                            Icon(Icons.Default.Delete, "Remover", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AddUnavailabilityDialog(
            brothers = brothers.filter { it.active },
            onDismiss = { showDialog = false },
            onAdd = { brotherId, start, end, reason ->
                vm.addUnavailability(brotherId, start, end, reason)
                showDialog = false
            }
        )
    }
}

@Composable
private fun AddUnavailabilityDialog(
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("Início (dd/MM/yyyy)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it },
                        label = { Text("Fim (dd/MM/yyyy)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motivo (ex: Viagem, Férias, Saúde)") },
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
            ) { Text("Confirmar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
