package br.com.willendary.designacoesjw.data

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.willendary.designacoesjw.sync.CloudGuard
import br.com.willendary.designacoesjw.sync.ContadorSincronizacao
import br.com.willendary.designacoesjw.sync.EstadoSincronizacao

/**
 * Persistência híbrida:
 * - SharedPreferences mantém o cache local e permite abrir o app offline.
 * - Cloud Firestore sincroniza os mesmos dados entre Android, Desktop e Web.
 */
private const val TAG_REPO = "AppRepository"

class AppRepository(context: Context) {
    private val prefs = context.getSharedPreferences("designacoes_jw", Context.MODE_PRIVATE)
    private val firestore = FirebaseFirestore.getInstance()
    private val workspace = firestore.collection("workspaces").document("designacoes-jw")
    private val brothersCollection = workspace.collection("brothers")
    private val privilegesCollection = workspace.collection("privileges")
    private val meetingsCollection = workspace.collection("meetings")
    private val publicTalksCollection = workspace.collection("publicTalks")
    private val groupsCollection = workspace.collection("fieldServiceGroups")
    private val cleaningCollection = workspace.collection("cleaningSchedules")
    private val settingsDocument = workspace.collection("settings").document("main")

    private val listeners = mutableListOf<ListenerRegistration>()

    /**
     * Canal de erro para a UI. Até aqui nenhuma falha de escrita era tratada:
     * todo `set()` era fire-and-forget, o Firestore podia recusar por permissão
     * insuficiente e o app continuava mostrando "salvo".
     */
    var onSyncError: ((String) -> Unit)? = null

    /**
     * Estado da sincronizacao, para a tela (#61).
     *
     * A logica esta em `shared`, e nao aqui: a ordem de prioridade entre
     * "falhou", "sincronizando", "nao enviada" e "cache local" e regra de
     * negocio, e regra de negocio nao se escreve duas vezes.
     *
     * Vive no repositorio porque e ele quem tem os `Task` do Firestore -- e o
     * `Task` resolve quando a escrita entra no armazenamento **local**, o que
     * sem rede e antes de subir. So o contador diz que aquilo continua na fila.
     */
    private val sync = ContadorSincronizacao()
    var estadoSincronizacao by mutableStateOf(EstadoSincronizacao())
        private set

    /**
     * Mudanca de estado para a UI.
     *
     * Um canal so. Ligar `ConnectivityManager` aqui seria redundante: o
     * `isFromCache` do proprio Firestore ja diz que a leitura saiu do cache,
     * e ele erra menos -- o aparelho pode estar "no ar" e sem rota para o
     * Firestore.
     */
    var aoMudarEstado: ((EstadoSincronizacao) -> Unit)? = null

    private fun atualizaSync() {
        estadoSincronizacao = sync.atual
        aoMudarEstado?.invoke(estadoSincronizacao)
    }

    /**
     * Se a tela de primeiro uso ainda precisa aparecer (#62).
     *
     * E `nunca viu **e** nao ha irmao`. Quem ja tem 40 irmao vindo do desktop
     * nao esta comecando, e mostrar a tela para essa pessoa e ruido que faz o
     * app parecer perdido.
     */
    fun precisaPrimeiroUso(temIrmao: Boolean): Boolean =
        !prefs.getBoolean("primeiro_uso_visto", false) && !temIrmao

    /** Marca como vista. Grava de verdade, nao `apply`: e uma vez na vida. */
    fun marcarPrimeiroUsoVisto() {
        prefs.edit().putBoolean("primeiro_uso_visto", true).commit()
    }

    private fun reportError(message: String) {
        Log.w(TAG, message)
        onSyncError?.invoke(message)
    }

    /**
     * Grava [writes] e só então remove os documentos fora de [keep].
     *
     * O prune era o caminho de perda de dados: rodava no sucesso do `get()`,
     * independentemente das escritas. Um usuário sem `manage_brothers` tinha
     * todos os `set()` recusados em silêncio, o `get()` passava (leitura só pede
     * `view_assignments`), e a limpeza apagava da nuvem tudo que não estivesse
     * na lista local — que podia estar vazia ou desatualizada.
     *
     * Sem todas as escritas confirmadas, não apaga nada.
     */
    private fun pushAndPrune(
        writes: List<Task<Void>>,
        keep: Set<String>,
        label: String,
        query: Query
    ) {
        val prune: () -> Unit = {
            query.get().addOnSuccessListener { snapshot ->
                val stale = snapshot.documents.filter { it.id !in keep }
                when {
                    stale.isEmpty() -> Unit
                    // Lista local vazia sobre coleção populada é quase sempre
                    // cache incompleto, não remoção intencional.
                    keep.isEmpty() -> reportError(
                        "Não apaguei $label do servidor: a lista local está vazia, " +
                            "o que normalmente é falha de sincronização. Confira a lista antes de salvar de novo."
                    )
                    else -> stale.forEach { it.reference.delete() }
                }
            }.addOnFailureListener { e ->
                reportError("Falha ao ler $label do servidor: ${e.message}")
            }
        }

        if (writes.isEmpty()) {
            prune()
        } else {
            sync.emGravacao()
            atualizaSync()
            Tasks.whenAllSuccess<Void>(writes)
                .addOnSuccessListener {
                    sync.confirmada()
                    atualizaSync()
                    prune()
                }
                .addOnFailureListener { e ->
                    // A escrita continua **nao enviada**: e por isso que o
                    // contador nao volta a zero aqui. Zerar deixaria a tela
                    // dizendo "Sincronizado" com a alteracao presa no aparelho.
                    sync.falhou(e.message ?: "falha ao gravar")
                    atualizaSync()
                    reportError(
                        "Não consegui gravar $label no servidor (${e.message}). " +
                            "A alteração ficou só neste aparelho — provavelmente sua conta não tem permissão de escrita."
                    )
                }
        }
    }

