package br.com.willendary.designacoesjw.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class AppRepository(context: Context) {
    private val prefs = context.getSharedPreferences("designacoes_jw", Context.MODE_PRIVATE)

    fun loadBrothers(): List<Brother> {
        val a = JSONArray(prefs.getString("brothers", "[]"))
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            val p = o.optJSONArray("privileges") ?: JSONArray()
            val ids = mutableSetOf<Long>()
            for (j in 0 until p.length()) ids += p.getLong(j)
            Brother(o.getLong("id"), o.getString("name"), o.optString("phone"), ids, o.optBoolean("active", true))
        }
    }

    fun saveBrothers(items: List<Brother>) {
        val a = JSONArray()
        items.forEach { b -> a.put(JSONObject().apply {
            put("id", b.id); put("name", b.name); put("phone", b.phone); put("active", b.active)
            put("privileges", JSONArray(b.privileges.toList()))
        }) }
        prefs.edit().putString("brothers", a.toString()).apply()
    }

    fun loadPrivileges(): List<Privilege> {
        val a = JSONArray(prefs.getString("privileges", "[]"))
        return List(a.length()) { i -> val o = a.getJSONObject(i); Privilege(o.getLong("id"), o.getString("name"), o.optInt("quantity", 1)) }
    }

    fun savePrivileges(items: List<Privilege>) {
        val a = JSONArray()
        items.forEach { p -> a.put(JSONObject().apply { put("id", p.id); put("name", p.name); put("quantity", p.quantity) }) }
        prefs.edit().putString("privileges", a.toString()).apply()
    }

    fun loadMeetings(): List<Meeting> {
        val a = JSONArray(prefs.getString("meetings", "[]"))
        return List(a.length()) { i ->
            val o = a.getJSONObject(i); val aa = o.optJSONArray("assignments") ?: JSONArray()
            val list = List(aa.length()) { j -> val x = aa.getJSONObject(j); Assignment(x.getLong("privilegeId"), x.getLong("brotherId")) }
            Meeting(o.getLong("id"), o.getString("date"), o.getString("type"), list)
        }
    }

    fun saveMeetings(items: List<Meeting>) {
        val a = JSONArray()
        items.forEach { m ->
            val aa = JSONArray()
            m.assignments.forEach { x -> aa.put(JSONObject().apply { put("privilegeId", x.privilegeId); put("brotherId", x.brotherId) }) }
            a.put(JSONObject().apply { put("id", m.id); put("date", m.date); put("type", m.type); put("assignments", aa) })
        }
        prefs.edit().putString("meetings", a.toString()).apply()
    }
}
