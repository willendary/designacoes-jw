package br.com.willendary.designacoesjw.data

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

/**
 * Persistência híbrida:
 * - SharedPreferences mantém o cache local e permite abrir o app offline.
 * - Cloud Firestore sincroniza os mesmos dados entre Android, Desktop e Web.
 */
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
        onError: (String) -> Unit = {}
    ) {
        closeCloudSync()

        listeners += brothersCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error.localizedMessage ?: "Erro ao sincronizar irmãos.")
                return@addSnapshotListener
            }
            val items = snapshot?.documents?.mapNotNull(::brotherFromDocument) ?: emptyList()
            if (items.isEmpty() && !cloudMigrationDone("brothers")) {
                val local = loadBrothers()
                cloudMigrationDone("brothers", true)
                if (local.isNotEmpty()) {
                    saveBrothers(local)
                    onBrothers(local)
                    return@addSnapshotListener
                }
            } else if (items.isNotEmpty()) {
                cloudMigrationDone("brothers", true)
            }
            saveBrothersLocal(items)
            onBrothers(items)
        }

        listeners += privilegesCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error.localizedMessage ?: "Erro ao sincronizar privilégios.")
                return@addSnapshotListener
            }
            val items = snapshot?.documents?.mapNotNull(::privilegeFromDocument) ?: emptyList()
            if (items.isEmpty() && !cloudMigrationDone("privileges")) {
                val local = loadPrivileges()
                cloudMigrationDone("privileges", true)
                if (local.isNotEmpty()) {
                    savePrivileges(local)
                    onPrivileges(local)
                    return@addSnapshotListener
                }
            } else if (items.isNotEmpty()) {
                cloudMigrationDone("privileges", true)
            }
            savePrivilegesLocal(items)
            onPrivileges(items)
        }

        listeners += meetingsCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error.localizedMessage ?: "Erro ao sincronizar histórico.")
                return@addSnapshotListener
            }
            val items = snapshot?.documents?.mapNotNull(::meetingFromDocument) ?: emptyList()
            if (items.isEmpty() && !cloudMigrationDone("meetings")) {
                val local = loadMeetings()
                cloudMigrationDone("meetings", true)
                if (local.isNotEmpty()) {
                    saveMeetings(local)
                    onMeetings(local)
                    return@addSnapshotListener
                }
            } else if (items.isNotEmpty()) {
                cloudMigrationDone("meetings", true)
            }
            saveMeetingsLocal(items)
            onMeetings(items)
        }

        listeners += settingsDocument.addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error.localizedMessage ?: "Erro ao sincronizar configurações.")
                return@addSnapshotListener
            }
            val data = snapshot?.data
            if (data == null) {
                val local = loadSchedule()
                if (local != MeetingSchedule()) {
                    saveSchedule(local)
                    onSchedule(local)
                }
                return@addSnapshotListener
            }
            val schedule = MeetingSchedule(
                (data["firstDay"] as? Number)?.toInt() ?: 3,
                (data["secondDay"] as? Number)?.toInt() ?: 6
            )
            saveScheduleLocal(schedule)
            onSchedule(schedule)
        }

        if (onPublicTalks != null) {
            listeners += publicTalksCollection.addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val items = snapshot.documents.mapNotNull(::publicTalkFromDocument)
                    savePublicTalksLocal(items)
                    onPublicTalks(items)
                }
            }
        }

        if (onGroups != null) {
            listeners += groupsCollection.addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val items = snapshot.documents.mapNotNull(::groupFromDocument)
                    saveFieldServiceGroupsLocal(items)
                    onGroups(items)
                }
            }
        }

        if (onCleaning != null) {
            listeners += cleaningCollection.addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val items = snapshot.documents.mapNotNull(::cleaningFromDocument)
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
                groupId = groupId
            )
        }
    }

    fun saveBrothers(items: List<Brother>) {
        saveBrothersLocal(items)
        items.forEach { brother ->
            brothersCollection.document(brother.id.toString()).set(brother.toMap(), SetOptions.merge())
        }
        val keep = items.map { it.id.toString() }.toSet()
        brothersCollection.get().addOnSuccessListener { snapshot ->
            snapshot.documents.filter { it.id !in keep }.forEach { it.reference.delete() }
        }
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
        items.forEach { privilege ->
            privilegesCollection.document(privilege.id.toString()).set(privilege.toMap(), SetOptions.merge())
        }
        val keep = items.map { it.id.toString() }.toSet()
        privilegesCollection.get().addOnSuccessListener { snapshot ->
            snapshot.documents.filter { it.id !in keep }.forEach { it.reference.delete() }
        }
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
            val program = mutableListOf<String>()
            val pa = o.optJSONArray("program") ?: org.json.JSONArray()
            for (j in 0 until pa.length()) pa.optString(j)?.takeIf { it.isNotBlank() }?.let { program += it }
            Meeting(
                o.getLong("id"), o.getString("date"), o.getString("type"), assignments, blocked,
                o.optString("theme"), program
            )
        }
    }

    fun saveMeetings(items: List<Meeting>) {
        saveMeetingsLocal(items)
        items.forEach { meeting ->
            meetingsCollection.document(meeting.id.toString()).set(meeting.toMap(), SetOptions.merge())
        }
        val keep = items.map { it.id.toString() }.toSet()
        meetingsCollection.get().addOnSuccessListener { snapshot ->
            snapshot.documents.filter { it.id !in keep }.forEach { it.reference.delete() }
        }
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
        items.forEach { talk ->
            publicTalksCollection.document(talk.id.toString()).set(talk.toMap(), SetOptions.merge())
        }
        val keep = items.map { it.id.toString() }.toSet()
        publicTalksCollection.get().addOnSuccessListener { snapshot ->
            snapshot.documents.filter { it.id !in keep }.forEach { it.reference.delete() }
        }
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
        items.forEach { group ->
            groupsCollection.document(group.id.toString()).set(group.toMap(), SetOptions.merge())
        }
        val keep = items.map { it.id.toString() }.toSet()
        groupsCollection.get().addOnSuccessListener { snapshot ->
            snapshot.documents.filter { it.id !in keep }.forEach { it.reference.delete() }
        }
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
        items.forEach { sched ->
            cleaningCollection.document(sched.id.toString()).set(sched.toMap(), SetOptions.merge())
        }
        val keep = items.map { it.id.toString() }.toSet()
        cleaningCollection.get().addOnSuccessListener { snapshot ->
            snapshot.documents.filter { it.id !in keep }.forEach { it.reference.delete() }
        }
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
        )
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
                put("allowedDays", org.json.JSONArray(p.allowedDays.toList()))
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
                put("type", m.type)
                put("assignments", aa)
                put("blockedBrotherIds", org.json.JSONArray(m.blockedBrotherIds.toList()))
                put("theme", m.theme)
                put("program", org.json.JSONArray(m.program))
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

    private fun Brother.toMap(): Map<String, Any?> = mutableMapOf<String, Any?>(
        "id" to id, "name" to name, "phone" to phone,
        "privileges" to privileges.toList(), "active" to active,
        "role" to role.name, "gender" to gender.name,
        "unavailabilities" to unavailabilities.map {
            mapOf("id" to it.id, "startDate" to it.startDate, "endDate" to it.endDate, "reason" to it.reason)
        }
    ).apply {
        groupId?.let { put("groupId", it) }
    }

    private fun Privilege.toMap() = mapOf(
        "id" to id, "name" to name, "quantity" to quantity,
        "active" to active, "allowedDays" to allowedDays.toList(),
        "minRole" to minRole.name, "maleOnly" to maleOnly
    )

    private fun Meeting.toMap() = mapOf(
        "id" to id, "date" to date, "type" to type,
        "assignments" to assignments.map { mapOf("privilegeId" to it.privilegeId, "brotherId" to it.brotherId) },
        "blockedBrotherIds" to blockedBrotherIds.toList(),
        "theme" to theme, "program" to program
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
            groupId = groupId
        )
    }

    private fun privilegeFromDocument(document: com.google.firebase.firestore.DocumentSnapshot): Privilege? {
        val d = document.data ?: return null
        val id = (d["id"] as? Number)?.toLong() ?: document.id.toLongOrNull() ?: return null
        val days = (d["allowedDays"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.toSet() ?: emptySet()
        val roleStr = d["minRole"]?.toString() ?: "PUBLISHER"
        val minRole = runCatching { BrotherRole.valueOf(roleStr) }.getOrDefault(BrotherRole.PUBLISHER)
        val maleOnly = d["maleOnly"] as? Boolean ?: true
        return Privilege(id, d["name"]?.toString() ?: return null, (d["quantity"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 1, d["active"] as? Boolean ?: true, days, minRole, maleOnly)
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
        return Meeting(id, d["date"]?.toString() ?: return null, d["type"]?.toString() ?: "Reunião", assignments, blocked)
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
}
