package br.com.willendary.designacoesjw.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.willendary.designacoesjw.AppViewModel
import br.com.willendary.designacoesjw.data.CleaningSchedule
import br.com.willendary.designacoesjw.data.FieldServiceGroup
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import br.com.willendary.designacoesjw.ui.JwCard
import br.com.willendary.designacoesjw.ui.JwCardRail
import br.com.willendary.designacoesjw.ui.JwCardTitle
import br.com.willendary.designacoesjw.ui.JwTheme
import br.com.willendary.designacoesjw.ui.corDeContorno
import br.com.willendary.designacoesjw.util.WhatsAppHelper
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsAndCleaningAndroidScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var subTab by remember { mutableIntStateOf(0) } // 0 = Limpeza, 1 = Grupos

    var showCleaningDialog by remember { mutableStateOf(false) }
    var editingCleaning by remember { mutableStateOf<CleaningSchedule?>(null) }
    var deletingCleaning by remember { mutableStateOf<CleaningSchedule?>(null) }

    var showGroupDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<FieldServiceGroup?>(null) }
    var deletingGroup by remember { mutableStateOf<FieldServiceGroup?>(null) }

    val currentMonth = remember { YearMonth.now() }
    val groups = vm.fieldServiceGroups.value.sortedBy { it.number }
    val schedules = vm.cleaningSchedules.value.sortedBy { AssignmentGenerator.parseDate(it.weekDate) }

    Scaffold(
        floatingActionButton = {
            if (subTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { editingCleaning = null; showCleaningDialog = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Nova Semana") }
                )
            } else {
                ExtendedFloatingActionButton(
                    onClick = { editingGroup = null; showGroupDialog = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Novo Grupo") }
                )
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = subTab) {
                Tab(
                    selected = subTab == 0,
                    onClick = { subTab = 0 },
                    text = { Text("Escala de Limpeza") },
                    icon = { Icon(Icons.Default.CleaningServices, null) }
                )
                Tab(
                    selected = subTab == 1,
                    onClick = { subTab = 1 },
                    text = { Text("Grupos de Campo") },
                    icon = { Icon(Icons.Default.Groups, null) }
                )
            }

            if (subTab == 0) {
                // ABA LIMPEZA
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        // Destaque: é o atalho que gera o mês inteiro, não só uma semana.
                        JwCard(destaque = true) {
                            JwCardTitle("Rodízio Automático do Salão")
                            Text(
                                "Gera as semanas para os sábados do mês atual, alternando os grupos de serviço de campo de forma sequencial.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                onClick = { vm.generateMonthCleaning(currentMonth) },
                                enabled = groups.isNotEmpty()
                            ) {
                                Icon(Icons.Default.AutoMode, null, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(JwTheme.spacing.sm))
                                Text("Gerar Escala de ${currentMonth.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))}")
                            }
                        }
                    }

                    if (schedules.isEmpty()) {
                        item {
                            JwCard {
                                Icon(Icons.Default.CleaningServices, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
                                Text("Nenhuma escala de limpeza cadastrada", fontWeight = FontWeight.SemiBold)
                                Text("Use o botão acima para gerar o rodízio do mês.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }

                    items(schedules, key = { it.id }) { item ->
                        val group = groups.firstOrNull { it.id == item.groupId }
                        val overseer = vm.brothers.value.firstOrNull { it.id == group?.overseerBrotherId }

                        JwCard(
                            actions = {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (overseer != null && overseer.phone.isNotBlank()) {
                                        TextButton(onClick = {
                                            val msg = "Olá, irmão ${overseer.name}! Lembramos que o seu Grupo ${group?.number ?: ""} é o responsável pela limpeza do Salão do Reino na semana de ${item.weekDate}. Obrigado pelo apoio amoroso!"
                                            val url = WhatsAppHelper.buildUniversalLink(overseer.phone, msg)
                                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                        }) {
                                            Icon(Icons.Default.Share, null, modifier = Modifier.size(22.dp), tint = JwTheme.colors.whatsapp)
                                            Spacer(Modifier.width(JwTheme.spacing.sm))
                                            Text("Avisar Dirigente", color = JwTheme.colors.whatsapp, fontWeight = FontWeight.SemiBold)
                                        }
                                    } else {
                                        Spacer(Modifier.width(1.dp))
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { editingCleaning = item; showCleaningDialog = true }) {
                                            Icon(Icons.Default.Edit, "Editar", modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                                        }
                                        // Separar o destrutivo do editar: a 48dp
                                        // de distância um toque errado apaga a semana.
                                        VerticalDivider(Modifier.height(28.dp), thickness = 1.dp, color = corDeContorno())
                                        IconButton(onClick = { deletingCleaning = item }) {
                                            Icon(Icons.Default.Delete, "Excluir", modifier = Modifier.size(22.dp), tint = JwTheme.colors.perigo)
                                        }
                                    }
                                }
                            }
                        ) {
                            // Semana concluída: trilho verde. Antes era fundo em
                            // alpha, que some na impressão.
                            if (item.completed) JwCardRail(JwTheme.colors.sucesso)

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Semana de ${item.weekDate}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(onClick = {
                                    vm.addOrUpdateCleaningSchedule(item.copy(completed = !item.completed))
                                }) {
                                    Icon(
                                        if (item.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = "Concluído",
                                        modifier = Modifier.size(22.dp),
                                        tint = if (item.completed) JwTheme.colors.sucesso else MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            Text(
                                text = if (group != null) "Grupo ${group.number} — ${group.name}" else "Nenhum grupo atribuído",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )

                            if (overseer != null) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm)) {
                                    Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                                    Text("Superintendente: ${overseer.name}", style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            if (item.details.isNotBlank()) {
                                Text(item.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                // ABA GRUPOS
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (groups.isEmpty()) {
                        item {
                            JwCard {
                                Icon(Icons.Default.Groups, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
                                Text("Nenhum grupo de campo cadastrado", fontWeight = FontWeight.SemiBold)
                                Text("Toque em 'Novo Grupo' para cadastrar os grupos da congregação.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }

                    items(groups, key = { it.id }) { group ->
                        val overseer = vm.brothers.value.firstOrNull { it.id == group.overseerBrotherId }
                        val assistant = vm.brothers.value.firstOrNull { it.id == group.assistantBrotherId }
                        val membersCount = vm.brothers.value.count { it.groupId == group.id }

                        JwCard(
                            actions = {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { editingGroup = group; showGroupDialog = true }) {
                                        Icon(Icons.Default.Edit, "Editar", modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                    VerticalDivider(Modifier.height(28.dp), thickness = 1.dp, color = corDeContorno())
                                    IconButton(onClick = { deletingGroup = group }) {
                                        Icon(Icons.Default.Delete, "Excluir", modifier = Modifier.size(22.dp), tint = JwTheme.colors.perigo)
                                    }
                                }
                            }
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Grupo ${group.number}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text(
                                        if (membersCount == 1) "1 membro" else "$membersCount membros",
                                        modifier = Modifier.padding(horizontal = JwTheme.spacing.sm, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            Text(group.name.ifBlank { "Sem nome específico" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)

                            overseer?.let {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm)) {
                                    Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                                    Text("Superintendente: ${it.name}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            assistant?.let {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm)) {
                                    Icon(Icons.Default.PersonOutline, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                                    Text("Ajudante: ${it.name}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Diálogos de Limpeza
    if (showCleaningDialog) {
        CleaningEditDialog(
            item = editingCleaning,
            groups = groups,
            onDismiss = { showCleaningDialog = false; editingCleaning = null },
            onSave = { updated ->
                vm.addOrUpdateCleaningSchedule(updated)
                showCleaningDialog = false
                editingCleaning = null
            }
        )
    }

    deletingCleaning?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingCleaning = null },
            title = { Text("Excluir semana de limpeza") },
            text = { Text("Deseja remover a escala da semana de ${item.weekDate}?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteCleaningSchedule(item.id)
                    deletingCleaning = null
                }) { Text("Excluir", color = JwTheme.colors.perigo) }
            },
            dismissButton = {
                TextButton(onClick = { deletingCleaning = null }) { Text("Cancelar") }
            }
        )
    }

    // Diálogos de Grupos
    if (showGroupDialog) {
        GroupEditDialog(
            group = editingGroup,
            brothers = vm.brothers.value,
            onDismiss = { showGroupDialog = false; editingGroup = null },
            onSave = { updated ->
                vm.addOrUpdateGroup(updated)
                showGroupDialog = false
                editingGroup = null
            }
        )
    }

    deletingGroup?.let { grp ->
        AlertDialog(
            onDismissRequest = { deletingGroup = null },
            title = { Text("Excluir grupo") },
            text = { Text("Deseja excluir o Grupo ${grp.number} (${grp.name})?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteGroup(grp.id)
                    deletingGroup = null
                }) { Text("Excluir", color = JwTheme.colors.perigo) }
            },
            dismissButton = {
                TextButton(onClick = { deletingGroup = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun CleaningEditDialog(
    item: CleaningSchedule?,
    groups: List<FieldServiceGroup>,
    onDismiss: () -> Unit,
    onSave: (CleaningSchedule) -> Unit
) {
    var weekDate by remember { mutableStateOf(item?.weekDate ?: LocalDate.now().format(AssignmentGenerator.DATE_FORMATTER)) }
    var groupId by remember { mutableStateOf(item?.groupId ?: groups.firstOrNull()?.id) }
    var details by remember { mutableStateOf(item?.details ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Nova Semana de Limpeza" else "Editar Limpeza") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = weekDate,
                    onValueChange = { weekDate = it },
                    label = { Text("Data da Semana (dd/MM/yyyy)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Grupo Responsável", style = MaterialTheme.typography.labelMedium)
                var expanded by remember { mutableStateOf(false) }
                val selectedGroup = groups.firstOrNull { it.id == groupId }
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedGroup?.let { "Grupo ${it.number} — ${it.name}" } ?: "Selecione o grupo")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        groups.forEach { g ->
                            DropdownMenuItem(
                                text = { Text("Grupo ${g.number} — ${g.name}") },
                                onClick = { groupId = g.id; expanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("Detalhes adicionais (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = (item ?: CleaningSchedule(id = 0L, weekDate = "")).copy(
                        weekDate = weekDate.trim(),
                        groupId = groupId,
                        details = details.trim()
                    )
                    onSave(updated)
                },
                enabled = weekDate.isNotBlank() && groupId != null
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun GroupEditDialog(
    group: FieldServiceGroup?,
    brothers: List<br.com.willendary.designacoesjw.data.Brother>,
    onDismiss: () -> Unit,
    onSave: (FieldServiceGroup) -> Unit
) {
    var numberStr by remember { mutableStateOf(group?.number?.toString() ?: "1") }
    var name by remember { mutableStateOf(group?.name ?: "") }
    var overseerId by remember { mutableStateOf(group?.overseerBrotherId) }
    var assistantId by remember { mutableStateOf(group?.assistantBrotherId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (group == null) "Novo Grupo de Campo" else "Editar Grupo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = numberStr,
                    onValueChange = { numberStr = it },
                    label = { Text("Número do Grupo") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do Grupo / Ponto de Saída") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Superintendente do Grupo", style = MaterialTheme.typography.labelMedium)
                var expOverseer by remember { mutableStateOf(false) }
                val currentOverseer = brothers.firstOrNull { it.id == overseerId }
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { expOverseer = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(currentOverseer?.name ?: "Nenhum selecionado")
                    }
                    DropdownMenu(expanded = expOverseer, onDismissRequest = { expOverseer = false }) {
                        DropdownMenuItem(text = { Text("Nenhum") }, onClick = { overseerId = null; expOverseer = false })
                        brothers.filter { it.active }.forEach { b ->
                            DropdownMenuItem(text = { Text(b.name) }, onClick = { overseerId = b.id; expOverseer = false })
                        }
                    }
                }

                Text("Ajudante do Grupo", style = MaterialTheme.typography.labelMedium)
                var expAssistant by remember { mutableStateOf(false) }
                val currentAssistant = brothers.firstOrNull { it.id == assistantId }
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { expAssistant = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(currentAssistant?.name ?: "Nenhum selecionado")
                    }
                    DropdownMenu(expanded = expAssistant, onDismissRequest = { expAssistant = false }) {
                        DropdownMenuItem(text = { Text("Nenhum") }, onClick = { assistantId = null; expAssistant = false })
                        brothers.filter { it.active }.forEach { b ->
                            DropdownMenuItem(text = { Text(b.name) }, onClick = { assistantId = b.id; expAssistant = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val num = numberStr.toIntOrNull() ?: 1
                    val updated = (group ?: FieldServiceGroup(id = 0L, number = num, name = "")).copy(
                        number = num,
                        name = name.trim(),
                        overseerBrotherId = overseerId,
                        assistantBrotherId = assistantId
                    )
                    onSave(updated)
                },
                enabled = numberStr.isNotBlank()
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
