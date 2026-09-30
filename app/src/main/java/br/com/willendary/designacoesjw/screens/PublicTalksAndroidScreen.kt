package br.com.willendary.designacoesjw.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.willendary.designacoesjw.AppViewModel
import br.com.willendary.designacoesjw.data.PublicTalk
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import br.com.willendary.designacoesjw.util.WhatsAppHelper
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicTalksAndroidScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var editingTalk by remember { mutableStateOf<PublicTalk?>(null) }
    var deletingTalk by remember { mutableStateOf<PublicTalk?>(null) }
    var filterConfirmedOnly by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val allTalks = vm.publicTalks.value.sortedByDescending { AssignmentGenerator.parseDate(it.date) }
    val talks = allTalks.filter { talk ->
        (!filterConfirmedOnly || talk.confirmed) &&
            (searchQuery.isBlank() ||
                talk.speakerName.contains(searchQuery, ignoreCase = true) ||
                talk.themeTitle.contains(searchQuery, ignoreCase = true) ||
                talk.speakerCongregation.contains(searchQuery, ignoreCase = true))
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editingTalk = null; showDialog = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Novo Discurso") }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Buscar orador, tema ou congregação") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = if (searchQuery.isNotBlank()) {
                        { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null) } }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${talks.size} discurso(s) agendado(s)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    FilterChip(
                        selected = filterConfirmedOnly,
                        onClick = { filterConfirmedOnly = !filterConfirmedOnly },
                        label = { Text("Confirmados") },
                        leadingIcon = if (filterConfirmedOnly) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            if (talks.isEmpty()) {
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
                            Icon(Icons.Default.RecordVoiceOver, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
                            Text("Nenhum discurso público encontrado", fontWeight = FontWeight.SemiBold)
                            Text("Toque no botão '+' abaixo para agendar um orador visitante.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            items(talks, key = { it.id }) { talk ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (talk.confirmed) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                talk.date.ifBlank { "Data a definir" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (talk.confirmed) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                            ) {
                                Text(
                                    text = if (talk.confirmed) "Confirmado" else "Pendente",
                                    color = if (talk.confirmed) Color(0xFF2E7D32) else Color(0xFFE65100),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Tema
                        Column {
                            val themeTitle = if (talk.themeNumber != null && talk.themeNumber > 0) {
                                "Nº ${talk.themeNumber} — ${talk.themeTitle.ifBlank { "Sem tema informado" }}"
                            } else {
                                talk.themeTitle.ifBlank { "Tema não cadastrado" }
                            }
                            Text(
                                themeTitle,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Orador e Congregação
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                            Text(
                                talk.speakerName.ifBlank { "Orador não informado" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (talk.speakerCongregation.isNotBlank()) {
                                Text("•", color = MaterialTheme.colorScheme.outline)
                                Text(talk.speakerCongregation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }

                        // Hospitalidade
                        if (talk.hospitalityBrotherId != null) {
                            val host = vm.brothers.value.firstOrNull { it.id == talk.hospitalityBrotherId }
                            if (host != null) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Restaurant, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text("Hospitalidade / Almoço: ${host.name}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        // Botões de Ação
                        Divider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // WhatsApp
                            if (talk.speakerPhone.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        val msg = WhatsAppHelper.buildPublicTalkSpeakerMessage(talk)
                                        val url = WhatsAppHelper.buildUniversalLink(talk.speakerPhone, msg)
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp), tint = Color(0xFF25D366))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Avisar WhatsApp", color = Color(0xFF25D366), fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { editingTalk = talk; showDialog = true }) {
                                    Icon(Icons.Default.Edit, "Editar", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { deletingTalk = talk }) {
                                    Icon(Icons.Default.Delete, "Excluir", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        PublicTalkEditDialog(
            talk = editingTalk,
            brothers = vm.brothers.value,
            onDismiss = { showDialog = false; editingTalk = null },
            onSave = { updated ->
                vm.addOrUpdatePublicTalk(updated)
                showDialog = false
                editingTalk = null
            }
        )
    }

    deletingTalk?.let { talk ->
        AlertDialog(
            onDismissRequest = { deletingTalk = null },
            title = { Text("Excluir discurso") },
            text = { Text("Deseja remover o discurso de ${talk.speakerName} do dia ${talk.date}?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePublicTalk(talk.id)
                    deletingTalk = null
                }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingTalk = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun PublicTalkEditDialog(
    talk: PublicTalk?,
    brothers: List<br.com.willendary.designacoesjw.data.Brother>,
    onDismiss: () -> Unit,
    onSave: (PublicTalk) -> Unit
) {
    var date by remember { mutableStateOf(talk?.date ?: LocalDate.now().format(AssignmentGenerator.DATE_FORMATTER)) }
    var themeNum by remember { mutableStateOf(talk?.themeNumber?.toString() ?: "") }
    var themeTitle by remember { mutableStateOf(talk?.themeTitle ?: "") }
    var speakerName by remember { mutableStateOf(talk?.speakerName ?: "") }
    var speakerCong by remember { mutableStateOf(talk?.speakerCongregation ?: "") }
    var speakerPhone by remember { mutableStateOf(talk?.speakerPhone ?: "") }
    var hospitalityBrotherId by remember { mutableStateOf(talk?.hospitalityBrotherId) }
    var confirmed by remember { mutableStateOf(talk?.confirmed ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (talk == null) "Novo Discurso Público" else "Editar Discurso") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                item {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Data (dd/MM/yyyy)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = themeNum,
                            onValueChange = { themeNum = it },
                            label = { Text("Nº Tema") },
                            modifier = Modifier.weight(0.35f)
                        )
                        OutlinedTextField(
                            value = themeTitle,
                            onValueChange = { themeTitle = it },
                            label = { Text("Título do Discurso") },
                            modifier = Modifier.weight(0.65f)
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = speakerName,
                        onValueChange = { speakerName = it },
                        label = { Text("Nome do Orador") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = speakerCong,
                        onValueChange = { speakerCong = it },
                        label = { Text("Congregação de Origem") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = speakerPhone,
                        onValueChange = { speakerPhone = it },
                        label = { Text("Telefone / WhatsApp do Orador") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text("Hospitalidade / Almoço", style = MaterialTheme.typography.labelMedium)
                    var hostExpanded by remember { mutableStateOf(false) }
                    val currentHost = brothers.firstOrNull { it.id == hospitalityBrotherId }
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { hostExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(currentHost?.name ?: "Ninguém definido")
                        }
                        DropdownMenu(expanded = hostExpanded, onDismissRequest = { hostExpanded = false }) {
                            DropdownMenuItem(text = { Text("Nenhum") }, onClick = { hospitalityBrotherId = null; hostExpanded = false })
                            brothers.filter { it.active }.forEach { b ->
                                DropdownMenuItem(text = { Text(b.name) }, onClick = { hospitalityBrotherId = b.id; hostExpanded = false })
                            }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Discurso confirmado pelo orador")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = (talk ?: PublicTalk(id = 0L)).copy(
                        date = date.trim(),
                        themeNumber = themeNum.toIntOrNull(),
                        themeTitle = themeTitle.trim(),
                        speakerName = speakerName.trim(),
                        speakerCongregation = speakerCong.trim(),
                        speakerPhone = speakerPhone.trim(),
                        hospitalityBrotherId = hospitalityBrotherId,
                        confirmed = confirmed
                    )
                    onSave(updated)
                },
                enabled = speakerName.isNotBlank() && date.isNotBlank()
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
