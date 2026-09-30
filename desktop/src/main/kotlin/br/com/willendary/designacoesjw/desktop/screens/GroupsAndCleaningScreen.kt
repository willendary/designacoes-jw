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
import androidx.compose.ui.unit.sp
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.CleaningSchedule
import br.com.willendary.designacoesjw.data.FieldServiceGroup
import br.com.willendary.designacoesjw.desktop.StoreController
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import br.com.willendary.designacoesjw.util.WhatsAppHelper
import java.awt.Desktop
import java.net.URI
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun GroupsAndCleaningScreen(c: StoreController) {
    var subTab by remember { mutableIntStateOf(0) }
    val subLabels = listOf("🧹 Escala de Limpeza", "👥 Grupos de Campo")

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Grupos de Campo & Limpeza do Salão", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Organize os grupos de serviço e o rodízio da limpeza do Salão do Reino",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TabRow(
                selectedTabIndex = subTab,
                modifier = Modifier.width(360.dp)
            ) {
                subLabels.forEachIndexed { idx, label ->
                    Tab(
                        selected = subTab == idx,
                        onClick = { subTab = idx },
                        text = { Text(label, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
        }

        when (subTab) {
            0 -> CleaningSection(c)
            1 -> GroupsSection(c)
        }
    }
}

@Composable
private fun CleaningSection(c: StoreController) {
    var showDialog by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<CleaningSchedule?>(null) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }

    val schedules = c.data.cleaningSchedules.sortedBy { AssignmentGenerator.parseDate(it.weekDate) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Escala de Limpeza (${schedules.size} registros)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { c.generateCleaningRotation(currentMonth) }) {
                    Icon(Icons.Default.Autorenew, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Gerar Rodízio do Mês")
                }

                OutlinedButton(onClick = { editingSchedule = null; showDialog = true }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Adicionar Data")
                }
            }
        }

        if (schedules.isEmpty()) {
            Card(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.CleaningServices, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Nenhuma escala de limpeza cadastrada", fontWeight = FontWeight.Bold)
                    Text(
                        "Clique em 'Gerar Rodízio do Mês' para distribuir as reuniões entre os grupos cadastrados.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(schedules) { item ->
                    val group = c.data.fieldServiceGroups.firstOrNull { it.id == item.groupId }
                    val overseer = c.data.brothers.firstOrNull { it.id == group?.overseerBrotherId }

                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.completed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("📅 Data: ${item.weekDate}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    if (item.completed) {
                                        Badge(containerColor = Color(0xFF2E7D32)) {
                                            Text("CONCLUÍDA", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Text(
                                    "🧹 Grupo Encarregado: ${group?.name ?: "Nenhum grupo vinculado"}",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                if (overseer != null) {
                                    Text(
                                        "👤 Dirigente: ${overseer.name} ${if (overseer.phone.isNotBlank()) "(${overseer.phone})" else ""}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                if (item.details.isNotBlank()) {
                                    Text("📝 Detalhes: ${item.details}", style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = {
                                        val text = WhatsAppHelper.buildCleaningScheduleMessage(
                                            schedule = item,
                                            group = group,
                                            overseerName = overseer?.name ?: "",
                                            overseerPhone = overseer?.phone ?: ""
                                        )
                                        val url = WhatsAppHelper.buildWebLink(overseer?.phone ?: "", text)
                                        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                ) {
                                    Icon(Icons.Default.Send, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("WhatsApp")
                                }

                                IconButton(onClick = { c.addOrUpdateCleaningSchedule(item.copy(completed = !item.completed)) }) {
                                    Icon(
                                        if (item.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = "Concluir",
                                        tint = if (item.completed) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline
                                    )
                                }

                                IconButton(onClick = { editingSchedule = item; showDialog = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar")
                                }

                                IconButton(onClick = { c.deleteCleaningSchedule(item.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        CleaningScheduleDialog(
            existing = editingSchedule,
            groups = c.data.fieldServiceGroups,
            onDismiss = { showDialog = false; editingSchedule = null },
            onSave = { saved ->
                c.addOrUpdateCleaningSchedule(saved)
                showDialog = false
                editingSchedule = null
            }
        )
    }
}

@Composable
private fun CleaningScheduleDialog(
    existing: CleaningSchedule?,
    groups: List<FieldServiceGroup>,
    onDismiss: () -> Unit,
    onSave: (CleaningSchedule) -> Unit
) {
    var weekDate by remember { mutableStateOf(existing?.weekDate ?: "") }
    var groupId by remember { mutableStateOf(existing?.groupId) }
    var details by remember { mutableStateOf(existing?.details ?: "") }
    var groupDropdownOpen by remember { mutableStateOf(false) }

    val selectedGroup = groups.firstOrNull { it.id == groupId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Adicionar Escala de Limpeza" else "Editar Escala") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(400.dp)) {
                OutlinedTextField(
                    value = weekDate,
                    onValueChange = { weekDate = it },
                    label = { Text("Data da Reunião/Semana (dd/MM/yyyy)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { groupDropdownOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedGroup?.name ?: "Selecionar Grupo de Campo", modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }

                    DropdownMenu(expanded = groupDropdownOpen, onDismissRequest = { groupDropdownOpen = false }) {
                        groups.forEach { g ->
                            DropdownMenuItem(
                                text = { Text(g.name) },
                                onClick = { groupId = g.id; groupDropdownOpen = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("Instruções de limpeza (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (weekDate.isNotBlank()) {
                    onSave(
                        CleaningSchedule(
                            id = existing?.id ?: AssignmentGenerator.nextId(),
                            weekDate = weekDate.trim(),
                            groupId = groupId,
                            details = details.trim(),
                            completed = existing?.completed ?: false
                        )
                    )
                }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun GroupsSection(c: StoreController) {
    var showDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<FieldServiceGroup?>(null) }
    var deletingGroup by remember { mutableStateOf<FieldServiceGroup?>(null) }

    val groups = c.data.fieldServiceGroups.sortedBy { it.number }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Grupos de Serviço de Campo (${groups.size} cadastrados)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Button(onClick = { editingGroup = null; showDialog = true }) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Cadastrar Grupo")
            }
        }

        if (groups.isEmpty()) {
            Card(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Groups, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Nenhum grupo de campo cadastrado", fontWeight = FontWeight.Bold)
                    Text("Cadastre os grupos da congregação para gerenciar dirigentes e a escala de limpeza.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { editingGroup = null; showDialog = true }) {
                        Text("Cadastrar primeiro grupo")
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(groups) { g ->
                    val overseer = c.data.brothers.firstOrNull { it.id == g.overseerBrotherId }
                    val assistant = c.data.brothers.firstOrNull { it.id == g.assistantBrotherId }
                    val publishersInGroup = c.data.brothers.count { it.groupId == g.id }

                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(g.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("👤 Dirigente: ${overseer?.name ?: "Não definido"}", style = MaterialTheme.typography.bodyMedium)
                                if (assistant != null) {
                                    Text("🤝 Ajudante: ${assistant.name}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text("👥 $publishersInGroup publicador(es) vinculado(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                IconButton(onClick = { editingGroup = g; showDialog = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar")
                                }
                                IconButton(onClick = { deletingGroup = g }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        GroupDialog(
            existing = editingGroup,
            brothers = c.data.brothers,
            existingGroups = groups,
            onDismiss = { showDialog = false; editingGroup = null },
            onSave = { saved ->
                c.addOrUpdateGroup(saved)
                showDialog = false
                editingGroup = null
            }
        )
    }

    deletingGroup?.let { g ->
        AlertDialog(
            onDismissRequest = { deletingGroup = null },
            title = { Text("Excluir ${g.name}?") },
            text = { Text("Deseja remover este grupo? Os publicadores vinculados não serão excluídos.") },
            confirmButton = {
                TextButton(onClick = {
                    c.deleteGroup(g.id)
                    deletingGroup = null
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { deletingGroup = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun GroupDialog(
    existing: FieldServiceGroup?,
    brothers: List<Brother>,
    existingGroups: List<FieldServiceGroup>,
    onDismiss: () -> Unit,
    onSave: (FieldServiceGroup) -> Unit
) {
    var numStr by remember { mutableStateOf(existing?.number?.toString() ?: (existingGroups.size + 1).toString()) }
    var name by remember { mutableStateOf(existing?.name ?: "Grupo ${existingGroups.size + 1}") }
    var overseerId by remember { mutableStateOf(existing?.overseerBrotherId) }
    var assistantId by remember { mutableStateOf(existing?.assistantBrotherId) }
    var overseerDropdown by remember { mutableStateOf(false) }
    var assistantDropdown by remember { mutableStateOf(false) }

    val overseer = brothers.firstOrNull { it.id == overseerId }
    val assistant = brothers.firstOrNull { it.id == assistantId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Cadastrar Grupo de Campo" else "Editar Grupo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(400.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = numStr,
                        onValueChange = { numStr = it },
                        label = { Text("Número") },
                        modifier = Modifier.width(90.dp)
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nome do Grupo") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { overseerDropdown = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (overseer != null) "Dirigente: ${overseer.name}" else "Dirigente: Selecionar", modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = overseerDropdown, onDismissRequest = { overseerDropdown = false }) {
                        DropdownMenuItem(text = { Text("Nenhum") }, onClick = { overseerId = null; overseerDropdown = false })
                        brothers.filter { it.active }.forEach { b ->
                            DropdownMenuItem(text = { Text(b.name) }, onClick = { overseerId = b.id; overseerDropdown = false })
                        }
                    }
                }

                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { assistantDropdown = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (assistant != null) "Ajudante: ${assistant.name}" else "Ajudante: Selecionar", modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = assistantDropdown, onDismissRequest = { assistantDropdown = false }) {
                        DropdownMenuItem(text = { Text("Nenhum") }, onClick = { assistantId = null; assistantDropdown = false })
                        brothers.filter { it.active }.forEach { b ->
                            DropdownMenuItem(text = { Text(b.name) }, onClick = { assistantId = b.id; assistantDropdown = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val n = numStr.toIntOrNull() ?: 1
                onSave(
                    FieldServiceGroup(
                        id = existing?.id ?: AssignmentGenerator.nextId(),
                        number = n,
                        name = name.trim().ifBlank { "Grupo $n" },
                        overseerBrotherId = overseerId,
                        assistantBrotherId = assistantId
                    )
                )
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
