package br.com.willendary.designacoesjw.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import br.com.willendary.designacoesjw.data.*
import br.com.willendary.designacoesjw.data.BuscaHistorico
import br.com.willendary.designacoesjw.ui.mostrarDesfazivel
import br.com.willendary.designacoesjw.desktop.components.CloudSyncBar
import br.com.willendary.designacoesjw.desktop.components.contar
import br.com.willendary.designacoesjw.desktop.export.ImageExportHelper
import br.com.willendary.designacoesjw.desktop.firebase.DesktopAuthManager
import br.com.willendary.designacoesjw.desktop.firebase.DesktopFirestoreClient
import br.com.willendary.designacoesjw.desktop.firebase.GoogleDesktopAuth
import br.com.willendary.designacoesjw.desktop.screens.DesktopEquityScreen
import br.com.willendary.designacoesjw.desktop.screens.DesktopKioskScreen
import br.com.willendary.designacoesjw.desktop.screens.DesktopPrintReportScreen
import br.com.willendary.designacoesjw.desktop.screens.DesktopUnavailabilityScreen
import br.com.willendary.designacoesjw.desktop.screens.GroupsAndCleaningScreen
import br.com.willendary.designacoesjw.desktop.screens.PublicTalksScreen
import br.com.willendary.designacoesjw.export.CsvDataHandler
import br.com.willendary.designacoesjw.export.HtmlReportGenerator
import br.com.willendary.designacoesjw.export.IcsExportHelper
import br.com.willendary.designacoesjw.export.MwbProgramImporter
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import br.com.willendary.designacoesjw.stats.EquityStatisticsHelper
import br.com.willendary.designacoesjw.sync.CoalescingWorker
import br.com.willendary.designacoesjw.sync.ContadorSincronizacao
import br.com.willendary.designacoesjw.sync.EstadoSincronizacao
import br.com.willendary.designacoesjw.sync.falhasDoPush
import br.com.willendary.designacoesjw.sync.mensagemDeFalhaNoPush
import br.com.willendary.designacoesjw.sync.mensagemDeNuvemVazia
import br.com.willendary.designacoesjw.sync.mesclarCloudComLocal
import br.com.willendary.designacoesjw.sync.Prune
import br.com.willendary.designacoesjw.sync.decidirPrune
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import br.com.willendary.designacoesjw.ui.MeetingProgramList
import br.com.willendary.designacoesjw.ui.JwCard
import br.com.willendary.designacoesjw.ui.JwCardRail
import br.com.willendary.designacoesjw.ui.JwCardTitle
import br.com.willendary.designacoesjw.ui.JwSectionLabel
import br.com.willendary.designacoesjw.ui.JwTheme
import br.com.willendary.designacoesjw.sync.Changelog
import br.com.willendary.designacoesjw.ui.JwThemeProvider
import br.com.willendary.designacoesjw.ui.MonthBoard
import br.com.willendary.designacoesjw.ui.corDeContorno
import br.com.willendary.designacoesjw.ui.superficieDeCartao
import br.com.willendary.designacoesjw.util.Datas
import br.com.willendary.designacoesjw.util.WhatsAppHelper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

/** Carência antes de enviar: uma rajada de edições vira um push só. */
private const val PUSH_DEBOUNCE_MS = 2000L
private val days = listOf(
    DayOfWeek.MONDAY to "Segunda-feira", DayOfWeek.TUESDAY to "Terça-feira",
    DayOfWeek.WEDNESDAY to "Quarta-feira", DayOfWeek.THURSDAY to "Quinta-feira",
    DayOfWeek.FRIDAY to "Sexta-feira", DayOfWeek.SATURDAY to "Sábado", DayOfWeek.SUNDAY to "Domingo"
)
private fun dayName(v: Int) = days.firstOrNull { it.first.value == v }?.second ?: "—"

private fun kindLabel(kind: PartKind): String = when (kind) {
    PartKind.INDIVIDUAL -> "Individual"
    PartKind.PAIR -> "Dupla"
    PartKind.DEMONSTRATION -> "Encenação"
    PartKind.GROUP -> "Grupo"
}

