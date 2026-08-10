package com.olusprogr.physikhuepfer

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

/**
 * Einzige Activity des Spiels. Zeigt ausschließlich die GameView an,
 * die das komplette Spielgeschehen (Physik, Rendering, Eingabe) übernimmt.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(GameView(this))
    }
}
