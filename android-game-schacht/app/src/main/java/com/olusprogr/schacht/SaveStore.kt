package com.olusprogr.schacht

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File

/** Kurzinfo zu einem Spielstand fuer die Menue-Liste. */
class SlotInfo(
    val id: String,
    val name: String,
    val savedAt: Long,
    val money: Double,
    val machines: Int
)

/**
 * Spielstand ausserhalb des App-Sandkastens ablegen, damit der Fortschritt eine
 * Deinstallation ueberlebt. Ab Android 10 ueber Scoped Storage (MediaStore) ohne
 * Berechtigung; davor per Datei im oeffentlichen Documents-Ordner. Alles in
 * try/catch – schlaegt es fehl, bleibt der interne Spielstand die Quelle.
 */
object ExternalStore {
    private const val DISPLAY = "schacht_saves.json"
    private val RELPATH = Environment.DIRECTORY_DOCUMENTS + "/SCHACHT"

    fun save(context: Context, content: String) {
        try {
            if (Build.VERSION.SDK_INT >= 29) saveScoped(context, content) else saveLegacy(content)
        } catch (_: Exception) { }
    }

    fun load(context: Context): String? = try {
        if (Build.VERSION.SDK_INT >= 29) loadScoped(context) else loadLegacy()
    } catch (_: Exception) { null }

    private fun collection(): Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    private fun findUri(context: Context): Uri? {
        val proj = arrayOf(MediaStore.MediaColumns._ID)
        val sel = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf(DISPLAY, "%SCHACHT%")
        context.contentResolver.query(collection(), proj, sel, args, null)?.use { c ->
            if (c.moveToFirst()) return ContentUris.withAppendedId(collection(), c.getLong(0))
        }
        return null
    }

    private fun saveScoped(context: Context, content: String) {
        val resolver = context.contentResolver
        var uri = findUri(context)
        if (uri == null) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, DISPLAY)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, RELPATH)
            }
            uri = resolver.insert(collection(), values)
        }
        uri ?: return
        resolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)); it.flush() }
    }

    private fun loadScoped(context: Context): String? {
        val uri = findUri(context) ?: return null
        context.contentResolver.openInputStream(uri)?.use { return it.readBytes().toString(Charsets.UTF_8) }
        return null
    }

    private fun legacyFile(): File {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "SCHACHT")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, DISPLAY)
    }
    private fun saveLegacy(content: String) { legacyFile().writeText(content) }
    private fun loadLegacy(): String? { val f = legacyFile(); return if (f.exists()) f.readText() else null }
}

/**
 * Mehrere benannte Spielstaende. Quelle der Wahrheit sind SharedPreferences
 * (schnell), zusaetzlich in den externen Speicher gespiegelt. Beim ersten Start
 * nach einer Neuinstallation wird von dort wiederhergestellt.
 */
class SaveStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("schacht_slots", Context.MODE_PRIVATE)

    private fun freshDb() = JSONObject().put("v", 1).put("slots", JSONObject())

    private fun readDb(): JSONObject {
        prefs.getString("db", null)?.let { try { return JSONObject(it) } catch (_: Exception) { } }
        // Nach Neuinstallation: aus externem Spiegel wiederherstellen
        ExternalStore.load(context)?.let {
            try {
                val o = JSONObject(it)
                prefs.edit().putString("db", it).apply()
                return o
            } catch (_: Exception) { }
        }
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
        val s = db.toString()
        prefs.edit().putString("db", s).apply()
        ExternalStore.save(context, s)
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
        val db = readDb()
        val slots = db.getJSONObject("slots")
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

    /** Zustand eines bestehenden Slots aktualisieren. */
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

    fun renameSlot(id: String, name: String) {
        val db = readDb()
        val slots = db.getJSONObject("slots")
        if (slots.has(id)) { slots.getJSONObject(id).put("name", name); persist(db) }
    }

    private fun nextName(): String {
        val slots = readDb().getJSONObject("slots")
        var n = 1
        while (slots.has(n.toString())) n++
        return "Fabrik $n"
    }
}
