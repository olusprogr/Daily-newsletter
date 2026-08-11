package com.olusprogr.schacht

import android.content.Context
import org.json.JSONObject

/** Kurzinfo zu einem Spielstand fuer die Menue-Liste. */
class SlotInfo(
    val id: String,
    val name: String,
    val savedAt: Long,
    val money: Double,
    val machines: Int
)

/**
 * Mehrere benannte Spielstaende, gespeichert in SharedPreferences (bleiben bei
 * normalem Beenden und bei App-Updates erhalten). Fuer das Ueberstehen einer
 * Deinstallation gibt es Export/Import in eine vom Nutzer gewaehlte Datei
 * (siehe exportAll/importAll + MainActivity).
 */
class SaveStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("schacht_slots", Context.MODE_PRIVATE)

    private fun freshDb() = JSONObject().put("v", 1).put("slots", JSONObject())

    private fun readDb(): JSONObject {
        prefs.getString("db", null)?.let { try { return JSONObject(it) } catch (_: Exception) { } }
        // Migration: alter Einzel-Spielstand -> Slot 1
        val old = context.getSharedPreferences("schacht_save", Context.MODE_PRIVATE)
        val oldState = old.getString("state", null)
        val db = freshDb()
        if (oldState != null) {
            val slot = JSONObject()
                .put("name", "Fabrik 1")
                .put("savedAt", System.currentTimeMillis())
                .put("state", oldState)
            applyMeta(slot, oldState)
            db.getJSONObject("slots").put("1", slot)
            persist(db)
            old.edit().remove("state").apply()
        }
        return db
    }

    private fun persist(db: JSONObject) {
        prefs.edit().putString("db", db.toString()).apply()
    }

    private fun applyMeta(slot: JSONObject, state: String) {
        var money = 0.0; var machines = 0
        try {
            val o = JSONObject(state)
            money = o.optDouble("money", 0.0)
            val cells = o.optJSONArray("cells")
            if (cells != null) for (i in 0 until cells.length()) {
                val ty = cells.getJSONObject(i).optInt("ty", -1)
                if (ty != MType.REAKTOR.ordinal) machines++
            }
        } catch (_: Exception) { }
        slot.put("money", money); slot.put("machines", machines)
    }

    fun slots(): List<SlotInfo> {
        val slots = readDb().getJSONObject("slots")
        val list = ArrayList<SlotInfo>()
        val keys = slots.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val s = slots.getJSONObject(id)
            list.add(
                SlotInfo(
                    id,
                    s.optString("name", "Fabrik"),
                    s.optLong("savedAt", 0L),
                    s.optDouble("money", 0.0),
                    s.optInt("machines", 0)
                )
            )
        }
        list.sortByDescending { it.savedAt }
        return list
    }

    fun loadState(id: String): String? {
        val slots = readDb().getJSONObject("slots")
        if (!slots.has(id)) return null
        return slots.getJSONObject(id).optString("state", null)
    }

    fun saveState(id: String, state: String) {
        val db = readDb()
        val slots = db.getJSONObject("slots")
        val slot = if (slots.has(id)) slots.getJSONObject(id) else JSONObject().put("name", nextName())
        slot.put("state", state)
        slot.put("savedAt", System.currentTimeMillis())
        applyMeta(slot, state)
        slots.put(id, slot)
        persist(db)
    }

    fun createSlot(state: String, name: String? = null): String {
        val db = readDb()
        val slots = db.getJSONObject("slots")
        var n = 1
        while (slots.has(n.toString())) n++
        val id = n.toString()
        val slot = JSONObject()
            .put("name", name ?: "Fabrik $id")
            .put("savedAt", System.currentTimeMillis())
            .put("state", state)
        applyMeta(slot, state)
        slots.put(id, slot)
        persist(db)
        return id
    }

    fun deleteSlot(id: String) {
        val db = readDb()
        db.getJSONObject("slots").remove(id)
        persist(db)
    }

    private fun nextName(): String {
        val slots = readDb().getJSONObject("slots")
        var n = 1
        while (slots.has(n.toString())) n++
        return "Fabrik $n"
    }

    // --- Backup: gesamte Slot-Datenbank als JSON-Text ---
    fun exportAll(): String = readDb().toString()

    /** Ersetzt alle Spielstaende durch die Datenbank aus einem Backup. */
    fun importAll(json: String): Boolean {
        return try {
            val o = JSONObject(json)
            if (!o.has("slots")) return false
            persist(o)
            true
        } catch (_: Exception) { false }
    }
}
