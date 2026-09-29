package br.com.willendary.designacoesjw.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import br.com.willendary.designacoesjw.data.*
import br.com.willendary.designacoesjw.export.CsvDataHandler
import br.com.willendary.designacoesjw.export.HtmlReportGenerator
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import br.com.willendary.designacoesjw.util.WhatsAppHelper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
private val days = listOf(
    DayOfWeek.MONDAY to "Segunda-feira", DayOfWeek.TUESDAY to "Terça-feira",
    DayOfWeek.WEDNESDAY to "Quarta-feira", DayOfWeek.THURSDAY to "Quinta-feira",
    DayOfWeek.FRIDAY to "Sexta-feira", DayOfWeek.SATURDAY to "Sábado", DayOfWeek.SUNDAY to "Domingo"
)
private fun dayName(v: Int) = days.firstOrNull { it.first.value == v }?.second ?: "—"

class StoreController {
    val file = File(System.getProperty("user.home"), ".designacoes-jw/dados.json")
    var data by mutableStateOf(load())
        private set

    private fun load() = runCatching {
        if (file.exists()) json.decodeFromString<Store>(file.readText()) else Store()
    }.getOrDefault(Store())

    private fun save(s: Store) {
        data = s
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(s))
    }

    fun addBrother(name: String, phone: String, role: BrotherRole = BrotherRole.PUBLISHER): String? {
        val norm = AssignmentGenerator.normalizeName(name)
        if (norm.isBlank()) return "Informe o nome do irmão."
        if (data.brothers.any { AssignmentGenerator.normalizeName(it.name) == norm }) {
            return "Já existe um irmão cadastrado com esse nome."
        }
        val newBrother = Brother(
            id = AssignmentGenerator.nextId(),
            name = name.trim(),
            phone = phone.trim(),
            role = role
        )
        save(data.copy(brothers = (data.brothers + newBrother).sortedBy { AssignmentGenerator.normalizeName(it.name) }))
        return null
    }

    fun updateBrother(id: Long, name: String, phone: String, role: BrotherRole): String? {
        val norm = AssignmentGenerator.normalizeName(name)
        if (norm.isBlank()) return "Informe o nome do irmão."
        if (data.brothers.any { it.id != id && AssignmentGenerator.normalizeName(it.name) == norm }) {
            return "Já existe outro irmão com esse nome."
        }
        save(data.copy(brothers = data.brothers.map {
            if (it.id == id) it.copy(name = name.trim(), phone = phone.trim(), role = role) else it
        }))
        return null
    }

    fun deleteBrother(id: Long) = save(data.copy(brothers = data.brothers.filterNot { it.id == id }))

    fun toggleBrotherActive(id: Long) = save(data.copy(brothers = data.brothers.map {
        if (it.id == id) it.copy(active = !it.active) else it
    }))

    fun addUnavailability(brotherId: Long, start: String, end: String, reason: String): String? {
        val s = AssignmentGenerator.parseDate(start)
        val e = AssignmentGenerator.parseDate(end)
        if (s == LocalDate.MIN || e == LocalDate.MIN) return "Data inválida (use dd/MM/yyyy)"
        if (e.isBefore(s)) return "Término não pode ser antes do início."
        val p = UnavailablePeriod(AssignmentGenerator.nextId(), start, end, reason.trim())
        save(data.copy(brothers = data.brothers.map {
            if (it.id == brotherId) it.copy(unavailabilities = it.unavailabilities + p) else it
        }))
        return null
    }

    fun removeUnavailability(brotherId: Long, periodId: Long) {
        save(data.copy(brothers = data.brothers.map {
            if (it.id == brotherId) it.copy(unavailabilities = it.unavailabilities.filterNot { u -> u.id == periodId }) else it
        }))
    }

    fun addPrivilege(name: String, quantity: Int, minRole: BrotherRole = BrotherRole.PUBLISHER): String? {
        val norm = AssignmentGenerator.normalizeName(name)
        if (norm.isBlank()) return "Informe o nome do privilégio."
        if (data.privileges.any { AssignmentGenerator.normalizeName(it.name) == norm }) {
            return "Já existe um privilégio com esse nome."
        }
        val p = Privilege(AssignmentGenerator.nextId(), name.trim(), quantity.coerceAtLeast(1), minRole = minRole)
        save(data.copy(privileges = (data.privileges + p).sortedBy { AssignmentGenerator.normalizeName(it.name) }))
        return null
    }

    fun updatePrivilege(id: Long, name: String, quantity: Int, minRole: BrotherRole): String? {
        val norm = AssignmentGenerator.normalizeName(name)
        if (norm.isBlank()) return "Informe o nome do privilégio."
        if (data.privileges.any { it.id != id && AssignmentGenerator.normalizeName(it.name) == norm }) {
            return "Já existe outro privilégio com esse nome."
        }
        save(data.copy(privileges = data.privileges.map {
            if (it.id == id) it.copy(name = name.trim(), quantity = quantity.coerceAtLeast(1), minRole = minRole) else it
        }))
        return null
    }

    fun deletePrivilege(id: Long) {
        save(data.copy(
            privileges = data.privileges.filterNot { it.id == id },
            brothers = data.brothers.map { it.copy(privileges = it.privileges - id) }
        ))
    }

    fun togglePrivilegeActive(id: Long) = save(data.copy(privileges = data.privileges.map {
        if (it.id == id) it.copy(active = !it.active) else it
    }))

    fun togglePrivilegeDay(privilegeId: Long, day: Int) {
        save(data.copy(privileges = data.privileges.map { p ->
            if (p.id != privilegeId) p else {
                val newDays = p.allowedDays.toMutableSet().also { if (!it.add(day)) it.remove(day) }
                p.copy(allowedDays = newDays)
            }
        }))
    }

    fun clearPrivilegeDays(privilegeId: Long) {
        save(data.copy(privileges = data.privileges.map {
            if (it.id == privilegeId) it.copy(allowedDays = emptySet()) else it
        }))
    }

    fun toggleBrotherPrivilege(brotherId: Long, privilegeId: Long) {
        save(data.copy(brothers = data.brothers.map { b ->
            if (b.id != brotherId) b else b.copy(
                privileges = b.privileges.toMutableSet().also { if (!it.add(privilegeId)) it.remove(privilegeId) }
            )
        }))
    }

    fun setMeetingDays(first: Int, second: Int) {
        if (first != second) save(data.copy(firstDay = first, secondDay = second))
    }

    fun generateMonth(month: YearMonth) {
        val prefix = month.format(java.time.format.DateTimeFormatter.ofPattern("MM/yyyy"))
        val keep = data.meetings.filterNot { it.date.endsWith("/$prefix") }
        val generated = AssignmentGenerator.generateMonth(
            yearMonth = month,
            schedule = MeetingSchedule(data.firstDay, data.secondDay),
            brothers = data.brothers,
            privileges = data.privileges,
            existingMeetings = keep
        )
        save(data.copy(meetings = (keep + generated).sortedBy { AssignmentGenerator.parseDate(it.date) }))
    }

    fun deleteMonth(month: YearMonth) {
        val prefix = month.format(java.time.format.DateTimeFormatter.ofPattern("MM/yyyy"))
        save(data.copy(meetings = data.meetings.filterNot { it.date.endsWith("/$prefix") }))
    }

    fun replaceAssignment(meetingId: Long, privilegeId: Long, oldBrotherId: Long, newBrotherId: Long) {
        save(data.copy(meetings = data.meetings.map { m ->
            if (m.id != meetingId) m else m.copy(
                assignments = m.assignments.map {
                    if (it.privilegeId == privilegeId && it.brotherId == oldBrotherId) it.copy(brotherId = newBrotherId) else it
                }
            )
        }))
    }

    fun deleteMeeting(meetingId: Long) = save(data.copy(meetings = data.meetings.filterNot { it.id == meetingId }))

    fun candidatesFor(meeting: Meeting, privilegeId: Long, currentBrotherId: Long) =
        AssignmentGenerator.candidatesFor(meeting, privilegeId, currentBrotherId, data.brothers, data.privileges)

    fun missingAssignments(meeting: Meeting) =
        AssignmentGenerator.missingAssignments(meeting, data.privileges)

    fun exportHtmlReport(month: YearMonth): File {
        val prefix = month.format(java.time.format.DateTimeFormatter.ofPattern("MM/yyyy"))
        val monthMeetings = data.meetings.filter { it.date.endsWith("/$prefix") }
        val html = HtmlReportGenerator.generateHtml(month, monthMeetings, data.brothers, data.privileges)
        val dir = File(System.getProperty("user.home"), ".designacoes-jw/relatorios").apply { mkdirs() }
        val file = File(dir, "designacoes-${month.year}-${month.monthValue.toString().padStart(2, '0')}.html")
        file.writeText(html, Charsets.UTF_8)
        return file
    }

    fun exportBrothersCsv(): File {
        val csv = CsvDataHandler.exportBrothersToCsv(data.brothers, data.privileges)
        val dir = File(System.getProperty("user.home"), ".designacoes-jw").apply { mkdirs() }
        val file = File(dir, "irmaos.csv")
        file.writeText(csv, Charsets.UTF_8)
        return file
    }

    fun importBrothersCsv(content: String): Int {
        val imported = CsvDataHandler.importBrothersFromCsv(content)
        if (imported.isEmpty()) return 0
        val currentBrothers = data.brothers.toMutableList()
        var count = 0
        imported.forEach { imp ->
            val norm = AssignmentGenerator.normalizeName(imp.name)
            if (norm.isNotBlank() && currentBrothers.none { AssignmentGenerator.normalizeName(it.name) == norm }) {
                val privIds = imp.privilegeNames.mapNotNull { pName ->
                    val np = AssignmentGenerator.normalizeName(pName)
                    data.privileges.find { AssignmentGenerator.normalizeName(it.name) == np }?.id
                }.toSet()
                currentBrothers += Brother(
                    id = AssignmentGenerator.nextId(),
                    name = imp.name,
                    phone = imp.phone,
                    privileges = privIds,
                    active = imp.active,
                    role = imp.role
                )
                count++
            }
        }
        if (count > 0) {
            save(data.copy(brothers = currentBrothers.sortedBy { AssignmentGenerator.normalizeName(it.name) }))
        }
        return count
    }

    fun saveTemplates(single: String, meeting: String) {
        save(data.copy(whatsappSingleTemplate = single, whatsappMeetingTemplate = meeting))
    }
}