private fun readerGrantLabel(grant: ReaderGrant): String = when (grant) {
    ReaderGrant.NONE -> "Nenhum (só marcado)"
    ReaderGrant.BOOK -> "Leitor"
    ReaderGrant.SENTINEL -> "Leitor de A Sentinela"
}

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

    /**
     * Mensagem de erro exibida quando o `dados.json` não pôde ser lido no boot.
     * Nulo quando o carregamento foi bem-sucedido (ou o arquivo ainda não existe).
     */
    var loadError by mutableStateOf<String?>(null)
        private set

    /** Erro de ação para a UI; antes nada reportava falha de escrita. */
    private val actionErrorState = mutableStateOf<String?>(null)
    val actionError: String? get() = actionErrorState.value
    fun reportError(message: String) { actionErrorState.value = message }
    fun clearActionError() { actionErrorState.value = null }

    /**
     * Estado inicial, lido uma vez. [data] e [lastStore] partem daqui: sem
     * isso o worker de push nasceria com um Store vazio e sobrescreveria a
     * nuvem com lista vazia na primeira alteração.
     */
    private val initialStore: Store = load()

    var data by mutableStateOf(initialStore)
        private set

    // Só o arquivo: instantâneo e sem rede. Antes isto chamava loadSession(),
    // que renova o token por HTTP com timeout de 10 s — no inicializador, na
    // thread de UI, congelava a janela no boot. O catch protegia falha de
    // socket, não o freeze. A renovação e a sincronização inicial foram para
    // uma thread, no init.
    var authSession by mutableStateOf(DesktopAuthManager.loadSessionFromDisk())
        private set
    var syncStatus by mutableStateOf<String?>(
        if (authSession != null) "Conectado à nuvem" else "Offline"
    )
        private set
    var isSyncing by mutableStateOf(false)
        private set

    // Serializa as gravações em disco: várias threads (rede, import, timers)
    // chamam save() ao mesmo tempo e, sem lock, duas escritas se intercalam
    // e corrompem o arquivo de forma permanente.
    private val writeLock = Any()

    // ── Push para a nuvem: um worker só, com debounce ────────────────────────
    //
    // Antes cada save() abria uma thread nova, que lia o estado VIVO `data` a
    // cada etapa do push. Três consequências ruins:
    //   1. Mudar o dia da semana disparava dezenas de threads, cada uma com 7
    //      batches sequenciais — rajada de requests e "Too many requests".
    //   2. Duas threads liam versões diferentes no meio do push, e o que fosse
    //      gravado por último podia ser o mais antigo. Update fora de ordem.
    //   3. Os prunes de cada thread competiam entre si, e uma podia apagar o
    //      que a outra acabara de gravar.
    //
    // A lógica está em CoalescingWorker (shared), onde tem testes: rajada vira
    // uma execução, nunca duas ao mesmo tempo, e mudança durante a execução
    // gera exatamente um retrabalho — nunca uma fila.
    @Volatile
    private var lastStore: Store = initialStore

    /**
     * A última falha de push veio de token recusado (HTTP 401)?
     *
     * [AtomicBoolean] e não um `var` porque é escrito na thread de push e lida na
     * mesma thread — mas ele também sobrevive a uma leitura de outra, e um campo
     * comum daria ao JVM a liberdade de emendar a leitura.
     */
    private val ultimaFalhaFoiDeToken = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * O que a tela mostra sobre a sincronização.
     *
     * [EstadoSincronizacao] e [ContadorSincronizacao] já existiam, com teste, e
     * são usados sete vezes no Android. No desktop havia só
     * `pendingLocalChange`: um booleano interno, sem contador, sem rótulo e sem
     * nenhum hook de UI — lido apenas dentro de `syncWithCloud`. Com a carência de
     * 2 s do [CoalescingWorker], o usuário editava, fechava o programa, e o push
     * podia não ter saído: **sem como ele saber**.
     *
     * Reaproveitar o mesmo tipo é o ponto. "1 alteração não enviada" e "Falha ao
     * enviar" são palavras que já foram escritas e testadas para o outro app; o
     * desktop não devia inventar um terceiro vocabulário para a mesma coisa.
     *
     * `var syncStatus` continua existindo e alimentando o rodapé antigo — trocar
     * a tela inteira é DJW-034, e as duas fontes dizem a mesma coisa agora.
     */
    private val sync = ContadorSincronizacao()

    /**
     * Há alteração local ainda não confirmada pelo servidor?
     *
     * Sem isto, um sync que chega da nuvem depois de uma edição local
     * sobrescreve a edição: o usuário mudava os dias da semana, o push ficava
     * 2 s na carência do CoalescingWorker, o pull retornava o valor antigo da
     * nuvem e a edição sumia da tela. O pull precisa saber que a local é mais
     * nova.
     */
    @Volatile
    private var pendingLocalChange = false

    private val pushWorker = CoalescingWorker<Store>(
        debounceMs = PUSH_DEBOUNCE_MS,
        current = { lastStore },
        action = { snapshot ->
            val token = tokenParaPush() ?: run {
                sync.falhou("sem sessão: entre de novo para sincronizar")
                throw PushIncompleto(listOf("sessão"))
            }
            sync.emGravacao()
            publicarEstadoSinc()
            var falhas = pushToCloud(token, snapshot)

            // Token revogado não se resolve com tempo: o servidor respondeu 401
            // mesmo com o token ainda no prazo. Uma renovação e uma nova tentativa,
            // e só uma — se não resolveu com o token novo, o problema é outro.
            if (falhas.isNotEmpty() && tokenFoiRecusado()) {
                // Só 401 entra aqui: 403 é falta de permissão, e renovar o token
                // não muda nada — dizer "renovei" seria mentira.
                val renovada = DesktopAuthManager.renovarAgora(authSession)
                if (renovada != null) {
                    Edt.publica { authSession = renovada }
                    if (renovada.idToken != token) falhas = pushToCloud(renovada.idToken, snapshot)
                }
            }

            // Lançar é o que faz o worker parar de fingir que deu certo: o
            // `pendingLocalChange = false` abaixo só roda se o push inteiro
            // passou, e o `onError` recebe o motivo.
            if (falhas.isNotEmpty()) throw PushIncompleto(falhas)
            pendingLocalChange = false
            sync.confirmada()
            publicarEstadoSinc()
        },
        onError = { e ->
            val colecoes = (e as? PushIncompleto)?.colecoes
            System.err.println("[StoreController] push falhou: ${e.message}")
            // `falhou` **não** volta o contador: a escrita continua não enviada, e
            // é isso que a pessoa precisa saber — o dado está seguro, falta
            // chegar aos outros.
            sync.falhou(e.message ?: "falha ao enviar")
            publicarEstadoSinc()
            // Continua marcado como pendente: o servidor ainda não tem isso.
            publicarFalhaDePush(
                if (colecoes != null) mensagemDeFalhaNoPush(colecoes)
                else "Não consegui enviar a alteração para a nuvem. Ela ficou só neste computador."
            )
        }
    )

    /**
     * Publica o [EstadoSincronizacao] na thread de UI.
     *
     * Existe como função porque o estado muda na thread do push e o Compose só
     * recompõe na EDT — e porque "publicar" aparece em quatro lugares, que é
     * exatamente onde uma assinatura esquecida passaria despercebida.
     */
    private fun publicarEstadoSinc() {
        Edt.publica { estadoSincronizacaoState.value = sync.atual }
    }

    /** Espelho do contador, em estado do Compose, para a barra reagir. */
    private val estadoSincronizacaoState =
        mutableStateOf(EstadoSincronizacao())

    /** O que a barra mostra. Lê o espelho, não o contador. */
    val estadoSincronizacao: EstadoSincronizacao get() = estadoSincronizacaoState.value

    /**
     * Token válido para o push, renovando se estiver perto de expirar. Faz rede.
     *
     * **É isto que mantinha o app quebrado depois de uma hora de uso.** A
     * renovação só acontecia no `init`, uma vez. O `idToken` do Firebase dura 1 h;
     * passado isso, todo `PATCH` voltava 401, sem retry e sem aviso — e como o
     * push descartava o resultado (DJW-002), o usuário via "Sincronizado" e a
     * congregação ficava só no aparelho até ele fechar e abrir o programa.
     *
     * @return o `idToken` a usar, ou `null` se não há sessão.
     */
    private fun tokenParaPush(): String? {
        val sessao = authSession ?: return null
        val renovada = DesktopAuthManager.refreshIfNeeded(sessao) ?: return null
        if (renovada != sessao) {
            // `authSession` é estado do Compose; a troca precisa ser na EDT.
            Edt.publica { authSession = renovada }
        }
        return renovada.idToken
    }

    /**
     * O último push foi recusado por token (HTTP 401)? Serve de gatilho para uma
     * renovação e uma nova tentativa.
     */
    private fun tokenFoiRecusado(): Boolean = ultimaFalhaFoiDeToken.get()

    /**
     * Pede um push.
     *
     * Sem token: o `pushWorker` chama [tokenParaPush] na hora de executar, e não
     * no momento do agendamento. A diferença é a会话 de uma hora — com o token
     * guardado no agendamento, o push usava sempre o token do último `save`, e
     * uma hora depois de aberto o app subia tudo com um token vencido.
     */
    private fun schedulePush() {
        pendingLocalChange = true
        pushWorker.schedule()
    }

    init {
        val diskSession = authSession
        if (diskSession != null) {
            // Renovar o token e sincronizar é rede. Fora da thread de UI, para
            // a janela abrir na hora; a UI é avisada quando terminar.
            Thread {
                val fresh = DesktopAuthManager.refreshIfNeeded(diskSession)
                Edt.publica {
                    if (fresh != null) {
                        authSession = fresh
                        syncStatus = "Conectado à nuvem"
                    } else {
                        authSession = null
                        syncStatus = "Offline"
                    }
                }
                syncWithCloud()
            }.apply {
                isDaemon = true
                name = "boot-sync"
                start()
            }
        }
    }

    private fun load(): Store {
        if (!file.exists()) return Store()
        return try {
            json.decodeFromString<Store>(file.readText())
        } catch (e: Exception) {
            // Preserva o arquivo corrompido em vez de devolver um Store vazio
            // silencioso: o usuário veria a lista vazia e poderia sobrescrever
            // o arquivo bom na próxima gravação.
            val backup = File(file.parentFile, "dados.json.corrompido-${System.currentTimeMillis()}")
            val renamed = runCatching { file.renameTo(backup) }.getOrDefault(false)
            if (!renamed) runCatching { file.copyTo(backup, overwrite = true) }
            loadError = "Não foi possível ler os dados salvos. Um backup foi criado em ${backup.name}."
            Store()
        }
    }

    private fun save(s: Store) {
        data = s
        lastStore = s
        val gravou = saveLocal(s)

        // O push acontece mesmo sem gravação local, e por um motivo que vale
        // registrar: o push usa o `Store` em memória, não o arquivo, então não
        // corre o risco de subir meio arquivo. E se o disco falhou, a nuvem é a
        // **única** cópia que vai sobreviver a um fechamento do programa.
        // Não publicar seria jogar fora a chance de não perder o dado.
        val sessao = authSession
        if (sessao != null) schedulePush()

        if (!gravou) {
            Edt.publica {
                reportError(
                    if (sessao != null) {
                        "Não consegui gravar os dados neste computador, mas enviei a alteração para a nuvem. " +
                            "Se fechar o programa agora, o que está no disco pode estar atrás."
                    } else {
                        "Não consegui gravar os dados neste computador. " +
                            "A alteração está só nesta janela: copie o que importa antes de fechar."
                    }
                )
            }
        }
    }

    /** [true] se o `dados.json` foi gravado. */
    private fun saveLocal(s: Store): Boolean {
        return try {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "dados.json.tmp")
            synchronized(writeLock) {
                tmp.writeText(json.encodeToString(s))
                try {
                    // Escrita atômica: grava em temporário e faz rename por cima,
                    // evitando deixar o dados.json pela metade se o app fechar no
                    // meio da escrita.
                    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
                    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }
            true
            // ponytail: sem fsync explícito. O rename atômico garante que o arquivo
            // nunca fica truncado, mas uma queda de energia logo após o rename pode
            // perder a última gravação (o SO ainda não fez flush). Custo de fsync:
            // uma chamada FileChannel.force() por gravação — adicionar se durabilidade
            // imediata virar requisito.
        } catch (e: Exception) {
            // Antes isto não tinha catch: `tmp.writeText` e `Files.move` propagavam
            // para o onClick do Compose. Disco cheio, pasta bloqueada pelo antivírus
            // ou OneDrive com conflito de sync viravam exceção na thread de UI,
            // sem snackbar e sem registro. O comentário de `reportError` na classe
            // falava em "falha de escrita" — e este caminho nunca usava ele.
            log("saveLocal falhou: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    fun login(email: String, pass: String): String? {
        val res = DesktopAuthManager.signInWithEmail(email, pass)
        return res.fold(
            onSuccess = { session ->
                authSession = session
                DesktopAuthManager.saveSessionPublic(session)
                syncWithCloud()
                null
            },
            onFailure = { it.message ?: "Falha ao entrar" }
        )
    }

    /**
     * Inicia o fluxo OAuth Google no browser e persiste a sessão.
     * Deve ser chamado em uma thread de background.
     * Retorna null em caso de sucesso, ou a mensagem de erro.
     */
    fun loginWithGoogle(onAuthUrl: (String) -> Unit = {}, onResult: (error: String?) -> Unit) {
        kotlin.concurrent.thread {
            val result = GoogleDesktopAuth.signInWithGoogle(onAuthUrl)
            if (result.session != null) {
                DesktopAuthManager.saveSessionPublic(result.session)
                Edt.publica {
                    authSession = result.session
                    syncWithCloud { ok, err ->
                        onResult(if (ok) null else err)
                    }
                }
            } else {
                onResult(result.error ?: "Falha ao entrar com Google.")
            }
        }
    }

    fun cancelGoogleLogin() = GoogleDesktopAuth.cancel()

    /**
     * Baixa o programa da Reunião Vida e Ministério da semana de [meetingId] no jw.org
     * e grava tema + itens na reunião. [onResult] recebe null em sucesso, ou a mensagem de erro.
     */
    fun importMwbProgram(meetingId: Long, onResult: (error: String?) -> Unit) {
        val meeting = data.meetings.firstOrNull { it.id == meetingId }
        if (meeting == null) {
            onResult("Reunião não encontrada.")
            return
        }
        val date = meeting.date
        kotlin.concurrent.thread {
            // Rede fora da EDT; save() e o callback voltam para a EDT — escrever
            // estado do Compose (data, importingId) de thread crua não recompoe.
            val result = runCatching {
                val program = MwbProgramImporter.fetch(AssignmentGenerator.parseDate(date))
                program to program.parts.map { p ->
                    ProgramItem(section = p.section, number = p.number, title = p.title, minutes = p.minutes)
                }
            }
            Edt.publica {
                result.fold(
                    onSuccess = { (program, items) ->
                        save(data.copy(meetings = data.meetings.map {
                            if (it.id == meetingId) it.copy(theme = program.theme, program = items) else it
                        }))
                        onResult(null)
                    },
                    onFailure = { onResult(it.message ?: "Não foi possível ler o programa no jw.org.") }
                )
            }
        }
    }

    fun logout() {
        DesktopAuthManager.logout()
        authSession = null
        syncStatus = "Desconectado"
    }

    fun syncWithCloud(onComplete: ((Boolean, String?) -> Unit)? = null) {
        // Sem fallback para loadSession(): aquele caminho renova o token por
        // rede, e syncWithCloud é chamado de botoes na thread de UI. A sessao
        // ja e renovada no init, em background.
        val session = authSession
        if (session == null) {
            syncStatus = "Offline"
            onComplete?.invoke(false, "Usuário não autenticado.")
            return
        }
        isSyncing = true
        syncStatus = "Sincronizando..."

        kotlin.concurrent.thread {
            val res = DesktopFirestoreClient.fetchCloudStore(session.idToken)
            res.fold(
                onSuccess = { cloudStore ->
                    // Push disparado aqui dentro do sync: o resultado conta igual.
                    // Antes, um pull que acabasse em push recusado dizia
                    // "Sincronizado" e zerava o pendente.
                    var falhas: List<String> = emptyList()
                    if (pendingLocalChange) {
                        // Tem edição local que o push ainda não levou. O que
                        // veio da nuvem é mais velho: aplicar aqui apagaria a
                        // edição da tela (issue #17). Envia a local e preserva.
                        log("sync: alteracao local pendente; enviando local em vez de sobrescrever")
                        falhas = pushToCloud(session.idToken, lastStore)
                    } else if (cloudStoreHasAlgo(cloudStore)) {
                        // `data` e estado do Compose: tem de ser escrito na EDT.
                        // `lastStore` é @Volatile e pode ficar aqui.
                        val localAGora = data
                        val merge = mesclarCloudComLocal(cloudStore, localAGora)
                        val gravou = saveLocal(merge.store)
                        Edt.publica {
                            data = merge.store
                            lastStore = merge.store
                            if (merge.preservadas.isNotEmpty()) {
                                // A nuvem voltou vazia em uma coleção. Não é erro,
                                // mas é o tipo de coisa que o usuário precisa saber
                                // para não achar que o cadastro sumiu.
                                reportError(mensagemDeNuvemVazia(merge.preservadas))
                            } else if (!gravou) {
                                // Veio dado bom da nuvem e ele não coube no disco:
                                // a tela mostra o certo, o arquivo está atrás, e o
                                // próximo boot pode reverter a tela sem explicação.
                                reportError(
                                    "Sincronizei, mas não consegui gravar o resultado neste computador. " +
                                        "Se o programa for fechado agora, ele pode abrir com os dados anteriores."
                                )
                            }
                        }
                    } else {
                        falhas = pushToCloud(session.idToken, lastStore)
                    }
                    val msg = if (falhas.isNotEmpty()) mensagemDeFalhaNoPush(falhas) else null
                    sync.lidaDoServidor()
                    if (msg != null) sync.falhou(msg)
                    Edt.publica {
                        isSyncing = false
                        estadoSincronizacaoState.value = sync.atual
                        if (msg != null) {
                            // A nuvem pode ter trazido dado bom mesmo assim; o que
                            // falhou foi enviar o que o usuário acabou de editar.
                            syncStatus = "Falha ao enviar"
                            reportError(msg)
                            onComplete?.invoke(false, msg)
                        } else {
                            syncStatus = "Sincronizado"
                            onComplete?.invoke(true, null)
                        }
                    }
                },
                onFailure = { err ->
                    Edt.publica {
                        syncStatus = "Erro de sincronização"
                        isSyncing = false
                        onComplete?.invoke(false, err.message)
                    }
                }
            )
        }
    }

    /**
     * @param snapshot o que será enviado. Passar a referência é seguro: [Store]
     * é uma data class imutável, e ler o estado vivo `data` aqui permitia que
     * a lista mudasse no meio do push — a mesma.push gravando metade do estado
     * antigo e metade do novo.
     */
    /**
     * @param snapshot o que será enviado. Passar a referência é seguro: [Store]
     * é uma data class imutável, e ler o estado vivo `data` aqui permitia que
     * a lista mudasse no meio do push — a mesma.push gravando metade do estado
     * antigo e metade do novo.
     * @return os rótulos das coleções que o servidor recusou. Vazio = tudo subiu.
     *
     * **Por que devolver.** Cada `push*` é um `runCatching`, então nenhuma
     * exceção escapa: o resultado era descartado e uma recusa do Firestore
     * (403 por falta de permissão, 401 por token vencido, 400 por regra) virava
     * silêncio. O `pendingLocalChange` era zerado, a barra dizia "Sincronizado",
     * e o usuário acreditava que estava na nuvem. O `onError` do worker nunca
     * disparava para falha de HTTP, porque falha de HTTP não era exceção.
     */
    private fun pushToCloud(token: String, snapshot: Store): List<String> {
        ultimaFalhaFoiDeToken.set(false)
        // Um `runCatching` por coleção: `runCatching` na volta inteira esconderia
        // a falha de uma e deixaria as outras sem registro, que é exatamente o
        // defeito que isto corrige.
        val resultados = listOf(
            "irmãos" to runCatching { DesktopFirestoreClient.pushBrothers(token, snapshot.brothers) }.getOrElse { logFalhaDePush("irmãos", it); Result.failure(it) },
            "privilégios" to runCatching { DesktopFirestoreClient.pushPrivileges(token, snapshot.privileges) }.getOrElse { logFalhaDePush("privilégios", it); Result.failure(it) },
            "reuniões" to runCatching { DesktopFirestoreClient.pushMeetings(token, snapshot.meetings) }.getOrElse { logFalhaDePush("reuniões", it); Result.failure(it) },
            "discursos" to runCatching { DesktopFirestoreClient.pushPublicTalks(token, snapshot.publicTalks) }.getOrElse { logFalhaDePush("discursos", it); Result.failure(it) },
            "grupos de campo" to runCatching { DesktopFirestoreClient.pushFieldServiceGroups(token, snapshot.fieldServiceGroups) }.getOrElse { logFalhaDePush("grupos de campo", it); Result.failure(it) },
            "escala de limpeza" to runCatching { DesktopFirestoreClient.pushCleaningSchedules(token, snapshot.cleaningSchedules) }.getOrElse { logFalhaDePush("escala de limpeza", it); Result.failure(it) },
            "configurações" to runCatching { DesktopFirestoreClient.pushScheduleSettings(token, snapshot.firstDay, snapshot.secondDay) }.getOrElse { logFalhaDePush("configurações", it); Result.failure(it) }
        )

        val falhas = falhasDoPush(resultados)

        // A exclusão só é tentada nas coleções que subiram inteiras. Apagar o que
        // "sobrou" depois de um push parcial apagaria o que não conseguiu ser
        // reenviado — o oposto do que se quer.
        if (falhas.isEmpty()) apararNoServidor(token, snapshot)

        return falhas
    }

    /**
     * A nuvem devolveu alguma coisa que vale aplicar?
     *
     * Todas as coleções, não três delas. O teste antigo era
     * `brothers.isNotEmpty() || meetings.isNotEmpty() || privileges.isNotEmpty()`,
     * e uma nuvem que só tinha discursos passava nele: o Store inteiro substituía o
     * local e os irmãos sumiam da tela. Uma coleção decidindo sobre todas.
     */
    private fun cloudStoreHasAlgo(cloud: Store): Boolean =
        cloud.brothers.isNotEmpty() ||
            cloud.privileges.isNotEmpty() ||
            cloud.meetings.isNotEmpty() ||
            cloud.publicTalks.isNotEmpty() ||
            cloud.fieldServiceGroups.isNotEmpty() ||
            cloud.cleaningSchedules.isNotEmpty()

    /**
     * Apaga do servidor o que o aparelho não tem mais.
     *
     * **Sem isto o desktop não excluía nada.** O cliente só fazia `PATCH`, então
     * apagar um irmão, um privilégio ou uma reunião deixava o documento lá, e o
     * pull seguinte trazia o item de volta para a tela. Quem excluía achava que
     * tinha excluído.
     *
     * Por coleção, e cada decisão vai para [decidirPrune] — inclusive a recusa
     * de apagar com o aparelho vazio, que é a que impede que um erro de leitura
     * apague a congregação inteira.
     */
    private fun apararNoServidor(token: String, snapshot: Store) {
        // `toString()` porque é assim que o `push*` nomeou o documento: o
        // `keep` do prune tem de ser a mesma chave que a escrita usou.
        apararColecao(token, "brothers", "irmãos", snapshot.brothers.map { it.id.toString() }.toSet())
        apararColecao(token, "privileges", "privilégios", snapshot.privileges.map { it.id.toString() }.toSet())
        apararColecao(token, "meetings", "reuniões", snapshot.meetings.map { it.id.toString() }.toSet())
        apararColecao(token, "publicTalks", "discursos", snapshot.publicTalks.map { it.id.toString() }.toSet())
        apararColecao(token, "fieldServiceGroups", "grupos de campo", snapshot.fieldServiceGroups.map { it.id.toString() }.toSet())
        apararColecao(token, "cleaningSchedules", "escala de limpeza", snapshot.cleaningSchedules.map { it.id.toString() }.toSet())
    }

    private fun apararColecao(token: String, collection: String, rotulo: String, keep: Set<String>) {
        val lidos = DesktopFirestoreClient.fetchIds(token, collection)
        val idsNoServidor = lidos.getOrNull() ?: run {
            log("prune: nao consegui ler $rotulo do servidor: ${lidos.exceptionOrNull()?.message}")
            return
        }

        when (val decisao = decidirPrune(idsNoServidor, keep, rotulo)) {
            is Prune.Apagar -> {
                log("prune: $rotulo, apagando ${decisao.ids.size} do servidor")
                for (id in decisao.ids) {
                    val r = runCatching { DesktopFirestoreClient.deleteDocument(token, collection, id) }
                    if (r.isFailure) log("prune: $rotulo/$id nao apagado: ${r.exceptionOrNull()?.message}")
                }
            }
            // Só a recusa com motivo vira mensagem. "Não havia nada" e "não
            // sobrou nada" são o caminho normal, não um problema para o usuário.
            is Prune.Preservar -> {
                if (keep.isEmpty() && idsNoServidor.isNotEmpty()) {
                    log("prune: $rotulo preservado: ${decisao.motivo}")
                    publicarFalhaDePush(decisao.motivo)
                }
            }
        }
    }

    private fun logFalhaDePush(rotulo: String, erro: Throwable) {
        log("push: $rotulo recusado: ${erro.javaClass.simpleName}: ${erro.message}")
        if (erro.message?.contains("HTTP 401") == true) {
            ultimaFalhaFoiDeToken.set(true)
        }
    }

    /** Push incompleto: alguma coleção ficou só neste aparelho. */
    private class PushIncompleto(val colecoes: List<String>) :
        Exception("não subiu: ${colecoes.joinToString(", ")}")

    /** Leva a falha do push para a tela, na thread de UI. */
    private fun publicarFalhaDePush(mensagem: String) {
        Edt.publica {
            syncStatus = "Falha ao enviar"
            reportError(mensagem)
        }
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

    /**
     * O que a última exclusão deixou para trás, enquanto o snackbar estiver na
     * tela. Mesmo contrato do `AppViewModel` do Android, e pelo mesmo motivo:
     * o snackbar fica no topo da janela e a exclusão acontece na lista.
     *
     * **Um slot só.** Duas exclusões seguidas não se acumulam — desfazer a
     * segunda desfaz a última e a primeira some. É o que "desfazer" quer dizer.
     */
    var undoPendente by mutableStateOf<Undo?>(null)
        private set

    sealed interface Undo {
        data class Irmao(val dados: Brother) : Undo
        data class Privilegio(val dados: Privilege, val afetados: List<Brother>) : Undo
        data class Reuniao(val dados: Meeting) : Undo
    }

    /** Aplica o desfazer pendente. Sem efeito se não houver nada pendente. */
    fun desfazer() {
        when (val u = undoPendente) {
            is Undo.Irmao ->
                if (data.brothers.none { it.id == u.dados.id }) {
                    save(data.copy(brothers = data.brothers + u.dados))
                }
            is Undo.Reuniao ->
                if (data.meetings.none { it.id == u.dados.id }) {
                    save(data.copy(meetings = data.meetings + u.dados))
                }
            is Undo.Privilegio -> {
                // O privilégio **e** as habilitações que a exclusão tirou. Sem
                // as habilitações o irmão volta sem nada que possa fazer, que
                // é pior que não voltar: parece dado correto e designa errado.
                val devolvidos = data.brothers.map { b ->
                    if (u.afetados.any { it.id == b.id } && u.dados.id !in b.privileges) {
                        b.copy(privileges = b.privileges + u.dados.id)
                    } else b
                }
                save(data.copy(
                    privileges = data.privileges + u.dados,
                    brothers = devolvidos
                ))
            }
            null -> Unit
        }
        undoPendente = null
    }

    /** O snackbar fechou sozinho: passou a janela, é definitiva. */
    fun descartarUndo() { undoPendente = null }

    fun deleteBrother(id: Long) {
        val removido = data.brothers.firstOrNull { it.id == id } ?: return
        save(data.copy(brothers = data.brothers.filterNot { it.id == id }))
        undoPendente = Undo.Irmao(removido)
    }

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

    fun setBrotherBaptized(id: Long, value: Boolean) = save(
        data.copy(brothers = data.brothers.map { if (it.id == id) it.copy(baptized = value) else it })
    )

    fun setBrotherTrainee(id: Long, value: Boolean) = save(
        data.copy(brothers = data.brothers.map { if (it.id == id) it.copy(trainee = value) else it })
    )

    fun setBrotherIsReader(id: Long, value: Boolean) = save(
        data.copy(brothers = data.brothers.map { b ->
            // Leitor de A Sentinela é leitor: manter os dois coerentes.
            if (b.id != id) b
            else if (!value) b.copy(isReader = false, isSentinelReader = false)
            else b.copy(isReader = true)
        })
    )

    fun setBrotherIsSentinelReader(id: Long, value: Boolean) = save(
        data.copy(brothers = data.brothers.map { b ->
            if (b.id != id) b
            else b.copy(isSentinelReader = value, isReader = if (value) true else b.isReader)
        })
    )

    fun setPrivilegeKind(id: Long, kind: PartKind) = save(
        data.copy(privileges = data.privileges.map { if (it.id == id) it.copy(kind = kind) else it })
    )

    fun setPrivilegeReaderGrant(id: Long, grant: ReaderGrant) = save(
        data.copy(privileges = data.privileges.map { if (it.id == id) it.copy(readerGrant = grant) else it })
    )

    fun setPrivilegeAllowedStatus(id: Long, status: Set<BrotherStatus>) = save(
        data.copy(privileges = data.privileges.map { if (it.id == id) it.copy(allowedStatus = status) else it })
    )

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
        val removido = data.privileges.firstOrNull { it.id == id } ?: return
        val afetados = data.brothers.filter { id in it.privileges }
        save(data.copy(
            privileges = data.privileges.filterNot { it.id == id },
            brothers = data.brothers.map { it.copy(privileges = it.privileges - id) }
        ))
        undoPendente = Undo.Privilegio(removido, afetados)
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

    /**
     * Vai para stderr **e** para um arquivo.
     *
     * O app empacotado quase nunca é aberto a partir de um prompt, então só o
     * stderr não resolve: quando o usuário relatava "não consegui gerar a
     * imagem" não havia como saber a causa. O arquivo fica em
     * `~/.designacoes-jw/erros.log`, é limitado a 200 KB e mantém as últimas
     * linhas.
     */
    private fun log(message: String) {
        val line = "[${java.time.LocalDateTime.now()}] $message"
        System.err.println("[StoreController] $message")
        runCatching {
            val dir = File(System.getProperty("user.home"), ".designacoes-jw")
            dir.mkdirs()
            val logFile = File(dir, "erros.log")
            if (logFile.length() > 200_000) {
                // Mantem a metade mais recente: o começo é o que já foi lido.
                val lines = logFile.readLines()
                logFile.writeText(lines.drop(lines.size / 2).joinToString("\n"))
            }
            logFile.appendText("$line\n")
        }
    }

    /** Detalhe de falha da exportação de imagem, com stack trace, em `erros.log`. */
    fun logImageError(what: String, e: Throwable) {
        log("$what: ${e.javaClass.name}: ${e.message}")
        e.stackTrace.take(12).forEach { log("    em $it") }
    }

    fun logImageOk(meetingDate: String, caminho: String) {
        log("imagem gerada para a reuniao $meetingDate -> $caminho")
    }

    /**
     * O diálogo guarda os dias em `remember` sem chave. Se o valor mudar
     * enquanto o diálogo está aberto — o que acontece quando um sync da nuvem
     * atualiza a configuração — a tela continuaria mostrando o valor antigo e
     * o "Salvar" gravaria por cima da edição.
     */
    fun setMeetingDays(first: Int, second: Int) {
        if (first == second) {
            log("dias de reuniao ignorados: primeiro e segundo sao o mesmo ($first)")
            return
        }
        save(data.copy(firstDay = first, secondDay = second))
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

    /**
     * Liga ou desliga [brotherId] da parte [position] (1-based) do programa.
     *
     * Sem guarda de permissão: o desktop é local. Não bloqueia por qualificação
     * teocrática — a parte do programa é aberta a qualquer irmão ativo, e quem
     * conduz a reunião decide quem faz a parte.
     */
    fun toggleProgramAssignment(meetingId: Long, position: Int, brotherId: Long) {
        save(data.copy(meetings = data.meetings.map { m ->
            if (m.id != meetingId) m else m.copy(
                programAssignments = togglePart(m.programAssignments, position, brotherId)
            )
        }))
    }

    /** Alterna um irmão numa parte, criando a parte se ainda não existir. */
    private fun togglePart(
        current: List<ProgramAssignment>,
        position: Int,
        brotherId: Long
    ): List<ProgramAssignment> {
        val ids = current.firstOrNull { it.item == position }?.brotherIds.orEmpty()
        val next = if (brotherId in ids) ids - brotherId else ids + brotherId
        // Parte sem ninguém sai da lista: é lixo e voltaria sozinho no próximo clique.
        val others = current.filterNot { it.item == position }
        return others + if (next.isEmpty()) emptyList() else listOf(ProgramAssignment(position, next))
    }

    fun deleteMeeting(meetingId: Long) {
        val removido = data.meetings.firstOrNull { it.id == meetingId } ?: return
        save(data.copy(meetings = data.meetings.filterNot { it.id == meetingId }))
        undoPendente = Undo.Reuniao(removido)
    }

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
                // Reaproveita o id da escala que já existe para a mesma semana, como
                // o Android faz. Com id novo a cada geração, e sem exclusão no
                // desktop, a mesma semana aparecia duplicada e o lixo acumulava.
                id = data.cleaningSchedules.firstOrNull { it.weekDate == m.date }?.id
                    ?: AssignmentGenerator.nextId(),
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

/**
 * Sair com alteração que não subiu.
 *
 * Três saídas, porque há três decisões diferentes:
 * - **Sincronizar e sair** — o caminho que preserva o dado;
 * - **Sair assim mesmo** — o usuário sabe o que está fazendo e pode sincronizar
 *   depois de abrir de novo;
 * - **Cancelar** — o padrão de todo diálogo, porque um modal sem caminho de
 *   volta é um erro.
 */
@Composable
private fun DialogoSaidaComPendencia(
    rotulo: String,
    aoEsperar: () -> Unit,
    aoSairAssimMesmo: () -> Unit,
    aoCancelar: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = aoCancelar) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
            modifier = Modifier.width(420.dp).padding(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CloudOff, null, tint = JwTheme.colors.alerta)
                    Text("Ainda não enviei para a nuvem", fontWeight = FontWeight.Bold)
                }
                Text("Estado atual: $rotulo.")
                Text(
                    "Se fechar agora, essa alteração fica só neste computador. " +
                        "Abrindo o programa de novo ela volta a ser enviada.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = aoCancelar) { Text("Cancelar") }
                    TextButton(onClick = aoSairAssimMesmo) { Text("Sair assim mesmo") }
                    Button(onClick = aoEsperar) { Text("Sincronizar e sair") }
                }
            }
        }
    }
}