    /**
     * Registra um listener de snapshot sem deixar exceção matar o processo.
     *
     * **Por que isto existe:** o callback do `addSnapshotListener` roda na
     * thread do Firestore. Uma exceção ali **não** é capturada pelo
     * `runCatching` de quem chamou e não aparece no logcat com contexto — ela
     * derruba o processo inteiro. O sintoma é o app abrir, pintar a tela
     * principal e fechar sozinho, sem rastro nenhum.
     *
     * Foi exatamente o que aconteceu: `loadMeetings()` era chamado de dentro
     * do listener, na migração de cache, e o `init` do ViewModel chamava a
     * mesma função. Proteger só o `init` não adiantou — a segunda chamada
     * continuava sem rede, e o fechamento continuou.
     */
    private fun com.google.firebase.firestore.CollectionReference.ouvir(
        rotulo: String,
        onError: (String) -> Unit,
        corpo: (com.google.firebase.firestore.QuerySnapshot?, Exception?) -> Unit
    ): ListenerRegistration = addSnapshotListener { snapshot, error ->
        try {
            // `isFromCache` e a resposta honesta para "o que estou vendo esta na
            // nuvem ou e cache velho": quem sabe e o proprio Firestore, nao o
            // relogio. Deduzir por tempo decorrido erra nos dois sentidos.
            if (snapshot != null) {
                if (snapshot.metadata.isFromCache) sync.lidaDoCache() else sync.lidaDoServidor()
                atualizaSync()
            }
            corpo(snapshot, error)
        } catch (e: Exception) {
            val mensagem = "Falha ao sincronizar $rotulo: ${e.message}"
            Log.e(TAG_REPO, mensagem, e)
            onError(mensagem)
        }
    }

    /** Mesma rede para documento único. */
    private fun com.google.firebase.firestore.DocumentReference.ouvir(
        rotulo: String,
        onError: (String) -> Unit,
        corpo: (com.google.firebase.firestore.DocumentSnapshot?, Exception?) -> Unit
    ): ListenerRegistration = addSnapshotListener { snapshot, error ->
        try {
            corpo(snapshot, error)
        } catch (e: Exception) {
            val mensagem = "Falha ao sincronizar $rotulo: ${e.message}"
            Log.e(TAG_REPO, mensagem, e)
            onError(mensagem)
        }
    }

