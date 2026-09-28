package br.com.willendary.designacoesjw

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.Privilege
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.TextStyle
import kotlinx.coroutines.launch

private val weekdays = listOf(
    DayOfWeek.MONDAY to "Segunda-feira",
    DayOfWeek.TUESDAY to "Terça-feira",
    DayOfWeek.WEDNESDAY to "Quarta-feira",
    DayOfWeek.THURSDAY to "Quinta-feira",
    DayOfWeek.FRIDAY to "Sexta-feira",
    DayOfWeek.SATURDAY to "Sábado",
    DayOfWeek.SUNDAY to "Domingo"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: AppViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var availableUpdate by remember { mutableStateOf<AppUpdate?>(null) }
    var checkingUpdate by remember { mutableStateOf(true) }
    var updating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        availableUpdate = UpdateManager.check(context)
        checkingUpdate = false
    }

    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Designações JW") }) },
        bottomBar = {
            NavigationBar {
                listOf("Início", "Irmãos", "Privilégios", "Histórico").forEachIndexed { i, label ->
                    NavigationBarItem(tab == i, { tab = i }, icon = { Text(listOf("⌂", "●", "✓", "▣")[i]) }, label = { Text(label) })
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> HomeScreen(vm)
                1 -> BrothersScreen(vm)
                2 -> PrivilegesScreen(vm)
                else -> HistoryScreen(vm)
            }
        }
    }
    availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { if (!updating) availableUpdate = null },
            title = { Text("Atualização disponível") },
            text = {
                Text(if (updating) "Baixando a versão " + update.versionName + "..."
                     else "A versão " + update.versionName + " do Designações JW está disponível.")
            },
            confirmButton = {
                TextButton(enabled = !updating, onClick = {
                    updating = true
                    scope.launch {
                        val ok = UpdateManager.downloadAndInstall(context, update)
                        updating = false
                        if (!ok) availableUpdate = null
                    }
                }) { Text(if (updating) "Baixando..." else "Atualizar") }
            },
            dismissButton = {
                if (!updating) TextButton({ availableUpdate = null }) { Text("Agora não") }
            }
        )
    }
}

