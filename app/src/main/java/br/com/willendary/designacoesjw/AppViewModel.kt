package br.com.willendary.designacoesjw

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import br.com.willendary.designacoesjw.data.*
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import br.com.willendary.designacoesjw.export.CsvDataHandler
import br.com.willendary.designacoesjw.export.MwbProgramImporter
import br.com.willendary.designacoesjw.util.WhatsAppHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

/**
 * Grafia gravada em [Meeting.type] pela reunião criada no import do jw.org.
 *
 * **Não duplique esta string.** Ela mora em [MeetingType.label] porque foi
 * duplicá-la — em dois lugares, com capitalização diferente — que nasceram as
 * duas grafias que conviviam em produção, e o app distinguia os tipos de
 * reunião por `type.contains(...)` espalhado por quatro arquivos, funcionando
 * só porque a diferença era a caixa (#48).
 *
 * A leitura normaliza: `MeetingType.parse` devolve sempre [MeetingType.label],
 * então registro antigo e novo não se misturam mais.
 */
private val TIPO_REUNIAO_MWB = MeetingType.MIDWEEK.label

private const val TAG = "AppViewModel"

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository(app)
    private val accessRepo = UserAccessRepository()
    private var usersListener: ListenerRegistration? = null
    var currentUserProfile = mutableStateOf<UserProfile?>(null); private set
    var users = mutableStateOf<List<UserProfile>>(emptyList()); private set
    var invitations = mutableStateOf<List<Invitation>>(emptyList()); private set
    var brothers = mutableStateOf(carga("irmãos") { repo.loadBrothers() }); private set
    var privileges = mutableStateOf(carga("privilégios") { repo.loadPrivileges() }); private set
    var meetings = mutableStateOf(carga("reuniões") { repo.loadMeetings() }); private set
    var schedule = mutableStateOf(cargaUnico("grade", MeetingSchedule()) { repo.loadSchedule() }); private set
    var publicTalks = mutableStateOf(carga("discursos") { repo.loadPublicTalks() }); private set
    var fieldServiceGroups = mutableStateOf(carga("grupos") { repo.loadFieldServiceGroups() }); private set
    var cleaningSchedules = mutableStateOf(carga("limpeza") { repo.loadCleaningSchedules() }); private set

    /**
     * Falhas de carga do armazenamento local, anotadas durante a construção.
     *
     * Precisa existir **antes** das propriedades acima: elas chamam [carga] no
     * inicializador, e o `lastActionError` só é criado depois.
     */
    private val falhasDeCarga = mutableListOf<String>()

    /**
     * Carrega uma lista do armazenamento local sem derrubar o app.
     *
     * Estas cargas rodam como inicializador de propriedade, dentro do `init`. Um
     * `throw` aqui estoura a construção do ViewModel dentro do `viewModel()`, na
     * composição — o app abre e morre, sem log e sem chance de recuperar. Dado
     * local corrompido por uma versão antiga é exatamente o que produz isso, e o
     * usuário não tem como sair do problema sem reinstalar.
     *
     * Devolve lista vazia e registra a falha, que aparece na tela e vai para
     * `erros.log`. **Vazio, nunca overwritten**: a nuvem repõe o que faltar.
     */
    private inline fun <T> carga(rotulo: String, bloco: () -> List<T>): List<T> = try {
        bloco()
    } catch (e: Exception) {
        registraFalhaDeCarga(rotulo, e)
        emptyList()
    }

    /** Mesma rede para a carga que devolve um valor só, e não uma lista. */
    private inline fun <T> cargaUnico(rotulo: String, vazio: T, bloco: () -> T): T = try {
        bloco()
    } catch (e: Exception) {
        registraFalhaDeCarga(rotulo, e)
        vazio
    }

    @PublishedApi
    internal fun registraFalhaDeCarga(rotulo: String, erro: Exception) {
        Log.e(TAG, "Falha ao carregar $rotulo do armazenamento local", erro)
        val arquivo = anotaErro("Falha ao carregar $rotulo", erro)
        falhasDeCarga += "Não consegui ler $rotulo do aparelho. Detalhes em $arquivo."
    }

    /**
     * Grava a falha em `erros.log` e devolve o caminho.
     *
     * Sem isto o app morre em silêncio e ninguém descobre o porquê: nem o
     * usuário, que não tem como abrir o logcat, nem quem for reparar.
     */
    private fun anotaErro(mensagem: String, erro: Throwable? = null): String {
        val arquivo = File(getApplication<Application>().filesDir, "erros.log")
        return try {
            arquivo.appendText(
                buildString {
                    appendLine("--- ${LocalDateTime.now()} ---")
                    appendLine(mensagem)
                    if (erro != null) appendLine(erro.stackTraceToString().take(4000))
                }
            )
            arquivo.absolutePath
        } catch (e: Exception) {
            "log não gravado: ${e.message}"
        }
    }

    /** O log de erros, para a tela de configurações mostrar. */
    fun lerLogDeErros(): String = CrashLog.ler(getApplication())

    /**
     * Último erro de sincronização ou de permissão, para a UI mostrar.
     * Antes these falhas eram engolidas: a escrita ia sem tratamento e o app
     * exibia "salvo" mesmo com o Firestore tendo recusado.
     */
    var lastActionError = mutableStateOf<String?>(null)
    fun clearActionError() { lastActionError.value = null }

    /** Mensagem de validação vinda da UI (regra de negócio no formulário). */
    fun reportError(message: String) { lastActionError.value = message }

    init {
        if (falhasDeCarga.isNotEmpty()) {
            lastActionError.value = falhasDeCarga.joinToString(" ")
        }
        FirebaseAuth.getInstance().currentUser?.let { user ->
            accessRepo.observeCurrentUser(
                uid = user.uid,
                email = user.email.orEmpty(),
                onProfile = { profile ->
                    currentUserProfile.value = profile
                    if (profile.permissions.contains(AppPermissions.MANAGE_USERS) || profile.role == "admin") {
                        usersListener?.remove()
                        usersListener = accessRepo.observeAllUsers(
                            onUsers = { users.value = it.sortedBy { u -> u.email.lowercase() } },
                            onError = {}
                        )
                        accessRepo.observeInvitations(
                            onInvitations = { invitations.value = it.sortedByDescending { i -> i.createdAt } },
                            onError = {}
                        )
                    }
                }
            )
        }
        repo.onSyncError = { lastActionError.value = it }
        repo.startCloudSync(
            onBrothers = { brothers.value = it },
            onPrivileges = { privileges.value = it },
            onMeetings = { meetings.value = it },
            onSchedule = { schedule.value = it },
            onPublicTalks = { publicTalks.value = it },
            onGroups = { fieldServiceGroups.value = it },
            onCleaning = { cleaningSchedules.value = it }
        )
    }

    override fun onCleared() {
        repo.closeCloudSync()
        usersListener?.remove()
        accessRepo.close()
        super.onCleared()
    }

    fun can(permission: String): Boolean {
        val profile = currentUserProfile.value ?: return false
        return profile.active && (profile.role == "admin" || permission in profile.permissions)
    }

    /**
     * Guarda de escrita. Até aqui só [createInvitation] e [updateUser]
     * verificavam permissão — as outras ~30 mutações escreviam sem gate, e a
     * UI só esconde botões com `enabled = can(...)`, o que é cliente e cobre 6
     * pontos do App.kt. Cada permissão abaixo espelha exatamente a regra do
     * `firestore.rules` correspondente; divergir entre as duas é o que produz
     * "salvou" na tela e nada no servidor.
     */
    private fun denied(permission: String, action: String): String =
        "Você não tem permissão para $action."

    fun setMeetingDays(first: Int, second: Int) {
        if (first == second) return
        if (!can(AppPermissions.MANAGE_SETTINGS)) {
            lastActionError.value = denied(AppPermissions.MANAGE_SETTINGS, "alterar os dias de reunião")
            return
        }
        schedule.value = MeetingSchedule(first, second)
        repo.saveSchedule(schedule.value)
    }

    fun createInvitation(email: String, name: String, permissions: Set<String>, onResult: (Invitation?, String?) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || !can(AppPermissions.MANAGE_USERS)) {
            onResult(null, "Você não tem permissão para convidar usuários.")
            return
        }
        accessRepo.saveInvitation(email, name, permissions, user.uid, onResult)
    }

    fun updateUser(profile: UserProfile, onResult: (String?) -> Unit) {
        if (!can(AppPermissions.MANAGE_USERS)) {
            onResult("Você não tem permissão para alterar usuários.")
            return
        }
        accessRepo.updateUser(profile, onResult)
    }

    fun addBrother(name: String, phone: String): String? {
        if (!can(AppPermissions.MANAGE_BROTHERS)) return denied(AppPermissions.MANAGE_BROTHERS, "cadastrar irmãos")
        val normalized = normalizeName(name)
        if (normalized.isBlank()) return "Informe o nome do irmão."
        if (brothers.value.any { normalizeName(it.name) == normalized }) return "Já existe um irmão cadastrado com esse nome."
        brothers.value = brothers.value + Brother(nextId(), name.trim(), phone.trim())
        repo.saveBrothers(brothers.value)
        return null
    }

    fun updateBrother(id: Long, name: String, phone: String): String? {
        if (!can(AppPermissions.MANAGE_BROTHERS)) return denied(AppPermissions.MANAGE_BROTHERS, "editar irmãos")
        val normalized = normalizeName(name)
        if (normalized.isBlank()) return "Informe o nome do irmão."
        if (brothers.value.any { it.id != id && normalizeName(it.name) == normalized }) return "Já existe outro irmão cadastrado com esse nome."
        brothers.value = brothers.value.map { if (it.id == id) it.copy(name = name.trim(), phone = phone.trim()) else it }
        repo.saveBrothers(brothers.value)
        return null
    }

    fun deleteBrother(id: Long) {
        if (!can(AppPermissions.MANAGE_BROTHERS)) { lastActionError.value = denied(AppPermissions.MANAGE_BROTHERS, "excluir irmãos"); return }
        brothers.value = brothers.value.filterNot { it.id == id }
        repo.saveBrothers(brothers.value)
    }

    fun setBrotherActive(id: Long, active: Boolean) {
        if (!can(AppPermissions.MANAGE_BROTHERS)) { lastActionError.value = denied(AppPermissions.MANAGE_BROTHERS, "alterar irmãos"); return }
        brothers.value = brothers.value.map { if (it.id == id) it.copy(active = active) else it }
        repo.saveBrothers(brothers.value)
    }

    fun addPrivilege(name: String, quantity: Int): String? {
        if (!can(AppPermissions.MANAGE_PRIVILEGES)) return denied(AppPermissions.MANAGE_PRIVILEGES, "cadastrar privilégios")
        val normalized = normalizeName(name)
        if (normalized.isBlank()) return "Informe o nome do privilégio."
        if (privileges.value.any { normalizeName(it.name) == normalized }) return "Já existe um privilégio cadastrado com esse nome."
        privileges.value = privileges.value + Privilege(nextId(), name.trim(), quantity.coerceAtLeast(1))
        repo.savePrivileges(privileges.value)
        return null
    }

    fun updatePrivilege(id: Long, name: String, quantity: Int): String? {
        if (!can(AppPermissions.MANAGE_PRIVILEGES)) return denied(AppPermissions.MANAGE_PRIVILEGES, "editar privilégios")
        val normalized = normalizeName(name)
        if (normalized.isBlank()) return "Informe o nome do privilégio."
        if (privileges.value.any { it.id != id && normalizeName(it.name) == normalized }) return "Já existe outro privilégio cadastrado com esse nome."
        privileges.value = privileges.value.map { if (it.id == id) it.copy(name = name.trim(), quantity = quantity.coerceAtLeast(1)) else it }
        repo.savePrivileges(privileges.value)
        return null
    }

    fun deletePrivilege(id: Long) {
        if (!can(AppPermissions.MANAGE_PRIVILEGES)) { lastActionError.value = denied(AppPermissions.MANAGE_PRIVILEGES, "excluir privilégios"); return }
        privileges.value = privileges.value.filterNot { it.id == id }
        repo.savePrivileges(privileges.value)
        brothers.value = brothers.value.map { it.copy(privileges = it.privileges - id) }
        repo.saveBrothers(brothers.value)
    }

    fun setPrivilegeActive(id: Long, active: Boolean) {
        if (!can(AppPermissions.MANAGE_PRIVILEGES)) { lastActionError.value = denied(AppPermissions.MANAGE_PRIVILEGES, "alterar privilégios"); return }
        privileges.value = privileges.value.map { if (it.id == id) it.copy(active = active) else it }
        repo.savePrivileges(privileges.value)
    }

    fun setPrivilegeAllowedDays(id: Long, days: Set<Int>) {
        if (!can(AppPermissions.MANAGE_PRIVILEGES)) { lastActionError.value = denied(AppPermissions.MANAGE_PRIVILEGES, "alterar dias de privilégio"); return }
        privileges.value = privileges.value.map { if (it.id == id) it.copy(allowedDays = days) else it }
        repo.savePrivileges(privileges.value)
    }

    fun togglePrivilege(brotherId: Long, privilegeId: Long) {
        if (!can(AppPermissions.MANAGE_BROTHERS)) { lastActionError.value = denied(AppPermissions.MANAGE_BROTHERS, "alterar privilégios de um irmão"); return }
        brothers.value = brothers.value.map {
            if (it.id != brotherId) it else it.copy(
                privileges = it.privileges.toMutableSet().also { s -> if (!s.add(privilegeId)) s.remove(privilegeId) }
            )
        }
        repo.saveBrothers(brothers.value)
    }

    fun generateMonth(yearMonth: YearMonth): List<Meeting> {
        if (!can(AppPermissions.GENERATE_ASSIGNMENTS)) { lastActionError.value = denied(AppPermissions.GENERATE_ASSIGNMENTS, "gerar designações"); return emptyList() }
        val monthPrefix = yearMonth.format(DateTimeFormatter.ofPattern("MM/yyyy"))
        val keep = meetings.value.filterNot { it.date.endsWith("/$monthPrefix") }
        val generated = AssignmentGenerator.generateMonth(
            yearMonth = yearMonth,
            schedule = schedule.value,
            brothers = brothers.value,
            privileges = privileges.value,
            existingMeetings = keep
        )
        meetings.value = (keep + generated).sortedBy { AssignmentGenerator.parseDate(it.date) }
        repo.saveMeetings(meetings.value)
        return generated
    }

    fun replaceAssignment(meetingId: Long, privilegeId: Long, oldBrotherId: Long, newBrotherId: Long) {
        if (!can(AppPermissions.GENERATE_ASSIGNMENTS)) { lastActionError.value = denied(AppPermissions.GENERATE_ASSIGNMENTS, "trocar designações"); return }
        meetings.value = meetings.value.map { meeting ->
            if (meeting.id != meetingId) meeting else meeting.copy(
                assignments = meeting.assignments.map {
                    if (it.privilegeId == privilegeId && it.brotherId == oldBrotherId) it.copy(brotherId = newBrotherId) else it
                }
            )
        }
        repo.saveMeetings(meetings.value)
    }

    /**
     * Liga ou desliga [brotherId] da parte [position] (1-based) do programa.
     *
     * **Não bloqueia por qualificação teocrática.** A parte do programa é aberta a
     * qualquer irmão ativo: quem conduz a reunião decide quem faz a parte, e a
     * regra de não batizado/aprendiz é do privilégio **mecânico**
     * (`AssignmentGenerator.isAuthorized`), que continua na lista cadastrada. Dois
     * não qualificados na mesma parte é
     * aviso, não recusa — sai por [lastActionError] depois de gravar, para o app
     * não devolver erro do que na verdade salvou.
     *
     * @return null em sucesso (mesmo havendo aviso, que vai para [lastActionError]),
     *   ou a mensagem de erro.
     */
    fun toggleProgramAssignment(meetingId: Long, position: Int, brotherId: Long): String? {
        if (!can(AppPermissions.GENERATE_ASSIGNMENTS)) {
            return denied(AppPermissions.GENERATE_ASSIGNMENTS, "designar as partes do programa")
                .also { lastActionError.value = it }
        }
        val meeting = meetings.value.firstOrNull { it.id == meetingId } ?: return "Reunião não encontrada."
        val updated = meeting.copy(
            programAssignments = togglePart(meeting.programAssignments, position, brotherId)
        )
        meetings.value = meetings.value.map { if (it.id == meetingId) updated else it }
        repo.saveMeetings(meetings.value)
        lastActionError.value = programWarning(updated, position)
        return null
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

    /** Aviso de não qualificados da parte, ou null quando não há o que avisar. */
    private fun programWarning(meeting: Meeting, position: Int): String? {
        val item = meeting.program.getOrNull(position - 1) ?: return null
        val ids = meeting.programAssignments.firstOrNull { it.item == position }?.brotherIds.orEmpty()
        return AssignmentGenerator.unqualifiedWarning(item, ids, brothers.value)
    }

    fun deleteMeeting(meetingId: Long) {
        if (!can(AppPermissions.GENERATE_ASSIGNMENTS)) { lastActionError.value = denied(AppPermissions.GENERATE_ASSIGNMENTS, "excluir reuniões"); return }
        meetings.value = meetings.value.filterNot { it.id == meetingId }
        repo.saveMeetings(meetings.value)
    }

    /**
     * Importa a semana de [meetingDate] a partir da tela "Reuniões".
     *
     * Delega para a mesma implementação do card: se a reunião de meio de semana já
     * existir na data, apenas o tema e o programa são atualizados, preservando as
     * designações; caso contrário a reunião é criada para ser designada depois.
     */
    fun importMwbWeek(meetingDate: LocalDate, onResult: (String?) -> Unit) {
        importMwb(meetingDate, meetingId = null, onResult = onResult)
    }

    /**
     * Atualiza o programa de uma reunião já existente (botão do card).
     * [onResult] recebe null em sucesso, ou a mensagem de erro.
     */
    fun importMwbProgram(meetingId: Long, onResult: (String?) -> Unit) {
        val meeting = meetings.value.firstOrNull { it.id == meetingId }
        if (meeting == null) {
            onResult("Reunião não encontrada.")
            return
        }
        importMwb(AssignmentGenerator.parseDate(meeting.date), meetingId, onResult)
    }

    /**
     * Implementação única do import do jw.org (#16).
     *
     * A rede roda fora da main thread; montar os [ProgramItem], gravar o estado do
     * Compose e chamar [onResult] voltam para ela — escrever `meetings.value` de
     * dentro da thread não recomõe a tela.
     *
     * @param meetingId reunião a atualizar. Nulo faz o import achar a reunião de
     *   meio de semana da data e criá-la quando ela ainda não existir.
     */
    private fun importMwb(date: LocalDate, meetingId: Long?, onResult: (String?) -> Unit) {
        if (!can(AppPermissions.GENERATE_ASSIGNMENTS)) {
            onResult(denied(AppPermissions.GENERATE_ASSIGNMENTS, "importar o programa"))
            return
        }
        Thread {
            val result = runCatching {
                val program = MwbProgramImporter.fetch(date)
                program to program.parts.map { p ->
                    ProgramItem(section = p.section, number = p.number, title = p.title, minutes = p.minutes)
                }
            }
            Handler(Looper.getMainLooper()).post {
                result.fold(
                    onSuccess = { (program, items) ->
                        meetings.value = applyMwbProgram(date, meetingId, program.theme, items)
                        repo.saveMeetings(meetings.value)
                        onResult(null)
                    },
                    onFailure = { onResult(it.message ?: "Não foi possível ler o programa no jw.org.") }
                )
            }
        }.start()
    }

    /**
     * Grava tema e itens do programa oficial em [meetings].
     *
     * Com [meetingId] a reunião existente é atualizada; sem ele, a reunião de meio
     * de semana de [date] é atualizada se existir — o que preserva as designações —,
     * ou criada vazia se ainda não existir.
     */
    private fun applyMwbProgram(
        date: LocalDate,
        meetingId: Long?,
        theme: String,
        items: List<ProgramItem>
    ): List<Meeting> {
        val current = meetings.value
        val dateText = date.format(AssignmentGenerator.DATE_FORMATTER)
        val targetId = meetingId ?: current.firstOrNull {
            it.date == dateText && it.isMidweek
        }?.id
        val updated = if (targetId != null) {
            current.map { if (it.id == targetId) it.copy(theme = theme, program = items) else it }
        } else {
            current + Meeting(
                id = AssignmentGenerator.nextId(),
                date = dateText,
                type = TIPO_REUNIAO_MWB,
                assignments = emptyList(),
                blockedBrotherIds = emptySet(),
                theme = theme,
                program = items
            )
        }
        return updated.sortedBy { AssignmentGenerator.parseDate(it.date) }
    }

    fun deleteMonth(yearMonth: YearMonth) {
        if (!can(AppPermissions.GENERATE_ASSIGNMENTS)) { lastActionError.value = denied(AppPermissions.GENERATE_ASSIGNMENTS, "excluir o mês"); return }
        val prefix = yearMonth.format(DateTimeFormatter.ofPattern("MM/yyyy"))
        meetings.value = meetings.value.filterNot {
            it.date.endsWith("/$prefix")
        }
        repo.saveMeetings(meetings.value)
    }

    fun candidatesFor(meeting: Meeting, privilegeId: Long, currentBrotherId: Long): List<Brother> =
        AssignmentGenerator.candidatesFor(meeting, privilegeId, currentBrotherId, brothers.value, privileges.value)

    fun missingAssignments(meeting: Meeting): List<Privilege> =
        AssignmentGenerator.missingAssignments(meeting, privileges.value)

    fun setBrotherRole(id: Long, role: BrotherRole) {
        if (!can(AppPermissions.MANAGE_BROTHERS)) { lastActionError.value = denied(AppPermissions.MANAGE_BROTHERS, "alterar o papel de um irmão"); return }
        brothers.value = brothers.value.map { if (it.id == id) it.copy(role = role) else it }
        repo.saveBrothers(brothers.value)
    }

    fun setBrotherBaptized(id: Long, baptized: Boolean) {
        if (!can(AppPermissions.MANAGE_BROTHERS)) { lastActionError.value = denied(AppPermissions.MANAGE_BROTHERS, "alterar um irmão"); return }
        brothers.value = brothers.value.map { if (it.id == id) it.copy(baptized = baptized) else it }
        repo.saveBrothers(brothers.value)
    }

    fun setBrotherTrainee(id: Long, trainee: Boolean) {
        if (!can(AppPermissions.MANAGE_BROTHERS)) { lastActionError.value = denied(AppPermissions.MANAGE_BROTHERS, "alterar um irmão"); return }
        brothers.value = brothers.value.map { if (it.id == id) it.copy(trainee = trainee) else it }
        repo.saveBrothers(brothers.value)
    }

    fun setBrotherIsReader(id: Long, value: Boolean) {
        if (!can(AppPermissions.MANAGE_BROTHERS)) { lastActionError.value = denied(AppPermissions.MANAGE_BROTHERS, "alterar um irmão"); return }
        brothers.value = brothers.value.map { b ->
            // Leitor de A Sentinela é leitor: manter os dois coerentes.
            when {
                !value && b.id == id -> b.copy(isReader = false, isSentinelReader = false)
                b.id == id -> b.copy(isReader = true)
                else -> b
            }
        }
        repo.saveBrothers(brothers.value)
    }

    fun setBrotherIsSentinelReader(id: Long, value: Boolean) {
        if (!can(AppPermissions.MANAGE_BROTHERS)) { lastActionError.value = denied(AppPermissions.MANAGE_BROTHERS, "alterar um irmão"); return }
        brothers.value = brothers.value.map { b ->
            if (b.id != id) b else b.copy(isSentinelReader = value, isReader = if (value) true else b.isReader)
        }
        repo.saveBrothers(brothers.value)
    }

    fun setPrivilegeKind(id: Long, kind: PartKind) {
        if (!can(AppPermissions.MANAGE_PRIVILEGES)) { lastActionError.value = denied(AppPermissions.MANAGE_PRIVILEGES, "alterar o tipo da parte"); return }
        privileges.value = privileges.value.map { if (it.id == id) it.copy(kind = kind) else it }
        repo.savePrivileges(privileges.value)
    }

    fun setPrivilegeMaleOnly(id: Long, maleOnly: Boolean) {
        if (!can(AppPermissions.MANAGE_PRIVILEGES)) { lastActionError.value = denied(AppPermissions.MANAGE_PRIVILEGES, "alterar quem pode fazer a parte"); return }
        privileges.value = privileges.value.map { if (it.id == id) it.copy(maleOnly = maleOnly) else it }
        repo.savePrivileges(privileges.value)
    }

    fun setPrivilegeReaderGrant(id: Long, grant: ReaderGrant) {
        if (!can(AppPermissions.MANAGE_PRIVILEGES)) { lastActionError.value = denied(AppPermissions.MANAGE_PRIVILEGES, "alterar privilégios"); return }
        privileges.value = privileges.value.map { if (it.id == id) it.copy(readerGrant = grant) else it }
        repo.savePrivileges(privileges.value)
    }

    fun setPrivilegeAllowedStatus(id: Long, status: Set<BrotherStatus>) {
        if (!can(AppPermissions.MANAGE_PRIVILEGES)) { lastActionError.value = denied(AppPermissions.MANAGE_PRIVILEGES, "alterar privilégios"); return }
        privileges.value = privileges.value.map { if (it.id == id) it.copy(allowedStatus = status) else it }
        repo.savePrivileges(privileges.value)
    }

    fun addUnavailability(brotherId: Long, startDate: String, endDate: String, reason: String): String? {
        if (!can(AppPermissions.GENERATE_ASSIGNMENTS)) return denied(AppPermissions.GENERATE_ASSIGNMENTS, "registrar ausências")
        val start = AssignmentGenerator.parseDate(startDate)
        val end = AssignmentGenerator.parseDate(endDate)
        if (start == LocalDate.MIN || end == LocalDate.MIN) return "Data inválida. Use o formato dd/MM/yyyy."
        if (end.isBefore(start)) return "A data de término não pode ser anterior à data de início."
        val newPeriod = UnavailablePeriod(AssignmentGenerator.nextId(), startDate, endDate, reason.trim())
        brothers.value = brothers.value.map {
            if (it.id == brotherId) it.copy(unavailabilities = it.unavailabilities + newPeriod) else it
        }
        repo.saveBrothers(brothers.value)
        return null
    }

    fun removeUnavailability(brotherId: Long, periodId: Long) {
        if (!can(AppPermissions.GENERATE_ASSIGNMENTS)) { lastActionError.value = denied(AppPermissions.GENERATE_ASSIGNMENTS, "remover ausências"); return }
        brothers.value = brothers.value.map {
            if (it.id == brotherId) it.copy(unavailabilities = it.unavailabilities.filterNot { p -> p.id == periodId }) else it
        }
        repo.saveBrothers(brothers.value)
    }

    fun setPrivilegeMinRole(id: Long, role: BrotherRole) {
        if (!can(AppPermissions.MANAGE_PRIVILEGES)) { lastActionError.value = denied(AppPermissions.MANAGE_PRIVILEGES, "alterar privilégios"); return }
        privileges.value = privileges.value.map { if (it.id == id) it.copy(minRole = role) else it }
        repo.savePrivileges(privileges.value)
    }

    fun exportBrothersCsv(): String = CsvDataHandler.exportBrothersToCsv(brothers.value, privileges.value)

    fun exportMeetingsCsv(): String = CsvDataHandler.exportMeetingsToCsv(meetings.value, brothers.value, privileges.value)

    fun importBrothersCsv(csvText: String): Int {
        if (!can(AppPermissions.MANAGE_BROTHERS)) { lastActionError.value = denied(AppPermissions.MANAGE_BROTHERS, "importar irmãos"); return 0 }
        val imported = CsvDataHandler.importBrothersFromCsv(csvText)
        if (imported.isEmpty()) return 0
        val currentBrothers = brothers.value.toMutableList()
        val currentPrivileges = privileges.value
        var count = 0
        imported.forEach { imp ->
            val normName = AssignmentGenerator.normalizeName(imp.name)
            if (normName.isNotBlank() && currentBrothers.none { AssignmentGenerator.normalizeName(it.name) == normName }) {
                val matchedPrivilegeIds = imp.privilegeNames.mapNotNull { pName ->
                    val normPName = AssignmentGenerator.normalizeName(pName)
                    currentPrivileges.find { AssignmentGenerator.normalizeName(it.name) == normPName }?.id
                }.toSet()
                currentBrothers += Brother(
                    id = AssignmentGenerator.nextId(),
                    name = imp.name,
                    phone = imp.phone,
                    privileges = matchedPrivilegeIds,
                    active = imp.active,
                    role = imp.role
                )
                count++
            }
        }
        if (count > 0) {
            brothers.value = currentBrothers.sortedBy { AssignmentGenerator.normalizeName(it.name) }
            repo.saveBrothers(brothers.value)
        }
        return count
    }

    fun setBrotherGender(id: Long, gender: Gender) {
        if (!can(AppPermissions.MANAGE_BROTHERS)) { lastActionError.value = denied(AppPermissions.MANAGE_BROTHERS, "alterar um irmão"); return }
        brothers.value = brothers.value.map { if (it.id == id) it.copy(gender = gender) else it }
        repo.saveBrothers(brothers.value)
    }

    fun addOrUpdatePublicTalk(talk: PublicTalk) {
        if (!can(AppPermissions.GENERATE_ASSIGNMENTS)) { lastActionError.value = denied(AppPermissions.GENERATE_ASSIGNMENTS, "alterar discursos"); return }
        val current = publicTalks.value
        val index = current.indexOfFirst { it.id == talk.id }
        val updated = if (index >= 0) {
            current.toMutableList().apply { set(index, talk) }
        } else {
            val id = if (talk.id == 0L) AssignmentGenerator.nextId() else talk.id
            current + talk.copy(id = id)
        }
        publicTalks.value = updated.sortedByDescending { AssignmentGenerator.parseDate(it.date) }
        repo.savePublicTalks(publicTalks.value)
    }

    fun deletePublicTalk(id: Long) {
        if (!can(AppPermissions.GENERATE_ASSIGNMENTS)) { lastActionError.value = denied(AppPermissions.GENERATE_ASSIGNMENTS, "excluir discursos"); return }
        publicTalks.value = publicTalks.value.filterNot { it.id == id }
        repo.savePublicTalks(publicTalks.value)
    }

    fun addOrUpdateGroup(group: FieldServiceGroup) {
        if (!can(AppPermissions.MANAGE_BROTHERS)) { lastActionError.value = denied(AppPermissions.MANAGE_BROTHERS, "alterar grupos de campo"); return }
        val current = fieldServiceGroups.value
        val index = current.indexOfFirst { it.id == group.id }
        val updated = if (index >= 0) {
            current.toMutableList().apply { set(index, group) }
        } else {
            val id = if (group.id == 0L) AssignmentGenerator.nextId() else group.id
            current + group.copy(id = id)
        }
        fieldServiceGroups.value = updated.sortedBy { it.number }
        repo.saveFieldServiceGroups(fieldServiceGroups.value)
    }

    fun deleteGroup(id: Long) {
        if (!can(AppPermissions.MANAGE_BROTHERS)) { lastActionError.value = denied(AppPermissions.MANAGE_BROTHERS, "excluir grupos de campo"); return }
        fieldServiceGroups.value = fieldServiceGroups.value.filterNot { it.id == id }
        repo.saveFieldServiceGroups(fieldServiceGroups.value)
    }

    fun addOrUpdateCleaningSchedule(scheduleItem: CleaningSchedule) {
        if (!can(AppPermissions.GENERATE_ASSIGNMENTS)) { lastActionError.value = denied(AppPermissions.GENERATE_ASSIGNMENTS, "alterar a limpeza"); return }
        val current = cleaningSchedules.value
        val index = current.indexOfFirst { it.id == scheduleItem.id }
        val updated = if (index >= 0) {
            current.toMutableList().apply { set(index, scheduleItem) }
        } else {
            val id = if (scheduleItem.id == 0L) AssignmentGenerator.nextId() else scheduleItem.id
            current + scheduleItem.copy(id = id)
        }
        cleaningSchedules.value = updated.sortedBy { AssignmentGenerator.parseDate(it.weekDate) }
        repo.saveCleaningSchedules(cleaningSchedules.value)
    }

    fun deleteCleaningSchedule(id: Long) {
        if (!can(AppPermissions.GENERATE_ASSIGNMENTS)) { lastActionError.value = denied(AppPermissions.GENERATE_ASSIGNMENTS, "excluir a limpeza"); return }
        cleaningSchedules.value = cleaningSchedules.value.filterNot { it.id == id }
        repo.saveCleaningSchedules(cleaningSchedules.value)
    }

    fun generateMonthCleaning(yearMonth: YearMonth) {
        if (!can(AppPermissions.GENERATE_ASSIGNMENTS)) { lastActionError.value = denied(AppPermissions.GENERATE_ASSIGNMENTS, "gerar a escala de limpeza"); return }
        val groups = fieldServiceGroups.value.sortedBy { it.number }
        if (groups.isEmpty()) return

        val dates = (1..yearMonth.lengthOfMonth())
            .map { yearMonth.atDay(it) }
            .filter { it.dayOfWeek == java.time.DayOfWeek.SATURDAY }

        val existing = cleaningSchedules.value.toMutableList()
        var groupIdx = 0

        dates.forEach { date ->
            val dateStr = date.format(AssignmentGenerator.DATE_FORMATTER)
            val group = groups[groupIdx % groups.size]
            groupIdx++

            val existingIdx = existing.indexOfFirst { it.weekDate == dateStr }
            if (existingIdx >= 0) {
                existing[existingIdx] = existing[existingIdx].copy(groupId = group.id)
            } else {
                existing.add(
                    CleaningSchedule(
                        id = AssignmentGenerator.nextId(),
                        weekDate = dateStr,
                        groupId = group.id,
                        details = "Limpeza do Salão pelo Grupo ${group.number}"
                    )
                )
            }
        }

        cleaningSchedules.value = existing.sortedBy { AssignmentGenerator.parseDate(it.weekDate) }
        repo.saveCleaningSchedules(cleaningSchedules.value)
    }

    private fun normalizeName(value: String): String = AssignmentGenerator.normalizeName(value)

    private fun nextId(): Long = AssignmentGenerator.nextId()
}