    fun closeCloudSync() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    fun startCloudSync(
        onBrothers: (List<Brother>) -> Unit,
        onPrivileges: (List<Privilege>) -> Unit,
        onMeetings: (List<Meeting>) -> Unit,
        onSchedule: (MeetingSchedule) -> Unit,
        onPublicTalks: ((List<PublicTalk>) -> Unit)? = null,
        onGroups: ((List<FieldServiceGroup>) -> Unit)? = null,
        onCleaning: ((List<CleaningSchedule>) -> Unit)? = null,
        onError: (String) -> Unit = { reportError(it) }
    ) {
        closeCloudSync()

        listeners += brothersCollection.ouvir("irmãos", onError) { snapshot, error ->
            if (error != null) {
                onError(error.localizedMessage ?: "Erro ao sincronizar irmãos.")
                return@ouvir
            }
            val items = snapshot?.documents?.mapNotNull(::brotherFromDocument) ?: emptyList()
            if (items.isEmpty() && !cloudMigrationDone("brothers")) {
                val local = loadBrothers()
                cloudMigrationDone("brothers", true)
                if (local.isNotEmpty()) {
                    saveBrothers(local)
                    onBrothers(local)
                    return@ouvir
                }
            } else if (items.isNotEmpty()) {
                cloudMigrationDone("brothers", true)
            }
            // Nuvem vazia sobre cache cheio: mantém o que está no aparelho.
            // Sem esta trava, uma permissão revogada apaga a lista da tela.
            //
            // O local é lido UMA vez e dentro do runCatching: chamar o
            // load duas vezes transformava a trava em exceção quando o
            // segundo parse falhava — que é justamente o caso que ela
            // existe para proteger.
            if (items.isEmpty()) {
                val local = runCatching { loadBrothers() }
                    .onFailure { erro ->
                        onError("Falha ao ler irmãos deste aparelho: ${erro.message}")
                    }
                    .getOrNull()
                // `local != null` e o `CloudGuard` sao checks diferentes: o
                // primeiro serve ao compilador (a entrega exige lista), o
                // segundo e a politica — e ela esta testada em `shared`.
                if (local != null && CloudGuard.deveManterLocal(items, local)) {
                    onBrothers(local)
                    avisaNuvemVazia("irmãos", temLocal = true, onError = onError)
                    return@ouvir
                }
            }
            saveBrothersLocal(items)
            onBrothers(items)
        }

        listeners += privilegesCollection.ouvir("privilégios", onError) { snapshot, error ->
            if (error != null) {
                onError(error.localizedMessage ?: "Erro ao sincronizar privilégios.")
                return@ouvir
            }
            val items = snapshot?.documents?.mapNotNull(::privilegeFromDocument) ?: emptyList()
            if (items.isEmpty() && !cloudMigrationDone("privileges")) {
                val local = loadPrivileges()
                cloudMigrationDone("privileges", true)
                if (local.isNotEmpty()) {
                    savePrivileges(local)
                    onPrivileges(local)
                    return@ouvir
                }
            } else if (items.isNotEmpty()) {
                cloudMigrationDone("privileges", true)
            }
            // Nuvem vazia sobre cache cheio: mantém o que está no aparelho.
            // Sem esta trava, uma permissão revogada apaga a lista da tela.
            //
            // O local é lido UMA vez e dentro do runCatching: chamar o
            // load duas vezes transformava a trava em exceção quando o
            // segundo parse falhava — que é justamente o caso que ela
            // existe para proteger.
            if (items.isEmpty()) {
                val local = runCatching { loadPrivileges() }
                    .onFailure { erro ->
                        onError("Falha ao ler privilégios deste aparelho: ${erro.message}")
                    }
                    .getOrNull()
                // `local != null` e o `CloudGuard` sao checks diferentes: o
                // primeiro serve ao compilador (a entrega exige lista), o
                // segundo e a politica — e ela esta testada em `shared`.
                if (local != null && CloudGuard.deveManterLocal(items, local)) {
                    onPrivileges(local)
                    avisaNuvemVazia("privilégios", temLocal = true, onError = onError)
                    return@ouvir
                }
            }
            savePrivilegesLocal(items)
            onPrivileges(items)
        }

        listeners += meetingsCollection.ouvir("reuniões", onError) { snapshot, error ->
            if (error != null) {
                onError(error.localizedMessage ?: "Erro ao sincronizar histórico.")
                return@ouvir
            }
            val items = snapshot?.documents?.mapNotNull(::meetingFromDocument) ?: emptyList()
            if (items.isEmpty() && !cloudMigrationDone("meetings")) {
                val local = loadMeetings()
                cloudMigrationDone("meetings", true)
                if (local.isNotEmpty()) {
                    saveMeetings(local)
                    onMeetings(local)
                    return@ouvir
                }
            } else if (items.isNotEmpty()) {
                cloudMigrationDone("meetings", true)
            }
            // Nuvem vazia sobre cache cheio: mantém o que está no aparelho.
            // Sem esta trava, uma permissão revogada apaga a lista da tela.
            //
            // O local é lido UMA vez e dentro do runCatching: chamar o
            // load duas vezes transformava a trava em exceção quando o
            // segundo parse falhava — que é justamente o caso que ela
            // existe para proteger.
            if (items.isEmpty()) {
                val local = runCatching { loadMeetings() }
                    .onFailure { erro ->
                        onError("Falha ao ler reuniões deste aparelho: ${erro.message}")
                    }
                    .getOrNull()
                // `local != null` e o `CloudGuard` sao checks diferentes: o
                // primeiro serve ao compilador (a entrega exige lista), o
                // segundo e a politica — e ela esta testada em `shared`.
                if (local != null && CloudGuard.deveManterLocal(items, local)) {
                    onMeetings(local)
                    avisaNuvemVazia("reuniões", temLocal = true, onError = onError)
                    return@ouvir
                }
            }
            saveMeetingsLocal(items)
            onMeetings(items)
        }

        listeners += settingsDocument.ouvir("configurações", onError) { snapshot, error ->
            if (error != null) {
                onError(error.localizedMessage ?: "Erro ao sincronizar configurações.")
                return@ouvir
            }
            val data = snapshot?.data
            if (data == null) {
                val local = loadSchedule()
                if (local != MeetingSchedule()) {
                    saveSchedule(local)
                    onSchedule(local)
                }
                return@ouvir
            }
            val schedule = MeetingSchedule(
                (data["firstDay"] as? Number)?.toInt() ?: 3,
                (data["secondDay"] as? Number)?.toInt() ?: 6
            )
            saveScheduleLocal(schedule)
            onSchedule(schedule)
        }

        if (onPublicTalks != null) {
            listeners += publicTalksCollection.ouvir("discursos públicos", onError) { snapshot, error ->
                if (error == null && snapshot != null) {
                    val items = snapshot.documents.mapNotNull(::publicTalkFromDocument)
                    // Nuvem vazia sobre cache cheio: mantém o que está no aparelho.
            // Sem esta trava, uma permissão revogada apaga a lista da tela.
            //
            // O local é lido UMA vez e dentro do runCatching: chamar o
            // load duas vezes transformava a trava em exceção quando o
            // segundo parse falhava — que é justamente o caso que ela
            // existe para proteger.
            if (items.isEmpty()) {
                val local = runCatching { loadPublicTalks() }
                    .onFailure { erro ->
                        onError("Falha ao ler discursos públicos deste aparelho: ${erro.message}")
                    }
                    .getOrNull()
                // `local != null` e o `CloudGuard` sao checks diferentes: o
                // primeiro serve ao compilador (a entrega exige lista), o
                // segundo e a politica — e ela esta testada em `shared`.
                if (local != null && CloudGuard.deveManterLocal(items, local)) {
                    onPublicTalks(local)
                    avisaNuvemVazia("discursos públicos", temLocal = true, onError = onError)
                    return@ouvir
                }
            }
            savePublicTalksLocal(items)
                    onPublicTalks(items)
                }
            }
        }

        if (onGroups != null) {
            listeners += groupsCollection.ouvir("grupos de campo", onError) { snapshot, error ->
                if (error == null && snapshot != null) {
                    val items = snapshot.documents.mapNotNull(::groupFromDocument)
                    // Nuvem vazia sobre cache cheio: mantém o que está no aparelho.
            // Sem esta trava, uma permissão revogada apaga a lista da tela.
            //
            // O local é lido UMA vez e dentro do runCatching: chamar o
            // load duas vezes transformava a trava em exceção quando o
            // segundo parse falhava — que é justamente o caso que ela
            // existe para proteger.
            if (items.isEmpty()) {
                val local = runCatching { loadFieldServiceGroups() }
                    .onFailure { erro ->
                        onError("Falha ao ler grupos de campo deste aparelho: ${erro.message}")
                    }
                    .getOrNull()
                // `local != null` e o `CloudGuard` sao checks diferentes: o
                // primeiro serve ao compilador (a entrega exige lista), o
                // segundo e a politica — e ela esta testada em `shared`.
                if (local != null && CloudGuard.deveManterLocal(items, local)) {
                    onGroups(local)
                    avisaNuvemVazia("grupos de campo", temLocal = true, onError = onError)
                    return@ouvir
                }
            }
            saveFieldServiceGroupsLocal(items)
                    onGroups(items)
                }
            }
        }

        if (onCleaning != null) {
            listeners += cleaningCollection.ouvir("limpeza", onError) { snapshot, error ->
                if (error == null && snapshot != null) {
                    val items = snapshot.documents.mapNotNull(::cleaningFromDocument)
                    // Nuvem vazia sobre cache cheio: mantém o que está no aparelho.
            // Sem esta trava, uma permissão revogada apaga a lista da tela.
            //
            // O local é lido UMA vez e dentro do runCatching: chamar o
            // load duas vezes transformava a trava em exceção quando o
            // segundo parse falhava — que é justamente o caso que ela
            // existe para proteger.
            if (items.isEmpty()) {
                val local = runCatching { loadCleaningSchedules() }
                    .onFailure { erro ->
                        onError("Falha ao ler a limpeza deste aparelho: ${erro.message}")
                    }
                    .getOrNull()
                // `local != null` e o `CloudGuard` sao checks diferentes: o
                // primeiro serve ao compilador (a entrega exige lista), o
                // segundo e a politica — e ela esta testada em `shared`.
                if (local != null && CloudGuard.deveManterLocal(items, local)) {
                    onCleaning(local)
                    avisaNuvemVazia("limpeza", temLocal = true, onError = onError)
                    return@ouvir
                }
            }
            saveCleaningSchedulesLocal(items)
                    onCleaning(items)
                }
            }
        }
    }

