package br.com.willendary.designacoesjw.data

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.util.Locale

/**
 * Persistência híbrida:
 * - SharedPreferences mantém o cache local e permite abrir o app offline.
 * - Cloud Firestore sincroniza os mesmos dados entre Android e desktop futuramente.
 *
 * O workspace atual é compartilhado pelos usuários autenticados do projeto.
 */
class AppRepository(context: Context) {
    private val prefs = context.getSharedPreferences("designacoes_jw", Context.MODE_PRIVATE)
    private val firestore = FirebaseFirestore.getInstance()
    private val workspace = firestore.collection("workspaces").document("designacoes-jw")
    private val brothersCollection = workspace.collection("brothers")
    private val privilegesCollection = workspace.collection("privileges")
    private val meetingsCollection = workspace.collection("meetings")
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
            val unavailArray = o.optJSONArray("unavailabilities") ?: org.json.JSONArray()
            val unavails = List(unavailArray.length()) { u ->
                val uObj = unavailArray.getJSONObject(u)
                UnavailablePeriod(uObj.optLong("id", 0L), uObj.getString("startDate"), uObj.getString("endDate"), uObj.optString("reason", ""))
            }
            Brother(o.getLong("id"), o.getString("name"), o.optString("phone"), ids, o.optBoolean("active", true), role, unavails)
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
            Privilege(
                o.getLong("id"),
                o.getString("name"),
                o.optInt("quantity", 1).coerceAtLeast(1),
                o.optBoolean("active", true),
                loadIntSet(o.optJSONArray("allowedDays"))
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
            Meeting(o.getLong("id"), o.getString("date"), o.getString("type"), assignments, blocked)
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

    private fun saveBrothersLocal(items: List<Brother>) {
        val a = org.json.JSONArray()
        items.forEach { b ->
            a.put(org.json.JSONObject().apply {
                put("id", b.id)
                put("name", b.name)
                put("phone", b.phone)
                put("active", b.active)
                put("role", b.role.name)
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
            })
        }
        prefs.edit().putString("meetings", a.toString()).apply()
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

    private fun Brother.toMap() = mapOf(
        "id" to id, "name" to name, "phone" to phone,
        "privileges" to privileges.toList(), "active" to active,
        "role" to role.name,
        "unavailabilities" to unavailabilities.map {
            mapOf("id" to it.id, "startDate" to it.startDate, "endDate" to it.endDate, "reason" to it.reason)
        }
    )

    private fun Privilege.toMap() = mapOf(
        "id" to id, "name" to name, "quantity" to quantity,
        "active" to active, "allowedDays" to allowedDays.toList(),
        "minRole" to minRole.name
    )

    private fun Meeting.toMap() = mapOf(
        "id" to id, "date" to date, "type" to type,
        "assignments" to assignments.map { mapOf("privilegeId" to it.privilegeId, "brotherId" to it.brotherId) },
        "blockedBrotherIds" to blockedBrotherIds.toList()
    )

    private fun brotherFromDocument(document: com.google.firebase.firestore.DocumentSnapshot): Brother? {
        val d = document.data ?: return null
        val id = (d["id"] as? Number)?.toLong() ?: document.id.toLongOrNull() ?: return null
        val privileges = (d["privileges"] as? List<*>)?.mapNotNull { (it as? Number)?.toLong() }?.toSet() ?: emptySet()
        val roleStr = d["role"]?.toString() ?: "PUBLISHER"
        val role = runCatching { BrotherRole.valueOf(roleStr) }.getOrDefault(BrotherRole.PUBLISHER)
        val unavailList = (d["unavailabilities"] as? List<*>)?.mapNotNull { raw ->
            val m = raw as? Map<*, *> ?: return@mapNotNull null
            val uid = (m["id"] as? Number)?.toLong() ?: 0L
            val start = m["startDate"]?.toString() ?: return@mapNotNull null
            val end = m["endDate"]?.toString() ?: return@mapNotNull null
            val reason = m["reason"]?.toString() ?: ""
            UnavailablePeriod(uid, start, end, reason)
        } ?: emptyList()
        return Brother(id, d["name"]?.toString() ?: return null, d["phone"]?.toString() ?: "", privileges, d["active"] as? Boolean ?: true, role, unavailList)
    }

    private fun privilegeFromDocument(document: com.google.firebase.firestore.DocumentSnapshot): Privilege? {
        val d = document.data ?: return null
        val id = (d["id"] as? Number)?.toLong() ?: document.id.toLongOrNull() ?: return null
        val days = (d["allowedDays"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.toSet() ?: emptySet()
        val roleStr = d["minRole"]?.toString() ?: "PUBLISHER"
        val minRole = runCatching { BrotherRole.valueOf(roleStr) }.getOrDefault(BrotherRole.PUBLISHER)
        return Privilege(id, d["name"]?.toString() ?: return null, (d["quantity"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 1, d["active"] as? Boolean ?: true, days, minRole)
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
}