@Composable
private fun HomeScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedMeetingId by remember { mutableStateOf<Long?>(null) }
    var showRegenerateConfirm by remember { mutableStateOf(false) }

    val monthMeetings = vm.meetings.value.filter {
        runCatching {
            val p = it.date.split("/")
            p.size == 3 && p[1].toInt() == month.monthValue && p[2].toInt() == month.year
        }.getOrDefault(false)
    }.sortedBy { it.date }

    val monthName = month.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
        .replaceFirstChar { it.uppercase() }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp)
    ) {
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Planejamento mensal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Configure os dias de reunião e gere todas as designações do mês.")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        FilledTonalButton({ month = month.minusMonths(1); selectedMeetingId = null }) { Text("‹") }
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                        ) {
                            Text(monthName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(month.year.toString(), style = MaterialTheme.typography.labelMedium)
                        }
                        FilledTonalButton({ month = month.plusMonths(1); selectedMeetingId = null }) { Text("›") }
                    }
                }
            }
        }

        item {
            var firstExpanded by remember { mutableStateOf(false) }
            var secondExpanded by remember { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Dias de reunião", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Escolha os dois dias da semana em que há reunião.", style = MaterialTheme.typography.bodySmall)

                    Text("Primeiro dia", style = MaterialTheme.typography.labelLarge)
                    Box {
                        OutlinedButton({ firstExpanded = true }, Modifier.fillMaxWidth()) {
                            Text(dayLabel(vm.schedule.value.firstDay))
                        }
                        DropdownMenu(expanded = firstExpanded, onDismissRequest = { firstExpanded = false }) {
                            weekdays.forEach { (day, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        if (day.value != vm.schedule.value.secondDay) {
                                            vm.setMeetingDays(day.value, vm.schedule.value.secondDay)
                                        }
                                        firstExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Text("Segundo dia", style = MaterialTheme.typography.labelLarge)
                    Box {
                        OutlinedButton({ secondExpanded = true }, Modifier.fillMaxWidth()) {
                            Text(dayLabel(vm.schedule.value.secondDay))
                        }
                        DropdownMenu(expanded = secondExpanded, onDismissRequest = { secondExpanded = false }) {
                            weekdays.forEach { (day, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        if (day.value != vm.schedule.value.firstDay) {
                                            vm.setMeetingDays(vm.schedule.value.firstDay, day.value)
                                        }
                                        secondExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        "Reuniões: ${dayLabel(vm.schedule.value.firstDay)} e ${dayLabel(vm.schedule.value.secondDay)}",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard("Irmãos", vm.brothers.value.count { it.active }.toString(), Modifier.weight(1f))
                SummaryCard("Privilégios", vm.privileges.value.count { it.active }.toString(), Modifier.weight(1f))
                SummaryCard("Reuniões", monthMeetings.size.toString(), Modifier.weight(1f))
            }
        }

        item {
            Button(
                onClick = {
                    if (monthMeetings.isNotEmpty()) showRegenerateConfirm = true
                    else selectedMeetingId = vm.generateMonth(month).firstOrNull()?.id
                },
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Text(if (monthMeetings.isEmpty()) "Gerar designações do mês" else "Regenerar designações do mês")
            }
        }

        if (monthMeetings.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Relatório do mês", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Gere uma tabela com as datas nas linhas e os privilégios nas colunas.", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { ReportGenerator.sharePdf(context, month, monthMeetings, vm.brothers.value, vm.privileges.value) },
                            modifier = Modifier.weight(1f)
                        ) { Text("PDF") }
                        OutlinedButton(
                            onClick = { ReportGenerator.shareDocx(context, month, monthMeetings, vm.brothers.value, vm.privileges.value) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Word") }
                    }
                }
            }
            item {
                Text("Reuniões de ${monthName}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            items(monthMeetings, key = { it.id }) { meeting ->
                MeetingSummaryCard(vm, meeting, meeting.id == selectedMeetingId) {
                    selectedMeetingId = meeting.id
                }
            }
            monthMeetings.find { it.id == selectedMeetingId }?.let { selected ->
                item { MeetingResult(vm, selected, context) {} }
            }
        } else {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Pronto para começar?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Depois de cadastrar irmãos e privilégios, gere o mês inteiro com um toque.")
                    }
                }
            }
        }
    }

    if (showRegenerateConfirm) {
        AlertDialog(
            onDismissRequest = { showRegenerateConfirm = false },
            title = { Text("Regenerar o mês?") },
            text = { Text("As ${monthMeetings.size} reuniões de ${monthName} serão substituídas.") },
            confirmButton = {
                TextButton({
                    vm.deleteMonth(month)
                    selectedMeetingId = vm.generateMonth(month).firstOrNull()?.id
                    showRegenerateConfirm = false
                }) { Text("Regenerar") }
            },
            dismissButton = { TextButton({ showRegenerateConfirm = false }) { Text("Cancelar") } }
        )
    }
}

private fun dayLabel(value: Int): String =
    weekdays.firstOrNull { it.first.value == value }?.second ?: "—"

@Composable
private fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun MeetingSummaryCard(
    vm: AppViewModel,
    meeting: Meeting,
    selected: Boolean,
    onClick: () -> Unit
) {
    val missing = vm.missingAssignments(meeting)
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(meeting.date, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${meeting.assignments.size} designação(ões)")
                }
                TextButton(onClick) { Text(if (selected) "Selecionada" else "Ver") }
            }
            if (missing.isNotEmpty()) {
                Text(
                    "⚠ ${missing.size} privilégio(s) sem candidatos suficientes",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun MeetingResult(
    vm: AppViewModel,
    meeting: Meeting,
    context: android.content.Context,
    onReplace: (Triple<Long, Long, Long>) -> Unit
) {
    val missing = vm.missingAssignments(meeting)
    Text("Designações", style = MaterialTheme.typography.titleLarge)
    if (missing.isNotEmpty()) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Atenção: faltaram candidatos para:")
                missing.forEach { Text("• ${it.name} (${it.quantity} necessário(s))") }
            }
        }
    }
    meeting.assignments.forEach { assignment ->
        val brother = vm.brothers.value.find { it.id == assignment.brotherId }
        val privilege = vm.privileges.value.find { it.id == assignment.privilegeId }
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(privilege?.name ?: "Privilégio", style = MaterialTheme.typography.titleMedium)
                    Text(brother?.name ?: "Irmão")
                }
                TextButton({ onReplace(Triple(meeting.id, assignment.privilegeId, assignment.brotherId)) }) { Text("Trocar") }
            }
        }
    }
    Button({
        val msg = buildString {
            append("Designações — ${meeting.type} em ${meeting.date}\n\n")
            meeting.assignments.forEach { a ->
                val b = vm.brothers.value.find { it.id == a.brotherId }
                val p = vm.privileges.value.find { it.id == a.privilegeId }
                append("${p?.name}: ${b?.name}\n")
            }
            if (missing.isNotEmpty()) append("\n⚠ Faltaram candidatos para: ${missing.joinToString { it.name }}")
        }
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=${Uri.encode(msg)}")))
    }, Modifier.fillMaxWidth()) { Text("Compartilhar no WhatsApp") }

    meeting.assignments.mapNotNull { a -> vm.brothers.value.find { it.id == a.brotherId } }.distinctBy { it.id }.forEach { brother ->
        if (brother.phone.isNotBlank()) {
            OutlinedButton({
                val phone = brother.phone.filter(Char::isDigit)
                val msg = "Olá, ${brother.name}! Você foi designado para a reunião de ${meeting.date}. Por favor, confirme o recebimento da designação."
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${phone}?text=${Uri.encode(msg)}")))
            }, Modifier.fillMaxWidth()) { Text("Enviar para ${brother.name}") }
        }
    }
}