    fun loadBrothers(): List<Brother> {
        val a = org.json.JSONArray(prefs.getString("brothers", "[]"))
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            val p = o.optJSONArray("privileges") ?: org.json.JSONArray()
            val ids = mutableSetOf<Long>()
            for (j in 0 until p.length()) ids += p.getLong(j)
            val roleStr = o.optString("role", "PUBLISHER")
            val role = runCatching { BrotherRole.valueOf(roleStr) }.getOrDefault(BrotherRole.PUBLISHER)
            val genderStr = o.optString("gender", "MALE")
            val gender = runCatching { Gender.valueOf(genderStr) }.getOrDefault(Gender.MALE)
            val groupId = if (o.has("groupId")) o.optLong("groupId").takeIf { it > 0L } else null
            val unavailArray = o.optJSONArray("unavailabilities") ?: org.json.JSONArray()
            val unavails = List(unavailArray.length()) { u ->
                val uObj = unavailArray.getJSONObject(u)
                UnavailablePeriod(uObj.optLong("id", 0L), uObj.getString("startDate"), uObj.getString("endDate"), uObj.optString("reason", ""))
            }
            Brother(
                id = o.getLong("id"),
                name = o.getString("name"),
                phone = o.optString("phone"),
                privileges = ids,
                active = o.optBoolean("active", true),
                role = role,
                unavailabilities = unavails,
                gender = gender,
                groupId = groupId,
                baptized = o.optBoolean("baptized", true),
                trainee = o.optBoolean("trainee", false),
                isReader = o.optBoolean("isReader", false),
                isSentinelReader = o.optBoolean("isSentinelReader", false),
                entrouEm = o.optString("entrouEm", "")
            )
        }
    }

    fun saveBrothers(items: List<Brother>) {
        saveBrothersLocal(items)
        val writes = items.map { brother ->
            brothersCollection.document(brother.id.toString()).set(brother.toMap(), SetOptions.merge())
        }
        pushAndPrune(writes, items.map { it.id.toString() }.toSet(), "os irmãos", brothersCollection)
    }

    fun loadPrivileges(): List<Privilege> {
        val a = org.json.JSONArray(prefs.getString("privileges", "[]"))
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            val roleStr = o.optString("minRole", "PUBLISHER")
            val minRole = runCatching { BrotherRole.valueOf(roleStr) }.getOrDefault(BrotherRole.PUBLISHER)
            Privilege(
                id = o.getLong("id"),
                name = o.getString("name"),
                quantity = o.optInt("quantity", 1).coerceAtLeast(1),
                active = o.optBoolean("active", true),
                allowedDays = loadIntSet(o.optJSONArray("allowedDays")),
                minRole = minRole,
                maleOnly = o.optBoolean("maleOnly", true)
            )
        }
    }

    fun savePrivileges(items: List<Privilege>) {
        savePrivilegesLocal(items)
        val writes = items.map { privilege ->
            privilegesCollection.document(privilege.id.toString()).set(privilege.toMap(), SetOptions.merge())
        }
        pushAndPrune(writes, items.map { it.id.toString() }.toSet(), "os privilégios", privilegesCollection)
    }

    fun loadMeetings(): List<Meeting> {
        val a = org.json.JSONArray(prefs.getString("meetings", "[]"))
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            val aa = o.optJSONArray("assignments") ?: org.json.JSONArray()
            val assignments = List(aa.length()) { j ->
                val x = aa.getJSONObject(j)
                Assignment(x.getLong("privilegeId"), x.getLong("brotherId"))
            }
            val blocked = mutableSetOf<Long>()
            val ba = o.optJSONArray("blockedBrotherIds") ?: org.json.JSONArray()
            for (j in 0 until ba.length()) blocked += ba.getLong(j)
            // Aceita os dois formatos: JSONObject (novo) e String (legado "N. Título (M min)").
            val program = mutableListOf<ProgramItem>()
            val pa = o.optJSONArray("program") ?: org.json.JSONArray()
            for (j in 0 until pa.length()) {
                val raw = pa.opt(j)
                when (raw) {
                    is org.json.JSONObject -> program += programItemFromJson(raw)
                    is String -> parseLegacyProgramItem(raw)?.let { program += it }
                }
            }
            // Reunião antiga não tem "programAssignments": lista vazia, sem erro.
            // Item sem "brotherIds" também é parte sem ninguém — não é erro.
            val paa = o.optJSONArray("programAssignments") ?: org.json.JSONArray()
            val programAssignments = List(paa.length()) { j ->
                val x = paa.getJSONObject(j)
                val ids = x.optJSONArray("brotherIds") ?: org.json.JSONArray()
                ProgramAssignment(
                    item = x.optInt("item"),
                    brotherIds = List(ids.length()) { k -> ids.getLong(k) }
                )
            }
            Meeting(
                o.getLong("id"), o.getString("date"), MeetingType.parse(o.getString("type")).label, assignments, blocked,
                o.optString("theme"), program, programAssignments
            )
        }
    }

    fun saveMeetings(items: List<Meeting>) {
        saveMeetingsLocal(items)
        val writes = items.map { meeting ->
            meetingsCollection.document(meeting.id.toString()).set(meeting.toMap(), SetOptions.merge())
        }
        pushAndPrune(writes, items.map { it.id.toString() }.toSet(), "as reuniões", meetingsCollection)
    }

    fun loadPublicTalks(): List<PublicTalk> {
        val a = org.json.JSONArray(prefs.getString("public_talks", "[]"))
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            PublicTalk(
                id = o.getLong("id"),
                meetingId = if (o.has("meetingId")) o.optLong("meetingId") else null,
                date = o.optString("date"),
                themeNumber = if (o.has("themeNumber")) o.optInt("themeNumber") else null,
                themeTitle = o.optString("themeTitle"),
                speakerName = o.optString("speakerName"),
                speakerCongregation = o.optString("speakerCongregation"),
                speakerPhone = o.optString("speakerPhone"),
                hospitalityBrotherId = if (o.has("hospitalityBrotherId")) o.optLong("hospitalityBrotherId") else null,
                hospitalityNotes = o.optString("hospitalityNotes"),
                confirmed = o.optBoolean("confirmed", false)
            )
        }
    }

    fun savePublicTalks(items: List<PublicTalk>) {
        savePublicTalksLocal(items)
        val writes = items.map { talk ->
            publicTalksCollection.document(talk.id.toString()).set(talk.toMap(), SetOptions.merge())
        }
        pushAndPrune(writes, items.map { it.id.toString() }.toSet(), "os discursos", publicTalksCollection)
    }

    fun loadFieldServiceGroups(): List<FieldServiceGroup> {
        val a = org.json.JSONArray(prefs.getString("field_service_groups", "[]"))
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            FieldServiceGroup(
                id = o.getLong("id"),
                number = o.optInt("number", 1),
                name = o.optString("name", "Grupo ${i + 1}"),
                overseerBrotherId = if (o.has("overseerBrotherId")) o.optLong("overseerBrotherId") else null,
                assistantBrotherId = if (o.has("assistantBrotherId")) o.optLong("assistantBrotherId") else null
            )
        }
    }

    fun saveFieldServiceGroups(items: List<FieldServiceGroup>) {
        saveFieldServiceGroupsLocal(items)
        val writes = items.map { group ->
            groupsCollection.document(group.id.toString()).set(group.toMap(), SetOptions.merge())
        }
        pushAndPrune(writes, items.map { it.id.toString() }.toSet(), "os grupos", groupsCollection)
    }

    fun loadCleaningSchedules(): List<CleaningSchedule> {
        val a = org.json.JSONArray(prefs.getString("cleaning_schedules", "[]"))
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            CleaningSchedule(
                id = o.getLong("id"),
                weekDate = o.optString("weekDate"),
                groupId = if (o.has("groupId")) o.optLong("groupId") else null,
                details = o.optString("details"),
                completed = o.optBoolean("completed", false)
            )
        }
    }

    fun saveCleaningSchedules(items: List<CleaningSchedule>) {
        saveCleaningSchedulesLocal(items)
        val writes = items.map { sched ->
            cleaningCollection.document(sched.id.toString()).set(sched.toMap(), SetOptions.merge())
        }
        pushAndPrune(writes, items.map { it.id.toString() }.toSet(), "a limpeza", cleaningCollection)
    }

    fun loadSchedule(): MeetingSchedule {
        return MeetingSchedule(
            prefs.getInt("schedule_first_day", 3),
            prefs.getInt("schedule_second_day", 6)
        )
    }

    fun saveSchedule(schedule: MeetingSchedule) {
        saveScheduleLocal(schedule)
        settingsDocument.set(
            mapOf("firstDay" to schedule.firstDay, "secondDay" to schedule.secondDay),
            SetOptions.merge()
        ).addOnFailureListener { e ->
            reportError("Não consegui gravar as configurações no servidor: ${e.message}")
        }
    }

    fun loadThemeMode(): ThemeMode {
        val modeStr = prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
        return runCatching { ThemeMode.valueOf(modeStr) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun saveThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    private fun saveBrothersLocal(items: List<Brother>) {
        val a = org.json.JSONArray()
        items.forEach { b ->
            a.put(org.json.JSONObject().apply {
                put("id", b.id)
                put("name", b.name)
                put("phone", b.phone)
                put("active", b.active)
                put("role", b.role.name)
                put("gender", b.gender.name)
                // Campos teocráticos. Sem isto as regras de não batizado,
                // aprendiz e leitor valem só em memória e o app volta ao
                // default quando recarrega.
                put("baptized", b.baptized)
                put("trainee", b.trainee)
                put("isReader", b.isReader)
                put("isSentinelReader", b.isSentinelReader)
                put("entrouEm", b.entrouEm)
                b.groupId?.let { put("groupId", it) }
                put("privileges", org.json.JSONArray(b.privileges.toList()))
                val ua = org.json.JSONArray()
                b.unavailabilities.forEach { u ->
                    ua.put(org.json.JSONObject().apply {
                        put("id", u.id)
                        put("startDate", u.startDate)
                        put("endDate", u.endDate)
                        put("reason", u.reason)
                    })
                }
                put("unavailabilities", ua)
            })
        }
        prefs.edit().putString("brothers", a.toString()).apply()
    }

    private fun savePrivilegesLocal(items: List<Privilege>) {
        val a = org.json.JSONArray()
        items.forEach { p ->
            a.put(org.json.JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("quantity", p.quantity)
                put("active", p.active)
                put("minRole", p.minRole.name)
                put("maleOnly", p.maleOnly)
                put("kind", p.kind.name)
                put("readerGrant", p.readerGrant.name)
                put("allowedDays", org.json.JSONArray(p.allowedDays.toList()))
                put("allowedStatus", org.json.JSONArray(p.allowedStatus.map { it.name }))
            })
        }
        prefs.edit().putString("privileges", a.toString()).apply()
    }

    private fun saveMeetingsLocal(items: List<Meeting>) {
        val a = org.json.JSONArray()
        items.forEach { m ->
            val aa = org.json.JSONArray()
            m.assignments.forEach { x ->
                aa.put(org.json.JSONObject().apply {
                    put("privilegeId", x.privilegeId)
                    put("brotherId", x.brotherId)
                })
            }
            a.put(org.json.JSONObject().apply {
                put("id", m.id)
                put("date", m.date)
                put("type", m.typeCanonical)
                put("assignments", aa)
                put("blockedBrotherIds", org.json.JSONArray(m.blockedBrotherIds.toList()))
                put("theme", m.theme)
                // Grava o formato novo (objetos), não a string legada.
                val pa = org.json.JSONArray()
                m.program.forEach { item ->
                    pa.put(org.json.JSONObject().apply {
                        put("section", item.section)
                        put("number", item.number)
                        put("title", item.title)
                        put("minutes", item.minutes)
                        // Sem isto, `programItemFromJson` volta em INDIVIDUAL a
                        // cada recarga local e a encenação perde o agrupamento.
                        put("kind", item.kind.name)
                    })
                }
                put("program", pa)
                val paa = org.json.JSONArray()
                m.programAssignments.forEach { x ->
                    paa.put(org.json.JSONObject().apply {
                        put("item", x.item)
                        put("brotherIds", org.json.JSONArray(x.brotherIds.toList()))
                    })
                }
                put("programAssignments", paa)
            })
        }
        prefs.edit().putString("meetings", a.toString()).apply()
    }

    private fun savePublicTalksLocal(items: List<PublicTalk>) {
        val a = org.json.JSONArray()
        items.forEach { t ->
            a.put(org.json.JSONObject().apply {
                put("id", t.id)
                t.meetingId?.let { put("meetingId", it) }
                put("date", t.date)
                t.themeNumber?.let { put("themeNumber", it) }
                put("themeTitle", t.themeTitle)
                put("speakerName", t.speakerName)
                put("speakerCongregation", t.speakerCongregation)
                put("speakerPhone", t.speakerPhone)
                t.hospitalityBrotherId?.let { put("hospitalityBrotherId", it) }
                put("hospitalityNotes", t.hospitalityNotes)
                put("confirmed", t.confirmed)
            })
        }
        prefs.edit().putString("public_talks", a.toString()).apply()
    }

    private fun saveFieldServiceGroupsLocal(items: List<FieldServiceGroup>) {
        val a = org.json.JSONArray()
        items.forEach { g ->
            a.put(org.json.JSONObject().apply {
                put("id", g.id)
                put("number", g.number)
                put("name", g.name)
                g.overseerBrotherId?.let { put("overseerBrotherId", it) }
                g.assistantBrotherId?.let { put("assistantBrotherId", it) }
            })
        }
        prefs.edit().putString("field_service_groups", a.toString()).apply()
    }

    private fun saveCleaningSchedulesLocal(items: List<CleaningSchedule>) {
        val a = org.json.JSONArray()
        items.forEach { s ->
            a.put(org.json.JSONObject().apply {
                put("id", s.id)
                put("weekDate", s.weekDate)
                s.groupId?.let { put("groupId", it) }
                put("details", s.details)
                put("completed", s.completed)
            })
        }
        prefs.edit().putString("cleaning_schedules", a.toString()).apply()
    }

    /**
     * Nuvem vazia **não** apaga o que está no aparelho.
     *
     * O caminho de escrita já tinha trava: lista local vazia sobre coleção
     * populada é tratada como cache incompleto. O de leitura não tinha, e é o
     * que destrói — a nuvem devolvendo vazio (permissão revogada, token
     * expirado, regra mudada, coleção recriada) fazia `save*Local(emptyList())`
     * e a tela mostrava lista vazia, sem explicação. É o inverso da #22.
     *
     * Devolve `true` quando o aparelho deve **manter** o que tem.
     *
     * Não trava ninguém: quem quiser mesmo a lista vazia apaga no app, e a
     * remoção vai para a nuvem. O que não pode é a nuvem decidir isso sozinha.
     */
    /**
     * Nuvem vazia sobre cache cheio: mantém o que está no aparelho e diz por quê.
     *
     * A decisão em si é [CloudGuard.deveManterLocal], em `shared` — é domínio,
     * vale igual no Android e no desktop, e é onde está o teste. Aqui só a
     * mensagem.
     */
    private fun avisaNuvemVazia(rotulo: String, temLocal: Boolean, onError: (String) -> Unit) {
        if (!temLocal) return
        onError(
            "A nuvem devolveu $rotulo vazio e eu mantive o que está neste aparelho. " +
                "Normalmente é permissão ou sincronização — confira se sua conta tem acesso. " +
                "Nada foi apagado."
        )
    }

    private fun cloudMigrationDone(key: String): Boolean = prefs.getBoolean("cloud_migration_$key", false)

    private fun cloudMigrationDone(key: String, done: Boolean) {
        prefs.edit().putBoolean("cloud_migration_$key", done).apply()
    }

    private fun saveScheduleLocal(schedule: MeetingSchedule) {
        prefs.edit()
            .putInt("schedule_first_day", schedule.firstDay)
            .putInt("schedule_second_day", schedule.secondDay)
            .apply()
    }

    private fun loadIntSet(array: org.json.JSONArray?): Set<Int> {
        if (array == null) return emptySet()
        val result = mutableSetOf<Int>()
        for (i in 0 until array.length()) result += array.getInt(i)
        return result
    }

    private fun programItemFromJson(o: org.json.JSONObject): ProgramItem = ProgramItem(
        section = o.optString("section", ""),
        number = o.optInt("number", 0),
        title = o.optString("title", ""),
        minutes = o.optInt("minutes", 0),
        // Programa gravado antes do campo existir: cai em INDIVIDUAL.
        kind = runCatching { PartKind.valueOf(o.optString("kind", "INDIVIDUAL")) }
            .getOrDefault(PartKind.INDIVIDUAL)
    )

    private fun Brother.toMap(): Map<String, Any?> = mutableMapOf<String, Any?>(
        "id" to id, "name" to name, "phone" to phone,
        "privileges" to privileges.toList(), "active" to active,
        "role" to role.name, "gender" to gender.name,
        // Campos teocráticos: sem isto as regras valem só em memória e o app
        // volta ao default depois de recarregar.
        "baptized" to baptized, "trainee" to trainee,
        "isReader" to isReader, "isSentinelReader" to isSentinelReader,
        "entrouEm" to entrouEm,
        "unavailabilities" to unavailabilities.map {
            mapOf("id" to it.id, "startDate" to it.startDate, "endDate" to it.endDate, "reason" to it.reason)
        }
    ).apply {
        groupId?.let { put("groupId", it) }
    }

    private fun Privilege.toMap(): Map<String, Any?> = mutableMapOf<String, Any?>(
        "id" to id, "name" to name, "quantity" to quantity,
        "active" to active, "allowedDays" to allowedDays.toList(),
        "minRole" to minRole.name, "maleOnly" to maleOnly,
        "kind" to kind.name,
        "allowedStatus" to allowedStatus.map { it.name },
        "readerGrant" to readerGrant.name
    )

    private fun Meeting.toMap() = mapOf(
        "id" to id, "date" to date, "type" to type,
        "assignments" to assignments.map { mapOf("privilegeId" to it.privilegeId, "brotherId" to it.brotherId) },
        "blockedBrotherIds" to blockedBrotherIds.toList(),
        "theme" to theme,
        // Grava o formato novo (mapas), não a string legada.
        "program" to program.map {
            mapOf(
                "section" to it.section, "number" to it.number,
                "title" to it.title, "minutes" to it.minutes,
                // Sem o kind gravado, toda parte volta a INDIVIDUAL ao
                // recarregar e a encenacao perde o agrupamento e o aviso.
                "kind" to it.kind.name
            )
        },
        // Contrato com o Firestore: a chave é "programAssignments".
        "programAssignments" to programAssignments.map { pa ->
            mapOf("item" to pa.item, "brotherIds" to pa.brotherIds.toList())
        }
    )

    private fun PublicTalk.toMap(): Map<String, Any?> = mutableMapOf<String, Any?>(
        "id" to id, "date" to date, "themeTitle" to themeTitle,
        "speakerName" to speakerName, "speakerCongregation" to speakerCongregation,
        "speakerPhone" to speakerPhone, "hospitalityNotes" to hospitalityNotes,
        "confirmed" to confirmed
    ).apply {
        meetingId?.let { put("meetingId", it) }
        themeNumber?.let { put("themeNumber", it) }
        hospitalityBrotherId?.let { put("hospitalityBrotherId", it) }
    }

    private fun FieldServiceGroup.toMap(): Map<String, Any?> = mutableMapOf<String, Any?>(
        "id" to id, "number" to number, "name" to name
    ).apply {
        overseerBrotherId?.let { put("overseerBrotherId", it) }
        assistantBrotherId?.let { put("assistantBrotherId", it) }
    }

    private fun CleaningSchedule.toMap(): Map<String, Any?> = mutableMapOf<String, Any?>(
        "id" to id, "weekDate" to weekDate, "details" to details, "completed" to completed
    ).apply {
        groupId?.let { put("groupId", it) }
    }

    private fun brotherFromDocument(document: com.google.firebase.firestore.DocumentSnapshot): Brother? {
        val d = document.data ?: return null
        val id = (d["id"] as? Number)?.toLong() ?: document.id.toLongOrNull() ?: return null
        val privileges = (d["privileges"] as? List<*>)?.mapNotNull { (it as? Number)?.toLong() }?.toSet() ?: emptySet()
        val roleStr = d["role"]?.toString() ?: "PUBLISHER"
        val role = runCatching { BrotherRole.valueOf(roleStr) }.getOrDefault(BrotherRole.PUBLISHER)
        val genderStr = d["gender"]?.toString() ?: "MALE"
        val gender = runCatching { Gender.valueOf(genderStr) }.getOrDefault(Gender.MALE)
        val groupId = (d["groupId"] as? Number)?.toLong()?.takeIf { it > 0L }
        val unavailList = (d["unavailabilities"] as? List<*>)?.mapNotNull { raw ->
            val m = raw as? Map<*, *> ?: return@mapNotNull null
            val uid = (m["id"] as? Number)?.toLong() ?: 0L
            val start = m["startDate"]?.toString() ?: return@mapNotNull null
            val end = m["endDate"]?.toString() ?: return@mapNotNull null
            val reason = m["reason"]?.toString() ?: ""
            UnavailablePeriod(uid, start, end, reason)
        } ?: emptyList()
        return Brother(
            id = id,
            name = d["name"]?.toString() ?: return null,
            phone = d["phone"]?.toString() ?: "",
            privileges = privileges,
            active = d["active"] as? Boolean ?: true,
            role = role,
            unavailabilities = unavailList,
            gender = gender,
            groupId = groupId,
            baptized = d["baptized"] as? Boolean ?: true,
            trainee = d["trainee"] as? Boolean ?: false,
            isReader = d["isReader"] as? Boolean ?: false,
            isSentinelReader = d["isSentinelReader"] as? Boolean ?: false,
            // Vazio = desconhecido. Dado antigo não tem o campo, e exigir
            // preenchimento obrigaria a congregação a redigitar a lista.
            entrouEm = d["entrouEm"]?.toString() ?: ""
        )
    }

    private fun privilegeFromDocument(document: com.google.firebase.firestore.DocumentSnapshot): Privilege? {
        val d = document.data ?: return null
        val id = (d["id"] as? Number)?.toLong() ?: document.id.toLongOrNull() ?: return null
        val days = (d["allowedDays"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.toSet() ?: emptySet()
        val roleStr = d["minRole"]?.toString() ?: "PUBLISHER"
        val minRole = runCatching { BrotherRole.valueOf(roleStr) }.getOrDefault(BrotherRole.PUBLISHER)
        val maleOnly = d["maleOnly"] as? Boolean ?: true
        val kind = runCatching { PartKind.valueOf(d["kind"]?.toString() ?: "INDIVIDUAL") }
            .getOrDefault(PartKind.INDIVIDUAL)
        val grant = runCatching { ReaderGrant.valueOf(d["readerGrant"]?.toString() ?: "NONE") }
            .getOrDefault(ReaderGrant.NONE)
        // Ausente = todos os status permitidos, que é o comportamento anterior.
        val status = (d["allowedStatus"] as? List<*>)
            ?.mapNotNull { runCatching { BrotherStatus.valueOf(it.toString()) }.getOrNull() }
            ?.toSet()
            ?: BrotherStatus.entries.toSet()
        return Privilege(
            id = id,
            name = d["name"]?.toString() ?: return null,
            quantity = (d["quantity"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 1,
            active = d["active"] as? Boolean ?: true,
            allowedDays = days,
            minRole = minRole,
            maleOnly = maleOnly,
            kind = kind,
            allowedStatus = status,
            readerGrant = grant
        )
    }

    private fun meetingFromDocument(document: com.google.firebase.firestore.DocumentSnapshot): Meeting? {
        val d = document.data ?: return null
        val id = (d["id"] as? Number)?.toLong() ?: document.id.toLongOrNull() ?: return null
        val assignments = (d["assignments"] as? List<*>)?.mapNotNull { raw ->
            val map = raw as? Map<*, *> ?: return@mapNotNull null
            val privilegeId = (map["privilegeId"] as? Number)?.toLong() ?: return@mapNotNull null
            val brotherId = (map["brotherId"] as? Number)?.toLong() ?: return@mapNotNull null
            Assignment(privilegeId, brotherId)
        } ?: emptyList()
        val blocked = (d["blockedBrotherIds"] as? List<*>)?.mapNotNull { (it as? Number)?.toLong() }?.toSet() ?: emptySet()
        val theme = d["theme"]?.toString() ?: ""
        // Aceita os dois formatos: Map (novo) e String (legado "N. Título (M min)").
        val program = (d["program"] as? List<*>)?.mapNotNull { raw ->
            when (raw) {
                is Map<*, *> -> ProgramItem(
                    section = raw["section"]?.toString() ?: "",
                    number = (raw["number"] as? Number)?.toInt() ?: 0,
                    title = raw["title"]?.toString() ?: "",
                    minutes = (raw["minutes"] as? Number)?.toInt() ?: 0
                )
                is String -> parseLegacyProgramItem(raw)
                else -> null
            }
        } ?: emptyList()
        // Documento antigo não tem a chave: lista vazia. "brotherIds" ausente
        // também — parte sem ninguém não invalida a parte.
        val programAssignments = (d["programAssignments"] as? List<*>)?.mapNotNull { raw ->
            val map = raw as? Map<*, *> ?: return@mapNotNull null
            val item = (map["item"] as? Number)?.toInt() ?: return@mapNotNull null
            val ids = (map["brotherIds"] as? List<*>)?.mapNotNull { (it as? Number)?.toLong() } ?: emptyList()
            ProgramAssignment(item = item, brotherIds = ids)
        } ?: emptyList()
        return Meeting(
            id = id,
            date = d["date"]?.toString() ?: return null,
            // Normaliza na leitura: as duas grafias antigas viram uma só daqui
            // para frente, e a próxima gravação já grava a certa (#48).
            type = MeetingType.parse(d["type"]?.toString() ?: "").label,
            assignments = assignments,
            blockedBrotherIds = blocked,
            theme = theme,
            program = program,
            programAssignments = programAssignments
        )
    }

    private fun publicTalkFromDocument(document: com.google.firebase.firestore.DocumentSnapshot): PublicTalk? {
        val d = document.data ?: return null
        val id = (d["id"] as? Number)?.toLong() ?: document.id.toLongOrNull() ?: return null
        return PublicTalk(
            id = id,
            meetingId = (d["meetingId"] as? Number)?.toLong(),
            date = d["date"]?.toString() ?: "",
            themeNumber = (d["themeNumber"] as? Number)?.toInt(),
            themeTitle = d["themeTitle"]?.toString() ?: "",
            speakerName = d["speakerName"]?.toString() ?: "",
            speakerCongregation = d["speakerCongregation"]?.toString() ?: "",
            speakerPhone = d["speakerPhone"]?.toString() ?: "",
            hospitalityBrotherId = (d["hospitalityBrotherId"] as? Number)?.toLong(),
            hospitalityNotes = d["hospitalityNotes"]?.toString() ?: "",
            confirmed = d["confirmed"] as? Boolean ?: false
        )
    }

    private fun groupFromDocument(document: com.google.firebase.firestore.DocumentSnapshot): FieldServiceGroup? {
        val d = document.data ?: return null
        val id = (d["id"] as? Number)?.toLong() ?: document.id.toLongOrNull() ?: return null
        return FieldServiceGroup(
            id = id,
            number = (d["number"] as? Number)?.toInt() ?: 1,
            name = d["name"]?.toString() ?: "Grupo",
            overseerBrotherId = (d["overseerBrotherId"] as? Number)?.toLong(),
            assistantBrotherId = (d["assistantBrotherId"] as? Number)?.toLong()
        )
    }

    private fun cleaningFromDocument(document: com.google.firebase.firestore.DocumentSnapshot): CleaningSchedule? {
        val d = document.data ?: return null
        val id = (d["id"] as? Number)?.toLong() ?: document.id.toLongOrNull() ?: return null
        return CleaningSchedule(
            id = id,
            weekDate = d["weekDate"]?.toString() ?: "",
            groupId = (d["groupId"] as? Number)?.toLong(),
            details = d["details"]?.toString() ?: "",
            completed = d["completed"] as? Boolean ?: false
        )
    }

    private companion object {
        const val TAG = "AppRepository"
    }
}
