package br.com.willendary.designacoesjw

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.willendary.designacoesjw.data.*
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import br.com.willendary.designacoesjw.export.ImageExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import br.com.willendary.designacoesjw.notification.MeetingReminderHelper
import br.com.willendary.designacoesjw.screens.EditBrotherDialog
import br.com.willendary.designacoesjw.screens.EditPrivilegeDialog
import br.com.willendary.designacoesjw.stats.EquityStatisticsHelper
import br.com.willendary.designacoesjw.ui.MeetingProgramList
import br.com.willendary.designacoesjw.export.MonthBoardPrint
import br.com.willendary.designacoesjw.sync.Changelog
import br.com.willendary.designacoesjw.ui.JwCard
import br.com.willendary.designacoesjw.ui.mostrarDesfazivel
import br.com.willendary.designacoesjw.ui.JwCardTitle
import br.com.willendary.designacoesjw.ui.JwSectionLabel
import br.com.willendary.designacoesjw.ui.JwTheme
import br.com.willendary.designacoesjw.ui.corDeContorno
import br.com.willendary.designacoesjw.ui.MonthBoard
import br.com.willendary.designacoesjw.util.Datas
import br.com.willendary.designacoesjw.util.WhatsAppHelper
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
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

// Rótulos usados também pelos diálogos de edição, em `screens/EditDialogs.kt`:
// um lugar só para o nome de cada valor.
internal fun kindLabel(kind: PartKind): String = when (kind) {
    PartKind.INDIVIDUAL -> "Individual"
    PartKind.PAIR -> "Dupla"
    PartKind.DEMONSTRATION -> "Encenação"
    PartKind.GROUP -> "Grupo"
}

internal fun readerGrantLabel(grant: ReaderGrant): String = when (grant) {
    ReaderGrant.NONE -> "Ninguém (só marcado)"
    ReaderGrant.BOOK -> "Leitor"
    ReaderGrant.SENTINEL -> "Leitor de A Sentinela"
}

internal fun dayLabel(value: Int): String =
    weekdays.firstOrNull { it.first.value == value }?.second ?: "—"

private fun getInitials(name: String): String {
    val parts = name.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts[0].first()}${parts.last().first()}".uppercase()
    }
}

/**
 * "1 irmao" ou "3 irmaos", com acento certo.
 *
 * Existe porque `${n} irmao(s)` espalhado por varias telas e `${n} irmao(s)`
 * em cada uma delas, para sempre, e alguem sempre esquece de um. A versao do
 * desktop mora em `components/Contagem.kt`; a daqui e a mesma ideia, porque o
 * modulo `app` nao enxerga o `desktop`.
 */
