package br.com.willendary.designacoesjw

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
fun App(
    vm: AppViewModel = viewModel(),
    themeIndex: Int = 0,
    onThemeChange: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var availableUpdate by remember { mutableStateOf<AppUpdate?>(null) }
    var checkingUpdate by remember { mutableStateOf(true) }
    var updating by remember { mutableStateOf(false) }

    suspend fun refreshUpdate() {
        checkingUpdate = true
        availableUpdate = UpdateManager.check(context)
        checkingUpdate = false
    }

    LaunchedEffect(Unit) {
        refreshUpdate()
    }

    var tab by remember { mutableIntStateOf(3) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_logo),
                            contentDescription = "Logo Designações JW",
                            modifier = Modifier.size(34.dp)
                        )
                        Text("Designações JW", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                listOf("Configurações", "Histórico", "Irmãos", "Início", "Privilégios").forEachIndexed { i, label ->
                    NavigationBarItem(tab == i, { tab = i }, icon = {
                        Icon(
                            when (i) {
                                0 -> Icons.Filled.Settings
                                1 -> Icons.Filled.History
                                2 -> Icons.Filled.Groups
                                3 -> Icons.Filled.Home
                                else -> Icons.Filled.Work
                            },
                            contentDescription = label
                        )
                    },
                    label = { Text(label) })
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> SettingsScreen(themeIndex, onThemeChange)
                1 -> HistoryScreen(vm)
                2 -> BrothersScreen(vm)
                3 -> HomeScreen(vm)
                else -> PrivilegesScreen(vm)
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
                    availableUpdate = null
                    scope.launch {
                        val result = UpdateManager.downloadAndInstall(context, update)
                        updating = false
                        when (result) {
                            UpdateInstallResult.STARTED -> Unit
                            UpdateInstallResult.NEED_PERMISSION -> {
                                Toast.makeText(
                                    context,
                                    "Permita a instalação de apps desconhecidos para concluir a atualização. Depois, abra o aplicativo novamente.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            UpdateInstallResult.FAILED -> {
                                availableUpdate = update
                                Toast.makeText(
                                    context,
                                    "Não foi possível iniciar a instalação. Tente novamente.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
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
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Planejamento mensal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Organize reuniões e designações em um só lugar.")
                        }
                    }
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
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                    Text("Reuniões de ${monthName}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
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
                    Text(
                        meeting.type,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
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
private fun MeetingResult(vm: AppViewModel, meeting: Meeting, context: android.content.Context, onReplace: (Triple<Long, Long, Long>) -> Unit) {
    val missing = vm.missingAssignments(meeting)
    var replaceTarget by remember { mutableStateOf<Triple<Long, Long, Long>?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Designações", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (missing.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text("Atenção: faltaram candidatos para:")
                missing.forEach { Text("• " + it.name + " (" + it.quantity + " necessário(s))") }
            } }
        }
        meeting.assignments.forEach { assignment ->
            val brother = vm.brothers.value.find { it.id == assignment.brotherId }
            val privilege = vm.privileges.value.find { it.id == assignment.privilegeId }
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text(privilege?.name ?: "Privilégio", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(brother?.name ?: "Irmão", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    IconButton({ replaceTarget = Triple(meeting.id, assignment.privilegeId, assignment.brotherId) }) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = "Trocar")
                    }
                }
            }
        }
        Button({
            val msg = buildString {
                append("Designações — " + meeting.type + " em " + meeting.date + "\n\n")
                meeting.assignments.forEach { a ->
                    val b = vm.brothers.value.find { it.id == a.brotherId }
                    val p = vm.privileges.value.find { it.id == a.privilegeId }
                    append((p?.name ?: "Privilégio") + ": " + (b?.name ?: "Irmão") + "\n")
                }
                if (missing.isNotEmpty()) append("\n⚠ Faltaram candidatos para: " + missing.joinToString { it.name })
            }
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=" + Uri.encode(msg))))
        }, Modifier.fillMaxWidth()) { Text("Compartilhar no WhatsApp") }
        meeting.assignments.mapNotNull { a -> vm.brothers.value.find { it.id == a.brotherId } }.distinctBy { it.id }.forEach { brother ->
            if (brother.phone.isNotBlank()) {
                OutlinedButton({
                    val phone = brother.phone.filter(Char::isDigit)
                    val msg = "Olá, " + brother.name + "! Você foi designado para a reunião de " + meeting.date + ". Por favor, confirme o recebimento da designação."
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + phone + "?text=" + Uri.encode(msg))))
                }, Modifier.fillMaxWidth()) { Text("Enviar para " + brother.name) }
            }
        }
    }
    replaceTarget?.let { target ->
        val candidates = vm.candidatesFor(meeting, target.second, target.third)
        ReplaceDialog(candidates, { newId -> vm.replaceAssignment(target.first, target.second, target.third, newId); replaceTarget = null }, { replaceTarget = null })
    }
}
@Composable
private fun BrothersScreen(vm: AppViewModel) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Brother?>(null) }
    var deleting by remember { mutableStateOf<Brother?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val filtered = vm.brothers.value.filter { it.name.contains(search.trim(), ignoreCase = true) }.sortedBy { it.name.lowercase(Locale.getDefault()) }
    Column(Modifier.padding(20.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Groups, contentDescription = null)
            Text("Irmãos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Text("${filtered.size} cadastro(s) encontrado(s)", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(phone, { phone = it }, label = { Text("WhatsApp (somente números)") }, modifier = Modifier.fillMaxWidth())
        Button({
            error = vm.addBrother(name, phone)
            if (error == null) { name = ""; phone = "" }
        }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Adicionar irmão")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            search, { search = it },
            label = { Text("Buscar irmão") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.id }) { brother ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(brother.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AssistChip(onClick = {}, enabled = false, label = { Text(if (brother.active) "Ativo" else "Inativo") })
                                if (brother.phone.isNotBlank()) AssistChip(onClick = {}, enabled = false, label = { Text("WhatsApp") })
                            }
                        }
                        Column {
                            IconButton({ editing = brother }) { Icon(Icons.Filled.Edit, contentDescription = "Editar") }
                            IconButton({ deleting = brother }) { Icon(Icons.Filled.Delete, contentDescription = "Excluir") }
                            TextButton({ vm.setBrotherActive(brother.id, !brother.active) }) { Text(if (brother.active) "Desativar" else "Ativar") }
                        }
                    }
                }
            }
        }
    }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("Não foi possível salvar") }, text = { Text(message) }, confirmButton = { TextButton({ error = null }) { Text("OK") } }) }
    editing?.let { brother -> EditBrotherDialog(brother, { n, p -> error = vm.updateBrother(brother.id, n, p); if (error == null) editing = null }, { editing = null }) }
    deleting?.let { brother ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Excluir irmão?") },
            text = { Text("O irmão " + brother.name + " será removido do cadastro. As designações já registradas no histórico serão mantidas.") },
            confirmButton = { TextButton({ vm.deleteBrother(brother.id); deleting = null }) { Text("Excluir") } },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancelar") } })
    }
}
@Composable
private fun PrivilegesScreen(vm: AppViewModel) {
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var searchBrother by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Privilege?>(null) }
    var deleting by remember { mutableStateOf<Privilege?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var panelScale by remember { mutableFloatStateOf(1f) }
    val filteredBrothers = vm.brothers.value.filter { it.name.contains(searchBrother.trim(), ignoreCase = true) }.sortedBy { it.name.lowercase(Locale.getDefault()) }
    Column(Modifier.padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Work, contentDescription = null)
            Text("Privilégios", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Painéis", style = MaterialTheme.typography.labelMedium)
                TextButton({ panelScale = (panelScale - 0.1f).coerceAtLeast(0.8f) }) { Text("−") }
                Text("${(panelScale * 100).toInt()}%")
                TextButton({ panelScale = (panelScale + 0.1f).coerceAtMost(1.4f) }) { Text("+") }
            }
        }
        OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit) }, label = { Text("Quantidade necessária") }, modifier = Modifier.fillMaxWidth())
        Button({ error = vm.addPrivilege(name, quantity.toIntOrNull() ?: 1); if (error == null) { name = ""; quantity = "1" } }) { Text("Adicionar privilégio") }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            searchBrother, { searchBrother = it },
            label = { Text("Buscar irmão para autorizar") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.privileges.value.sortedBy { it.name.lowercase(Locale.getDefault()) }, key = { it.id }) { privilege ->
                Card(Modifier.fillMaxWidth().heightIn(min = (145f * panelScale).dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) { Text(privilege.name, style = MaterialTheme.typography.titleMedium); Text("Necessários: " + privilege.quantity + " • " + if (privilege.active) "Ativo" else "Inativo") }
                            Column {
                                TextButton({ editing = privilege }) { Text("Editar") }
                                TextButton({ deleting = privilege }) { Text("Excluir") }
                                TextButton({ vm.setPrivilegeActive(privilege.id, !privilege.active) }) { Text(if (privilege.active) "Desativar" else "Ativar") }
                            }
                        }
                        Text("Dias permitidos para este privilégio", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(vm.schedule.value.firstDay, vm.schedule.value.secondDay).distinct().forEach { day ->
                                FilterChip(
                                    selected = day in privilege.allowedDays,
                                    onClick = {
                                        val newDays = privilege.allowedDays.toMutableSet().also { set ->
                                            if (!set.add(day)) set.remove(day)
                                        }
                                        vm.setPrivilegeAllowedDays(privilege.id, newDays)
                                    },
                                    label = { Text(dayLabel(day).take(3).replaceFirstChar { it.uppercase() }) }
                                )
                            }
                            FilterChip(
                                selected = privilege.allowedDays.isEmpty(),
                                onClick = { vm.setPrivilegeAllowedDays(privilege.id, emptySet()) },
                                label = { Text("Todos") }
                            )
                        }
                        Text(
                            if (privilege.allowedDays.isEmpty()) "Permitido em qualquer dia de reunião"
                            else "Permitido: " + privilege.allowedDays.sorted().joinToString(" e ") { dayLabel(it) },
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (privilege.name.trim().lowercase(Locale.getDefault()) in setOf("leitor do livro", "leitor livro", "leitor da sentinela", "leitor sentinela")) {
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = {
                                    Text(
                                        if (privilege.name.trim().lowercase(Locale.getDefault()) in setOf("leitor da sentinela", "leitor sentinela"))
                                            "Leitor da Sentinela → também pode ler o Livro"
                                        else "Leitor do Livro"
                                    )
                                }
                            )
                        }
                        Text("Irmãos autorizados", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        filteredBrothers.forEach { brother ->
                            val directAuthorization = privilege.id in brother.privileges
                            val inheritedFromSentinel = !directAuthorization &&
                                privilege.name.trim().lowercase(Locale.getDefault()) in setOf("leitor do livro", "leitor livro") &&
                                vm.privileges.value.any { p ->
                                    p.name.trim().lowercase(Locale.getDefault()) in setOf("leitor da sentinela", "leitor sentinela") &&
                                        p.id in brother.privileges
                                }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(brother.name)
                                    if (inheritedFromSentinel) {
                                        Text(
                                            "Autorizado automaticamente por ser Leitor da Sentinela",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                Checkbox(
                                    checked = directAuthorization || inheritedFromSentinel,
                                    onCheckedChange = {
                                        if (!inheritedFromSentinel) {
                                            vm.togglePrivilege(brother.id, privilege.id)
                                        }
                                    },
                                    enabled = !inheritedFromSentinel
                                )
                            }
                        }
                        if (filteredBrothers.isEmpty()) Text("Nenhum irmão encontrado.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("Não foi possível salvar") }, text = { Text(message) }, confirmButton = { TextButton({ error = null }) { Text("OK") } }) }
    editing?.let { privilege -> EditPrivilegeDialog(privilege, { n, q -> error = vm.updatePrivilege(privilege.id, n, q); if (error == null) editing = null }, { editing = null }) }
    deleting?.let { privilege -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Excluir privilégio?") }, text = { Text("O privilégio " + privilege.name + " será removido do cadastro e deixará de estar autorizado para os irmãos.") }, confirmButton = { TextButton({ vm.deletePrivilege(privilege.id); deleting = null }) { Text("Excluir") } }, dismissButton = { TextButton({ deleting = null }) { Text("Cancelar") } }) }
}
@Composable
private fun HistoryScreen(vm: AppViewModel) {
    var confirmDelete by remember { mutableStateOf<Meeting?>(null) }
    LazyColumn(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.History, contentDescription = null)
                Text("Histórico", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text("${vm.meetings.value.size} reunião(ões) registrada(s)")
        }
        items(vm.meetings.value.sortedByDescending { parseDateForSort(it.date) }, key = { it.id }) { meeting ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(meeting.date, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(meeting.type, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
private fun SettingsScreen(themeIndex: Int, onThemeChange: (Int) -> Unit) {
    val themes = listOf(
        "Azul" to androidx.compose.ui.graphics.Color(0xFF1565C0),
        "Verde" to androidx.compose.ui.graphics.Color(0xFF2E7D32),
        "Roxo" to androidx.compose.ui.graphics.Color(0xFF6A1B9A),
        "Laranja" to androidx.compose.ui.graphics.Color(0xFFEF6C00),
        "Vinho" to androidx.compose.ui.graphics.Color(0xFF8E244D)
    )
    LazyColumn(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = null)
                Text("Configurações", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text("Personalize a aparência do aplicativo.", style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Cor do aplicativo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    themes.forEachIndexed { index, item ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(Modifier.size(28.dp), shape = MaterialTheme.shapes.small, color = item.second) {}
                                Text(item.first)
                            }
                            RadioButton(selected = themeIndex == index, onClick = { onThemeChange(index) })
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Regras de leitura", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Na tela Privilégios, marque os dias permitidos. Assim, um Leitor do livro pode ficar somente na quarta, enquanto um Leitor da Sentinela pode ficar somente no sábado.")
                }
            }
        }
    }
}

private fun parseDateForSort(value: String): java.time.LocalDate = runCatching {
    java.time.LocalDate.parse(value, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}.getOrElse { java.time.LocalDate.MIN }

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
private fun ReplaceDialog(candidates: List<Brother>, onSelect: (Long) -> Unit, onDismiss: () -> Unit) {
    var search by remember { mutableStateOf("") }
    val filtered = candidates.filter { it.name.contains(search.trim(), ignoreCase = true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Trocar designação") },
        text = { Column {
            OutlinedTextField(search, { search = it }, label = { Text("Buscar irmão") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) Text("Não há outro irmão autorizado e disponível.")
            filtered.forEach { brother -> TextButton({ onSelect(brother.id) }, Modifier.fillMaxWidth()) { Text(brother.name) } }
        } },
        confirmButton = { TextButton(onDismiss) { Text("Cancelar") } })
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
