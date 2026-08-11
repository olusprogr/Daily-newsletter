package com.olusprogr.schacht

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Einzige Activity. Die GameView uebernimmt Simulation, Rendering und Eingabe.
 * Zusaetzlich haengen hier die Datei-Dialoge fuer Backup speichern/laden (SAF),
 * damit ein Spielstand eine Deinstallation ueberleben kann.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var view: GameView

    // Backup speichern: Datei anlegen und die Slot-Datenbank hineinschreiben.
    private val createBackup =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val ok = try {
                contentResolver.openOutputStream(uri)?.use {
                    it.write(view.exportSave().toByteArray(Charsets.UTF_8)); it.flush()
                }
                true
            } catch (_: Exception) { false }
            toast(if (ok) I18n.t("backup_saved") else I18n.t("backup_failed"))
        }

    // Backup laden: Datei waehlen und die Slot-Datenbank ersetzen.
    private val openBackup =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val content = try {
                contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            } catch (_: Exception) { null }
            val ok = content != null && view.importSave(content)
            toast(if (ok) I18n.t("backup_loaded") else I18n.t("backup_failed"))
        }

    fun startBackupExport() {
        view.persist()   // aktuellen Spielstand vor dem Export sichern
        try { createBackup.launch("schacht-backup.json") } catch (_: Exception) { toast(I18n.t("backup_failed")) }
    }

    fun startBackupImport() {
        try { openBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
        catch (_: Exception) { toast(I18n.t("backup_failed")) }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = GameView(this)
        setContentView(view)
    }

    override fun onPause() {
        super.onPause()
        view.persist()
        view.pauseAudio()
    }

    override fun onResume() {
        super.onResume()
        view.resumeAudio()
    }
}