private fun contar(n: Int, singular: String, plural: String): String =
    "$n " + (if (n == 1) singular else plural)

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
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
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

    // O "o que ha de novo" e a checagem de atualizacao sao a mesma ida ao
    // servidor: uma so chamada de rede na abertura, nao duas.
    var novidades by remember { mutableStateOf<Changelog?>(null) }
    LaunchedEffect(Unit) {
        refreshUpdate()
        novidades = carregarChangelogSeMostrar(context)
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var tab by remember { mutableIntStateOf(3) }

    val currentTitle = tituloDaTela(tab)

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

                        // O menu é uma lista de dado (Menu.kt), não treze
                        // NavigationDrawerItem escritos à mão: reorganizar
                        // virou editar um item de lista em vez de caçar o
                        // bloco certo no meio de 200 linhas.
                        items(MENU) { entrada ->
                            when (entrada) {
                                is MenuSection -> Text(
                                    entrada.titulo,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )

                                is MenuItem -> {
                                    val selecionado = tab == entrada.tab
                                    NavigationDrawerItem(
                                        label = { Text(entrada.rotulo) },
                                        icon = { Icon(entrada.icone, null) },
                                        selected = selecionado,
                                        onClick = {
                                            tab = entrada.tab
                                            scope.launch { drawerState.close() }
                                        },
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) {
        // Erro de gravacao no Firestore ou de permissao. Antes estas falhas
        // eram engolidas no repositorio e o app mostrava "salvo" mesmo com o
        // servidor tendo recusado.
        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(vm.lastActionError.value) {
            vm.lastActionError.value?.let {
                snackbarHostState.showSnackbar(it, withDismissAction = true, duration = SnackbarDuration.Long)
                vm.clearActionError()
            }
        }

        // Desfazer. O diálogo de confirmação saiu de propósito: para ação
        // reversível, confirmar **e** oferecer desfazer é pedir a mesma coisa
        // duas vezes. Quem apaga por engano toca em "Desfazer"; quem apaga de
        // propósito não precisou confirmar.
        LaunchedEffect(vm.undoPendente.value) {
            val undo = vm.undoPendente.value ?: return@LaunchedEffect
            val rotulo = when (undo) {
                is AppViewModel.Undo.Irmao -> "Irmão excluído"
                is AppViewModel.Undo.Privilegio -> "Privilégio excluído"
                is AppViewModel.Undo.Reuniao -> "Reunião excluída"
            }
            val desfez = snackbarHostState.mostrarDesfazivel(rotulo) { vm.desfazer() }
            // O snackbar fechou sozinho: passou a janela, é definitiva.
            if (!desfez) vm.descartarUndo()
        }
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        0 -> SettingsScreen(vm, themeIndex, onThemeChange, themeMode, onThemeModeChange, onSignOut)
                        1 -> HistoryScreen(vm)
                        16 -> MeetingsScreen(vm)
                        2 -> BrothersScreen(vm)
                        3 -> HomeScreen(vm)
                        4 -> PrivilegesScreen(vm)
                        5 -> UserManagementScreen(vm)
                        11 -> br.com.willendary.designacoesjw.screens.PublicTalksAndroidScreen(vm)
                        12 -> br.com.willendary.designacoesjw.screens.GroupsAndCleaningAndroidScreen(vm)
                        13 -> RelatorioA4Screen(vm)
                        14 -> br.com.willendary.designacoesjw.screens.UnavailabilityScreen(vm)
                        15 -> br.com.willendary.designacoesjw.screens.EquityStatisticsScreen(vm)
                    }
                }
            }
        }
    }

    novidades?.let { changelog ->
        DialogoNovidades(changelog, aoFechar = { novidades = null })
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

/**
 * Rótulo de bloco dentro de um card.
 *
 * Existe para separar o que é privilégio **mecânico** (fixo: Som, Anunciante,
 * Orações) do que é **parte do programa** (muda toda semana). São listas
 * diferentes com origens diferentes, e juntas sem rótulo leem como uma só.
 *
 * Delegado a [JwSectionLabel]: a versão local só diferia no padding, e duas
 * etiquetas de seção com nomes diferentes é exatamente a divergência que o
 * redesenho vem para tirar.
 */
@Composable
private fun SectionLabel(texto: String) {
    JwSectionLabel(texto, Modifier.padding(top = 8.dp, bottom = 2.dp))
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

    val monthName = Datas.nomeDoMes(month.month)

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        // --- 0. BANNER INTELIGENTE: PRÓXIMA REUNIÃO DA CONGREGAÇÃO ---
        nextMeetingInfo?.let { (nextMeeting, daysUntil) ->
            item {
                // Destaque: é a próxima reunião, a informação que se procura
                // ao abrir o app.
                JwCard(destaque = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                text = Datas.mesEAno(month),
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
    var importing by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    val dateParts = meeting.date.split("/")
    val dayNum = dateParts.getOrNull(0) ?: "--"
    val parsedDate = runCatching {
        LocalDate.of(dateParts[2].toInt(), dateParts[1].toInt(), dateParts[0].toInt())
    }.getOrNull()
    val dayOfWeekShort = parsedDate?.let { Datas.diaDaSemanaCurto(it) } ?: "REU"

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

                // Exportar a imagem da reunião. No desktop isso já existia
                // (java.awt); no celular não havia botão nem gerador, porque
                // java.awt não existe no Android.
                var exportingImage by remember { mutableStateOf(false) }
                IconButton(
                    onClick = {
                        if (exportingImage) return@IconButton
                        exportingImage = true
                        coroutineScope.launch {
                            val bitmap = withContext(Dispatchers.Main) {
                                ImageExport.render(
                                    context, meeting,
                                    vm.brothers.value, vm.privileges.value
                                )
                            }
                            exportingImage = false
                            if (bitmap == null) {
                                Toast.makeText(context, "Não consegui gerar a imagem.", Toast.LENGTH_LONG).show()
                                return@launch
                            }
                            val fileName = ImageExport.fileNameFor(meeting)
                            val uri = withContext(Dispatchers.IO) {
                                ImageExport.saveToGallery(context, bitmap, fileName)
                            }
                            val share = ImageExport.shareIntent(context, bitmap, fileName)
                            if (share != null) {
                                context.startActivity(Intent.createChooser(share, "Enviar designações"))
                            } else if (uri != null) {
                                Toast.makeText(
                                    context,
                                    "Salvo em Imagens/Designações JW.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(context, "Não consegui salvar a imagem.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !exportingImage
                ) {
                    if (exportingImage) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Share, contentDescription = "Exportar imagem da reunião")
                    }
                }

                // Importar o programa oficial. Acao de manutencao: fica como
                // icone ao lado das outras acoes, nao como botao de largura
                // natural no meio do conteudo do card.
                if (meeting.isMidweek) {
                    IconButton(
                        onClick = {
                            importing = true
                            importError = null
                            vm.importMwbProgram(meeting.id) { err ->
                                importing = false
                                if (err != null) importError = err
                            }
                        },
                        enabled = !importing
                    ) {
                        if (importing) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                if (meeting.theme.isBlank()) Icons.Filled.CloudDownload else Icons.Filled.CloudDone,
                                contentDescription = if (meeting.theme.isBlank())
                                    "Importar programa do jw.org" else "Atualizar programa do jw.org"
                            )
                        }
                    }
                }

                IconButton(onClick = onToggleExpand) {
                    Icon(
                        if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "Expandir detalhes"
                    )
                }
            }

            // Programa oficial da semana, importado do jw.org.
            // Delegado a shared: cada tela tinha a sua renderizacao e elas
            // ja divergiram entre Android e Desktop.
            if (meeting.theme.isNotBlank()) {
                Text(
                    "📖 ${meeting.theme}",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (meeting.isMidweek && importError != null) {
                // Erro em linha, sem AlertDialog: na maioria das vezes o bimestre
                // ainda nao foi publicado no jw.org, o que e condicao normal e
                // nao justifica interromper a tela.
                Text(
                    importError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
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
                                Text("• ${it.name} — ${it.quantity} necessário${if (it.quantity == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }

                // Cabeçalho do bloco. Sem ele, a lista de privilégios e a de
                // partes do programa leem como uma só: são duas naturezas
                // diferentes — o mecânico é fixo, a parte muda toda semana.
                // A separação no código veio no #51; na tela só veio agora.
                if (meeting.program.isNotEmpty()) {
                    SectionLabel("Programa da semana")
                }
                MeetingProgramList(
                    meeting = meeting,
                    brothers = vm.brothers.value,
                    privileges = vm.privileges.value,
                    canAssign = vm.brothers.value.filter { it.active },
                    onToggleAssignment = { position, brotherId ->
                        vm.toggleProgramAssignment(meeting.id, position, brotherId)
                    }
                )

                if (meeting.assignments.isNotEmpty()) {
                    SectionLabel("Privilégios")
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

    importError?.let { msg ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text("Não foi possível importar o programa") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { importError = null }) { Text("Fechar") }
            }
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
                    JwCard(onClick = { selectedBrotherForProfile = brother }) {
                        Row(
                            Modifier.fillMaxWidth(),
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
                                    fontWeight = FontWeight.SemiBold,
                                    // Esmaece o nome de quem saiu, e não o cartão
                                    // inteiro: fundo com alpha ficava ilegível no
                                    // modo escuro e sumia na impressão.
                                    color = if (brother.active) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
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
                                        text = brother.phone,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Ações do Irmão
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { unavailBrother = brother }) {
                                    Icon(
                                        Icons.Filled.Event,
                                        contentDescription = "Ausências",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                IconButton(onClick = { editing = brother }) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = "Editar",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                // Excluir fica **na cor de perigo** e separado por
                                // um traço. A 48dp de distância do editar, um toque
                                // errado apaga a pessoa — e o diálogo de confirmação existe,
                                // mas ninguém lê um diálogo que não esperava.
                                VerticalDivider(
                                    modifier = Modifier.height(28.dp),
                                    color = corDeContorno()
                                )
                                IconButton(onClick = { vm.deleteBrother(brother.id) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Excluir",
                                        tint = JwTheme.colors.perigo,
                                        modifier = Modifier.size(22.dp)
                                    )
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
            vm = vm,
            brother = brother,
            onErro = { error = it },
            onFechar = { editing = null }
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

                // Situação teocrática do irmão. Não existia forma de declarar
                // publicador não batizado, aprendiz, leitor ou leitor de A
                // Sentinela — o gerador não tinha como aplicar as regras.
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Situação", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !brother.baptized,
                                    onClick = { vm.setBrotherBaptized(brother.id, !brother.baptized) },
                                    label = { Text("Não batizado") }
                                )
                                FilterChip(
                                    selected = brother.trainee,
                                    onClick = { vm.setBrotherTrainee(brother.id, !brother.trainee) },
                                    label = { Text("Aprendiz") }
                                )
                                FilterChip(
                                    selected = brother.isReader,
                                    onClick = { vm.setBrotherIsReader(brother.id, !brother.isReader) },
                                    label = { Text("Leitor") }
                                )
                                FilterChip(
                                    selected = brother.isSentinelReader,
                                    onClick = { vm.setBrotherIsSentinelReader(brother.id, !brother.isSentinelReader) },
                                    label = { Text("Leitor de A Sentinela") }
                                )
                            }
                            if (brother.trainee) {
                                Text(
                                    "Aprendiz não é escolhido automaticamente e não pode ser o segundo não qualificado de uma parte com duas pessoas.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Privilégios autorizados. Usa a MESMA função do gerador:
                // a regra duplicada aqui já divergiu do gerador uma vez.
                val authorizedPrivileges = vm.privileges.value.filter { p ->
                    AssignmentGenerator.isAuthorized(brother, p)
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
                    val authorizedCount = vm.brothers.value.count { brother ->
                        AssignmentGenerator.isAuthorized(brother, privilege)
                    }
                    val isBook = privilege.readerGrant == ReaderGrant.BOOK
                    val isSentinel = privilege.readerGrant == ReaderGrant.SENTINEL

                    JwCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Linha superior: nome, quantidade e ações
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = privilege.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (privilege.active) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        // "1 irmão(s)" era o texto. O número é o
                                        // que importa e a concordância errava todo
                                        // singular — "1 irmão(s)" lê como robô.
                                        text = buildString {
                                            append(
                                                if (privilege.quantity == 1) "1 irmão por reunião"
                                                else "${privilege.quantity} irmãos por reunião"
                                            )
                                            append(" • ")
                                            append(if (privilege.active) "Ativo" else "Inativo")
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { editing = privilege }) {
                                        Icon(
                                            Icons.Filled.Edit,
                                            contentDescription = "Editar",
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    IconButton(onClick = { vm.deletePrivilege(privilege.id) }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Excluir",
                                            tint = JwTheme.colors.perigo,
                                            modifier = Modifier.size(22.dp)
                                        )
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

                            // Tipo da parte: individual, dupla, encenação ou grupo.
                            // Sem isso o app não distingue uma leitura de uma
                            // encenação, e o gerador não tem como tratá-las.
                            Text("Tipo de parte:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PartKind.entries.forEach { kind ->
                                    FilterChip(
                                        selected = privilege.kind == kind,
                                        onClick = { vm.setPrivilegeKind(privilege.id, kind) },
                                        label = { Text(kindLabel(kind)) }
                                    )
                                }
                            }

                            // Quem pode fazer: batizado, não batizado, ou ambos.
                            Text("Pode ser feito por:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                BrotherStatus.entries.forEach { status ->
                                    FilterChip(
                                        selected = status in privilege.allowedStatus,
                                        onClick = {
                                            val next = privilege.allowedStatus.toMutableSet().also { s ->
                                                if (!s.add(status)) s.remove(status)
                                            }
                                            if (next.isEmpty()) {
                                                vm.reportError("Escolha pelo menos um. Remover todos deixaria a parte sem regra.")
                                            } else {
                                                vm.setPrivilegeAllowedStatus(privilege.id, next)
                                            }
                                        },
                                        label = { Text(status.label) }
                                    )
                                }
                            }

                            // Qual habilitação do irmão concede este privilégio.
                            Text("Concedido a quem é:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ReaderGrant.entries.forEach { grant ->
                                    FilterChip(
                                        selected = privilege.readerGrant == grant,
                                        onClick = { vm.setPrivilegeReaderGrant(privilege.id, grant) },
                                        label = { Text(readerGrantLabel(grant)) }
                                    )
                                }
                            }
                            if (privilege.readerGrant != ReaderGrant.NONE) {
                                Text(
                                    when (privilege.readerGrant) {
                                        ReaderGrant.BOOK -> "Irmãos marcados como Leitor (e Leitores de A Sentinela) podem fazer esta parte sem precisar marcar o privilégio um a um."
                                        ReaderGrant.SENTINEL -> "Somente irmãos marcados como Leitor de A Sentinela. O leitor de A Sentinela também pode ler o Livro."
                                        ReaderGrant.NONE -> ""
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                                    "✓ ${contar(authorizedCount, "irmão autorizado", "irmãos autorizados")}",
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
            vm = vm,
            privilege = privilege,
            onErro = { error = it },
            onFechar = { editing = null }
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
    val isBook = privilege.readerGrant == ReaderGrant.BOOK

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
                        // Herança: quem é leitor de A Sentinela também pode ler o livro.
                        val inheritedFromSentinel = !directAuthorization && isBook && brother.isSentinelReader

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
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Histórico", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                    buildString {
                        val n = vm.meetings.value.size
                        append(if (n == 1) "1 reunião registrada" else "$n reuniões registradas")
                    },
                    style = MaterialTheme.typography.bodySmall
                )
        }
        items(vm.meetings.value.sortedByDescending { parseDateForSort(it.date) }, key = { it.id }) { meeting ->
            JwCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(meeting.date, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(meeting.type, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    val quantas = meeting.assignments.size
                    Text(if (quantas == 1) "1 designação" else "$quantas designações")
                    meeting.assignments.forEach { a ->
                        val brother = vm.brothers.value.find { it.id == a.brotherId }
                        val privilege = vm.privileges.value.find { it.id == a.privilegeId }
                        Text("• ${privilege?.name}: ${brother?.name ?: "Irmão removido"}", style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = { vm.deleteMeeting(meeting.id) }, modifier = Modifier.align(Alignment.End)) {
                        Text("Excluir registro", color = JwTheme.colors.perigo, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    vm: AppViewModel,
    themeIndex: Int,
    onThemeChange: (Int) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var showImportDialog by remember { mutableStateOf(false) }
    var showLogErros by remember { mutableStateOf(false) }
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
            JwCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Backup, null, tint = MaterialTheme.colorScheme.primary)
                        JwCardTitle("Backup e Exportação (CSV)")
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
            JwCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    JwCardTitle("Claro ou escuro")
                    Text(
                        "Padrão do sistema segue o modo do aparelho.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ThemeMode.entries.forEach { modo ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onThemeModeChange(modo) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeMode == modo,
                                onClick = { onThemeModeChange(modo) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(modo.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }

        item {
            JwCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    JwCardTitle("Cor do aplicativo")
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

        // Log de erros. Existe por causa de um app que abria e fechava sem
        // deixar rastro: sem isto, nem o usuário — que não tem como abrir o
        // logcat — nem quem fosse reparar soube o porquê. Se o app voltar a
        // travar na abertura, o texto daqui já diz a exceção.
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Diagnóstico", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Se o app fechar sozinho ao abrir, este log diz o porquê.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = { showLogErros = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Ver log de erros") }
                }
            }
        }
    }

    if (showLogErros) {
        AlertDialog(
            onDismissRequest = { showLogErros = false },
            title = { Text("Log de erros") },
            text = {
                Text(
                    remember(showLogErros) { vm.lerLogDeErros().ifBlank { "Nenhum erro registrado." } },
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = { TextButton(onClick = { showLogErros = false }) { Text("Fechar") } }
        )
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
                        message = if (count > 0) {
                            "${contar(count, "irmão importado", "irmãos importados")} com sucesso!"
                        } else "Nenhum irmão novo encontrado no CSV."
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
                    missing.forEach {
                        val n = it.quantity
                        Text("• " + it.name + " — " + n + " necessário" + if (n == 1) "" else "s")
                    }
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
    val monthName = Datas.mesEAno(month)

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
                    Text(monthName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
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
                                // Média só sobre irmãos efetivamente designados.
                                // Dividir por ranking.size (que inclui quem tem
                                // count == 0) diluía a média e dava NaN com zero
                                // irmãos ativos. Null = ninguém foi designado.
                                val avg = report.averageAssignmentsPerAssignedBrother
                                    ?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
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
                                    "⚠ ${contar(report.unassignedActiveBrothers.size, "irmão ativo", "irmãos ativos")} sem designação no mês:",
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

private const val TAG_IMAGENS_MES = "ImagensReuniao"

/**
 * Aba 13 — relatório A4.
 *
 * O mês mora **aqui**, e o `PrintReportScreen` o recebe por parâmetro. Com o
 * mês preso dentro dele, o quadro e a exportação de imagens abriam o mês de
 * outubro enquanto o relatório embaixo continuava mostrando setembro — três
 * coisas na mesma tela discordando de qual mês é o atual.
 */
@Composable
private fun RelatorioA4Screen(vm: AppViewModel) {
    var quadroAberto by remember { mutableStateOf(false) }
    var envioAberto by remember { mutableStateOf(false) }
    var mes by remember { mutableStateOf(YearMonth.now()) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = { quadroAberto = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.GridView, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Quadro do mês", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(onClick = { envioAberto = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Collections, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Imagens do mês", style = MaterialTheme.typography.labelMedium)
            }
        }

        br.com.willendary.designacoesjw.screens.PrintReportScreen(
            vm = vm,
            month = mes,
            onMonthChange = { mes = it }
        )
    }

    if (quadroAberto) {
        QuadroDoMesDialog(vm, mes, onMonthChange = { mes = it }, onDismiss = { quadroAberto = false })
    }
    if (envioAberto) {
        ImagensDoMesDialog(vm, mes, onDismiss = { envioAberto = false })
    }
}

/**
 * O quadro em tela cheia.
 *
 * **Imprimir, e não fotografar.** A primeira versão dizia "o usuário imprime a
 * tela ou tira foto dela" — verdade no desktop, onde `Ctrl+P` existe. No
 * celular não existe atalho de teclado, e a única saída era foto da tela, que é
 * o contrário de imprimir. `PrintManager` é a via nativa e resolve.
 */
@Composable
private fun QuadroDoMesDialog(
    vm: AppViewModel,
    mes: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var imprimindo by remember { mutableStateOf(false) }
    val reunioes = remember(mes, vm.meetings.value) { reunioesDoMes(vm.meetings.value, mes) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(Modifier.fillMaxSize()) {
            MonthBoard(
                month = mes,
                meetings = reunioes,
                brothers = vm.brothers.value,
                privileges = vm.privileges.value,
                modifier = Modifier.fillMaxSize()
            )

            // Controles no rodapé, não sobre o quadro: quem fotografa a tela
            // não quer botão nenhum no meio da foto da parede.
            Row(
                Modifier.align(Alignment.BottomCenter).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SeletorMes(mes, onMonthChange)
                TextButton(
                    onClick = {
                        if (imprimindo) return@TextButton
                        imprimindo = true
                        scope.launch {
                            val ok = withContext(Dispatchers.Main) {
                                MonthBoardPrint.imprimir(
                                    context, mes, reunioes,
                                    vm.brothers.value, vm.privileges.value
                                )
                            }
                            imprimindo = false
                            if (!ok) {
                                Toast.makeText(
                                    context,
                                    "Não consegui preparar o quadro para imprimir.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = !imprimindo
                ) {
                    Icon(
                        Icons.Filled.Print,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (imprimindo) "Preparando…" else "Imprimir")
                }
                TextButton(onClick = onDismiss) { Text("Fechar") }
            }
        }
    }
}

/**
 * Gera a imagem de cada reunião do mês e salva todas na galeria.
 *
 * Não abre o compartilhador: o Android não aguenta N intents de uma vez, e o
 * usuário não quer escolher um app de cada imagem. Uma pasta na galeria e um
 * aviso com a quantidade resolve — e é o mesmo destino que o botão do card já
 * usava.
 */
@Composable
private fun ImagensDoMesDialog(vm: AppViewModel, mes: YearMonth, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enviando by remember { mutableStateOf(false) }
    val reunioes = remember(mes, vm.meetings.value) { reunioesDoMes(vm.meetings.value, mes) }

    fun gerar() {
        if (enviando) return
        if (reunioes.isEmpty()) {
            Toast.makeText(context, "Nenhuma reunião neste mês.", Toast.LENGTH_LONG).show()
            return
        }
        scope.launch {
            enviando = true
            var salvas = 0
            var falhas = 0
            for (reuniao in reunioes) {
                // Uma reunião quebrada não pode derrubar o mês inteiro: quem
                // pediu foram as 8 imagens, não a 3ª delas.
                try {
                    // O `render` monta um ComposeView, então só na main thread.
                    val bitmap = withContext(Dispatchers.Main) {
                        ImageExport.render(context, reuniao, vm.brothers.value, vm.privileges.value)
                    } ?: throw IllegalStateException("render devolveu null")
                    val uri = withContext(Dispatchers.IO) {
                        ImageExport.saveToGallery(context, bitmap, ImageExport.fileNameFor(reuniao))
                    } ?: throw IllegalStateException("saveToGallery devolveu null")
                    salvas++
                } catch (e: Exception) {
                    Log.e(TAG_IMAGENS_MES, "Falha ao gerar a imagem de ${reuniao.date}", e)
                    falhas++
                }
            }
            enviando = false
            onDismiss()
            val destino = "Imagens/Designações JW"
            Toast.makeText(
                context,
                if (falhas == 0) "$salvas imagens salvas em $destino."
                else "$salvas salvas e $falhas falharam, em $destino.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Imagens de todas as reuniões") },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Sem seletor aqui: o mês é o mesmo que o usuário escolheu na
                // tela de baixo, e dois seletores na mesma tela discordariam.
                Text(
                    "${reunioes.size} reuniões em ${rotuloMes(mes)}. As imagens vão para a galeria.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                if (enviando) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(onClick = { gerar() }, enabled = !enviando) {
                Text(if (enviando) "Gerando…" else "Gerar e salvar")
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } }
    )
}

/** Mesma conta de mês do `PrintReportScreen`: a data é `dd/MM/yyyy`. */
private fun reunioesDoMes(meetings: List<Meeting>, mes: YearMonth): List<Meeting> {
    val prefixo = mes.format(DateTimeFormatter.ofPattern("MM/yyyy"))
    return meetings.filter { it.date.endsWith("/$prefixo") }
        .sortedBy { AssignmentGenerator.parseDate(it.date) }
}

/** Um seletor só, para os dois diálogos: os dois precisam do mesmo mês. */
@Composable
private fun SeletorMes(mes: YearMonth, onChange: (YearMonth) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(mes.minusMonths(1)) }) {
            Icon(Icons.Filled.ChevronLeft, "Mês anterior")
        }
        Text(rotuloMes(mes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        IconButton(onClick = { onChange(mes.plusMonths(1)) }) {
            Icon(Icons.Filled.ChevronRight, "Próximo mês")
        }
    }
}

private fun rotuloMes(mes: YearMonth): String = Datas.mesEAno(mes)
