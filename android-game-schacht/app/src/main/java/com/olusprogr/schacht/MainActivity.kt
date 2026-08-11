package com.olusprogr.schacht

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

/**
 * Einzige Activity. Die GameView uebernimmt Simulation, Rendering und Eingabe.
 * Beim Pausieren wird der Zustand lokal gespeichert (fuer Offline-Progress).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var view: GameView

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
