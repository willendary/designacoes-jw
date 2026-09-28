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
        items.forEach { b ->
            a.put(JSONObject().apply {
                put("id", b.id); put("name", b.name); put("phone", b.phone)
                put("active", b.active); put("privileges", JSONArray(b.privileges.toList()))
            })
        }
        prefs.edit().putString("brothers", a.toString()).apply()
    }

    fun loadPrivileges(): List<Privilege> {
        val a = JSONArray(prefs.getString("privileges", "[]"))
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Privilege(o.getLong("id"), o.getString("name"), o.optInt("quantity", 1).coerceAtLeast(1), o.optBoolean("active", true), loadIntSet(o.optJSONArray("allowedDays")) )
        }
    }

    fun savePrivileges(items: List<Privilege>) {
        val a = JSONArray()
        items.forEach { p ->
            a.put(JSONObject().apply {
                put("id", p.id); put("name", p.name); put("quantity", p.quantity); put("active", p.active); put("allowedDays", JSONArray(p.allowedDays.toList()))
            })
        }
        prefs.edit().putString("privileges", a.toString()).apply()
    }

    fun loadMeetings(): List<Meeting> {
        val a = JSONArray(prefs.getString("meetings", "[]"))
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            val aa = o.optJSONArray("assignments") ?: JSONArray()
            val assignments = List(aa.length()) { j ->
                val x = aa.getJSONObject(j)
                Assignment(x.getLong("privilegeId"), x.getLong("brotherId"))
            }
            val blocked = mutableSetOf<Long>()
            val ba = o.optJSONArray("blockedBrotherIds") ?: JSONArray()
            for (j in 0 until ba.length()) blocked += ba.getLong(j)
            Meeting(o.getLong("id"), o.getString("date"), o.getString("type"), assignments, blocked)
        }
    }

    fun saveMeetings(items: List<Meeting>) {
        val a = JSONArray()
        items.forEach { m ->
            val aa = JSONArray()
            m.assignments.forEach { x ->
                aa.put(JSONObject().apply {
                    put("privilegeId", x.privilegeId); put("brotherId", x.brotherId)
                })
            }
            a.put(JSONObject().apply {
                put("id", m.id); put("date", m.date); put("type", m.type)
                put("assignments", aa); put("blockedBrotherIds", JSONArray(m.blockedBrotherIds.toList()))
            })
        }
        prefs.edit().putString("meetings", a.toString()).apply()
    }

    private fun loadIntSet(array: JSONArray?): Set<Int> {
        if (array == null) return emptySet()
        val result = mutableSetOf<Int>()
        for (i in 0 until array.length()) result += array.getInt(i)
        return result
    }

    fun loadSchedule(): MeetingSchedule {
        return MeetingSchedule(
            prefs.getInt("schedule_first_day", 3),
            prefs.getInt("schedule_second_day", 6)
        )
    }

    fun saveSchedule(schedule: MeetingSchedule) {
        prefs.edit()
            .putInt("schedule_first_day", schedule.firstDay)
            .putInt("schedule_second_day", schedule.secondDay)
            .apply()
    }
}