fun main() = application {
    val c = remember { StoreController() }
    var updateInfo by remember { mutableStateOf<WindowsUpdateInfo?>(null) }
    var checkingUpdate by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        updateInfo = WindowsUpdateManager.checkForUpdate()
        checkingUpdate = false
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Designações JW $CURRENT_VERSION",
        state = rememberWindowState(width = 1280.dp, height = 800.dp)
    ) {
        MaterialTheme {
            DesktopApp(c)
            if (!checkingUpdate && updateInfo != null) {
                UpdateDialog(
                    info = updateInfo!!,
                    onUpdate = { WindowsUpdateManager.downloadAndInstall(updateInfo!!) },
                    onDismiss = { updateInfo = null }
                )
            }
        }
    }
}

@Composable
private fun UpdateDialog(info: WindowsUpdateInfo, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova versão disponível") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Uma nova versão do Designações JW está disponível.")
                Text("Instalada: $CURRENT_VERSION")
                Text("Nova versão: ${info.version}", fontWeight = FontWeight.Bold)
                Text("O aplicativo será fechado e reaberto automaticamente durante a atualização.")
            }
        },
        confirmButton = { Button(onClick = onUpdate) { Text("Atualizar agora") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Depois") } }
    )
}

@Composable
fun DesktopApp(c: StoreController) {
    var tab by remember { mutableIntStateOf(0) }
    val labels = listOf("Início", "Irmãos", "Privilégios", "Histórico", "Configurações")
    val icons = listOf(Icons.Default.Home, Icons.Default.Groups, Icons.Default.Work, Icons.Default.History, Icons.Default.Settings)

    Row(Modifier.fillMaxSize()) {
        NavigationRail {
            Text("DJW", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            labels.forEachIndexed { i, label ->
                NavigationRailItem(
                    selected = tab == i,
                    onClick = { tab = i },
                    icon = { Icon(icons[i], label) },
                    label = { Text(label) }
                )
            }
        }
        VerticalDivider()
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Text("Designações JW", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("Versão $CURRENT_VERSION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(16.dp))
            when (tab) {
                0 -> Home(c)
                1 -> Brothers(c)
                2 -> Privileges(c)
                3 -> History(c)
                4 -> Settings(c)
            }
        }
    }
}

@Composable
private fun Home(c: StoreController) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedMeetingId by remember { mutableStateOf<Long?>(null) }
    var replaceTarget by remember { mutableStateOf<Triple<Long, Long, Long>?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    val prefix = month.format(java.time.format.DateTimeFormatter.ofPattern("MM/yyyy"))
    val meetings = c.data.meetings.filter { it.date.endsWith("/$prefix") }.sortedBy { AssignmentGenerator.parseDate(it.date) }
    val monthName = month.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR")).replaceFirstChar { it.uppercase() } + " " + month.year

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton({ month = month.minusMonths(1); selectedMeetingId = null }) { Text("‹ Mês anterior") }
            Text(monthName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            FilledTonalButton({ month = month.plusMonths(1); selectedMeetingId = null }) { Text("Próximo mês ›") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Irmãos ativos", c.data.brothers.count { it.active })
            Stat("Privilégios ativos", c.data.privileges.count { it.active })
            Stat("Reuniões no mês", meetings.size)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button({ c.generateMonth(month); selectedMeetingId = null }) {
                Icon(Icons.Default.AutoAwesome, null)
                Spacer(Modifier.width(6.dp))
                Text(if (meetings.isEmpty()) "Gerar designações do mês" else "Regenerar mês")
            }
            OutlinedButton(
                enabled = meetings.isNotEmpty(),
                onClick = {
                    val file = c.exportHtmlReport(month)
                    if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file)
                    infoMessage = "Relatório HTML gerado em: ${file.absolutePath}"
                }
            ) {
                Icon(Icons.Default.Print, null)
                Spacer(Modifier.width(6.dp))
                Text("Imprimir / Relatório HTML")
            }
            Text("Dias: " + dayName(c.data.firstDay) + " e " + dayName(c.data.secondDay), style = MaterialTheme.typography.bodyMedium)
        }

        infoMessage?.let { msg ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    IconButton({ infoMessage = null }) { Icon(Icons.Default.Close, null) }
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(meetings) { m ->
                val missing = c.missingAssignments(m)
                val isSelected = m.id == selectedMeetingId
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(m.date + " — " + m.type, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${m.assignments.size} designação(ões)")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button({
                                    val text = WhatsAppHelper.buildMeetingBroadcastMessage(
                                        c.data.whatsappMeetingTemplate, m, c.data.brothers, c.data.privileges, missing
                                    )
                                    val url = WhatsAppHelper.buildWebLink("", text)
                                    if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url))
                                }) {
                                    Icon(Icons.Default.Share, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("WhatsApp da reunião")
                                }
                                OutlinedButton({ selectedMeetingId = if (isSelected) null else m.id }) {
                                    Text(if (isSelected) "Ocultar" else "Ver detalhes")
                                }
                            }
                        }

                        if (missing.isNotEmpty()) {
                            Text("⚠ Faltaram candidatos para: " + missing.joinToString { it.name }, color = MaterialTheme.colorScheme.error)
                        }

                        if (isSelected) {
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            m.assignments.forEach { a ->
                                val p = c.data.privileges.find { it.id == a.privilegeId }
                                val b = c.data.brothers.find { it.id == a.brotherId }
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("• ${p?.name ?: "Privilégio"}: ${b?.name ?: "Irmão"}", fontWeight = FontWeight.SemiBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (b?.phone?.isNotBlank() == true) {
                                            TextButton({
                                                if (p != null) {
                                                    val text = WhatsAppHelper.buildSingleMessage(c.data.whatsappSingleTemplate, b, p, m)
                                                    val url = WhatsAppHelper.buildWebLink(b.phone, text)
                                                    if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url))
                                                }
                                            }) { Text("Avisar no WhatsApp") }
                                        }
                                        IconButton({ replaceTarget = Triple(m.id, a.privilegeId, a.brotherId) }) {
                                            Icon(Icons.Default.SwapHoriz, "Trocar")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    replaceTarget?.let { target ->
        val m = meetings.find { it.id == target.first }
        val candidates = if (m != null) c.candidatesFor(m, target.second, target.third) else emptyList()
        AlertDialog(
            onDismissRequest = { replaceTarget = null },
            title = { Text("Trocar designação") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (candidates.isEmpty()) Text("Nenhum outro irmão autorizado e disponível para esta reunião.")
                    candidates.forEach { b ->
                        TextButton(
                            onClick = {
                                c.replaceAssignment(target.first, target.second, target.third, b.id)
                                replaceTarget = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(b.name + " (" + b.role.label + ")") }
                    }
                }
            },
            confirmButton = { TextButton({ replaceTarget = null }) { Text("Fechar") } }
        )
    }
}

@Composable
private fun Stat(title: String, value: Int) {
    Card(Modifier.width(190.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Brothers(c: StoreController) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(BrotherRole.PUBLISHER) }
    var search by remember { mutableStateOf("") }
    var editingBrother by remember { mutableStateOf<Brother?>(null) }
    var unavailBrother by remember { mutableStateOf<Brother?>(null) }
    var deletingBrother by remember { mutableStateOf<Brother?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val filtered = c.data.brothers.filter {
        AssignmentGenerator.normalizeName(it.name).contains(AssignmentGenerator.normalizeName(search))
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Irmãos cadastrados", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.weight(1.5f), singleLine = true)
            OutlinedTextField(phone, { phone = it }, label = { Text("WhatsApp (com DDD)") }, modifier = Modifier.weight(1f), singleLine = true)
            var roleMenu by remember { mutableStateOf(false) }
            Box {
                OutlinedButton({ roleMenu = true }) { Text(selectedRole.label) }
                DropdownMenu(roleMenu, { roleMenu = false }) {
                    BrotherRole.values().forEach { r ->
                        DropdownMenuItem(text = { Text(r.label) }, onClick = { selectedRole = r; roleMenu = false })
                    }
                }
            }
            Button({
                errorMsg = c.addBrother(name, phone, selectedRole)
                if (errorMsg == null) { name = ""; phone = "" }
            }) { Text("Adicionar") }
        }

        OutlinedTextField(
            search, { search = it },
            label = { Text("Buscar irmão") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered) { b ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(b.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                AssistChip(onClick = {}, enabled = false, label = { Text(b.role.label) })
                                AssistChip(onClick = {}, enabled = false, label = { Text(if (b.active) "Ativo" else "Inativo") })
                                if (b.unavailabilities.isNotEmpty()) {
                                    AssistChip(onClick = {}, enabled = false, label = { Text("${b.unavailabilities.size} ausência(s)") })
                                }
                            }
                            Text(if (b.phone.isBlank()) "Sem WhatsApp" else "WhatsApp: ${b.phone}", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton({ unavailBrother = b }) { Text("Ausências") }
                            TextButton({ c.toggleBrotherActive(b.id) }) { Text(if (b.active) "Desativar" else "Ativar") }
                            IconButton({ editingBrother = b }) { Icon(Icons.Default.Edit, "Editar") }
                            IconButton({ deletingBrother = b }) { Icon(Icons.Default.Delete, "Excluir") }
                        }
                    }
                }
            }
        }
    }

    editingBrother?.let { b ->
        var editName by remember { mutableStateOf(b.name) }
        var editPhone by remember { mutableStateOf(b.phone) }
        var editRole by remember { mutableStateOf(b.role) }
        var roleDropdown by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { editingBrother = null },
            title = { Text("Editar irmão") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(editName, { editName = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(editPhone, { editPhone = it }, label = { Text("WhatsApp") }, modifier = Modifier.fillMaxWidth())
                    Box {
                        OutlinedButton({ roleDropdown = true }, Modifier.fillMaxWidth()) { Text("Cargo: " + editRole.label) }
                        DropdownMenu(roleDropdown, { roleDropdown = false }) {
                            BrotherRole.values().forEach { r ->
                                DropdownMenuItem(text = { Text(r.label) }, onClick = { editRole = r; roleDropdown = false })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    errorMsg = c.updateBrother(b.id, editName, editPhone, editRole)
                    if (errorMsg == null) editingBrother = null
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton({ editingBrother = null }) { Text("Cancelar") } }
        )
    }

    unavailBrother?.let { b ->
        var startDate by remember { mutableStateOf("") }
        var endDate by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("") }
        var periodErr by remember { mutableStateOf<String?>(null) }
        val currentBro = c.data.brothers.find { it.id == b.id } ?: b

        AlertDialog(
            onDismissRequest = { unavailBrother = null },
            title = { Text("Ausências e Férias — ${b.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Períodos cadastrados:", fontWeight = FontWeight.Bold)
                    if (currentBro.unavailabilities.isEmpty()) Text("Nenhuma ausência registrada.")
                    currentBro.unavailabilities.forEach { u ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${u.startDate} a ${u.endDate} ${if (u.reason.isNotBlank()) "(${u.reason})" else ""}", style = MaterialTheme.typography.bodySmall)
                            IconButton({ c.removeUnavailability(b.id, u.id) }) { Icon(Icons.Default.Close, "Remover") }
                        }
                    }
                    HorizontalDivider()
                    Text("Adicionar ausência:", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(startDate, { startDate = it }, label = { Text("Início (dd/MM/yyyy)") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(endDate, { endDate = it }, label = { Text("Fim (dd/MM/yyyy)") }, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(reason, { reason = it }, label = { Text("Motivo (ex: Viagem, Férias)") }, modifier = Modifier.fillMaxWidth())
                    periodErr?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Button({
                        periodErr = c.addUnavailability(b.id, startDate, endDate, reason)
                        if (periodErr == null) { startDate = ""; endDate = ""; reason = "" }
                    }) { Text("Adicionar período") }
                }
            },
            confirmButton = { TextButton({ unavailBrother = null }) { Text("Concluir") } }
        )
    }

    deletingBrother?.let { b ->
        AlertDialog(
            onDismissRequest = { deletingBrother = null },
            title = { Text("Excluir irmão?") },
            text = { Text("Deseja realmente remover ${b.name} do cadastro?") },
            confirmButton = { TextButton({ c.deleteBrother(b.id); deletingBrother = null }) { Text("Excluir") } },
            dismissButton = { TextButton({ deletingBrother = null }) { Text("Cancelar") } }
        )
    }

    errorMsg?.let { msg ->
        AlertDialog(onDismissRequest = { errorMsg = null }, title = { Text("Aviso") }, text = { Text(msg) }, confirmButton = { TextButton({ errorMsg = null }) { Text("OK") } })
    }
}

@Composable
private fun Privileges(c: StoreController) {
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var selectedMinRole by remember { mutableStateOf(BrotherRole.PUBLISHER) }
    var searchBrother by remember { mutableStateOf("") }
    var editingPrivilege by remember { mutableStateOf<Privilege?>(null) }
    var deletingPrivilege by remember { mutableStateOf<Privilege?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val filteredBrothers = c.data.brothers.filter {
        AssignmentGenerator.normalizeName(it.name).contains(AssignmentGenerator.normalizeName(searchBrother))
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Privilégios e Autorizações", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(name, { name = it }, label = { Text("Nome do privilégio") }, modifier = Modifier.weight(1.5f), singleLine = true)
            OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit) }, label = { Text("Quantidade") }, modifier = Modifier.width(110.dp), singleLine = true)
            var roleMenu by remember { mutableStateOf(false) }
            Box {
                OutlinedButton({ roleMenu = true }) { Text("Mínimo: " + selectedMinRole.label) }
                DropdownMenu(roleMenu, { roleMenu = false }) {
                    BrotherRole.values().forEach { r ->
                        DropdownMenuItem(text = { Text(r.label) }, onClick = { selectedMinRole = r; roleMenu = false })
                    }
                }
            }
            Button({
                errorMsg = c.addPrivilege(name, quantity.toIntOrNull() ?: 1, selectedMinRole)
                if (errorMsg == null) { name = ""; quantity = "1" }
            }) { Text("Adicionar") }
        }

        OutlinedTextField(
            searchBrother, { searchBrother = it },
            label = { Text("Filtrar irmãos para autorizar") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(c.data.privileges) { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Quantidade: ${p.quantity} • Cargo mínimo: ${p.minRole.label}")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton({ c.togglePrivilegeActive(p.id) }) { Text(if (p.active) "Desativar" else "Ativar") }
                                IconButton({ editingPrivilege = p }) { Icon(Icons.Default.Edit, "Editar") }
                                IconButton({ deletingPrivilege = p }) { Icon(Icons.Default.Delete, "Excluir") }
                            }
                        }

                        Text("Dias de reunião permitidos:", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(c.data.firstDay, c.data.secondDay).distinct().forEach { d ->
                                FilterChip(
                                    selected = d in p.allowedDays,
                                    onClick = { c.togglePrivilegeDay(p.id, d) },
                                    label = { Text(dayName(d).take(3).replaceFirstChar { it.uppercase() }) }
                                )
                            }
                            FilterChip(
                                selected = p.allowedDays.isEmpty(),
                                onClick = { c.clearPrivilegeDays(p.id) },
                                label = { Text("Todos os dias") }
                            )
                        }

                        if (AssignmentGenerator.isSentinelReaderPrivilege(p)) {
                            Text("ℹ Leitor da Sentinela → também é elegível para o Livro automaticamente", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                        }

                        Text("Irmãos autorizados:", fontWeight = FontWeight.SemiBold)
                        filteredBrothers.forEach { b ->
                            val direct = p.id in b.privileges
                            val inherited = !direct && AssignmentGenerator.isBookReaderPrivilege(p) &&
                                c.data.privileges.any { AssignmentGenerator.isSentinelReaderPrivilege(it) && it.id in b.privileges }

                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = direct || inherited,
                                    onCheckedChange = { if (!inherited) c.toggleBrotherPrivilege(b.id, p.id) },
                                    enabled = !inherited
                                )
                                Text(b.name + " (" + b.role.label + ")")
                                if (inherited) Text("  (Herdado da Sentinela)", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    editingPrivilege?.let { p ->
        var editName by remember { mutableStateOf(p.name) }
        var editQty by remember { mutableStateOf(p.quantity.toString()) }
        var editRole by remember { mutableStateOf(p.minRole) }
        var roleDropdown by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { editingPrivilege = null },
            title = { Text("Editar privilégio") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(editName, { editName = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(editQty, { editQty = it.filter(Char::isDigit) }, label = { Text("Quantidade") }, modifier = Modifier.fillMaxWidth())
                    Box {
                        OutlinedButton({ roleDropdown = true }, Modifier.fillMaxWidth()) { Text("Cargo mínimo: " + editRole.label) }
                        DropdownMenu(roleDropdown, { roleDropdown = false }) {
                            BrotherRole.values().forEach { r ->
                                DropdownMenuItem(text = { Text(r.label) }, onClick = { editRole = r; roleDropdown = false })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    errorMsg = c.updatePrivilege(p.id, editName, editQty.toIntOrNull() ?: 1, editRole)
                    if (errorMsg == null) editingPrivilege = null
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton({ editingPrivilege = null }) { Text("Cancelar") } }
        )
    }

    deletingPrivilege?.let { p ->
        AlertDialog(
            onDismissRequest = { deletingPrivilege = null },
            title = { Text("Excluir privilégio?") },
            text = { Text("Deseja remover '${p.name}'? Os irmãos autorizados perderão esse privilégio.") },
            confirmButton = { TextButton({ c.deletePrivilege(p.id); deletingPrivilege = null }) { Text("Excluir") } },
            dismissButton = { TextButton({ deletingPrivilege = null }) { Text("Cancelar") } }
        )
    }

    errorMsg?.let { msg ->
        AlertDialog(onDismissRequest = { errorMsg = null }, title = { Text("Aviso") }, text = { Text(msg) }, confirmButton = { TextButton({ errorMsg = null }) { Text("OK") } })
    }
}

@Composable
private fun History(c: StoreController) {
    var confirmDelete by remember { mutableStateOf<Meeting?>(null) }
    val groups = c.data.meetings.sortedByDescending { AssignmentGenerator.parseDate(it.date) }.groupBy {
        it.date.substringAfterLast("/")
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Histórico de Reuniões", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("${c.data.meetings.size} reunião(ões) registrada(s)")

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEach { (year, meetings) ->
                item {
                    Text(year, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                }
                items(meetings) { m ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.date + " — " + m.type, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${m.assignments.size} designação(ões)")
                                m.assignments.forEach { a ->
                                    val p = c.data.privileges.find { it.id == a.privilegeId }?.name ?: "Privilégio"
                                    val b = c.data.brothers.find { it.id == a.brotherId }?.name ?: "Irmão"
                                    Text("• $p: $b", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            IconButton({ confirmDelete = m }) { Icon(Icons.Default.Delete, "Excluir") }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Excluir registro do histórico?") },
            text = { Text("A reunião de ${m.date} será excluída do histórico.") },
            confirmButton = { TextButton({ c.deleteMeeting(m.id); confirmDelete = null }) { Text("Excluir") } },
            dismissButton = { TextButton({ confirmDelete = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun Settings(c: StoreController) {
    var first by remember { mutableIntStateOf(c.data.firstDay) }
    var second by remember { mutableIntStateOf(c.data.secondDay) }
    var singleTmpl by remember { mutableStateOf(c.data.whatsappSingleTemplate.ifBlank { WhatsAppHelper.DEFAULT_SINGLE_TEMPLATE }) }
    var meetingTmpl by remember { mutableStateOf(c.data.whatsappMeetingTemplate.ifBlank { WhatsAppHelper.DEFAULT_MEETING_TEMPLATE }) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Configurações do Sistema", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Dias de Reunião da Congregação", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Selector("Primeiro dia", first) { first = it; c.setMeetingDays(first, second) }
                        Selector("Segundo dia", second) { second = it; c.setMeetingDays(first, second) }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Mensagens personalizadas do WhatsApp", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Variáveis disponíveis: {nome}, {privilegio}, {data}, {diaSemana}, {tipo}, {designacoes}", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = singleTmpl,
                        onValueChange = { singleTmpl = it },
                        label = { Text("Template de aviso individual") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    OutlinedTextField(
                        value = meetingTmpl,
                        onValueChange = { meetingTmpl = it },
                        label = { Text("Template de aviso geral da reunião") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
                    )
                    Button({
                        c.saveTemplates(singleTmpl, meetingTmpl)
                        statusMsg = "Modelos de mensagem salvos com sucesso!"
                    }) { Text("Salvar modelos") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Importação e Exportação (CSV e Backup)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton({
                            val f = c.exportBrothersCsv()
                            statusMsg = "Arquivo de irmãos exportado em: ${f.absolutePath}"
                        }) { Text("Exportar irmãos para CSV") }
                        OutlinedButton({
                            val dir = c.file.parentFile
                            if (Desktop.isDesktopSupported() && dir != null) Desktop.getDesktop().open(dir)
                        }) { Text("Abrir pasta dos dados") }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Armazenamento local do Windows", style = MaterialTheme.typography.labelLarge)
                    Text(c.file.absolutePath, style = MaterialTheme.typography.bodySmall)
                    Text("Designações JW versão $CURRENT_VERSION", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        statusMsg?.let { msg ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton({ statusMsg = null }) { Icon(Icons.Default.Close, null) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Selector(title: String, value: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ open = true }) { Text("$title: " + dayName(value)) }
        DropdownMenu(open, { open = false }) {
            days.forEach { (d, l) ->
                DropdownMenuItem(text = { Text(l) }, onClick = { onChange(d.value); open = false })
            }
        }
    }
}
