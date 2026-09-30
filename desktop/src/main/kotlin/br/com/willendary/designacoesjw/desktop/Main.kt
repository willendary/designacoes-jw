package br.com.willendary.designacoesjw.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import br.com.willendary.designacoesjw.data.*
import br.com.willendary.designacoesjw.desktop.components.CloudSyncBar
import br.com.willendary.designacoesjw.desktop.export.ImageExportHelper
import br.com.willendary.designacoesjw.desktop.firebase.DesktopAuthManager
import br.com.willendary.designacoesjw.desktop.firebase.DesktopFirestoreClient
import br.com.willendary.designacoesjw.desktop.screens.GroupsAndCleaningScreen
import br.com.willendary.designacoesjw.desktop.screens.PublicTalksScreen
import br.com.willendary.designacoesjw.export.CsvDataHandler
import br.com.willendary.designacoesjw.export.HtmlReportGenerator
import br.com.willendary.designacoesjw.export.IcsExportHelper
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import br.com.willendary.designacoesjw.stats.EquityStatisticsHelper
import br.com.willendary.designacoesjw.util.WhatsAppHelper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
private val days = listOf(
    DayOfWeek.MONDAY to "Segunda-feira", DayOfWeek.TUESDAY to "Terça-feira",
    DayOfWeek.WEDNESDAY to "Quarta-feira", DayOfWeek.THURSDAY to "Quinta-feira",
    DayOfWeek.FRIDAY to "Sexta-feira", DayOfWeek.SATURDAY to "Sábado", DayOfWeek.SUNDAY to "Domingo"
)
private fun dayName(v: Int) = days.firstOrNull { it.first.value == v }?.second ?: "—"

private fun getInitials(name: String): String {
    val parts = name.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts[0].first()}${parts.last().first()}".uppercase()
    }
}

private val avatarColors = listOf(
    Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFF8E24AA),
    Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFF00ACC1),
    Color(0xFF3949AB), Color(0xFFD81B60), Color(0xFF00897B)
)

private fun getAvatarColor(name: String): Color {
    val hash = kotlin.math.abs(name.hashCode())
    return avatarColors[hash % avatarColors.size]
}

private fun getNextMeetingInfo(meetings: List<Meeting>): Pair<Meeting, Long>? {
    val today = LocalDate.now()
    val upcoming = meetings.mapNotNull { m ->
        val date = AssignmentGenerator.parseDate(m.date)
        if (date != LocalDate.MIN && !date.isBefore(today)) {
            val daysUntil = ChronoUnit.DAYS.between(today, date)
            Pair(m, daysUntil)
        } else null
    }
    return upcoming.minByOrNull { it.second }
}

class StoreController {
    val file = File(System.getProperty("user.home"), ".designacoes-jw/dados.json")
    var data by mutableStateOf(load())
        private set

    var authSession by mutableStateOf(DesktopAuthManager.loadSession())
        private set
    var syncStatus by mutableStateOf<String?>(if (DesktopAuthManager.currentSession != null) "Conectado à nuvem" else "Offline")
        private set
    var isSyncing by mutableStateOf(false)
        private set

    init {
        if (authSession != null) {
            syncWithCloud()
        }
    }

    private fun load() = runCatching {
        if (file.exists()) json.decodeFromString<Store>(file.readText()) else Store()
    }.getOrDefault(Store())

    private fun save(s: Store) {
        data = s
        saveLocal(s)
        authSession?.let { session ->
            kotlin.concurrent.thread { pushToCloud(session.idToken) }
        }
    }

