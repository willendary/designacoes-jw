package br.com.willendary.designacoesjw

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.willendary.designacoesjw.data.*
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import br.com.willendary.designacoesjw.notification.MeetingReminderHelper
import br.com.willendary.designacoesjw.stats.EquityStatisticsHelper
import br.com.willendary.designacoesjw.util.WhatsAppHelper
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val weekdays = listOf(
    DayOfWeek.MONDAY to "Segunda-feira",
    DayOfWeek.TUESDAY to "Terça-feira",
    DayOfWeek.WEDNESDAY to "Quarta-feira",
    DayOfWeek.THURSDAY to "Quinta-feira",
    DayOfWeek.FRIDAY to "Sexta-feira",
    DayOfWeek.SATURDAY to "Sábado",
    DayOfWeek.SUNDAY to "Domingo"
)

private fun dayLabel(value: Int): String =
    weekdays.firstOrNull { it.first.value == value }?.second ?: "—"

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

private fun shareText(context: Context, title: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, title))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(
    vm: AppViewModel = viewModel(),
    themeIndex: Int = 0,
    onThemeChange: (Int) -> Unit = {},
    onSignOut: () -> Unit = {}
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

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var tab by remember { mutableIntStateOf(3) }

    val currentTitle = when (tab) {
        0 -> "Configurações"
        1 -> "Histórico de Reuniões"
        2 -> "Irmãos & Irmãs"
        3 -> "Quadro de Reuniões"
        4 -> "Privilégios"
        5 -> "Usuários & Acesso"
        11 -> "Discursos Públicos"
        12 -> "Grupos & Limpeza"
        13 -> "Relatório de Impressão A4"
        14 -> "Férias & Ausências"
        15 -> "Estatísticas de Equidade"
        else -> "Designações JW"
    }

    if (tab == 10) {
        br.com.willendary.designacoesjw.screens.KioskScreen(vm, onClose = { tab = 3 })
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(300.dp),
                    drawerContainerColor = MaterialTheme.colorScheme.surface
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        // Cabeçalho do Menu
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_logo),
                                            contentDescription = "Logo",
                                            modifier = Modifier.size(44.dp).clip(CircleShape)
                                        )
                                        Column {
                                            Text(
                                                "Designações JW",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                "Quadro Teocrático",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                            )
                                        }
                                    }

                                    vm.currentUserProfile.value?.let { profile ->
                                        Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                profile.name.ifBlank { profile.email },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    profile.email,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                                )
                                                if (profile.role == "admin") {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.primary
                                                    ) {
                                                        Text(
                                                            "Admin",
                                                            color = MaterialTheme.colorScheme.onPrimary,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        // Seção 1: REUNIÕES & ESCALAS
                        item {
                            Text(
                                "REUNIÕES & ESCALAS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                        item {
                            NavigationDrawerItem(
                                label = { Text("Início (Quadro Semanal)") },
                                icon = { Icon(Icons.Filled.Home, null) },
                                selected = tab == 3,
                                onClick = { tab = 3; scope.launch { drawerState.close() } },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }
                        item {
                            NavigationDrawerItem(
                                label = { Text("Histórico de Reuniões") },
                                icon = { Icon(Icons.Filled.History, null) },
                                selected = tab == 1,
                                onClick = { tab = 1; scope.launch { drawerState.close() } },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }
                        item {
                            NavigationDrawerItem(
                                label = { Text("Modo Telão (Kiosk)") },
                                icon = { Icon(Icons.Filled.Tv, null) },
                                badge = {
                                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                        Text("TV", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp))
                                    }
                                },
                                selected = false,
                                onClick = { tab = 10; scope.launch { drawerState.close() } },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }

                        // Seção 2: PROGRAMAÇÃO ESPECIAL
                        item {
                            Spacer(Modifier.height(8.dp))
                            Divider(modifier = Modifier.padding(horizontal = 16.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "PROGRAMAÇÃO ESPECIAL",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                        item {
                            NavigationDrawerItem(
                                label = { Text("Discursos Públicos") },
                                icon = { Icon(Icons.Filled.RecordVoiceOver, null) },
                                selected = tab == 11,
                                onClick = { tab = 11; scope.launch { drawerState.close() } },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }
                        item {
                            NavigationDrawerItem(
                                label = { Text("Grupos & Limpeza") },
                                icon = { Icon(Icons.Filled.CleaningServices, null) },
                                selected = tab == 12,
                                onClick = { tab = 12; scope.launch { drawerState.close() } },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }
                        item {
                            NavigationDrawerItem(
                                label = { Text("Relatório Diagramado A4") },
                                icon = { Icon(Icons.Filled.Print, null) },
                                selected = tab == 13,
                                onClick = { tab = 13; scope.launch { drawerState.close() } },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }

                        // Seção 3: PESSOAS & EQUIDADE
                        item {
                            Spacer(Modifier.height(8.dp))
                            Divider(modifier = Modifier.padding(horizontal = 16.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "PESSOAS & EQUIDADE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                        item {
                            NavigationDrawerItem(
                                label = { Text("Irmãos & Irmãs") },
                                icon = { Icon(Icons.Filled.Groups, null) },
                                selected = tab == 2,
                                onClick = { tab = 2; scope.launch { drawerState.close() } },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }
                        item {
                            NavigationDrawerItem(
                                label = { Text("Férias & Ausências") },
                                icon = { Icon(Icons.Filled.EventBusy, null) },
                                selected = tab == 14,
                                onClick = { tab = 14; scope.launch { drawerState.close() } },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }
                        item {
                            NavigationDrawerItem(
                                label = { Text("Privilégios da Reunião") },
                                icon = { Icon(Icons.Filled.Work, null) },
                                selected = tab == 4,
                                onClick = { tab = 4; scope.launch { drawerState.close() } },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }
                        item {
                            NavigationDrawerItem(
                                label = { Text("Estatísticas de Equidade") },
                                icon = { Icon(Icons.Filled.BarChart, null) },
                                selected = tab == 15,
                                onClick = { tab = 15; scope.launch { drawerState.close() } },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }

                        // Seção 4: ADMINISTRAÇÃO
                        item {
                            Spacer(Modifier.height(8.dp))
                            Divider(modifier = Modifier.padding(horizontal = 16.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "ADMINISTRAÇÃO",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                        item {
                            NavigationDrawerItem(
                                label = { Text("Configurações") },
                                icon = { Icon(Icons.Filled.Settings, null) },
                                selected = tab == 0,
                                onClick = { tab = 0; scope.launch { drawerState.close() } },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }
                        if (vm.can(AppPermissions.MANAGE_USERS)) {
                            item {
                                NavigationDrawerItem(
                                    label = { Text("Usuários & Acesso") },
                                    icon = { Icon(Icons.Filled.AdminPanelSettings, null) },
                                    selected = tab == 5,
                                    onClick = { tab = 5; scope.launch { drawerState.close() } },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Rodapé do Drawer
                        item {
                            Spacer(Modifier.height(16.dp))
                            Divider(modifier = Modifier.padding(horizontal = 16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = onSignOut) {
                                    Icon(Icons.Filled.Logout, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Sair", color = MaterialTheme.colorScheme.error)
                                }
                                Text("v0.2.7", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(currentTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("Designações JW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menu lateral")
                            }
                        },
                        actions = {
                            IconButton(onClick = { tab = 10 }) {
                                Icon(Icons.Filled.Tv, contentDescription = "Modo Telão")
                            }
                        }
                    )
                }
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    when (tab) {
                        0 -> SettingsScreen(vm, themeIndex, onThemeChange, onSignOut)
                        1 -> HistoryScreen(vm)
                        2 -> BrothersScreen(vm)
                        3 -> HomeScreen(vm)
                        4 -> PrivilegesScreen(vm)
                        5 -> UserManagementScreen(vm)
                        11 -> br.com.willendary.designacoesjw.screens.PublicTalksAndroidScreen(vm)
                        12 -> br.com.willendary.designacoesjw.screens.GroupsAndCleaningAndroidScreen(vm)
                        13 -> br.com.willendary.designacoesjw.screens.PrintReportScreen(vm)
                        14 -> br.com.willendary.designacoesjw.screens.UnavailabilityScreen(vm)
                        15 -> br.com.willendary.designacoesjw.screens.EquityStatisticsScreen(vm)
                    }
                }
            }
        }
    }

    availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { if (!updating) availableUpdate = null },
            title = { Text("Atualização disponível") },
            text = {
                Text(
                    if (updating) "Baixando a versão " + update.versionName + "..."
                    else "A versão " + update.versionName + " do Designações JW está disponível."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !updating,
                    onClick = {
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
                    }
                ) { Text(if (updating) "Baixando..." else "Atualizar") }
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
    var viewMode by remember { mutableStateOf("LIST") } // "LIST" or "CALENDAR"
    var showEquityDialog by remember { mutableStateOf(false) }
    var showMeetingDaysDialog by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }

    val allMeetings = vm.meetings.value
    val monthMeetings = allMeetings.filter {
        runCatching {
            val p = it.date.split("/")
            p.size == 3 && p[1].toInt() == month.monthValue && p[2].toInt() == month.year
        }.getOrDefault(false)
    }.sortedBy { it.date }

    val nextMeetingInfo = remember(allMeetings) { getNextMeetingInfo(allMeetings) }

    val monthName = month.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
        .replaceFirstChar { it.uppercase() }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        // --- 0. BANNER INTELIGENTE: PRÓXIMA REUNIÃO DA CONGREGAÇÃO ---
        nextMeetingInfo?.let { (nextMeeting, daysUntil) ->
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f))
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Próxima Reunião", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
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

                        Text(
                            "${nextMeeting.date} — ${nextMeeting.type}",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge
                        )

                        // Resumo dos irmãos designados
                        val brotherNames = nextMeeting.assignments.mapNotNull { a ->
                            vm.brothers.value.find { it.id == a.brotherId }?.name
                        }.take(4)

                        if (brotherNames.isNotEmpty()) {
                            Text(
                                "Designados: " + brotherNames.joinToString(", ") + if (nextMeeting.assignments.size > 4) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(
                                onClick = {
                                    val missing = vm.missingAssignments(nextMeeting)
                                    val msg = WhatsAppHelper.buildMeetingBroadcastMessage(
                                        null, nextMeeting, vm.brothers.value, vm.privileges.value, missing
                                    )
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WhatsAppHelper.buildUniversalLink("", msg))))
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Avisar no WhatsApp", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        // --- 1. CABEÇALHO DO MÊS & NAVEGAÇÃO COMPACTA ---
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Seletor de Mês
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = { month = month.minusMonths(1); selectedMeetingId = null },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "Mês anterior")
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$monthName ${month.year}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        FilledTonalIconButton(
                            onClick = { month = month.plusMonths(1); selectedMeetingId = null },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Próximo mês")
                        }
                    }

                    // Chips de Status Rápido & Configuração de Dias
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (vm.can(AppPermissions.MANAGE_SETTINGS)) {
                            AssistChip(
                                onClick = { showMeetingDaysDialog = true },
                                label = { Text("Dias: ${dayLabel(vm.schedule.value.firstDay).take(3)} e ${dayLabel(vm.schedule.value.secondDay).take(3)}") },
                                leadingIcon = { Icon(Icons.Filled.CalendarMonth, null, modifier = Modifier.size(16.dp)) },
                                trailingIcon = { Icon(Icons.Filled.Edit, null, modifier = Modifier.size(14.dp)) }
                            )
                        } else {
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = { Text("Dias: ${dayLabel(vm.schedule.value.firstDay).take(3)} e ${dayLabel(vm.schedule.value.secondDay).take(3)}") }
                            )
                        }

                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("${vm.brothers.value.count { it.active }} irmãos ativos") },
                            leadingIcon = { Icon(Icons.Filled.Groups, null, modifier = Modifier.size(16.dp)) }
                        )

                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("${vm.privileges.value.count { it.active }} privilégios") },
                            leadingIcon = { Icon(Icons.Filled.Work, null, modifier = Modifier.size(16.dp)) }
                        )

                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("${monthMeetings.size} reuniões") },
                            leadingIcon = { Icon(Icons.Filled.Event, null, modifier = Modifier.size(16.dp)) }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Barra de Ações Rápidas
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botão Primário de Geração
                        Button(
                            enabled = vm.can(AppPermissions.GENERATE_ASSIGNMENTS),
                            onClick = {
                                if (monthMeetings.isNotEmpty()) showRegenerateConfirm = true
                                else selectedMeetingId = vm.generateMonth(month).firstOrNull()?.id
                            },
                            modifier = Modifier.weight(1.5f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (monthMeetings.isEmpty()) "Gerar Escala" else "Regenerar",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        // Botão Equidade
                        OutlinedButton(
                            onClick = { showEquityDialog = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Filled.BarChart, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Equidade", style = MaterialTheme.typography.labelMedium)
                        }

                        // Menu Exportar
                        Box {
                            OutlinedButton(
                                enabled = monthMeetings.isNotEmpty() && vm.can(AppPermissions.EXPORT_REPORTS),
                                onClick = { showExportMenu = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Exportar", style = MaterialTheme.typography.labelMedium)
                            }
                            DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Compartilhar PDF") },
                                    leadingIcon = { Icon(Icons.Filled.PictureAsPdf, null) },
                                    onClick = {
                                        showExportMenu = false
                                        ReportGenerator.sharePdf(context, month, monthMeetings, vm.brothers.value, vm.privileges.value)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Compartilhar Word (.docx)") },
                                    leadingIcon = { Icon(Icons.Filled.Description, null) },
                                    onClick = {
                                        showExportMenu = false
                                        ReportGenerator.shareDocx(context, month, monthMeetings, vm.brothers.value, vm.privileges.value)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Exportar iCal (.ics)") },
                                    leadingIcon = { Icon(Icons.Filled.CalendarMonth, null) },
                                    onClick = {
                                        showExportMenu = false
                                        ReportGenerator.shareIcs(context, month, monthMeetings, vm.brothers.value, vm.privileges.value)
                                    }
                                )
                            }
                        }

                        // Alternador de Visualização
                        IconButton(
                            onClick = { viewMode = if (viewMode == "LIST") "CALENDAR" else "LIST" },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                if (viewMode == "LIST") Icons.Filled.CalendarMonth else Icons.Filled.ViewList,
                                contentDescription = "Alternar modo de exibição",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // --- 2. CONTEÚDO PRINCIPAL (LISTA DE REUNIÕES OU GRADE) ---
        if (monthMeetings.isNotEmpty()) {
            if (viewMode == "CALENDAR") {
                item {
                    CalendarGridView(
                        month = month,
                        meetings = monthMeetings,
                        selectedMeetingId = selectedMeetingId,
                        onSelectMeeting = { selectedMeetingId = if (selectedMeetingId == it) null else it }
                    )
                }
                monthMeetings.find { it.id == selectedMeetingId }?.let { selected ->
                    item { MeetingResult(vm, selected, context) {} }
                }
            } else {
                items(monthMeetings, key = { it.id }) { meeting ->
                    MeetingCardView(
                        vm = vm,
                        meeting = meeting,
                        isExpanded = meeting.id == selectedMeetingId,
                        onToggleExpand = {
                            selectedMeetingId = if (selectedMeetingId == meeting.id) null else meeting.id
                        }
                    )
                }
            }
        } else {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    Column(
                        Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Event,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            "Nenhuma designação gerada para $monthName",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Toque no botão 'Gerar Escala' acima para criar a escala automática do mês com base nos privilégios e disponibilidade.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    // Diálogos Modais da Home
    if (showRegenerateConfirm) {
        AlertDialog(
            onDismissRequest = { showRegenerateConfirm = false },
            title = { Text("Regenerar escala do mês?") },
            text = { Text("As ${monthMeetings.size} reuniões de ${monthName} serão recalculadas e substituídas de acordo com as regras atuais.") },
            confirmButton = {
                Button({
                    vm.deleteMonth(month)
                    selectedMeetingId = vm.generateMonth(month).firstOrNull()?.id
                    showRegenerateConfirm = false
                }) { Text("Regenerar") }
            },
            dismissButton = { TextButton({ showRegenerateConfirm = false }) { Text("Cancelar") } }
        )
    }

    if (showMeetingDaysDialog) {
        MeetingDaysDialog(
            currentFirstDay = vm.schedule.value.firstDay,
            currentSecondDay = vm.schedule.value.secondDay,
            onSave = { d1, d2 -> vm.setMeetingDays(d1, d2) },
            onDismiss = { showMeetingDaysDialog = false }
        )
    }

    if (showEquityDialog) {
        EquityStatisticsDialog(
            month = month,
            meetings = vm.meetings.value,
            brothers = vm.brothers.value,
            privileges = vm.privileges.value,
            onDismiss = { showEquityDialog = false }
        )
    }
}

@Composable
private fun MeetingCardView(
    vm: AppViewModel,
    meeting: Meeting,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val context = LocalContext.current
    val missing = vm.missingAssignments(meeting)
    var replaceTarget by remember { mutableStateOf<Triple<Long, Long, Long>?>(null) }
    var showQuickUnavailability by remember { mutableStateOf(false) }

    val dateParts = meeting.date.split("/")
    val dayNum = dateParts.getOrNull(0) ?: "--"
    val parsedDate = runCatching {
        LocalDate.of(dateParts[2].toInt(), dateParts[1].toInt(), dateParts[0].toInt())
    }.getOrNull()
    val dayOfWeekShort = parsedDate?.dayOfWeek?.getDisplayName(TextStyle.SHORT, Locale("pt", "BR"))?.uppercase() ?: "REU"

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Bloco Visual de Data Estilo Calendário
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp)
                        ),
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

                // Centro: Tipo e Badges
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = meeting.type,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (missing.isEmpty()) {
                            Badge(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                                Text(
                                    "✓ ${meeting.assignments.size} designações",
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        } else {
                            Badge(containerColor = MaterialTheme.colorScheme.errorContainer) {
                                Text(
                                    "⚠ Faltam candidatos",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Ações Rápidas: WhatsApp da Reunião e Toggle Detalhes
                FilledTonalIconButton(
                    onClick = {
                        val msg = WhatsAppHelper.buildMeetingBroadcastMessage(
                            null, meeting, vm.brothers.value, vm.privileges.value, missing
                        )
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WhatsAppHelper.buildUniversalLink("", msg))))
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = "WhatsApp", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }

                IconButton(onClick = onToggleExpand, modifier = Modifier.size(38.dp)) {
                    Icon(
                        if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "Expandir detalhes"
                    )
                }
            }

            // Seção Expandida com as Designações e Trocas
            if (isExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                if (missing.isNotEmpty()) {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f))
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Atenção: faltaram candidatos para:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                            missing.forEach {
                                Text("• ${it.name} (${it.quantity} necessário(s))", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    meeting.assignments.forEach { assignment ->
                        val brother = vm.brothers.value.find { it.id == assignment.brotherId }
                        val privilege = vm.privileges.value.find { it.id == assignment.privilegeId }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        privilege?.name ?: "Privilégio",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        brother?.name ?: "Irmão",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (brother?.phone?.isNotBlank() == true && privilege != null) {
                                        IconButton(
                                            onClick = {
                                                val msg = WhatsAppHelper.buildSingleMessage(null, brother, privilege, meeting)
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WhatsAppHelper.buildUniversalLink(brother.phone, msg))))
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Filled.Share, contentDescription = "Avisar via WhatsApp", modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    IconButton(
                                        enabled = vm.can(AppPermissions.GENERATE_ASSIGNMENTS),
                                        onClick = { replaceTarget = Triple(meeting.id, assignment.privilegeId, assignment.brotherId) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Filled.SwapHoriz, contentDescription = "Trocar", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showQuickUnavailability = true },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Filled.PersonOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Quem falta?", style = MaterialTheme.typography.labelMedium)
                    }

                    FilledTonalButton(
                        onClick = {
                            val ok = MeetingReminderHelper.notifyMeeting(context, meeting, vm.brothers.value, vm.privileges.value)
                            if (ok) {
                                Toast.makeText(context, "Lembrete enviado para a barra de notificações!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Ative as notificações para o aplicativo nas configurações do aparelho.", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Notificação", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }

    if (showQuickUnavailability) {
        QuickMeetingUnavailabilityDialog(
            meetingDate = meeting.date,
            brothers = vm.brothers.value,
            onAddUnavailability = { bId, start, end, reason ->
                vm.addUnavailability(bId, start, end, reason)
                showQuickUnavailability = false
                Toast.makeText(context, "Ausência registrada para esta data!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showQuickUnavailability = false }
        )
    }

    replaceTarget?.let { target ->
        val candidates = vm.candidatesFor(meeting, target.second, target.third)
        ReplaceDialog(
            candidates = candidates,
            onSelect = { newId ->
                vm.replaceAssignment(target.first, target.second, target.third, newId)
                replaceTarget = null
            },
            onDismiss = { replaceTarget = null }
        )
    }
}

@Composable
private fun QuickMeetingUnavailabilityDialog(
    meetingDate: String,
    brothers: List<Brother>,
    onAddUnavailability: (Long, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = brothers.filter { it.name.contains(search.trim(), ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Marcar Ausência em $meetingDate", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Selecione o irmão que não poderá participar desta reunião.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Buscar irmão") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filtered, key = { it.id }) { b ->
                        val alreadyUnavailable = b.unavailabilities.any { u ->
                            u.startDate == meetingDate || (u.startDate <= meetingDate && u.endDate >= meetingDate)
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!alreadyUnavailable) {
                                        onAddUnavailability(b.id, meetingDate, meetingDate, "Ausente na reunião")
                                    }
                                },
                            shape = RoundedCornerShape(6.dp),
                            color = if (alreadyUnavailable) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
private fun MeetingDaysDialog(
    currentFirstDay: Int,
    currentSecondDay: Int,
    onSave: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var firstDay by remember { mutableIntStateOf(currentFirstDay) }
    var secondDay by remember { mutableIntStateOf(currentSecondDay) }
    var firstExpanded by remember { mutableStateOf(false) }
    var secondExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                Text("Dias de Reunião", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Selecione os dois dias da semana em que sua congregação realiza as reuniões.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Reunião de Meio de Semana", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Box {
                        OutlinedButton({ firstExpanded = true }, Modifier.fillMaxWidth()) {
                            Text(dayLabel(firstDay))
                        }
                        DropdownMenu(firstExpanded, { firstExpanded = false }) {
                            weekdays.forEach { (d, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        if (d.value != secondDay) firstDay = d.value
                                        firstExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Reunião de Fim de Semana", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Box {
                        OutlinedButton({ secondExpanded = true }, Modifier.fillMaxWidth()) {
                            Text(dayLabel(secondDay))
                        }
                        DropdownMenu(secondExpanded, { secondExpanded = false }) {
                            weekdays.forEach { (d, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        if (d.value != firstDay) secondDay = d.value
                                        secondExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(firstDay, secondDay); onDismiss() }) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun BrothersScreen(vm: AppViewModel) {
    var search by remember { mutableStateOf("") }
    var selectedRoleFilter by remember { mutableStateOf<BrotherRole?>(null) }
    var selectedBrotherForProfile by remember { mutableStateOf<Brother?>(null) }
    var editing by remember { mutableStateOf<Brother?>(null) }
    var unavailBrother by remember { mutableStateOf<Brother?>(null) }
    var deleting by remember { mutableStateOf<Brother?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val allBrothers = vm.brothers.value
    val filtered = allBrothers.filter { b ->
        val matchesSearch = b.name.contains(search.trim(), ignoreCase = true) || b.phone.contains(search.trim())
        val matchesRole = selectedRoleFilter == null || b.role == selectedRoleFilter
        matchesSearch && matchesRole
    }.sortedBy { it.name.lowercase(Locale.getDefault()) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Cabeçalho da Tela
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Irmãos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Badge {
                    Text("${allBrothers.count { it.active }} ativos / ${allBrothers.size} total")
                }
            }

            // Barra de Pesquisa Rápida
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Buscar por nome ou telefone") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (search.isNotBlank()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Limpar busca")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Chips de Filtro por Cargo
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
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

            // Lista de Irmãos
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (filtered.isEmpty()) {
                    item {
                        Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            Column(
                                Modifier.padding(24.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Nenhum irmão encontrado", fontWeight = FontWeight.Bold)
                                Text("Tente mudar os termos da busca ou clique no botão + para cadastrar.", color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                items(filtered, key = { it.id }) { brother ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selectedBrotherForProfile = brother },
                        colors = CardDefaults.cardColors(
                            containerColor = if (brother.active) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        )
                    ) {
                        Row(
                            Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Avatar Circular com Iniciais
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(getAvatarColor(brother.name), shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = getInitials(brother.name),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            // Informações do Irmão
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = brother.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Badge(
                                        containerColor = when (brother.role) {
                                            BrotherRole.ELDER -> MaterialTheme.colorScheme.primaryContainer
                                            BrotherRole.MINISTERIAL_SERVANT -> MaterialTheme.colorScheme.secondaryContainer
                                            BrotherRole.PUBLISHER -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ) {
                                        Text(
                                            brother.role.label,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }

                                    if (!brother.active) {
                                        Badge(containerColor = MaterialTheme.colorScheme.errorContainer) {
                                            Text(
                                                "Inativo",
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }

                                    if (brother.unavailabilities.isNotEmpty()) {
                                        Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                                            Text(
                                                "🏖 ${brother.unavailabilities.size} ausência(s)",
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                                if (brother.phone.isNotBlank()) {
                                    Text(
                                        text = "WhatsApp: ${brother.phone}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Ações do Irmão
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { unavailBrother = brother }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Filled.Event, contentDescription = "Ausências", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { editing = brother }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Editar", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { deleting = brother }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Excluir", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // FAB Flutuante para Adicionar Novo Irmão
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Adicionar irmão")
        }
    }

    // Ficha / Perfil Completo do Irmão
    selectedBrotherForProfile?.let { brother ->
        BrotherProfileDialog(
            brother = brother,
            vm = vm,
            onDismiss = { selectedBrotherForProfile = null }
        )
    }

    // Diálogo Modal para Cadastrar Irmão
    if (showAddDialog) {
        AddBrotherDialog(
            onSave = { n, p, r ->
                val err = vm.addBrother(n, p)
                if (err == null) {
                    val newBro = vm.brothers.value.lastOrNull()
                    if (newBro != null && r != BrotherRole.PUBLISHER) {
                        vm.setBrotherRole(newBro.id, r)
                    }
                    showAddDialog = false
                } else {
                    error = err
                }
            },
            onDismiss = { showAddDialog = false }
        )
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Aviso") },
            text = { Text(message) },
            confirmButton = { TextButton({ error = null }) { Text("OK") } }
        )
    }

    editing?.let { brother ->
        EditBrotherDialog(
            brother,
            onSave = { n, p, r ->
                error = vm.updateBrother(brother.id, n, p)
                if (error == null) {
                    vm.setBrotherRole(brother.id, r)
                    editing = null
                }
            },
            onDismiss = { editing = null }
        )
    }

    unavailBrother?.let { brother ->
        val current = vm.brothers.value.find { it.id == brother.id } ?: brother
        BrotherUnavailabilityDialog(
            brother = current,
            onAdd = { start, end, reason -> vm.addUnavailability(brother.id, start, end, reason) },
            onRemove = { periodId -> vm.removeUnavailability(brother.id, periodId) },
            onDismiss = { unavailBrother = null }
        )
    }

    deleting?.let { brother ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Excluir irmão?") },
            text = { Text("O irmão ${brother.name} será removido do cadastro. As designações já registradas no histórico serão mantidas.") },
            confirmButton = { TextButton({ vm.deleteBrother(brother.id); deleting = null }) { Text("Excluir") } },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun BrotherProfileDialog(
    brother: Brother,
    vm: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val brotherMeetings = vm.meetings.value.filter { m ->
        m.assignments.any { it.brotherId == brother.id }
    }.sortedByDescending { AssignmentGenerator.parseDate(it.date) }

    val totalAssignments = brotherMeetings.sumOf { m ->
        m.assignments.count { it.brotherId == brother.id }
    }

    val privilegeCounts = mutableMapOf<String, Int>()
    brotherMeetings.forEach { m ->
        m.assignments.filter { it.brotherId == brother.id }.forEach { a ->
            val pName = vm.privileges.value.find { it.id == a.privilegeId }?.name ?: "Privilégio"
            privilegeCounts[pName] = (privilegeCounts[pName] ?: 0) + 1
        }
    }

    val lastMeeting = brotherMeetings.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(getAvatarColor(brother.name), shape = CircleShape),
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Estatísticas Rápidas
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$totalAssignments", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                                Text("Total de partes", style = MaterialTheme.typography.labelSmall)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${brotherMeetings.size}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                                Text("Reuniões", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // Última participação
                lastMeeting?.let { m ->
                    val myAssignment = m.assignments.find { it.brotherId == brother.id }
                    val privName = vm.privileges.value.find { it.id == myAssignment?.privilegeId }?.name ?: "Designação"
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))) {
                            Column(Modifier.padding(10.dp)) {
                                Text("Última designação:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                Text("${m.date} — $privName (${m.type})", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // Frequência por Privilégio
                if (privilegeCounts.isNotEmpty()) {
                    item {
                        Text("Distribuição por Privilégio:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                    items(privilegeCounts.entries.toList()) { (priv, count) ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(priv, style = MaterialTheme.typography.bodySmall)
                                Text("$count vez(es)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // Privilégios autorizados
                val authorizedPrivileges = vm.privileges.value.filter { p ->
                    val direct = p.id in brother.privileges
                    val isBook = AssignmentGenerator.isBookReaderPrivilege(p)
                    val inherited = !direct && isBook && vm.privileges.value.any {
                        AssignmentGenerator.isSentinelReaderPrivilege(it) && it.id in brother.privileges
                    }
                    direct || inherited
                }
                if (authorizedPrivileges.isNotEmpty()) {
                    item {
                        Text("Privilégios habilitados:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            authorizedPrivileges.forEach { p ->
                                AssistChip(onClick = {}, enabled = false, label = { Text(p.name) })
                            }
                        }
                    }
                }

                // Botão de WhatsApp direto
                if (brother.phone.isNotBlank()) {
                    item {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WhatsAppHelper.buildUniversalLink(brother.phone, "Olá irmão ${brother.name}!"))))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Share, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Abrir conversa no WhatsApp")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
private fun AddBrotherDialog(
    onSave: (String, String, BrotherRole) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(BrotherRole.PUBLISHER) }
    var roleDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.PersonAdd, null, tint = MaterialTheme.colorScheme.primary)
                Text("Adicionar Irmão", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome completo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("WhatsApp (com DDD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedButton(onClick = { roleDropdown = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Cargo: ${role.label}")
                    }
                    DropdownMenu(expanded = roleDropdown, onDismissRequest = { roleDropdown = false }) {
                        BrotherRole.values().forEach { r ->
                            DropdownMenuItem(
                                text = { Text(r.label) },
                                onClick = { role = r; roleDropdown = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = { onSave(name, phone, role) }
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun PrivilegesScreen(vm: AppViewModel) {
    var searchPrivilege by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Privilege?>(null) }
    var deleting by remember { mutableStateOf<Privilege?>(null) }
    var manageBrothersPrivilege by remember { mutableStateOf<Privilege?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val allPrivileges = vm.privileges.value
    val filtered = allPrivileges.filter {
        it.name.contains(searchPrivilege.trim(), ignoreCase = true)
    }.sortedBy { it.name.lowercase(Locale.getDefault()) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Cabeçalho da Tela
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Work, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Privilégios", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Badge {
                    Text("${allPrivileges.count { it.active }} ativos / ${allPrivileges.size} total")
                }
            }

            // Barra de Busca
            OutlinedTextField(
                value = searchPrivilege,
                onValueChange = { searchPrivilege = it },
                label = { Text("Buscar privilégio") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchPrivilege.isNotBlank()) {
                        IconButton(onClick = { searchPrivilege = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Limpar")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Lista de Cards de Privilégio
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (filtered.isEmpty()) {
                    item {
                        Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            Column(
                                Modifier.padding(24.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Nenhum privilégio encontrado", fontWeight = FontWeight.Bold)
                                Text("Toque no botão + para adicionar um novo privilégio.", color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                items(filtered, key = { it.id }) { privilege ->
                    val isBook = AssignmentGenerator.isBookReaderPrivilege(privilege)
                    val isSentinel = AssignmentGenerator.isSentinelReaderPrivilege(privilege)

                    val authorizedCount = vm.brothers.value.count { brother ->
                        val direct = privilege.id in brother.privileges
                        val inherited = !direct && isBook && vm.privileges.value.any { p ->
                            AssignmentGenerator.isSentinelReaderPrivilege(p) && p.id in brother.privileges
                        }
                        direct || inherited
                    }

                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (privilege.active) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Linha Superior: Nome, Quantidade e Ações
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = privilege.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${privilege.quantity} irmão(s) por reunião • ${if (privilege.active) "Ativo" else "Inativo"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { editing = privilege }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Editar", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { deleting = privilege }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Excluir", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            // Destaque Teocrático de Leitores (Quarta x Sábado)
                            if (isBook) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                                ) {
                                    Row(
                                        Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Column {
                                            Text("📖 Reunião de Meio de Semana (Quarta-feira)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                            Text("Irmãos que são Leitores da Sentinela se qualificam automaticamente para ler o Livro.", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            } else if (isSentinel) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f))
                                ) {
                                    Row(
                                        Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Filled.Article, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                        Column {
                                            Text("📰 Reunião de Fim de Semana (Sábado/Domingo)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                            Text("Qualifica automaticamente o irmão para a leitura do Livro de meio de semana.", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }

                            // Seletor de Dias Permitidos
                            Text("Dias permitidos:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
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
                                    label = { Text("Qualquer dia") }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            // Botão e Contador de Autorizações
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "✓ $authorizedCount irmão(s) autorizados",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Button(
                                    onClick = { manageBrothersPrivilege = privilege },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Gerenciar Irmãos", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }

        // FAB Flutuante para Adicionar Novo Privilégio
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Adicionar privilégio")
        }
    }

    // Diálogo Modal para Cadastrar Privilégio
    if (showAddDialog) {
        AddPrivilegeDialog(
            onSave = { n, q ->
                val err = vm.addPrivilege(n, q)
                if (err == null) showAddDialog = false
                else error = err
            },
            onDismiss = { showAddDialog = false }
        )
    }

    // Diálogo Modal para Gerenciar Irmãos de um Privilégio
    manageBrothersPrivilege?.let { priv ->
        ManagePrivilegeBrothersDialog(
            vm = vm,
            privilege = priv,
            onDismiss = { manageBrothersPrivilege = null }
        )
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Aviso") },
            text = { Text(message) },
            confirmButton = { TextButton({ error = null }) { Text("OK") } }
        )
    }

    editing?.let { privilege ->
        EditPrivilegeDialog(
            privilege,
            onSave = { n, q ->
                error = vm.updatePrivilege(privilege.id, n, q)
                if (error == null) editing = null
            },
            onDismiss = { editing = null }
        )
    }

    deleting?.let { privilege ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Excluir privilégio?") },
            text = { Text("O privilégio ${privilege.name} será removido do cadastro e deixará de estar autorizado para os irmãos.") },
            confirmButton = { TextButton({ vm.deletePrivilege(privilege.id); deleting = null }) { Text("Excluir") } },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun AddPrivilegeDialog(
    onSave: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Work, null, tint = MaterialTheme.colorScheme.primary)
                Text("Novo Privilégio", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome (ex: Leitor do Livro, Indicador)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it.filter(Char::isDigit) },
                    label = { Text("Quantidade necessária por reunião") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = { onSave(name, quantity.toIntOrNull() ?: 1) }
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun ManagePrivilegeBrothersDialog(
    vm: AppViewModel,
    privilege: Privilege,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val isBook = AssignmentGenerator.isBookReaderPrivilege(privilege)
    val sentinelPrivilege = vm.privileges.value.find { AssignmentGenerator.isSentinelReaderPrivilege(it) }

    val filteredBrothers = vm.brothers.value.filter {
        it.name.contains(search.trim(), ignoreCase = true)
    }.sortedBy { it.name.lowercase(Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Autorizações — ${privilege.name}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Marque os irmãos qualificados para esta designação.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Buscar irmão") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isBook) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))) {
                        Text(
                            "Nota teocrática: Leitores da Sentinela já são autorizados automaticamente para o Livro.",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredBrothers, key = { it.id }) { brother ->
                        val directAuthorization = privilege.id in brother.privileges
                        val inheritedFromSentinel = !directAuthorization && isBook &&
                            sentinelPrivilege != null && sentinelPrivilege.id in brother.privileges

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !inheritedFromSentinel) {
                                    vm.togglePrivilege(brother.id, privilege.id)
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (directAuthorization || inheritedFromSentinel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(brother.name, fontWeight = FontWeight.Medium)
                                    if (inheritedFromSentinel) {
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
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Concluir") }
        }
    )
}

@Composable
private fun HistoryScreen(vm: AppViewModel) {
    var confirmDelete by remember { mutableStateOf<Meeting?>(null) }
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Histórico", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text("${vm.meetings.value.size} reunião(ões) registrada(s)", style = MaterialTheme.typography.bodySmall)
        }
        items(vm.meetings.value.sortedByDescending { parseDateForSort(it.date) }, key = { it.id }) { meeting ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(meeting.date, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(meeting.type, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("${meeting.assignments.size} designação(ões)")
                    meeting.assignments.forEach { a ->
                        val brother = vm.brothers.value.find { it.id == a.brotherId }
                        val privilege = vm.privileges.value.find { it.id == a.privilegeId }
                        Text("• ${privilege?.name}: ${brother?.name ?: "Irmão removido"}", style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = { confirmDelete = meeting }, modifier = Modifier.align(Alignment.End)) {
                        Text("Excluir registro", color = MaterialTheme.colorScheme.error)
                    }
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
private fun SettingsScreen(
    vm: AppViewModel,
    themeIndex: Int,
    onThemeChange: (Int) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var showImportDialog by remember { mutableStateOf(false) }
    var importCsvText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    val themes = listOf(
        "Azul" to androidx.compose.ui.graphics.Color(0xFF1565C0),
        "Verde" to androidx.compose.ui.graphics.Color(0xFF2E7D32),
        "Roxo" to androidx.compose.ui.graphics.Color(0xFF6A1B9A),
        "Laranja" to androidx.compose.ui.graphics.Color(0xFFEF6C00),
        "Vinho" to androidx.compose.ui.graphics.Color(0xFF8E244D)
    )

    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Configurações", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text("Personalize o aplicativo e gerencie backups.", style = MaterialTheme.typography.bodyMedium)
        }

        // Backup e Exportação
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Backup, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Backup e Exportação (CSV)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text("Guarde cópias de segurança ou compartilhe cadastros.", style = MaterialTheme.typography.bodySmall)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val csv = vm.exportBrothersCsv()
                                shareText(context, "irmaos.csv", csv)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Exportar Irmãos")
                        }
                        OutlinedButton(
                            onClick = {
                                val csv = vm.exportMeetingsCsv()
                                shareText(context, "escala.csv", csv)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Exportar Escala")
                        }
                    }

                    Button(
                        onClick = { showImportDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.FileDownload, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Importar Irmãos via CSV")
                    }
                }
            }
        }

        // Aparência
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Cor do aplicativo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    themes.forEachIndexed { index, item ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(Modifier.size(28.dp), shape = MaterialTheme.shapes.small, color = item.second) {}
                                Text(item.first)
                            }
                            RadioButton(selected = themeIndex == index, onClick = { onThemeChange(index) })
                        }
                    }
                }
            }
        }

        // Regras Teocráticas
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Regras teocráticas de leitores", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("• Leitor do Livro (EBC): Atua nas reuniões de meio de semana (quarta-feira). Irmãos qualificados como Leitor da Sentinela são elegíveis automaticamente.")
                    Text("• Leitor da Sentinela: Atua nas reuniões de fim de semana (sábado/domingo). Irmãos que apenas leem o livro nunca leem a Sentinela.")
                }
            }
        }

        // Conta
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Conta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Sua sessão é protegida pelo Firebase Authentication.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sair da conta") }
                }
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Importar Irmãos (CSV)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cole o conteúdo do arquivo CSV abaixo:", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = importCsvText,
                        onValueChange = { importCsvText = it },
                        label = { Text("Conteúdo CSV") },
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        maxLines = 8
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = importCsvText.isNotBlank(),
                    onClick = {
                        val count = vm.importBrothersCsv(importCsvText)
                        showImportDialog = false
                        importCsvText = ""
                        message = if (count > 0) "$count irmão(s) importado(s) com sucesso!" else "Nenhum irmão novo encontrado no CSV."
                    }
                ) { Text("Importar") }
            },
            dismissButton = { TextButton(onClick = { showImportDialog = false }) { Text("Cancelar") } }
        )
    }

    message?.let { msg ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("Aviso") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }
        )
    }
}

private fun parseDateForSort(value: String): LocalDate = runCatching {
    LocalDate.parse(value, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}.getOrElse { LocalDate.MIN }

@Composable
private fun CalendarGridView(
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
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                daysOfWeek.forEach { dayName ->
                    Text(
                        text = dayName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 2.dp))

            for (row in 0 until totalRows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                    .aspectRatio(1f)
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
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
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
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (meeting != null) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else if (meeting != null) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (meeting != null) {
                                        Text(
                                            text = "${meeting.assignments.size} des.",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
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
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Atenção: faltaram candidatos para:")
                    missing.forEach { Text("• " + it.name + " (" + it.quantity + " necessário(s))") }
                }
            }
        }
        meeting.assignments.forEach { assignment ->
            val brother = vm.brothers.value.find { it.id == assignment.brotherId }
            val privilege = vm.privileges.value.find { it.id == assignment.privilegeId }
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(privilege?.name ?: "Privilégio", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(brother?.name ?: "Irmão", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(enabled = vm.can(AppPermissions.GENERATE_ASSIGNMENTS), onClick = { replaceTarget = Triple(meeting.id, assignment.privilegeId, assignment.brotherId) }) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = "Trocar")
                    }
                }
            }
        }
        Button({
            val missing = vm.missingAssignments(meeting)
            val msg = WhatsAppHelper.buildMeetingBroadcastMessage(
                null, meeting, vm.brothers.value, vm.privileges.value, missing
            )
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WhatsAppHelper.buildUniversalLink("", msg))))
        }, Modifier.fillMaxWidth()) { Text("Compartilhar no WhatsApp") }
        FilledTonalButton(
            onClick = {
                val ok = MeetingReminderHelper.notifyMeeting(context, meeting, vm.brothers.value, vm.privileges.value)
                if (ok) {
                    Toast.makeText(context, "Lembrete enviado para as notificações do aparelho!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Ative as notificações para o aplicativo nas configurações do aparelho.", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Notifications, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Lembrete na barra de notificações")
        }
    }
    replaceTarget?.let { target ->
        val candidates = vm.candidatesFor(meeting, target.second, target.third)
        ReplaceDialog(candidates, { newId -> vm.replaceAssignment(target.first, target.second, target.third, newId); replaceTarget = null }, { replaceTarget = null })
    }
}

@Composable
private fun EquityStatisticsDialog(
    month: YearMonth,
    meetings: List<Meeting>,
    brothers: List<Brother>,
    privileges: List<Privilege>,
    onDismiss: () -> Unit
) {
    val report = remember(month, meetings, brothers, privileges) {
        EquityStatisticsHelper.calculateMonthStats(month, meetings, brothers, privileges)
    }
    val monthName = month.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
        .replaceFirstChar { it.uppercase() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Equidade e Estatísticas", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("$monthName ${month.year}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
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
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f))
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
                    Text("Ranking de Participação", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                                Column(Modifier.weight(1f)) {
                                    Text(item.brother.name, fontWeight = FontWeight.Bold)
                                    Text(item.brother.role.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                }
                                Badge(containerColor = if (item.count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) {
                                    Text("${item.count} vez(es)", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
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
                        Text("Distribuição por Privilégio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
private fun ReplaceDialog(candidates: List<Brother>, onSelect: (Long) -> Unit, onDismiss: () -> Unit) {
    var search by remember { mutableStateOf("") }
    val filtered = candidates.filter { it.name.contains(search.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trocar designação") },
        text = {
            Column {
                OutlinedTextField(search, { search = it }, label = { Text("Buscar irmão") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) Text("Não há outro irmão autorizado e disponível.")
                filtered.forEach { brother -> TextButton({ onSelect(brother.id) }, Modifier.fillMaxWidth()) { Text(brother.name) } }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun EditBrotherDialog(brother: Brother, onSave: (String, String, BrotherRole) -> Unit, onDismiss: () -> Unit) {
    var name by remember(brother.id) { mutableStateOf(brother.name) }
    var phone by remember(brother.id) { mutableStateOf(brother.phone) }
    var role by remember(brother.id) { mutableStateOf(brother.role) }
    var roleDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar irmão") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("WhatsApp") }, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedButton({ roleDropdown = true }, Modifier.fillMaxWidth()) { Text("Cargo: " + role.label) }
                    DropdownMenu(roleDropdown, { roleDropdown = false }) {
                        BrotherRole.values().forEach { r ->
                            DropdownMenuItem(text = { Text(r.label) }, onClick = { role = r; roleDropdown = false })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ onSave(name, phone, role) }) { Text("Salvar") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun BrotherUnavailabilityDialog(
    brother: Brother,
    onAdd: (String, String, String) -> String?,
    onRemove: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ausências — ${brother.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (brother.unavailabilities.isEmpty()) {
                    Text("Nenhuma ausência registrada para este irmão.")
                } else {
                    Text("Períodos cadastrados:", fontWeight = FontWeight.Bold)
                    brother.unavailabilities.forEach { u ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${u.startDate} a ${u.endDate}${if (u.reason.isNotBlank()) " (${u.reason})" else ""}", style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { onRemove(u.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remover")
                            }
                        }
                    }
                }
                HorizontalDivider()
                Text("Adicionar novo período:", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(start, { start = it }, label = { Text("Início (dd/MM/yyyy)") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(end, { end = it }, label = { Text("Fim (dd/MM/yyyy)") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(reason, { reason = it }, label = { Text("Motivo (ex: Viagem, Férias)") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Button(
                    onClick = {
                        error = onAdd(start, end, reason)
                        if (error == null) {
                            start = ""
                            end = ""
                            reason = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Adicionar ausência")
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Concluir") } }
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit) }, label = { Text("Quantidade") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton({ onSave(name, quantity.toIntOrNull() ?: 1) }) { Text("Salvar") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } }
    )
}