@Composable
private fun BrothersScreen(vm: AppViewModel) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Brother?>(null) }
    Column(Modifier.padding(20.dp)) {
        Text("Irmãos", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text("WhatsApp (somente números)") }, modifier = Modifier.fillMaxWidth())
        Button({ vm.addBrother(name, phone); name = ""; phone = "" }) { Text("Adicionar irmão") }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(vm.brothers.value, key = { it.id }) { brother ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(brother.name, style = MaterialTheme.typography.titleMedium)
                            Text(if (brother.phone.isBlank()) "Sem WhatsApp" else brother.phone)
                            Text(if (brother.active) "Ativo" else "Inativo")
                        }
                        Column {
                            TextButton({ editing = brother }) { Text("Editar") }
                            TextButton({ vm.setBrotherActive(brother.id, !brother.active) }) {
                                Text(if (brother.active) "Desativar" else "Ativar")
                            }
                        }
                    }
                }
            }
        }
    }
    editing?.let { brother ->
        EditBrotherDialog(brother, { n, p -> vm.updateBrother(brother.id, n, p); editing = null }, { editing = null })
    }
}

@Composable
private fun PrivilegesScreen(vm: AppViewModel) {
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var editing by remember { mutableStateOf<Privilege?>(null) }
    Column(Modifier.padding(20.dp)) {
        Text("Privilégios", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit) }, label = { Text("Quantidade necessária") }, modifier = Modifier.fillMaxWidth())
        Button({ vm.addPrivilege(name, quantity.toIntOrNull() ?: 1); name = ""; quantity = "1" }) { Text("Adicionar privilégio") }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.privileges.value, key = { it.id }) { privilege ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(privilege.name, style = MaterialTheme.typography.titleMedium)
                                Text("Necessários: ${privilege.quantity} • ${if (privilege.active) "Ativo" else "Inativo"}")
                            }
                            Column {
                                TextButton({ editing = privilege }) { Text("Editar") }
                                TextButton({ vm.setPrivilegeActive(privilege.id, !privilege.active) }) {
                                    Text(if (privilege.active) "Desativar" else "Ativar")
                                }
                            }
                        }
                        Text("Irmãos autorizados", style = MaterialTheme.typography.labelLarge)
                        vm.brothers.value.forEach { brother ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(brother.name)
                                Checkbox(privilege.id in brother.privileges, { vm.togglePrivilege(brother.id, privilege.id) })
                            }
                        }
                    }
                }
            }
        }
    }
    editing?.let { privilege ->
        EditPrivilegeDialog(privilege, { n, q -> vm.updatePrivilege(privilege.id, n, q); editing = null }, { editing = null })
    }
}