    private fun saveLocal(s: Store) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(s))
    }

    fun login(email: String, pass: String): String? {
        val res = DesktopAuthManager.signInWithEmail(email, pass)
        return res.fold(
            onSuccess = { session ->
                authSession = session
                syncWithCloud()
                null
            },
            onFailure = { it.message ?: "Falha ao entrar" }
        )
    }

    fun logout() {
        DesktopAuthManager.logout()
        authSession = null
        syncStatus = "Desconectado"
    }

    fun syncWithCloud(onComplete: ((Boolean, String?) -> Unit)? = null) {
        val session = authSession ?: DesktopAuthManager.loadSession()
        if (session == null) {
            syncStatus = "Offline"
            onComplete?.invoke(false, "Usuário não autenticado.")
            return
        }
        authSession = session
        isSyncing = true
        syncStatus = "Sincronizando..."

        kotlin.concurrent.thread {
            val res = DesktopFirestoreClient.fetchCloudStore(session.idToken)
            res.fold(
                onSuccess = { cloudStore ->
                    if (cloudStore.brothers.isNotEmpty() || cloudStore.meetings.isNotEmpty() || cloudStore.privileges.isNotEmpty()) {
                        data = cloudStore.copy(themeMode = data.themeMode)
                        saveLocal(data)
                    } else {
                        pushToCloud(session.idToken)
                    }
                    syncStatus = "Sincronizado"
                    isSyncing = false
                    onComplete?.invoke(true, null)
                },
                onFailure = { err ->
                    syncStatus = "Erro de sincronização"
                    isSyncing = false
                    onComplete?.invoke(false, err.message)
                }
            )
        }
    }

    private fun pushToCloud(token: String) {
        DesktopFirestoreClient.pushBrothers(token, data.brothers)
        DesktopFirestoreClient.pushPrivileges(token, data.privileges)
        DesktopFirestoreClient.pushMeetings(token, data.meetings)
        DesktopFirestoreClient.pushPublicTalks(token, data.publicTalks)
        DesktopFirestoreClient.pushFieldServiceGroups(token, data.fieldServiceGroups)
        DesktopFirestoreClient.pushCleaningSchedules(token, data.cleaningSchedules)
        DesktopFirestoreClient.pushScheduleSettings(token, data.firstDay, data.secondDay)
    }

    fun addBrother(name: String, phone: String, role: BrotherRole = BrotherRole.PUBLISHER, gender: Gender = Gender.MALE, groupId: Long? = null): String? {
        val norm = AssignmentGenerator.normalizeName(name)
        if (norm.isBlank()) return "Informe o nome do irmão."
        if (data.brothers.any { AssignmentGenerator.normalizeName(it.name) == norm }) {
            return "Já existe um irmão cadastrado com esse nome."
        }
        val newBrother = Brother(
            id = AssignmentGenerator.nextId(),
            name = name.trim(),
            phone = phone.trim(),
            role = role,
            gender = gender,
            groupId = groupId
        )
        save(data.copy(brothers = (data.brothers + newBrother).sortedBy { AssignmentGenerator.normalizeName(it.name) }))
        return null
    }

    fun updateBrother(id: Long, name: String, phone: String, role: BrotherRole, gender: Gender = Gender.MALE, groupId: Long? = null): String? {
        val norm = AssignmentGenerator.normalizeName(name)
        if (norm.isBlank()) return "Informe o nome do irmão."
        if (data.brothers.any { it.id != id && AssignmentGenerator.normalizeName(it.name) == norm }) {
            return "Já existe outro irmão com esse nome."
        }
        save(data.copy(brothers = data.brothers.map {
            if (it.id == id) it.copy(name = name.trim(), phone = phone.trim(), role = role, gender = gender, groupId = groupId) else it
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

    fun addPrivilege(name: String, quantity: Int, minRole: BrotherRole = BrotherRole.PUBLISHER, maleOnly: Boolean = true): String? {
        val norm = AssignmentGenerator.normalizeName(name)
        if (norm.isBlank()) return "Informe o nome do privilégio."
        if (data.privileges.any { AssignmentGenerator.normalizeName(it.name) == norm }) {
            return "Já existe um privilégio com esse nome."
        }
        val p = Privilege(AssignmentGenerator.nextId(), name.trim(), quantity.coerceAtLeast(1), minRole = minRole, maleOnly = maleOnly)
        save(data.copy(privileges = (data.privileges + p).sortedBy { AssignmentGenerator.normalizeName(it.name) }))
        return null
    }

    fun updatePrivilege(id: Long, name: String, quantity: Int, minRole: BrotherRole, maleOnly: Boolean = true): String? {
        val norm = AssignmentGenerator.normalizeName(name)
        if (norm.isBlank()) return "Informe o nome do privilégio."
        if (data.privileges.any { it.id != id && AssignmentGenerator.normalizeName(it.name) == norm }) {
            return "Já existe outro privilégio com esse nome."
        }
        save(data.copy(privileges = data.privileges.map {
            if (it.id == id) it.copy(name = name.trim(), quantity = quantity.coerceAtLeast(1), minRole = minRole, maleOnly = maleOnly) else it
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
        val prefix = month.format(DateTimeFormatter.ofPattern("MM/yyyy"))
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
        val prefix = month.format(DateTimeFormatter.ofPattern("MM/yyyy"))
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

    fun addOrUpdatePublicTalk(talk: PublicTalk) {
        val existingIndex = data.publicTalks.indexOfFirst { it.id == talk.id }
        val updated = if (existingIndex >= 0) {
            data.publicTalks.toMutableList().apply { set(existingIndex, talk) }
        } else {
            data.publicTalks + talk
        }
        save(data.copy(publicTalks = updated.sortedByDescending { AssignmentGenerator.parseDate(it.date) }))
    }

    fun deletePublicTalk(id: Long) {
        save(data.copy(publicTalks = data.publicTalks.filterNot { it.id == id }))
    }

    fun addOrUpdateGroup(group: FieldServiceGroup) {
        val existingIndex = data.fieldServiceGroups.indexOfFirst { it.id == group.id }
        val updated = if (existingIndex >= 0) {
            data.fieldServiceGroups.toMutableList().apply { set(existingIndex, group) }
        } else {
            data.fieldServiceGroups + group
        }
        save(data.copy(fieldServiceGroups = updated.sortedBy { it.number }))
    }

    fun deleteGroup(id: Long) {
        save(data.copy(
            fieldServiceGroups = data.fieldServiceGroups.filterNot { it.id == id },
            brothers = data.brothers.map { if (it.groupId == id) it.copy(groupId = null) else it }
        ))
    }

    fun addOrUpdateCleaningSchedule(schedule: CleaningSchedule) {
        val existingIndex = data.cleaningSchedules.indexOfFirst { it.id == schedule.id }
        val updated = if (existingIndex >= 0) {
            data.cleaningSchedules.toMutableList().apply { set(existingIndex, schedule) }
        } else {
            data.cleaningSchedules + schedule
        }
        save(data.copy(cleaningSchedules = updated.sortedBy { AssignmentGenerator.parseDate(it.weekDate) }))
    }

    fun deleteCleaningSchedule(id: Long) {
        save(data.copy(cleaningSchedules = data.cleaningSchedules.filterNot { it.id == id }))
    }

    fun generateCleaningRotation(month: YearMonth) {
        if (data.fieldServiceGroups.isEmpty()) return
        val prefix = month.format(DateTimeFormatter.ofPattern("MM/yyyy"))
        val monthMeetings = data.meetings.filter { it.date.endsWith("/$prefix") }.sortedBy { AssignmentGenerator.parseDate(it.date) }
        if (monthMeetings.isEmpty()) return

        val sortedGroups = data.fieldServiceGroups.sortedBy { it.number }
        val newSchedules = monthMeetings.mapIndexed { index, m ->
            val group = sortedGroups[index % sortedGroups.size]
            CleaningSchedule(
                id = AssignmentGenerator.nextId(),
                weekDate = m.date,
                groupId = group.id,
                details = "Limpeza após ${m.type}",
                completed = false
            )
        }

        val keep = data.cleaningSchedules.filterNot { it.weekDate.endsWith("/$prefix") }
        save(data.copy(cleaningSchedules = keep + newSchedules))
    }

    fun setThemeMode(mode: ThemeMode) {
        save(data.copy(themeMode = mode))
    }

    fun exportHtmlReport(month: YearMonth): File {
        val prefix = month.format(DateTimeFormatter.ofPattern("MM/yyyy"))
        val monthMeetings = data.meetings.filter { it.date.endsWith("/$prefix") }
        val html = HtmlReportGenerator.generateHtml(month, monthMeetings, data.brothers, data.privileges)
        val dir = File(System.getProperty("user.home"), ".designacoes-jw/relatorios").apply { mkdirs() }
        val file = File(dir, "designacoes-${month.year}-${month.monthValue.toString().padStart(2, '0')}.html")
        file.writeText(html, Charsets.UTF_8)
        return file
    }

    fun exportIcsReport(month: YearMonth): File {
        val prefix = month.format(DateTimeFormatter.ofPattern("MM/yyyy"))
        val monthMeetings = data.meetings.filter { it.date.endsWith("/$prefix") }
        val ics = IcsExportHelper.generateIcs(monthMeetings, data.brothers, data.privileges)
        val dir = File(System.getProperty("user.home"), ".designacoes-jw/calendarios").apply { mkdirs() }
        val file = File(dir, "designacoes-${month.year}-${month.monthValue.toString().padStart(2, '0')}.ics")
        file.writeText(ics, Charsets.UTF_8)
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

    val isDark = when (c.data.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Designações JW $CURRENT_VERSION",
        state = rememberWindowState(width = 1280.dp, height = 800.dp)
    ) {
        MaterialTheme(
            colorScheme = if (isDark) darkColorScheme(
                primary = Color(0xFF90CAF9),
                onPrimary = Color(0xFF0D47A1),
                primaryContainer = Color(0xFF1E3A5F),
                onPrimaryContainer = Color(0xFFE3F2FD),
                secondary = Color(0xFF81D4FA),
                background = Color(0xFF121212),
                surface = Color(0xFF1E1E1E),
                surfaceVariant = Color(0xFF2C2C2C)
            ) else lightColorScheme(
                primary = Color(0xFF1565C0),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE3F2FD),
                onPrimaryContainer = Color(0xFF0D47A1),
                secondary = Color(0xFF0288D1),
                background = Color(0xFFF5F7FB),
                surface = Color.White
            )
        ) {
            DesktopApp(c, onShowUpdate = { updateInfo = it })
            if (!checkingUpdate && updateInfo != null) {
                UpdateDialog(
                    info = updateInfo!!,
                    onDismiss = { updateInfo = null }
                )
            }
        }
    }
}

@Composable
private fun UpdateDialog(info: WindowsUpdateInfo, onDismiss: () -> Unit) {
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

    AlertDialog(
        onDismissRequest = {
            if (updateState !is UpdateState.Downloading && updateState !is UpdateState.Installing) {
                onDismiss()
            }
        },
        icon = {
            when (updateState) {
                is UpdateState.Error -> Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                is UpdateState.Downloading, is UpdateState.Installing -> Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary)
                else -> Icon(Icons.Default.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
            }
        },
        title = {
            Text(
                when (updateState) {
                    is UpdateState.Downloading -> "Baixando Atualização..."
                    is UpdateState.Installing -> "Instalando..."
                    is UpdateState.Error -> "Falha na Atualização"
                    else -> "Nova Versão Disponível"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                when (val state = updateState) {
                    is UpdateState.Idle -> {
                        Text("Uma nova versão do Designações JW está disponível para instalação.")
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Versão instalada: v$CURRENT_VERSION", style = MaterialTheme.typography.bodySmall)
                                Text("Nova versão: v${info.version}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                if (info.sizeBytes > 0) {
                                    Text("Tamanho aproximado: ${WindowsUpdateManager.formatBytes(info.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Text(
                            "Ao clicar em \"Atualizar agora\", o aplicativo baixará o instalador oficial e iniciará a instalação automaticamente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is UpdateState.Downloading -> {
                        val progressFraction = if (state.totalBytes > 0) {
                            (state.bytesDownloaded.toFloat() / state.totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        if (state.totalBytes > 0) {
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier.fillMaxWidth().height(8.dp)
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(8.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${WindowsUpdateManager.formatBytes(state.bytesDownloaded)} / ${WindowsUpdateManager.formatBytes(state.totalBytes)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "${state.percent}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "Baixando pacote oficial de atualização... Por favor, aguarde.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is UpdateState.Installing -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text("Iniciando instalador... O aplicativo fechará automaticamente.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    is UpdateState.Error -> {
                        Text("Não foi possível concluir o download automático:", color = MaterialTheme.colorScheme.error)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                state.message,
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            "Você pode tentar novamente ou baixar diretamente pelo navegador:",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (updateState) {
                is UpdateState.Idle -> {
                    Button(
                        onClick = {
                            updateState = UpdateState.Downloading(0L, info.sizeBytes, 0)
                            WindowsUpdateManager.downloadAndInstall(
                                info = info,
                                onProgress = { downloaded, total, pct ->
                                    updateState = UpdateState.Downloading(downloaded, total, pct)
                                },
                                onInstalling = {
                                    updateState = UpdateState.Installing
                                },
                                onError = { msg ->
                                    updateState = UpdateState.Error(msg)
                                }
                            )
                        }
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Atualizar agora")
                    }
                }
                is UpdateState.Error -> {
                    Button(
                        onClick = {
                            updateState = UpdateState.Downloading(0L, info.sizeBytes, 0)
                            WindowsUpdateManager.downloadAndInstall(
                                info = info,
                                onProgress = { downloaded, total, pct ->
                                    updateState = UpdateState.Downloading(downloaded, total, pct)
                                },
                                onInstalling = {
                                    updateState = UpdateState.Installing
                                },
                                onError = { msg ->
                                    updateState = UpdateState.Error(msg)
                                }
                            )
                        }
                    ) {
                        Text("Tentar novamente")
                    }
                }
                else -> Unit
            }
        },
        dismissButton = {
            when (updateState) {
                is UpdateState.Idle -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { WindowsUpdateManager.openInBrowser(info.releasePageUrl.ifBlank { info.downloadUrl }) }) {
                            Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Abrir página")
                        }
                        TextButton(onClick = onDismiss) {
                            Text("Depois")
                        }
                    }
                }
                is UpdateState.Error -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { WindowsUpdateManager.openInBrowser(info.releasePageUrl.ifBlank { info.downloadUrl }) }) {
                            Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Baixar no Navegador")
                        }
                        TextButton(onClick = onDismiss) {
                            Text("Fechar")
                        }
                    }
                }
                else -> Unit
            }
        }
    )
}

@Composable
fun DesktopApp(c: StoreController, onShowUpdate: (WindowsUpdateInfo) -> Unit = {}) {
    var tab by remember { mutableIntStateOf(0) }
    val labels = listOf("Início", "Irmãos", "Privilégios", "Discursos", "Limpeza", "Histórico", "Configurações")
    val icons = listOf(
        Icons.Default.Home,
        Icons.Default.Groups,
        Icons.Default.Work,
        Icons.Default.RecordVoiceOver,
        Icons.Default.CleaningServices,
        Icons.Default.History,
        Icons.Default.Settings
    )

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
                CloudSyncBar(c)
                Spacer(Modifier.width(12.dp))
                Text("Versão $CURRENT_VERSION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(16.dp))
            when (tab) {
                0 -> Home(c)
                1 -> Brothers(c)
                2 -> Privileges(c)
                3 -> PublicTalksScreen(c)
                4 -> GroupsAndCleaningScreen(c)
                5 -> History(c)
                6 -> Settings(c, onShowUpdate = onShowUpdate)
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
    var viewMode by remember { mutableStateOf("LIST") } // "LIST" or "CALENDAR"
    var showEquityStats by remember { mutableStateOf(false) }
    var showDaysDialog by remember { mutableStateOf(false) }

    val allMeetings = c.data.meetings
    val prefix = month.format(DateTimeFormatter.ofPattern("MM/yyyy"))
    val meetings = allMeetings.filter { it.date.endsWith("/$prefix") }.sortedBy { AssignmentGenerator.parseDate(it.date) }
    val monthName = month.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR")).replaceFirstChar { it.uppercase() } + " " + month.year

    val nextMeetingInfo = remember(allMeetings) { getNextMeetingInfo(allMeetings) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Banner Inteligente da Próxima Reunião
        nextMeetingInfo?.let { (nextMeeting, daysUntil) ->
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f))
            ) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Event, null, tint = MaterialTheme.colorScheme.primary)
                            Text("Próxima Reunião", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Badge(
                                containerColor = if (daysUntil == 0L) MaterialTheme.colorScheme.error
                                else if (daysUntil == 1L) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    when (daysUntil) {
                                        0L -> "HOJE"
                                        1L -> "AMANHÃ"
                                        else -> "Em $daysUntil dias"
                                    },
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text("${nextMeeting.date} — ${nextMeeting.type}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        val names = nextMeeting.assignments.mapNotNull { a -> c.data.brothers.find { it.id == a.brotherId }?.name }
                        if (names.isNotEmpty()) {
                            Text("Designados: " + names.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Button(onClick = {
                        val missing = c.missingAssignments(nextMeeting)
                        val text = WhatsAppHelper.buildMeetingBroadcastMessage(
                            c.data.whatsappMeetingTemplate, nextMeeting, c.data.brothers, c.data.privileges, missing
                        )
                        val url = WhatsAppHelper.buildWebLink("", text)
                        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url))
                    }) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Avisar no WhatsApp")
                    }
                }
            }
        }

        // Cabeçalho de Navegação e Chips Informativos
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(onClick = { month = month.minusMonths(1); selectedMeetingId = null }) {
                        Text("‹ Mês anterior")
                    }
                    Text(monthName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    FilledTonalButton(onClick = { month = month.plusMonths(1); selectedMeetingId = null }) {
                        Text("Próximo mês ›")
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = { showDaysDialog = true },
                        label = { Text("Dias: ${dayName(c.data.firstDay).take(3)} e ${dayName(c.data.secondDay).take(3)}") },
                        leadingIcon = { Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp)) }
                    )
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("${c.data.brothers.count { it.active }} irmãos ativos") },
                        leadingIcon = { Icon(Icons.Default.Groups, null, modifier = Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("${c.data.privileges.count { it.active }} privilégios") },
                        leadingIcon = { Icon(Icons.Default.Work, null, modifier = Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("${meetings.size} reuniões") },
                        leadingIcon = { Icon(Icons.Default.Event, null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }

        // Barra de Ações Rápidas
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    Text("Imprimir / HTML")
                }
                OutlinedButton(
                    enabled = meetings.isNotEmpty(),
                    onClick = {
                        val file = c.exportIcsReport(month)
                        infoMessage = "Calendário iCal exportado para: ${file.absolutePath}"
                    }
                ) {
                    Icon(Icons.Default.CalendarToday, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Exportar (.ics)")
                }
                OutlinedButton(
                    enabled = meetings.isNotEmpty(),
                    onClick = { showEquityStats = true }
                ) {
                    Icon(Icons.Default.BarChart, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Equidade")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewMode = "LIST" }) {
                    Icon(
                        Icons.Default.ViewList,
                        contentDescription = "Lista",
                        tint = if (viewMode == "LIST") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
                IconButton(onClick = { viewMode = "CALENDAR" }) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = "Grade Calendário",
                        tint = if (viewMode == "CALENDAR") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        infoMessage?.let { msg ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    IconButton({ infoMessage = null }) { Icon(Icons.Default.Close, null) }
                }
            }
        }

        if (viewMode == "CALENDAR") {
            DesktopCalendarGrid(
                month = month,
                meetings = meetings,
                selectedMeetingId = selectedMeetingId,
                onSelectMeeting = { selectedMeetingId = if (selectedMeetingId == it) null else it }
            )

            val selectedMeeting = meetings.find { it.id == selectedMeetingId }
            if (selectedMeeting != null) {
                MeetingCardItem(
                    m = selectedMeeting,
                    isSelected = true,
                    c = c,
                    onToggleSelect = { selectedMeetingId = null },
                    onReplace = { target -> replaceTarget = target }
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(meetings) { m ->
                    MeetingCardItem(
                        m = m,
                        isSelected = m.id == selectedMeetingId,
                        c = c,
                        onToggleSelect = { selectedMeetingId = if (selectedMeetingId == m.id) null else m.id },
                        onReplace = { target -> replaceTarget = target }
                    )
                }
            }
        }
    }

    if (showDaysDialog) {
        DesktopMeetingDaysDialog(
            currentFirstDay = c.data.firstDay,
            currentSecondDay = c.data.secondDay,
            onSave = { d1, d2 -> c.setMeetingDays(d1, d2) },
            onDismiss = { showDaysDialog = false }
        )
    }

    if (showEquityStats) {
        DesktopEquityDialog(
            month = month,
            meetings = c.data.meetings,
            brothers = c.data.brothers,
            privileges = c.data.privileges,
            onDismiss = { showEquityStats = false }
        )
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
private fun DesktopMeetingDaysDialog(
    currentFirstDay: Int,
    currentSecondDay: Int,
    onSave: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var firstDay by remember { mutableIntStateOf(currentFirstDay) }
    var secondDay by remember { mutableIntStateOf(currentSecondDay) }
    var firstOpen by remember { mutableStateOf(false) }
    var secondOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                Text("Dias de Reunião da Congregação")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.width(420.dp)) {
                Text("Escolha os dois dias da semana em que ocorrem as reuniões.")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Reunião de Meio de Semana", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    Box {
                        OutlinedButton({ firstOpen = true }, Modifier.fillMaxWidth()) { Text(dayName(firstDay)) }
                        DropdownMenu(firstOpen, { firstOpen = false }) {
                            days.forEach { (d, l) ->
                                DropdownMenuItem(text = { Text(l) }, onClick = {
                                    if (d.value != secondDay) firstDay = d.value
                                    firstOpen = false
                                })
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Reunião de Fim de Semana", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    Box {
                        OutlinedButton({ secondOpen = true }, Modifier.fillMaxWidth()) { Text(dayName(secondDay)) }
                        DropdownMenu(secondOpen, { secondOpen = false }) {
                            days.forEach { (d, l) ->
                                DropdownMenuItem(text = { Text(l) }, onClick = {
                                    if (d.value != firstDay) secondDay = d.value
                                    secondOpen = false
                                })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(firstDay, secondDay); onDismiss() }) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun MeetingCardItem(
    m: Meeting,
    isSelected: Boolean,
    c: StoreController,
    onToggleSelect: () -> Unit,
    onReplace: (Triple<Long, Long, Long>) -> Unit
) {
    val missing = c.missingAssignments(m)
    var showQuickUnavailability by remember { mutableStateOf(false) }

    val dateParts = m.date.split("/")
    val dayNum = dateParts.getOrNull(0) ?: "--"
    val parsedDate = runCatching {
        LocalDate.of(dateParts[2].toInt(), dateParts[1].toInt(), dateParts[0].toInt())
    }.getOrNull()
    val dayOfWeekShort = parsedDate?.dayOfWeek?.getDisplayName(TextStyle.SHORT, Locale("pt", "BR"))?.uppercase() ?: "REU"

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Bloco Visual de Data
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = dayOfWeekShort.take(3),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = dayNum,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(m.type, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (missing.isEmpty()) {
                                Badge(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                                    Text(
                                        "✓ ${m.assignments.size} designações",
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            } else {
                                Badge(containerColor = MaterialTheme.colorScheme.errorContainer) {
                                    Text(
                                        "⚠ Faltam candidatos",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        val talk = c.data.publicTalks.find { it.date == m.date }
                        val clean = c.data.cleaningSchedules.find { it.weekDate == m.date }
                        val group = c.data.fieldServiceGroups.find { it.id == clean?.groupId }
                        val img = ImageExportHelper.generateMeetingCard(
                            meeting = m,
                            brothers = c.data.brothers,
                            privileges = c.data.privileges,
                            publicTalk = talk,
                            cleaningSchedule = clean,
                            cleaningGroup = group
                        )
                        ImageExportHelper.copyImageToClipboard(img)
                        val cardDir = File(System.getProperty("user.home"), ".designacoes-jw/cards").apply { mkdirs() }
                        val dateSafe = m.date.replace("/", "-")
                        ImageExportHelper.saveToPngFile(img, File(cardDir, "card-reuniao-$dateSafe.png"))
                    }) {
                        Icon(Icons.Default.Image, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Imagem PNG")
                    }

                    Button(onClick = {
                        val text = WhatsAppHelper.buildMeetingBroadcastMessage(
                            c.data.whatsappMeetingTemplate, m, c.data.brothers, c.data.privileges, missing
                        )
                        val url = WhatsAppHelper.buildWebLink("", text)
                        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url))
                    }) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("WhatsApp")
                    }
                    OutlinedButton(onClick = onToggleSelect) {
                        Text(if (isSelected) "Ocultar" else "Ver detalhes")
                    }
                }
            }

            if (missing.isNotEmpty()) {
                Text("⚠ Faltaram candidatos para: " + missing.joinToString { it.name }, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (isSelected) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    m.assignments.forEach { a ->
                        val p = c.data.privileges.find { it.id == a.privilegeId }
                        val b = c.data.brothers.find { it.id == a.brotherId }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(p?.name ?: "Privilégio", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    Text(b?.name ?: "Irmão", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (b?.phone?.isNotBlank() == true && p != null) {
                                        TextButton(onClick = {
                                            val text = WhatsAppHelper.buildSingleMessage(c.data.whatsappSingleTemplate, b, p, m)
                                            val url = WhatsAppHelper.buildWebLink(b.phone, text)
                                            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url))
                                        }) {
                                            Icon(Icons.Default.Share, null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Avisar")
                                        }
                                    }
                                    IconButton(onClick = { onReplace(Triple(m.id, a.privilegeId, a.brotherId)) }) {
                                        Icon(Icons.Default.SwapHoriz, "Trocar")
                                    }
                                }
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = { showQuickUnavailability = true }) {
                        Icon(Icons.Default.PersonOff, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Marcar irmão ausente nesta data")
                    }
                }
            }
        }
    }

    if (showQuickUnavailability) {
        DesktopQuickUnavailabilityDialog(
            meetingDate = m.date,
            brothers = c.data.brothers,
            onAdd = { bId, start, end, reason ->
                c.addUnavailability(bId, start, end, reason)
                showQuickUnavailability = false
            },
            onDismiss = { showQuickUnavailability = false }
        )
    }
}

@Composable
private fun DesktopQuickUnavailabilityDialog(
    meetingDate: String,
    brothers: List<Brother>,
    onAdd: (Long, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = brothers.filter { AssignmentGenerator.normalizeName(it.name).contains(AssignmentGenerator.normalizeName(search)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Marcar Ausência em $meetingDate") },
        text = {
            Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Selecione o irmão que não poderá participar desta reunião.")
                OutlinedTextField(search, { search = it }, label = { Text("Buscar irmão") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filtered) { b ->
                        val alreadyUnavailable = b.unavailabilities.any { u ->
                            u.startDate == meetingDate || (u.startDate <= meetingDate && u.endDate >= meetingDate)
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (!alreadyUnavailable) onAdd(b.id, meetingDate, meetingDate, "Ausente na reunião")
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = if (alreadyUnavailable) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(b.name, fontWeight = FontWeight.Medium)
                                if (alreadyUnavailable) {
                                    Badge(containerColor = MaterialTheme.colorScheme.errorContainer) { Text("Já ausente") }
                                } else {
                                    Text("Marcar ausente", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Concluir") } }
    )
}

@Composable
private fun DesktopCalendarGrid(
    month: YearMonth,
    meetings: List<Meeting>,
    selectedMeetingId: Long?,
    onSelectMeeting: (Long) -> Unit
) {
    val daysOfWeek = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
    val firstDayOfWeek = month.atDay(1).dayOfWeek.value % 7
    val daysInMonth = month.lengthOfMonth()
    val totalCells = firstDayOfWeek + daysInMonth
    val totalRows = (totalCells + 6) / 7

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                daysOfWeek.forEach { dayName ->
                    Text(
                        text = dayName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            for (row in 0 until totalRows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (col in 0..6) {
                        val cellIndex = row * 7 + col
                        val dayNumber = cellIndex - firstDayOfWeek + 1
                        if (dayNumber in 1..daysInMonth) {
                            val dayStr = dayNumber.toString().padStart(2, '0')
                            val meeting = meetings.find { it.date.startsWith("$dayStr/") }
                            val isSelected = meeting != null && meeting.id == selectedMeetingId

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(72.dp)
                                    .then(
                                        if (meeting != null) {
                                            Modifier
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.primaryContainer,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable { onSelectMeeting(meeting.id) }
                                        } else {
                                            Modifier.background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = dayNumber.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (meeting != null) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else if (meeting != null) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (meeting != null) {
                                        Text(
                                            text = "${meeting.assignments.size} designações",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.weight(1f).height(72.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopEquityDialog(
    month: YearMonth,
    meetings: List<Meeting>,
    brothers: List<Brother>,
    privileges: List<Privilege>,
    onDismiss: () -> Unit
) {
    val report = remember(month, meetings, brothers, privileges) {
        EquityStatisticsHelper.calculateMonthStats(month, meetings, brothers, privileges)
    }
    val monthName = month.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR")).replaceFirstChar { it.uppercase() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.BarChart, null, tint = MaterialTheme.colorScheme.primary)
                Text("Equidade e Estatísticas ($monthName ${month.year})", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            LazyColumn(Modifier.width(600.dp).heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${report.totalMeetings}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                Text("Reuniões", style = MaterialTheme.typography.labelMedium)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${report.totalAssignments}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                Text("Designações", style = MaterialTheme.typography.labelMedium)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val avg = if (report.ranking.isNotEmpty()) {
                                    String.format(Locale.US, "%.1f", report.totalAssignments.toFloat() / report.ranking.size)
                                } else "0.0"
                                Text(avg, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                Text("Média/Irmão", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                if (report.unassignedActiveBrothers.isNotEmpty()) {
                    item {
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "⚠ ${report.unassignedActiveBrothers.size} irmão(s) ativo(s) sem designação no mês:",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    report.unassignedActiveBrothers.joinToString(", ") { "${it.name} (${it.role.label})" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                item {
                    Text("Ranking de Participações", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                items(report.ranking) { item ->
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.count == 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.brother.name, fontWeight = FontWeight.Bold)
                                    Badge { Text(item.brother.role.label) }
                                }
                                Text("${item.count} vez(es)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            if (item.privilegesCount.isNotEmpty()) {
                                Text(
                                    item.privilegesCount.entries.joinToString(" • ") { "${it.key}: ${it.value}" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (report.privilegeTotals.isNotEmpty()) {
                    item {
                        Text("Totais por Privilégio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                report.privilegeTotals.forEach { (priv, count) ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(priv, style = MaterialTheme.typography.bodyMedium)
                                        Text("$count", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        }
    )
}

@Composable
private fun Brothers(c: StoreController) {
    var search by remember { mutableStateOf("") }
    var selectedRoleFilter by remember { mutableStateOf<BrotherRole?>(null) }
    var selectedBrotherForProfile by remember { mutableStateOf<Brother?>(null) }
    var showAddBrotherDialog by remember { mutableStateOf(false) }
    var editingBrother by remember { mutableStateOf<Brother?>(null) }
    var unavailBrother by remember { mutableStateOf<Brother?>(null) }
    var deletingBrother by remember { mutableStateOf<Brother?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val allBrothers = c.data.brothers
    val filtered = allBrothers.filter { b ->
        val matchesSearch = AssignmentGenerator.normalizeName(b.name).contains(AssignmentGenerator.normalizeName(search)) ||
            b.phone.contains(search.trim())
        val matchesRole = selectedRoleFilter == null || b.role == selectedRoleFilter
        matchesSearch && matchesRole
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Cabeçalho e Barra de Ações
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Irmãos Cadastrados", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Badge { Text("${allBrothers.count { it.active }} ativos / ${allBrothers.size} total") }
            }

            Button(onClick = { showAddBrotherDialog = true }) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Novo Irmão")
            }
        }

        // Barra de Busca e Filtros por Cargo
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Buscar por nome ou WhatsApp") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (search.isNotBlank()) {
                        IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, null) }
                    }
                },
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = selectedRoleFilter == null,
                    onClick = { selectedRoleFilter = null },
                    label = { Text("Todos (${allBrothers.size})") }
                )
                BrotherRole.values().forEach { r ->
                    val count = allBrothers.count { it.role == r }
                    FilterChip(
                        selected = selectedRoleFilter == r,
                        onClick = { selectedRoleFilter = if (selectedRoleFilter == r) null else r },
                        label = { Text("${r.label} ($count)") }
                    )
                }
            }
        }

        // Lista de Irmãos com Avatar
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (filtered.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Column(
                            Modifier.padding(32.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Nenhum irmão encontrado", fontWeight = FontWeight.Bold)
                            Text("Utilize o botão 'Novo Irmão' para cadastrar publicadores e servos.", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            items(filtered) { b ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { selectedBrotherForProfile = b },
                    colors = CardDefaults.cardColors(
                        containerColor = if (b.active) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    )
                ) {
                    Row(
                        Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            // Avatar Circular
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(getAvatarColor(b.name), shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(getInitials(b.name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(b.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Badge { Text(b.role.label) }
                                    if (!b.active) {
                                        Badge(containerColor = MaterialTheme.colorScheme.errorContainer) {
                                            Text("Inativo", color = MaterialTheme.colorScheme.onErrorContainer)
                                        }
                                    }
                                    if (b.unavailabilities.isNotEmpty()) {
                                        Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                                            Text("🏖 ${b.unavailabilities.size} ausência(s)")
                                        }
                                    }
                                }
                                Text(
                                    if (b.phone.isBlank()) "Sem WhatsApp informado" else "WhatsApp: ${b.phone}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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

    selectedBrotherForProfile?.let { b ->
        DesktopBrotherProfileDialog(
            brother = b,
            c = c,
            onDismiss = { selectedBrotherForProfile = null }
        )
    }

    if (showAddBrotherDialog) {
        DesktopAddBrotherDialog(
            groups = c.data.fieldServiceGroups,
            onSave = { n, p, r, g, gid ->
                errorMsg = c.addBrother(n, p, r, g, gid)
                if (errorMsg == null) showAddBrotherDialog = false
            },
            onDismiss = { showAddBrotherDialog = false }
        )
    }

    editingBrother?.let { b ->
        var editName by remember { mutableStateOf(b.name) }
        var editPhone by remember { mutableStateOf(b.phone) }
        var editRole by remember { mutableStateOf(b.role) }
        var editGender by remember { mutableStateOf(b.gender) }
        var editGroupId by remember { mutableStateOf(b.groupId) }
        var roleDropdown by remember { mutableStateOf(false) }
        var groupDropdown by remember { mutableStateOf(false) }

        val selectedGroup = c.data.fieldServiceGroups.firstOrNull { it.id == editGroupId }

        AlertDialog(
            onDismissRequest = { editingBrother = null },
            title = { Text(if (editGender == Gender.FEMALE) "Editar irmã" else "Editar irmão") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(420.dp)) {
                    OutlinedTextField(editName, { editName = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(editPhone, { editPhone = it }, label = { Text("WhatsApp") }, modifier = Modifier.fillMaxWidth())

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Gênero:", fontWeight = FontWeight.SemiBold, modifier = Modifier.width(70.dp))
                        FilterChip(
                            selected = editGender == Gender.MALE,
                            onClick = { editGender = Gender.MALE },
                            label = { Text("Irmão") }
                        )
                        FilterChip(
                            selected = editGender == Gender.FEMALE,
                            onClick = { editGender = Gender.FEMALE },
                            label = { Text("Irmã") }
                        )
                    }

                    if (editGender == Gender.MALE) {
                        Box {
                            OutlinedButton({ roleDropdown = true }, Modifier.fillMaxWidth()) { Text("Cargo: " + editRole.label) }
                            DropdownMenu(roleDropdown, { roleDropdown = false }) {
                                BrotherRole.values().forEach { r ->
                                    DropdownMenuItem(text = { Text(r.label) }, onClick = { editRole = r; roleDropdown = false })
                                }
                            }
                        }
                    }

                    if (c.data.fieldServiceGroups.isNotEmpty()) {
                        Box {
                            OutlinedButton({ groupDropdown = true }, Modifier.fillMaxWidth()) {
                                Text(if (selectedGroup != null) "Grupo: ${selectedGroup.name}" else "Grupo de Campo: (Nenhum)")
                            }
                            DropdownMenu(groupDropdown, { groupDropdown = false }) {
                                DropdownMenuItem(text = { Text("Nenhum") }, onClick = { editGroupId = null; groupDropdown = false })
                                c.data.fieldServiceGroups.forEach { g ->
                                    DropdownMenuItem(text = { Text(g.name) }, onClick = { editGroupId = g.id; groupDropdown = false })
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    errorMsg = c.updateBrother(
                        id = b.id,
                        name = editName,
                        phone = editPhone,
                        role = if (editGender == Gender.FEMALE) BrotherRole.PUBLISHER else editRole,
                        gender = editGender,
                        groupId = editGroupId
                    )
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(420.dp)) {
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
private fun DesktopBrotherProfileDialog(
    brother: Brother,
    c: StoreController,
    onDismiss: () -> Unit
) {
    val brotherMeetings = c.data.meetings.filter { m ->
        m.assignments.any { it.brotherId == brother.id }
    }.sortedByDescending { AssignmentGenerator.parseDate(it.date) }

    val totalAssignments = brotherMeetings.sumOf { m ->
        m.assignments.count { it.brotherId == brother.id }
    }

    val privilegeCounts = mutableMapOf<String, Int>()
    brotherMeetings.forEach { m ->
        m.assignments.filter { it.brotherId == brother.id }.forEach { a ->
            val pName = c.data.privileges.find { it.id == a.privilegeId }?.name ?: "Privilégio"
            privilegeCounts[pName] = (privilegeCounts[pName] ?: 0) + 1
        }
    }

    val lastMeeting = brotherMeetings.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(50.dp).background(getAvatarColor(brother.name), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(getInitials(brother.name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Column {
                    Text(brother.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Badge { Text(brother.role.label) }
                        if (brother.active) {
                            Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) { Text("Ativo") }
                        } else {
                            Badge(containerColor = MaterialTheme.colorScheme.errorContainer) { Text("Inativo") }
                        }
                    }
                }
            }
        },
        text = {
            LazyColumn(modifier = Modifier.width(480.dp).heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                        Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$totalAssignments", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
                                Text("Designações realizadas", style = MaterialTheme.typography.labelSmall)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${brotherMeetings.size}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
                                Text("Reuniões", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                lastMeeting?.let { m ->
                    val myAssignment = m.assignments.find { it.brotherId == brother.id }
                    val privName = c.data.privileges.find { it.id == myAssignment?.privilegeId }?.name ?: "Designação"
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))) {
                            Column(Modifier.padding(10.dp)) {
                                Text("Última participação:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                Text("${m.date} — $privName (${m.type})", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                if (privilegeCounts.isNotEmpty()) {
                    item {
                        Text("Frequência por Privilégio:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                    items(privilegeCounts.entries.toList()) { (priv, count) ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(priv, style = MaterialTheme.typography.bodyMedium)
                                Text("$count vez(es)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                if (brother.phone.isNotBlank()) {
                    item {
                        OutlinedButton(
                            onClick = {
                                val url = WhatsAppHelper.buildWebLink(brother.phone, "Olá irmão ${brother.name}!")
                                if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Abrir conversa no WhatsApp Web")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
private fun DesktopAddBrotherDialog(
    groups: List<FieldServiceGroup>,
    onSave: (String, String, BrotherRole, Gender, Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(BrotherRole.PUBLISHER) }
    var gender by remember { mutableStateOf(Gender.MALE) }
    var groupId by remember { mutableStateOf<Long?>(null) }
    var roleDropdown by remember { mutableStateOf(false) }
    var groupDropdown by remember { mutableStateOf(false) }

    val selectedGroup = groups.firstOrNull { it.id == groupId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar Novo Publicador") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(420.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome completo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("WhatsApp (com DDD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Gênero:", fontWeight = FontWeight.SemiBold, modifier = Modifier.width(70.dp))
                    FilterChip(
                        selected = gender == Gender.MALE,
                        onClick = { gender = Gender.MALE },
                        label = { Text("Irmão (Masculino)") }
                    )
                    FilterChip(
                        selected = gender == Gender.FEMALE,
                        onClick = { gender = Gender.FEMALE },
                        label = { Text("Irmã (Feminino)") }
                    )
                }

                if (gender == Gender.MALE) {
                    Box {
                        OutlinedButton({ roleDropdown = true }, Modifier.fillMaxWidth()) { Text("Privilégio/Cargo: " + role.label) }
                        DropdownMenu(roleDropdown, { roleDropdown = false }) {
                            BrotherRole.values().forEach { r ->
                                DropdownMenuItem(text = { Text(r.label) }, onClick = { role = r; roleDropdown = false })
                            }
                        }
                    }
                }

                if (groups.isNotEmpty()) {
                    Box {
                        OutlinedButton({ groupDropdown = true }, Modifier.fillMaxWidth()) {
                            Text(if (selectedGroup != null) "Grupo: ${selectedGroup.name}" else "Grupo de Campo: (Nenhum)")
                        }
                        DropdownMenu(groupDropdown, { groupDropdown = false }) {
                            DropdownMenuItem(text = { Text("Nenhum") }, onClick = { groupId = null; groupDropdown = false })
                            groups.forEach { g ->
                                DropdownMenuItem(text = { Text(g.name) }, onClick = { groupId = g.id; groupDropdown = false })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = { onSave(name, phone, if (gender == Gender.FEMALE) BrotherRole.PUBLISHER else role, gender, groupId) }
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun Privileges(c: StoreController) {
    var searchPrivilege by remember { mutableStateOf("") }
    var showAddPrivilegeDialog by remember { mutableStateOf(false) }
    var manageBrothersPrivilege by remember { mutableStateOf<Privilege?>(null) }
    var editingPrivilege by remember { mutableStateOf<Privilege?>(null) }
    var deletingPrivilege by remember { mutableStateOf<Privilege?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val filteredPrivileges = c.data.privileges.filter {
        AssignmentGenerator.normalizeName(it.name).contains(AssignmentGenerator.normalizeName(searchPrivilege))
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Cabeçalho e Botão Adicionar
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Privilégios e Autorizações", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Badge { Text("${c.data.privileges.count { it.active }} ativos / ${c.data.privileges.size} total") }
            }

            Button(onClick = { showAddPrivilegeDialog = true }) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Novo Privilégio")
            }
        }

        // Barra de Busca
        OutlinedTextField(
            value = searchPrivilege,
            onValueChange = { searchPrivilege = it },
            label = { Text("Buscar privilégio") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (searchPrivilege.isNotBlank()) {
                    IconButton(onClick = { searchPrivilege = "" }) { Icon(Icons.Default.Close, null) }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Lista de Privilégios
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filteredPrivileges) { p ->
                val isBook = AssignmentGenerator.isBookReaderPrivilege(p)
                val isSentinel = AssignmentGenerator.isSentinelReaderPrivilege(p)

                val authorizedCount = c.data.brothers.count { b ->
                    val direct = p.id in b.privileges
                    val inherited = !direct && isBook && c.data.privileges.any {
                        AssignmentGenerator.isSentinelReaderPrivilege(it) && it.id in b.privileges
                    }
                    direct || inherited
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Quantidade: ${p.quantity} irmão(s) • Cargo mínimo: ${p.minRole.label} • ${if (p.active) "Ativo" else "Inativo"}")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton({ c.togglePrivilegeActive(p.id) }) { Text(if (p.active) "Desativar" else "Ativar") }
                                IconButton({ editingPrivilege = p }) { Icon(Icons.Default.Edit, "Editar") }
                                IconButton({ deletingPrivilege = p }) { Icon(Icons.Default.Delete, "Excluir") }
                            }
                        }

                        // Destaque Teocrático Inteligente
                        if (isBook) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))) {
                                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                                    Column {
                                        Text("📖 Reunião de Meio de Semana (Quarta-feira)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                        Text("Irmãos que são Leitores da Sentinela se qualificam automaticamente para ler o Livro.", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        } else if (isSentinel) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f))) {
                                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Article, null, tint = MaterialTheme.colorScheme.secondary)
                                    Column {
                                        Text("📰 Reunião de Fim de Semana (Sábado/Domingo)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                        Text("Qualifica automaticamente o irmão para a leitura do Livro de meio de semana.", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        // Dias permitidos
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Dias permitidos:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
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

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Botão de Gerenciamento de Irmãos Autorizados
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "✓ $authorizedCount irmão(s) autorizados",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Button(onClick = { manageBrothersPrivilege = p }) {
                                Icon(Icons.Default.Groups, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Gerenciar Autorizações")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddPrivilegeDialog) {
        DesktopAddPrivilegeDialog(
            onSave = { n, q, r ->
                errorMsg = c.addPrivilege(n, q, r)
                if (errorMsg == null) showAddPrivilegeDialog = false
            },
            onDismiss = { showAddPrivilegeDialog = false }
        )
    }

    manageBrothersPrivilege?.let { p ->
        DesktopManagePrivilegeBrothersDialog(
            c = c,
            privilege = p,
            onDismiss = { manageBrothersPrivilege = null }
        )
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(400.dp)) {
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
private fun DesktopAddPrivilegeDialog(
    onSave: (String, Int, BrotherRole) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var minRole by remember { mutableStateOf(BrotherRole.PUBLISHER) }
    var roleDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo Privilégio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(400.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome do privilégio") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit) }, label = { Text("Quantidade necessária") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedButton({ roleDropdown = true }, Modifier.fillMaxWidth()) { Text("Cargo mínimo: " + minRole.label) }
                    DropdownMenu(roleDropdown, { roleDropdown = false }) {
                        BrotherRole.values().forEach { r ->
                            DropdownMenuItem(text = { Text(r.label) }, onClick = { minRole = r; roleDropdown = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = { onSave(name, quantity.toIntOrNull() ?: 1, minRole) }
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun DesktopManagePrivilegeBrothersDialog(
    c: StoreController,
    privilege: Privilege,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val isBook = AssignmentGenerator.isBookReaderPrivilege(privilege)
    val sentinelPrivilege = c.data.privileges.find { AssignmentGenerator.isSentinelReaderPrivilege(it) }

    val filteredBrothers = c.data.brothers.filter {
        AssignmentGenerator.normalizeName(it.name).contains(AssignmentGenerator.normalizeName(search))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Autorizações — ${privilege.name}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Selecione os irmãos aptos para receber essa designação.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        },
        text = {
            Column(Modifier.width(480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Buscar irmão") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isBook) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))) {
                        Text(
                            "Nota teocrática: Leitores da Sentinela já são qualificados automaticamente para o Livro.",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredBrothers) { brother ->
                        val direct = privilege.id in brother.privileges
                        val inherited = !direct && isBook && sentinelPrivilege != null && sentinelPrivilege.id in brother.privileges

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !inherited) {
                                    c.toggleBrotherPrivilege(brother.id, privilege.id)
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (direct || inherited) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(brother.name, fontWeight = FontWeight.Medium)
                                    if (inherited) {
                                        Text(
                                            "Autorizado automaticamente (Leitor da Sentinela)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Text(brother.role.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                                Checkbox(
                                    checked = direct || inherited,
                                    onCheckedChange = {
                                        if (!inherited) c.toggleBrotherPrivilege(brother.id, privilege.id)
                                    },
                                    enabled = !inherited
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Concluir") } }
    )
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
private fun Settings(c: StoreController, onShowUpdate: (WindowsUpdateInfo) -> Unit = {}) {
    var first by remember { mutableIntStateOf(c.data.firstDay) }
    var second by remember { mutableIntStateOf(c.data.secondDay) }
    var singleTmpl by remember { mutableStateOf(c.data.whatsappSingleTemplate.ifBlank { WhatsAppHelper.DEFAULT_SINGLE_TEMPLATE }) }
    var meetingTmpl by remember { mutableStateOf(c.data.whatsappMeetingTemplate.ifBlank { WhatsAppHelper.DEFAULT_MEETING_TEMPLATE }) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var isCheckingUpdateManual by remember { mutableStateOf(false) }
    var manualUpdateFeedback by remember { mutableStateOf<String?>(null) }

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
                    Text("Aparência e Tema", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ThemeMode.values().forEach { mode ->
                            FilterChip(
                                selected = c.data.themeMode == mode,
                                onClick = { c.setThemeMode(mode) },
                                label = { Text(mode.label) },
                                leadingIcon = if (c.data.themeMode == mode) {
                                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
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
                    Text("Regras teocráticas de leitores", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("• Leitor do Livro (EBC): Atua exclusivamente nas reuniões de meio de semana (quarta-feira). Irmãos qualificados como Leitor da Sentinela podem ler o Livro automaticamente.")
                    Text("• Leitor da Sentinela: Atua nas reuniões de fim de semana (sábado/domingo). Irmãos habilitados apenas para o Livro nunca leem a Sentinela.")
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Atualizações e Armazenamento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Armazenamento local do Windows: ${c.file.absolutePath}", style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Versão instalada: $CURRENT_VERSION", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        if (isCheckingUpdateManual) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("Verificando...", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            OutlinedButton(onClick = {
                                isCheckingUpdateManual = true
                                manualUpdateFeedback = null
                                kotlin.concurrent.thread(isDaemon = true) {
                                    val info = WindowsUpdateManager.checkForUpdate()
                                    isCheckingUpdateManual = false
                                    if (info != null) {
                                        onShowUpdate(info)
                                    } else {
                                        manualUpdateFeedback = "Você já está utilizando a versão mais recente ($CURRENT_VERSION)."
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Verificar atualizações")
                            }
                        }
                    }
                    manualUpdateFeedback?.let { feedback ->
                        Text(feedback, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
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