fun main() = application {
    val controller = remember { StoreController() }
    val c = controller
    var updateInfo by remember { mutableStateOf<WindowsUpdateInfo?>(null) }
    var checkingUpdate by remember { mutableStateOf(true) }
    var mostrarConfirmacaoDeSaida by remember { mutableStateOf(false) }

    // "O que ha de novo" e a checagem de atualizacao sao a mesma ida ao
    // servidor. Uma so chamada na abertura, nao duas.
    var novidades by remember { mutableStateOf<Changelog?>(null) }

    LaunchedEffect(Unit) {
        // checkForUpdate faz GET no GitHub com timeout de 8 s. Em
        // LaunchedEffect(Unit) rodava na main e travava a janela na abertura.
        val info = withContext(Dispatchers.IO) { WindowsUpdateManager.checkForUpdate() }
        updateInfo = info
        checkingUpdate = false
        novidades = withContext(Dispatchers.IO) { carregarChangelogSeMostrar(c) }
    }

    val isDark = when (c.data.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    Window(
        // Fechar com alteração não enviada é a forma de perder o dado: o push
        // tem 2 s de carência, e fechar mata o worker antes dele sair. O aviso
        // dá uma escolha — sair esperando a barra confirmar, ou sair mesmo assim
        // sabendo que vai precisar sincronizar de novo.
        onCloseRequest = {
            val estado = controller.estadoSincronizacao
            if (estado.mereceAviso) {
                mostrarConfirmacaoDeSaida = true
            } else {
                exitApplication()
            }
        },
        title = "Designações JW $CURRENT_VERSION",
        state = rememberWindowState(width = 1280.dp, height = 800.dp)
    ) {
        // Paleta e modo escuro vêm de shared, o mesmo caminho do Android.
        // Aqui havia uma lista de cor própria, e as duas já não eram iguais.
        JwThemeProvider(isDark = isDark) {
            // Erro de ação. O StoreController.reportError() existia, mas
            // nada lia o estado — a mensagem sumia. Sem isto, "não consegui
            // gerar a imagem" e os erros de permissão eram engolidos.
            val snackbarHostState = remember { SnackbarHostState() }

            // Desfazer. Sem diálogo de confirmação, pelo mesmo motivo do
            // Android: para ação reversível, confirmar **e** oferecer desfazer
            // é pedir a mesma coisa duas vezes.
            LaunchedEffect(c.undoPendente) {
                val undo = c.undoPendente ?: return@LaunchedEffect
                val rotulo = when (undo) {
                    is StoreController.Undo.Irmao -> "Irmão excluído"
                    is StoreController.Undo.Privilegio -> "Privilégio excluído"
                    is StoreController.Undo.Reuniao -> "Reunião excluída"
                }
                val desfez = snackbarHostState.mostrarDesfazivel(rotulo) { c.desfazer() }
                if (!desfez) c.descartarUndo()
            }

            LaunchedEffect(c.actionError) {
                c.actionError?.let {
                    snackbarHostState.showSnackbar(it, withDismissAction = true, duration = SnackbarDuration.Long)
                    c.clearActionError()
                }
            }
            Box(Modifier.fillMaxSize()) {
                DesktopApp(c, onShowUpdate = { updateInfo = it })
                SnackbarHost(
                    snackbarHostState,
                    Modifier.align(Alignment.BottomCenter).padding(20.dp)
                )
            }
            novidades?.let { changelog ->
                DialogoNovidades(changelog, aoFechar = { novidades = null })
            }
            if (mostrarConfirmacaoDeSaida) {
                DialogoSaidaComPendencia(
                    rotulo = c.estadoSincronizacao.rotulo,
                    aoEsperar = {
                        mostrarConfirmacaoDeSaida = false
                        c.syncWithCloud { _, _ -> mostrarConfirmacaoDeSaida = true }
                    },
                    aoSairAssimMesmo = { exitApplication() },
                    aoCancelar = { mostrarConfirmacaoDeSaida = false }
                )
            }
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
                        JwCard {
                            Text("Versão instalada: v$CURRENT_VERSION", style = MaterialTheme.typography.bodySmall)
                            Text("Nova versão: v${info.version}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            if (info.sizeBytes > 0) {
                                Text("Tamanho aproximado: ${WindowsUpdateManager.formatBytes(info.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
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
    var showLoadError by remember { mutableStateOf(c.loadError != null) }

    if (showLoadError) {
        AlertDialog(
            onDismissRequest = { showLoadError = false },
            title = { Text("Erro ao carregar dados") },
            text = { Text(c.loadError ?: "Os dados salvos não puderam ser lidos.") },
            confirmButton = { TextButton(onClick = { showLoadError = false }) { Text("OK") } }
        )
    }

    if (tab == 2) {
        // Modo Telão (Kiosk) em tela cheia da janela
        DesktopKioskScreen(c = c, onExit = { tab = 0 })
    } else {
        Row(Modifier.fillMaxSize()) {
            DesktopSidebar(
                selectedTab = tab,
                onSelectTab = { tab = it },
                c = c
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                    1 -> History(c)
                    3 -> PublicTalksScreen(c)
                    4 -> GroupsAndCleaningScreen(c)
                    5 -> RelatorioComAcoesExtras(c)
                    6 -> Brothers(c)
                    7 -> DesktopUnavailabilityScreen(c)
                    8 -> Privileges(c)
                    9 -> DesktopEquityScreen(c)
                    10 -> Settings(c, onShowUpdate = onShowUpdate)
                }
            }
        }
    }
}

/**
 * Aba 5: o relatório A4 que já existia, mais as duas ações que não couberam
 * dentro dele — o quadro do mês em tela cheia e as imagens do mês.
 *
 * **Por que uma barra própria e não botões dentro de `DesktopPrintReportScreen`.**
 * A tela do relatório tem o mês dela em estado privado, que não dá para ler de
 * fora. Para não mostrar um quadro de um mês e as imagens de outro, esta barra
 * tem o próprio seletor, com o mês escrito no rótulo — quem usa sabe qual mês os
 * botões vão usar, mesmo que a grade acima esteja mostrando outro.
 */
@Composable
private fun RelatorioComAcoesExtras(c: StoreController) {
    var mes by remember { mutableStateOf(YearMonth.now()) }
    var mostrarQuadro by remember { mutableStateOf(false) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var salvando by remember { mutableStateOf(false) }

    val prefixo = mes.format(DateTimeFormatter.ofPattern("MM/yyyy"))
    val reunioesDoMes = c.data.meetings
        .filter { it.date.endsWith("/$prefixo") }
        .sortedBy { AssignmentGenerator.parseDate(it.date) }
    val rotuloMes = Datas.mesEAno(mes)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Quadro e imagens — $rotuloMes",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            IconButton(onClick = { mes = mes.minusMonths(1) }) {
                Icon(Icons.Default.ChevronLeft, "Mês anterior do quadro e das imagens")
            }
            IconButton(onClick = { mes = mes.plusMonths(1) }) {
                Icon(Icons.Default.ChevronRight, "Próximo mês do quadro e das imagens")
            }

            OutlinedButton(
                enabled = reunioesDoMes.isNotEmpty(),
                onClick = { mostrarQuadro = true }
            ) {
                Icon(Icons.Default.Dashboard, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Quadro do Mês")
            }

            OutlinedButton(
                enabled = reunioesDoMes.isNotEmpty() && !salvando,
                onClick = {
                    // Desenha e grava: centenas de ms por reunião, e um mês cheio
                    // de reuniões congelaria a janela. Mesma razão do "Imagem PNG"
                    // da lista de reuniões.
                    salvando = true
                    salvarImagensDoMes(
                        c = c,
                        reunioes = reunioesDoMes,
                        aoTerminar = { pasta, salvas, falhas ->
                            salvando = false
                            infoMessage = if (falhas.isEmpty()) {
                                "$salvas imagens salvas em: ${pasta.absolutePath}"
                            } else {
                                "$salvas imagens salvas em: ${pasta.absolutePath}. Falharam: ${falhas.size}"
                            }
                        },
                        aoReportarErro = { mensagem ->
                            salvando = false
                            c.reportError(mensagem)
                        }
                    )
                }
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (salvando) "Salvando..." else "Salvar as imagens do mês")
            }

            Spacer(Modifier.weight(1f))
            infoMessage?.let { msg ->
                IconButton(onClick = { infoMessage = null }) {
                    Icon(Icons.Default.Close, "Fechar aviso")
                }
            }
        }

        infoMessage?.let { msg ->
            Text(
                msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f)) {
            DesktopPrintReportScreen(c)
        }
    }

    if (mostrarQuadro) {
        MonthBoardDialog(
            month = mes,
            meetings = reunioesDoMes,
            brothers = c.data.brothers,
            privileges = c.data.privileges,
            onFechar = { mostrarQuadro = false }
        )
    }
}

/**
 * O quadro do mês em janela própria, do tamanho da tela.
 *
 * **Por que uma `Dialog` e não rasterizar para PNG.** O usuário imprime com
 * Ctrl+P direto daqui. Rasterizar Compose para bitmap no desktop nunca foi
 * testado, seria o único ponto frágil da issue, e o `MonthBoard` já vem na
 * proporção de paisagem A4 — o que sai da impressora do salão.
 *
 * O nome da congregação fica vazio: não há campo para ele em `Store`, e inventar
 * um seria mostrar um cabeçalho errado na parede.
 */
@Composable
private fun MonthBoardDialog(
    month: YearMonth,
    meetings: List<Meeting>,
    brothers: List<Brother>,
    privileges: List<Privilege>,
    onFechar: () -> Unit
) {
    // Esc e o "X" chegam pelo mesmo caminho: `onDismissRequest`. Não há
    // `onKeyEvent` aqui de propósito — o `Dialog` do Compose Desktop já chama
    // esse callback na tecla Esc, e é como os outros diálogos do projeto fecham.
    Dialog(
        onDismissRequest = onFechar,
        // Sem isto a janela do diálogo nasce estreita (largura padrão da
        // plataforma) e o quadro sai espremido, sem a proporção A4.
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Ctrl+P imprime o quadro. Esc fecha.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onFechar) { Text("Fechar") }
            }

            MonthBoard(
                month = month,
                meetings = meetings,
                brothers = brothers,
                privileges = privileges,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Gera e grava o card de cada reunião do mês em `~/.designacoes-jw/cards/`.
 *
 * Só salva arquivo: a área de transferência guarda **uma** imagem por vez, então
 * um mês inteiro passaria por lá sem o usuário ver o resultado. O que ele quer
 * é a pasta com os PNGs para mandar no grupo.
 *
 * Roda fora da thread de UI e volta por `Edt.publica`, como o
 * "Imagem PNG" da lista de reuniões: `c.reportError` escreve em estado do
 * Compose, que não pode ser tocado da thread de desenho.
 */
private fun salvarImagensDoMes(
    c: StoreController,
    reunioes: List<Meeting>,
    aoTerminar: (pasta: File, salvas: Int, falhas: List<String>) -> Unit,
    aoReportarErro: (String) -> Unit
) {
    // Cópia do estado agora: entre a leitura e o desenho o usuário pode editar
    // qualquer coisa, e o card precisa sair do que estava na tela quando ele
    // apertou o botão.
    val irmaos = c.data.brothers
    val privilegios = c.data.privileges
    val discursos = c.data.publicTalks
    val escalas = c.data.cleaningSchedules
    val grupos = c.data.fieldServiceGroups

    val pasta = File(System.getProperty("user.home"), ".designacoes-jw/cards").apply { mkdirs() }

    Thread {
        // Uma reunião que falha não derruba as outras: um único card com letra
        // ausente não pode custar o mês inteiro de imagens.
        var salvas = 0
        val falhas = mutableListOf<String>()

        reunioes.forEach { reuniao ->
            val dataSafe = reuniao.date.replace("/", "-")
            val discurso = discursos.find { it.date == reuniao.date }
            val limpeza = escalas.find { it.weekDate == reuniao.date }
            val grupo = grupos.find { it.id == limpeza?.groupId }

            runCatching {
                val imagem = ImageExportHelper.generateMeetingCard(
                    meeting = reuniao,
                    brothers = irmaos,
                    privileges = privilegios,
                    publicTalk = discurso,
                    cleaningSchedule = limpeza,
                    cleaningGroup = grupo
                )
                ImageExportHelper.saveToPngFile(imagem, File(pasta, "card-$dataSafe.png"))
            }.onSuccess {
                salvas++
                c.logImageOk(reuniao.date, it.absolutePath)
            }.onFailure { erro ->
                c.logImageError("falha ao gerar a imagem de ${reuniao.date}", erro)
                falhas += "${reuniao.date}: ${erro.message}"
            }
        }

        Edt.publica {
            // Falha parcial ainda é sucesso: as imagens que saíram estão no
            // disco. Por isso o resumo vem sempre, e o erro é um acréscimo —
            // senão o usuário fica procurando um arquivo que nunca apareceu.
            aoTerminar(pasta, salvas, falhas)
            if (falhas.isNotEmpty()) {
                aoReportarErro(
                    "Some das imagens não saíram (${falhas.size} de ${reunioes.size}): " +
                        falhas.joinToString("; ")
                )
            }
        }
    }.apply {
        isDaemon = true
        name = "export-imagens-mes"
        start()
    }
}

@Composable
private fun DesktopSidebar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    c: StoreController
) {
    Surface(
        modifier = Modifier.width(260.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Teocrático
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            "Designações JW",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Quadro Teocrático",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            // Lista rolável de categorias da Sidebar
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Seção 1: REUNIÕES
                item {
                    Text(
                        "REUNIÕES & ESCALAS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
                    )
                }
                item {
                    DesktopSidebarItem(
                        label = "Início (Quadro)",
                        icon = Icons.Default.Home,
                        selected = selectedTab == 0,
                        onClick = { onSelectTab(0) }
                    )
                }
                item {
                    DesktopSidebarItem(
                        label = "Histórico",
                        icon = Icons.Default.History,
                        selected = selectedTab == 1,
                        onClick = { onSelectTab(1) }
                    )
                }
                item {
                    DesktopSidebarItem(
                        label = "Modo Telão (Kiosk)",
                        icon = Icons.Default.Tv,
                        selected = selectedTab == 2,
                        badge = "TV",
                        onClick = { onSelectTab(2) }
                    )
                }

                // Seção 2: PROGRAMAÇÃO ESPECIAL
                item {
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = corDeContorno(), modifier = Modifier.padding(horizontal = 4.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "PROGRAMAÇÃO ESPECIAL",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                    )
                }
                item {
                    DesktopSidebarItem(
                        label = "Discursos Públicos",
                        icon = Icons.Default.RecordVoiceOver,
                        selected = selectedTab == 3,
                        onClick = { onSelectTab(3) }
                    )
                }
                item {
                    DesktopSidebarItem(
                        label = "Grupos & Limpeza",
                        icon = Icons.Default.CleaningServices,
                        selected = selectedTab == 4,
                        onClick = { onSelectTab(4) }
                    )
                }
                item {
                    DesktopSidebarItem(
                        label = "Relatório A4 / Imprimir",
                        icon = Icons.Default.Print,
                        selected = selectedTab == 5,
                        onClick = { onSelectTab(5) }
                    )
                }

                // Seção 3: CONGREGAÇÃO
                item {
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = corDeContorno(), modifier = Modifier.padding(horizontal = 4.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "CONGREGAÇÃO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                    )
                }
                item {
                    DesktopSidebarItem(
                        label = "Irmãos & Irmãs",
                        icon = Icons.Default.Groups,
                        selected = selectedTab == 6,
                        onClick = { onSelectTab(6) }
                    )
                }
                item {
                    DesktopSidebarItem(
                        label = "Férias & Ausências",
                        icon = Icons.Default.EventBusy,
                        selected = selectedTab == 7,
                        onClick = { onSelectTab(7) }
                    )
                }
                item {
                    DesktopSidebarItem(
                        label = "Privilégios",
                        icon = Icons.Default.Work,
                        selected = selectedTab == 8,
                        onClick = { onSelectTab(8) }
                    )
                }
                item {
                    DesktopSidebarItem(
                        label = "Estatísticas de Equidade",
                        icon = Icons.Default.BarChart,
                        selected = selectedTab == 9,
                        onClick = { onSelectTab(9) }
                    )
                }

                // Seção 4: SISTEMA
                item {
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = corDeContorno(), modifier = Modifier.padding(horizontal = 4.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "SISTEMA",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                    )
                }
                item {
                    DesktopSidebarItem(
                        label = "Configurações",
                        icon = Icons.Default.Settings,
                        selected = selectedTab == 10,
                        onClick = { onSelectTab(10) }
                    )
                }
            }

            // Rodapé da Sidebar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = superficieDeCartao(isSystemInDarkTheme())
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier.size(8.dp).background(
                                if (c.authSession != null) JwTheme.colors.sucesso else JwTheme.colors.alerta,
                                CircleShape
                            )
                        )
                        Text(
                            c.authSession?.email ?: "Modo Offline",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("v$CURRENT_VERSION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        if (c.isSyncing) {
                            Text("Sincronizando...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text(c.syncStatus ?: "Pronto", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopSidebarItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    badge: String? = null
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val weight = if (selected) FontWeight.Bold else FontWeight.Medium

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = bg,
        modifier = Modifier.fillMaxWidth().height(42.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(20.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = weight,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
            if (badge != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
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
    val monthName = Datas.mesEAno(month)

    val nextMeetingInfo = remember(allMeetings) { getNextMeetingInfo(allMeetings) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Banner Inteligente da Próxima Reunião
        nextMeetingInfo?.let { (nextMeeting, daysUntil) ->
            JwCard(destaque = true) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.xs), modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Event, null, tint = MaterialTheme.colorScheme.primary)
                            JwCardTitle("Próxima Reunião")
                            Badge(
                                containerColor = when {
                                    daysUntil == 0L -> MaterialTheme.colorScheme.error
                                    daysUntil == 1L -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                }
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
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Avisar no WhatsApp")
                    }
                }
            }
        }

        // Cabeçalho de Navegação e Chips Informativos
        JwCard {
            Column(verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm)) {
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
                    horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = { showDaysDialog = true },
                        label = { Text("Dias: ${dayName(c.data.firstDay).take(3)} e ${dayName(c.data.secondDay).take(3)}") },
                        leadingIcon = { Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(contar(c.data.brothers.count { it.active }, "irmão ativo", "irmãos ativos")) },
                        leadingIcon = { Icon(Icons.Default.Groups, null, modifier = Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(contar(c.data.privileges.count { it.active }, "privilégio", "privilégios")) },
                        leadingIcon = { Icon(Icons.Default.Work, null, modifier = Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(contar(meetings.size, "reunião", "reuniões")) },
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
            // Aviso de ação, não cartão: precisa do fundo de destaque para ser
            // lido, e `JwCard` não tem essa cor.
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    IconButton({ infoMessage = null }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) }
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
    // `key` nos dias: sem ele, o remember segura o valor antigo e o diálogo
    // passa a mostrar — e a salvar — um dia que não é o atual, quando um sync
    // atualiza a configuração com o diálogo aberto.
    var firstDay by remember(currentFirstDay) { mutableIntStateOf(currentFirstDay) }
    var secondDay by remember(currentSecondDay) { mutableIntStateOf(currentSecondDay) }
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
    val dayOfWeekShort = parsedDate?.let { Datas.diaDaSemanaCurto(it) } ?: "REU"

    // Uma reunião é uma unidade, e é o que se lê primeiro: cartão. Selecionada
    // vira `destaque` — o realce é o que o usuário abriu, não um alfa no fundo.
    JwCard(destaque = isSelected) {
        Column(verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.md)
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
                        JwCardTitle(m.type)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (missing.isEmpty()) {
                                Badge(containerColor = JwTheme.colors.sucessoContainer) {
                                    Text(
                                        "✓ ${contar(m.assignments.size, "designação", "designações")}",
                                        color = JwTheme.colors.sucesso,
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
                        // Gerar o BufferedImage, copiar e salvar em disco leva
                        // centenas de ms a segundos. No onClick, sem try/catch:
                        // travava a janela e um HeadlessException matava o app.
                        val talk = c.data.publicTalks.find { it.date == m.date }
                        val clean = c.data.cleaningSchedules.find { it.weekDate == m.date }
                        val group = c.data.fieldServiceGroups.find { it.id == clean?.groupId }
                        val brothers = c.data.brothers
                        val privileges = c.data.privileges
                        val dateSafe = m.date.replace("/", "-")
                        Thread {
                            // Cada etapa e independente. A versao anterior
                            // jogava as tres num unico runCatching, na ordem
                            // gerar -> area de transferencia -> salvar: se a
                            // area falhasse, o PNG nunca era gravado e o
                            // usuario via "nao consegui gerar a imagem" sem
                            // ter arquivo nenhum. Salvar e o que importa; a
                            // area de transferencia e conveniencia.
                            val imagem = runCatching {
                                ImageExportHelper.generateMeetingCard(
                                    meeting = m,
                                    brothers = brothers,
                                    privileges = privileges,
                                    publicTalk = talk,
                                    cleaningSchedule = clean,
                                    cleaningGroup = group
                                )
                            }.getOrElse { erro ->
                                c.logImageError("falha ao desenhar a imagem", erro)
                                Edt.publica {
                                    c.reportError("Não consegui desenhar a imagem: ${erro.message}")
                                }
                                return@Thread
                            }

                            val cardDir = File(System.getProperty("user.home"), ".designacoes-jw/cards")
                                .apply { mkdirs() }
                            val arquivo = File(cardDir, "card-reuniao-$dateSafe.png")
                            runCatching { ImageExportHelper.saveToPngFile(imagem, arquivo) }
                                .onFailure { erro ->
                                    c.logImageError("falha ao salvar o PNG", erro)
                                    Edt.publica {
                                        c.reportError("Desenhei a imagem mas não consegui salvar: ${erro.message}")
                                    }
                                    return@Thread
                                }

                            // A area de transferencia e do AWT e nao gosta de
                            // thread paralela; e falhar aqui ja nao importa,
                            // porque o arquivo esta no disco.
                            runCatching {
                                java.awt.EventQueue.invokeAndWait {
                                    ImageExportHelper.copyImageToClipboard(imagem)
                                }
                            }.onFailure { erro ->
                                c.logImageError("imagem salva, mas a area de transferencia falhou", erro)
                                Edt.publica {
                                    c.reportError("Imagem salva em ${arquivo.name}, mas não consegui copiar para a área de transferência.")
                                }
                            }

                            c.logImageOk(m.date, arquivo.absolutePath)

                        }.apply {
                            isDaemon = true
                            name = "export-image"
                            start()
                        }
                    }) {
                        Icon(Icons.Default.Image, null, modifier = Modifier.size(22.dp))
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
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(22.dp))
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
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = corDeContorno())
                // Cada designação é uma linha repetida dentro do cartão:
                // divisória, não um cartão dentro do cartão.
                Column {
                    m.assignments.forEach { a ->
                        val p = c.data.privileges.find { it.id == a.privilegeId }
                        val b = c.data.brothers.find { it.id == a.brotherId }

                        Row(
                            Modifier.fillMaxWidth().padding(vertical = JwTheme.spacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(p?.name ?: "Privilégio", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text(b?.name ?: "Irmão", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                                if (b?.phone?.isNotBlank() == true && p != null) {
                                    TextButton(onClick = {
                                        val text = WhatsAppHelper.buildSingleMessage(c.data.whatsappSingleTemplate, b, p, m)
                                        val url = WhatsAppHelper.buildWebLink(b.phone, text)
                                        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url))
                                    }) {
                                        Icon(Icons.Default.Share, null, modifier = Modifier.size(22.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Avisar")
                                    }
                                }
                                IconButton(onClick = { onReplace(Triple(m.id, a.privilegeId, a.brotherId)) }) {
                                    Icon(Icons.Default.SwapHoriz, "Trocar", modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                        HorizontalDivider(color = corDeContorno())
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
                            color = if (alreadyUnavailable) MaterialTheme.colorScheme.errorContainer
                            else superficieDeCartao(isSystemInDarkTheme())
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

    JwCard {
        Column(verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm)) {
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
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = corDeContorno())

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
                                                MaterialTheme.colorScheme.surfaceVariant,
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
    val monthName = Datas.mesEAno(month)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.BarChart, null, tint = MaterialTheme.colorScheme.primary)
                Text("Equidade e Estatísticas ($monthName)", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            LazyColumn(Modifier.width(600.dp).heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    JwCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
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
                        // Alerta: precisa do vermelho de `errorContainer`, que
                        // `JwCard` não tem. Trilho no lugar do fundo tingido.
                        JwCard {
                            JwCardRail(MaterialTheme.colorScheme.error)
                            Text(
                                "⚠ ${contar(report.unassignedActiveBrothers.size, "irmão ativo", "irmãos ativos")} sem designação no mês:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                report.unassignedActiveBrothers.joinToString(", ") { "${it.name} (${it.role.label})" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Text("Ranking de Participações", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                items(report.ranking) { item ->
                    // Linha de ranking: item repetido. Divisória, não cartão.
                    Column(Modifier.fillMaxWidth().padding(vertical = JwTheme.spacing.sm)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                Text(item.brother.name, fontWeight = FontWeight.Bold)
                                Badge { Text(item.brother.role.label) }
                            }
                            Text(
                                contar(item.count, "vez", "vezes"),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (item.privilegesCount.isNotEmpty()) {
                            Text(
                                item.privilegesCount.entries.joinToString(" • ") { "${it.key}: ${it.value}" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider(
                            Modifier.padding(top = JwTheme.spacing.sm),
                            color = corDeContorno()
                        )
                    }
                }

                if (report.privilegeTotals.isNotEmpty()) {
                    item {
                        Text("Totais por Privilégio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    item {
                        JwCard {
                            Column {
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
        LazyColumn(verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm)) {
            if (filtered.isEmpty()) {
                item {
                    JwCard {
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.xs)
                        ) {
                            Text("Nenhum irmão encontrado", fontWeight = FontWeight.Bold)
                            Text("Utilize o botão 'Novo Irmão' para cadastrar publicadores e servos.", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            items(filtered) { b ->
                // Uma pessoa é uma unidade: cartão. E o cartão inteiro é
                // clicável, então o `.clickable` solto vira `onClick` do JwCard.
                JwCard(onClick = { selectedBrotherForProfile = b }) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.md)) {
                            // Avatar Circular
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(getAvatarColor(b.name), shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(getInitials(b.name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.xs)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                    JwCardTitle(b.name)
                                    Badge { Text(b.role.label) }
                                    if (!b.active) {
                                        Badge(containerColor = MaterialTheme.colorScheme.errorContainer) {
                                            Text("Inativo", color = MaterialTheme.colorScheme.onErrorContainer)
                                        }
                                    }
                                    if (b.unavailabilities.isNotEmpty()) {
                                        Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                                            Text("🏖 " + contar(b.unavailabilities.size, "ausência", "ausências"))
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

                        Row(horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                            TextButton({ unavailBrother = b }) { Text("Ausências") }
                            TextButton({ c.toggleBrotherActive(b.id) }) { Text(if (b.active) "Desativar" else "Ativar") }
                            IconButton({ editingBrother = b }) { Icon(Icons.Default.Edit, "Editar", modifier = Modifier.size(22.dp)) }
                            // Destrutivo separado dos demais ícones da linha.
                            VerticalDivider(color = corDeContorno())
                            IconButton({ deletingBrother = b }) { Icon(Icons.Default.Delete, "Excluir", modifier = Modifier.size(22.dp), tint = JwTheme.colors.perigo) }
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
                    JwCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
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
                        // Última participação é o que o usuário procura aqui.
                        JwCard(destaque = true) {
                            JwSectionLabel("Última participação")
                            Text("${m.date} — $privName (${m.type})", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                if (privilegeCounts.isNotEmpty()) {
                    item {
                        JwSectionLabel("Frequência por privilégio")
                    }
                    items(privilegeCounts.entries.toList()) { (priv, count) ->
                        // Item repetido: linha com divisória, não cartão.
                        Column(Modifier.fillMaxWidth().padding(vertical = JwTheme.spacing.xs)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(priv, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    contar(count, "vez", "vezes"),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            HorizontalDivider(
                                Modifier.padding(top = JwTheme.spacing.xs),
                                color = corDeContorno()
                            )
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
                // Mesma função do gerador: a regra duplicada na UI já divergiu.
                val authorizedCount = c.data.brothers.count { b ->
                    AssignmentGenerator.isAuthorized(b, p)
                }
                val isBook = p.readerGrant == ReaderGrant.BOOK
                val isSentinel = p.readerGrant == ReaderGrant.SENTINEL

                JwCard {
                    Column(verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                JwCardTitle(p.name)
                                Text(
                                    // "1 irmão(s)" era o texto: o número é o que
                                    // importa e a concordância errava todo
                                    // singular — "1 irmão(s)" lê como robô.
                                    text = "Quantidade: ${contar(p.quantity, "irmão", "irmãos")} • Cargo mínimo: ${p.minRole.label} • ${if (p.active) "Ativo" else "Inativo"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                                TextButton({ c.togglePrivilegeActive(p.id) }) { Text(if (p.active) "Desativar" else "Ativar") }
                                IconButton({ editingPrivilege = p }) { Icon(Icons.Default.Edit, "Editar", modifier = Modifier.size(22.dp)) }
                                // Destrutivo separado dos demais ícones da linha.
                                VerticalDivider(color = corDeContorno())
                                IconButton({ deletingPrivilege = p }) { Icon(Icons.Default.Delete, "Excluir", modifier = Modifier.size(22.dp), tint = JwTheme.colors.perigo) }
                            }
                        }

                        // Destaque Teocrático Inteligente
                        if (isBook) {
                            JwCard(destaque = true) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm)) {
                                    Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                                    Column {
                                        Text("📖 Reunião de Meio de Semana (Quarta-feira)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                        Text("Irmãos que são Leitores da Sentinela se qualificam automaticamente para ler o Livro.", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        } else if (isSentinel) {
                            JwCard(destaque = true) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm)) {
                                    Icon(Icons.Default.Article, null, tint = MaterialTheme.colorScheme.secondary)
                                    Column {
                                        Text("📰 Reunião de Fim de Semana (Sábado/Domingo)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                        Text("Qualifica automaticamente o irmão para a leitura do Livro de meio de semana.", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        // Tipo de parte: individual, dupla, encenação ou grupo.
                        Column {
                            JwSectionLabel("Tipo de parte")
                            Row(horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.xs)) {
                                PartKind.entries.forEach { kind ->
                                    FilterChip(
                                        selected = p.kind == kind,
                                        onClick = { c.setPrivilegeKind(p.id, kind) },
                                        label = { Text(kindLabel(kind)) }
                                    )
                                }
                            }
                        }

                        // Quem pode fazer a parte.
                        Column {
                            JwSectionLabel("Pode ser feito por")
                            Row(horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.xs)) {
                                BrotherStatus.entries.forEach { status ->
                                    FilterChip(
                                        selected = status in p.allowedStatus,
                                        onClick = {
                                            val next = p.allowedStatus.toMutableSet().also { s ->
                                                if (!s.add(status)) s.remove(status)
                                            }
                                            if (next.isEmpty()) {
                                                c.reportError("Escolha pelo menos um. Remover todos deixaria a parte sem regra.")
                                            } else {
                                                c.setPrivilegeAllowedStatus(p.id, next)
                                            }
                                        },
                                        label = { Text(status.label) }
                                    )
                                }
                            }
                        }

                        // Qual habilitação do irmão concede este privilégio.
                        Column {
                            JwSectionLabel("Concedido a quem é")
                            Row(horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.xs)) {
                                ReaderGrant.entries.forEach { grant ->
                                    FilterChip(
                                        selected = p.readerGrant == grant,
                                        onClick = { c.setPrivilegeReaderGrant(p.id, grant) },
                                        label = { Text(readerGrantLabel(grant)) }
                                    )
                                }
                            }
                        }

                        // Dias permitidos
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm)) {
                            JwSectionLabel("Dias permitidos")
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

                        HorizontalDivider(color = corDeContorno())

                        // Botão de Gerenciamento de Irmãos Autorizados
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "✓ ${contar(authorizedCount, "irmão autorizado", "irmãos autorizados")}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Button(onClick = { manageBrothersPrivilege = p }) {
                                Icon(Icons.Default.Groups, null, modifier = Modifier.size(22.dp))
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
    val isBook = privilege.readerGrant == ReaderGrant.BOOK

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
                    JwCard(destaque = true) {
                        Text(
                            "Nota teocrática: Leitores da Sentinela já são qualificados automaticamente para o Livro.",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredBrothers) { brother ->
                        val direct = privilege.id in brother.privileges
                        // Herança: quem é leitor de A Sentinela também pode ler o livro.
                        val inherited = !direct && isBook && brother.isSentinelReader

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !inherited) {
                                    c.toggleBrotherPrivilege(brother.id, privilege.id)
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (direct || inherited) MaterialTheme.colorScheme.primaryContainer
                            else superficieDeCartao(isSystemInDarkTheme())
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
    var importingId by remember { mutableStateOf<Long?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var busca by remember { mutableStateOf("") }

    // Mesma função do Android: uma regra de busca, dois lugares que a usam.
    val filtradas = remember(busca, c.data.meetings, c.data.brothers, c.data.privileges) {
        BuscaHistorico.filtrar(
            termo = busca,
            meetings = c.data.meetings,
            brothers = c.data.brothers,
            privileges = c.data.privileges
        )
    }
    val groups = filtradas.sortedByDescending { AssignmentGenerator.parseDate(it.date) }.groupBy {
        it.date.substringAfterLast("/")
    }

    Column(verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.md)) {
        Text("Histórico de Reuniões", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = busca,
            onValueChange = { busca = it },
            label = { Text("Buscar por irmão, privilégio, tema ou data") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (busca.isNotEmpty()) {
                    TextButton(onClick = { busca = "" }) { Text("Limpar") }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        // Só conta o que a busca devolveu quando ela filtra: "12 reuniões"
        // embaixo de uma busca que achou 1 é o que faz a pessoa concluir que a
        // busca não funciona.
        Text(
            if (busca.isBlank()) {
                contar(c.data.meetings.size, "reunião registrada", "reuniões registradas")
            } else {
                contar(filtradas.size, "reunião encontrada", "reuniões encontradas")
            }
        )
        if (busca.isNotBlank() && groups.isEmpty()) {
            Text(
                "Nada encontrado para \"$busca\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm)) {
            groups.forEach { (year, meetings) ->
                item {
                    JwSectionLabel(year, Modifier.padding(top = JwTheme.spacing.sm))
                }
                items(meetings) { m ->
                    // Uma reunião é uma unidade e traz o programa inteiro
                    // dentro: cartão, não linha de tabela.
                    JwCard {
                        Column(verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.xs)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    JwCardTitle(m.date + " — " + m.type)
                                    Text(
                                        contar(m.assignments.size, "designação", "designações"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton({ confirmDelete = m }) { Icon(Icons.Default.Delete, "Excluir", modifier = Modifier.size(22.dp), tint = JwTheme.colors.perigo) }
                            }

                            if (m.theme.isNotBlank()) {
                                Text(
                                    "📖 ${m.theme}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            // Delegado a shared: cada tela tinha a sua
                            // renderizacao e elas ja divergiram.
                            MeetingProgramList(
                                meeting = m,
                                brothers = c.data.brothers,
                                privileges = c.data.privileges,
                                canAssign = c.data.brothers.filter { it.active },
                                onToggleAssignment = { position, brotherId ->
                                    c.toggleProgramAssignment(m.id, position, brotherId)
                                }
                            )

                            if (m.isMidweek) {
                                TextButton(
                                    onClick = {
                                        importingId = m.id
                                        importError = null
                                        c.importMwbProgram(m.id) { err ->
                                            importingId = null
                                            if (err != null) importError = err
                                        }
                                    },
                                    enabled = importingId == null
                                ) {
                                    if (importingId == m.id) {
                                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(if (m.theme.isBlank()) "Importar programa do jw.org" else "Atualizar programa do jw.org")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    importError?.let { msg ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text("Não foi possível importar o programa") },
            text = { Text(msg) },
            confirmButton = { TextButton({ importError = null }) { Text("Fechar") } }
        )
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
            JwCard {
                Column(verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm)) {
                    JwCardTitle("Dias de Reunião da Congregação")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Selector("Primeiro dia", first) { first = it; c.setMeetingDays(first, second) }
                        Selector("Segundo dia", second) { second = it; c.setMeetingDays(first, second) }
                    }
                }
            }
        }
        item {
            JwCard {
                Column(verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm)) {
                    JwCardTitle("Aparência e Tema")
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
            JwCard {
                Column(verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm)) {
                    JwCardTitle("Mensagens personalizadas do WhatsApp")
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
            JwCard {
                Column(verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm)) {
                    JwCardTitle("Importação e Exportação (CSV e Backup)")
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
            JwCard {
                Column(verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.xs)) {
                    JwCardTitle("Regras teocráticas de leitores")
                    Text("• Leitor do Livro (EBC): Atua exclusivamente nas reuniões de meio de semana (quarta-feira). Irmãos qualificados como Leitor da Sentinela podem ler o Livro automaticamente.", style = MaterialTheme.typography.bodyMedium)
                    Text("• Leitor da Sentinela: Atua nas reuniões de fim de semana (sábado/domingo). Irmãos habilitados apenas para o Livro nunca leem a Sentinela.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            JwCard {
                Column(verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm)) {
                    JwCardTitle("Atualizações e Armazenamento")
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
                                    // Estado do Compose só na thread de UI.
                                    Edt.publica {
                                        isCheckingUpdateManual = false
                                        if (info != null) {
                                            onShowUpdate(info)
                                        } else {
                                            manualUpdateFeedback = "Você já está utilizando a versão mais recente ($CURRENT_VERSION)."
                                        }
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(22.dp))
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
                // Aviso de ação, não cartão: precisa do fundo de destaque para
                // ser lido, e `JwCard` não tem essa cor.
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton({ statusMsg = null }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) }
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