@Composable
private fun HistoryScreen(vm: AppViewModel) {
    var confirmDelete by remember { mutableStateOf<Meeting?>(null) }
    LazyColumn(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Histórico", style = MaterialTheme.typography.headlineSmall)
            Text("${vm.meetings.value.size} reunião(ões) registrada(s)")
        }
        items(vm.meetings.value.reversed(), key = { it.id }) { meeting ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("${meeting.type} — ${meeting.date}", style = MaterialTheme.typography.titleMedium)
                    Text("${meeting.assignments.size} designação(ões)")
                    meeting.assignments.forEach { a ->
                        val brother = vm.brothers.value.find { it.id == a.brotherId }
                        val privilege = vm.privileges.value.find { it.id == a.privilegeId }
                        Text("${privilege?.name}: ${brother?.name ?: "Irmão removido"}")
                    }
                    TextButton({ confirmDelete = meeting }) { Text("Excluir registro") }
                }
            }
        }
    }
    confirmDelete?.let { meeting ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Excluir histórico?") },
            text = { Text("A reunião ${meeting.type} de ${meeting.date} será removida do histórico.") },
            confirmButton = { TextButton({ vm.deleteMeeting(meeting.id); confirmDelete = null }) { Text("Excluir") } },
            dismissButton = { TextButton({ confirmDelete = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun BlockBrothersDialog(
    brothers: List<Brother>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Indisponíveis nesta reunião") },
        text = {
            Column {
                if (brothers.isEmpty()) Text("Cadastre irmãos ativos primeiro.")
                brothers.forEach { brother ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(brother.name)
                        Checkbox(brother.id in selected, { onToggle(brother.id) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Concluir") } }
    )
}

@Composable
private fun ReplaceDialog(
    candidates: List<Brother>,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trocar designação") },
        text = {
            Column {
                if (candidates.isEmpty()) Text("Não há outro irmão autorizado e disponível.")
                candidates.forEach { brother ->
                    TextButton({ onSelect(brother.id) }, Modifier.fillMaxWidth()) { Text(brother.name) }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun EditBrotherDialog(brother: Brother, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember(brother.id) { mutableStateOf(brother.name) }
    var phone by remember(brother.id) { mutableStateOf(brother.phone) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar irmão") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") })
                OutlinedTextField(phone, { phone = it }, label = { Text("WhatsApp") })
            }
        },
        confirmButton = { TextButton({ onSave(name, phone) }) { Text("Salvar") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun EditPrivilegeDialog(privilege: Privilege, onSave: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var name by remember(privilege.id) { mutableStateOf(privilege.name) }
    var quantity by remember(privilege.id) { mutableStateOf(privilege.quantity.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar privilégio") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") })
                OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit) }, label = { Text("Quantidade") })
            }
        },
        confirmButton = { TextButton({ onSave(name, quantity.toIntOrNull() ?: 1) }) { Text("Salvar") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } }
    )
}
