package com.olusprogr.schacht

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

/**
 * SCHACHT – vertikale Scheibe. Rendering, Eingabe und alle UI-Overlays in einer
 * Canvas-View. Bewusst ohne Engine: das Spiel ist ein Gitter, eine
 * Tick-Simulation und etwas Canvas-Zeichnung.
 */
class GameView(context: Context) : View(context) {

    private val sim = Simulation()

    private enum class Tool { INSPECT, BOHRER, OFEN, PRESSE, GENERATOR, LAGER, DROHNE, REPARIEREN, ABRISS }
    private var tool = Tool.INSPECT

    private enum class Screen { GAME, TECH, STAT, REPORT }
    private var screen = Screen.GAME

    private var selR = -1
    private var selC = -1
    private var report: OfflineReport? = null

    private val prefs = context.getSharedPreferences("schacht_save", Context.MODE_PRIVATE)

    // Farben
    private val cBg = Color.rgb(26, 23, 20)
    private val cPanel = Color.rgb(36, 31, 26)
    private val cCell = Color.rgb(42, 38, 34)
    private val cGridLine = Color.rgb(21, 18, 15)
    private val cText = Color.rgb(232, 224, 213)
    private val cDim = Color.rgb(150, 140, 128)
    private val cAccent = Color.rgb(228, 161, 75)
    private val cGood = Color.rgb(120, 200, 130)
    private val cBad = Color.rgb(224, 90, 80)
    private val cWarn = Color.rgb(228, 198, 75)

    private fun mColor(t: MType) = when (t) {
        MType.BOHRER -> Color.rgb(192, 135, 63)
        MType.OFEN -> Color.rgb(226, 100, 59)
        MType.PRESSE -> Color.rgb(79, 127, 176)
        MType.GENERATOR -> Color.rgb(228, 198, 75)
        MType.LAGER -> Color.rgb(122, 108, 93)
        MType.DROHNE -> Color.rgb(87, 184, 148)
        MType.REAKTOR -> Color.rgb(156, 106, 222)
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cText; textAlign = Paint.Align.LEFT }
    private val pTextC = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cText; textAlign = Paint.Align.CENTER }

    private var W = 0
    private var H = 0
    private var dens = context.resources.displayMetrics.density
    private fun dp(v: Float) = v * dens

    // Layout
    private var gridLeft = 0f
    private var gridTop = 0f
    private var cell = 0f
    private var gridSide = 0f
    private var headerH = 0f

    private class Btn(val rect: RectF, val id: String, val label: String, var enabled: Boolean = true, var active: Boolean = false, val color: Int = 0)
    private val buttons = ArrayList<Btn>()

    private val handler = Handler(Looper.getMainLooper())
    private var running = true
    private var lastNanos = 0L
    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.nanoTime()
            var dt = (now - lastNanos) / 1_000_000_000.0
            lastNanos = now
            if (dt > 0.25) dt = 0.25
            if (screen == Screen.GAME || screen == Screen.STAT) sim.step(dt)
            invalidate()
            handler.postDelayed(this, 33)
        }
    }

    init {
        val saved = prefs.getString("state", null)
        if (saved != null) {
            try {
                val savedT = sim.fromJson(saved)
                val elapsed = ((System.currentTimeMillis() - savedT) / 1000L).toInt()
                if (savedT > 0 && elapsed > 60) {
                    report = sim.runOffline(elapsed)
                    screen = Screen.REPORT
                }
            } catch (e: Exception) {
                sim.newGame()
            }
        } else {
            sim.newGame()
        }
        lastNanos = System.nanoTime()
        handler.post(loop)
    }

    fun persist() {
        try {
            prefs.edit().putString("state", sim.toJson(System.currentTimeMillis())).apply()
        } catch (_: Exception) { }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        W = w; H = h
        headerH = dp(78f)
        val margin = dp(10f)
        gridSide = (w - 2 * margin)
        val maxGrid = h - headerH - dp(210f)
        if (gridSide > maxGrid) gridSide = maxGrid
        cell = gridSide / sim.n
        gridSide = cell * sim.n
        gridLeft = (w - gridSide) / 2f
        gridTop = headerH + dp(6f)
    }

    // ---------------- Rendering ----------------

    override fun onDraw(canvas: Canvas) {
        buttons.clear()
        canvas.drawColor(cBg)
        drawHeader(canvas)
        drawGrid(canvas)
        if (screen == Screen.GAME) drawPalette(canvas)
        when (screen) {
            Screen.TECH -> drawTech(canvas)
            Screen.STAT -> drawStat(canvas)
            Screen.REPORT -> drawReport(canvas)
            Screen.GAME -> if (selR >= 0) drawDetail(canvas)
        }
    }

    private fun drawHeader(canvas: Canvas) {
        p.color = cPanel
        canvas.drawRect(0f, 0f, W.toFloat(), headerH, p)
        pText.textSize = dp(20f)
        pText.color = cAccent
        canvas.drawText("PRODUKTIONSWERT  ${fmt(sim.produktionswertPerMin)} /min", dp(12f), dp(26f), pText)
        pText.textSize = dp(15f)
        pText.color = cText
        val powOk = sim.powerDemand <= sim.powerSupply + 1e-6
        canvas.drawText("Barren ${fmt(sim.globalBarren)}", dp(12f), dp(50f), pText)
        canvas.drawText("Platten ${fmt(sim.globalPlatten)}", dp(140f), dp(50f), pText)
        pText.color = if (powOk) cGood else cBad
        canvas.drawText("Strom ${fmt(sim.powerSupply)}/${fmt(sim.powerDemand)}", dp(12f), dp(70f), pText)
        pText.color = cDim
        canvas.drawText("Platten/min ${oneDec(sim.plattenPerMin)}", dp(140f), dp(70f), pText)

        // Tech / Statistik Buttons rechts oben
        buttons.removeAll { it.id == "tech" || it.id == "stat" }
        val bw = dp(84f); val bh = dp(30f)
        val tR = RectF(W - dp(12f) - bw, dp(8f), W - dp(12f), dp(8f) + bh)
        val sR = RectF(W - dp(12f) - bw, dp(42f), W - dp(12f), dp(42f) + bh)
        drawButton(canvas, Btn(tR, "tech", "Tech", true, screen == Screen.TECH, cAccent))
        drawButton(canvas, Btn(sR, "stat", "Statistik", true, screen == Screen.STAT, cAccent))
        buttons.add(Btn(tR, "tech", "Tech"))
        buttons.add(Btn(sR, "stat", "Statistik"))
    }

    private fun drawGrid(canvas: Canvas) {
        for (r in 0 until sim.n) for (c in 0 until sim.n) {
            val x = gridLeft + c * cell
            val y = gridTop + r * cell
            p.color = cCell
            canvas.drawRect(x + 1, y + 1, x + cell - 1, y + cell - 1, p)
            val m = sim.grid[r][c]
            if (m != null) drawMachine(canvas, m, x, y)
        }
        // Auswahlrahmen
        if (selR >= 0 && screen == Screen.GAME) {
            p.color = cAccent; p.style = Paint.Style.STROKE; p.strokeWidth = dp(3f)
            canvas.drawRect(gridLeft + selC * cell + 1, gridTop + selR * cell + 1,
                gridLeft + selC * cell + cell - 1, gridTop + selR * cell + cell - 1, p)
            p.style = Paint.Style.FILL
        }
    }

    private fun drawMachine(canvas: Canvas, m: Machine, x: Float, y: Float) {
        val pad = cell * 0.08f
        val base = mColor(m.type)
        // Zustand als leichte Abdunklung (Rost)
        val wear = (m.condition / 100.0).toFloat().coerceIn(0f, 1f)
        val col = blend(base, cCell, 1f - (0.35f + 0.65f * wear))
        p.color = col
        canvas.drawRoundRect(x + pad, y + pad, x + cell - pad, y + cell - pad, cell * 0.12f, cell * 0.12f, p)

        // Auslastungs-Puls (Rahmen)
        if (m.util > 0.02) {
            p.color = Color.argb((120 + 135 * m.util).toInt().coerceIn(0, 255), 255, 255, 255)
            p.style = Paint.Style.STROKE; p.strokeWidth = dp(2f)
            canvas.drawRoundRect(x + pad, y + pad, x + cell - pad, y + cell - pad, cell * 0.12f, cell * 0.12f, p)
            p.style = Paint.Style.FILL
        }

        // Symbol
        pTextC.color = Color.rgb(20, 18, 15)
        pTextC.textSize = cell * 0.42f
        canvas.drawText(m.type.sym, x + cell / 2f, y + cell * 0.6f, pTextC)

        // Zustandsbalken unten
        if (m.type != MType.REAKTOR && m.type != MType.LAGER) {
            val bw = cell - 2 * pad
            val by = y + cell - pad - dp(4f)
            p.color = cGridLine
            canvas.drawRect(x + pad, by, x + pad + bw, by + dp(4f), p)
            p.color = when { m.condition < 20 -> cBad; m.condition < 50 -> cWarn; else -> cGood }
            canvas.drawRect(x + pad, by, x + pad + bw * wear, by + dp(4f), p)
        }

        // Warnmarker
        if (m.condition <= 0.0) {
            pTextC.color = cBad; pTextC.textSize = cell * 0.5f
            canvas.drawText("X", x + cell * 0.5f, y + cell * 0.62f, pTextC)
        } else if (m.starved) {
            p.color = cWarn
            canvas.drawCircle(x + cell - pad - dp(5f), y + pad + dp(5f), dp(4f), p)
        }
    }

    private fun drawPalette(canvas: Canvas) {
        buttons.removeAll { it.id.startsWith("tool_") }
        val tools = listOf(
            Tool.INSPECT to "Info", Tool.BOHRER to "Bohrer", Tool.OFEN to "Ofen",
            Tool.PRESSE to "Presse", Tool.GENERATOR to "Generat.", Tool.LAGER to "Lager",
            Tool.DROHNE to "Drohne", Tool.REPARIEREN to "Reparat.", Tool.ABRISS to "Abriss"
        )
        val cols = 3
        val margin = dp(10f)
        val gap = dp(6f)
        val bw = (W - 2 * margin - (cols - 1) * gap) / cols
        val bh = dp(38f)
        val top = gridTop + gridSide + dp(10f)
        for ((i, tl) in tools.withIndex()) {
            val col = i % cols
            val row = i / cols
            val x = margin + col * (bw + gap)
            val yy = top + row * (bh + gap)
            val rect = RectF(x, yy, x + bw, yy + bh)
            val enabled = when (tl.first) {
                Tool.INSPECT, Tool.REPARIEREN, Tool.ABRISS -> true
                Tool.BOHRER -> sim.canBuild(MType.BOHRER)
                Tool.OFEN -> sim.canBuild(MType.OFEN)
                Tool.PRESSE -> sim.canBuild(MType.PRESSE)
                Tool.GENERATOR -> sim.canBuild(MType.GENERATOR)
                Tool.LAGER -> sim.canBuild(MType.LAGER)
                Tool.DROHNE -> sim.canBuild(MType.DROHNE)
            }
            val active = tool == tl.first
            drawButton(canvas, Btn(rect, "tool_${tl.first.name}", tl.second, enabled, active))
            buttons.add(Btn(rect, "tool_${tl.first.name}", tl.second, enabled))
        }
    }

    private fun drawDetail(canvas: Canvas) {
        val m = sim.grid[selR][selC] ?: return
        val top = H - dp(150f)
        p.color = cPanel
        canvas.drawRect(0f, top, W.toFloat(), H.toFloat(), p)
        pText.color = cAccent; pText.textSize = dp(18f)
        canvas.drawText("${m.type.label}  (${selR + 1},${selC + 1})", dp(12f), top + dp(24f), pText)
        pText.color = cText; pText.textSize = dp(14f)
        var yy = top + dp(48f)
        canvas.drawText("Zustand: ${m.condition.roundToInt()}%   Auslastung: ${(m.util * 100).roundToInt()}%", dp(12f), yy, pText)
        yy += dp(20f)
        val io = when (m.type) {
            MType.BOHRER -> "Aus: ${oneDec(m.output[0])} Roherz"
            MType.OFEN -> "Ein: ${oneDec(m.input[0])} Roherz  |  Aus: ${oneDec(m.output[1])} Barren"
            MType.PRESSE -> "Ein: ${oneDec(m.input[1])} Barren  |  Aus: ${oneDec(m.output[2])} Platten"
            MType.GENERATOR -> "Brennstoff: ${oneDec(m.input[0])} Roherz  →  +${Simulation.GEN_POWER.toInt()} Strom"
            MType.LAGER -> "Puffer: ${oneDec(m.output[0])}E ${oneDec(m.output[1])}B ${oneDec(m.output[2])}P"
            MType.REAKTOR -> "Liefert ${Simulation.REAKTOR_POWER.toInt()} Strom (fest)"
            MType.DROHNE -> "Repariert Nachbarn (${Simulation.DROHNE_RATE.toInt()}%/s)"
        }
        canvas.drawText(io, dp(12f), yy, pText)
        yy += dp(20f)
        if (m.type != MType.REAKTOR) {
            pText.color = cDim
            canvas.drawText("Stromverbrauch: ${m.type.power.toInt()}", dp(12f), yy, pText)
        }
        // Reparieren-Button
        buttons.removeAll { it.id == "repair_sel" }
        if (m.type != MType.REAKTOR && m.type != MType.LAGER) {
            val rw = dp(150f); val rh = dp(34f)
            val rr = RectF(W - dp(12f) - rw, top + dp(20f), W - dp(12f), top + dp(20f) + rh)
            drawButton(canvas, Btn(rr, "repair_sel", "Reparieren (${Simulation.REPAIR_COST.toInt()} B)",
                sim.globalBarren >= Simulation.REPAIR_COST, false, cAccent))
            buttons.add(Btn(rr, "repair_sel", "Reparieren"))
        }
    }

    private fun drawTech(canvas: Canvas) {
        p.color = Color.argb(238, 20, 17, 14)
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        pText.color = cAccent; pText.textSize = dp(22f)
        canvas.drawText("Tech-Baum", dp(16f), dp(40f), pText)
        pText.color = cDim; pText.textSize = dp(13f)
        canvas.drawText("Forschung wird mit Barren bezahlt.  Verfuegbar: ${fmt(sim.globalBarren)} B", dp(16f), dp(62f), pText)

        buttons.removeAll { it.id.startsWith("buy_") || it.id == "close" }
        var yy = dp(84f)
        val bh = dp(52f)
        for (node in Simulation.TECHS) {
            val rect = RectF(dp(16f), yy, W - dp(16f), yy + bh)
            val owned = sim.tech.contains(node.id)
            val preOk = node.prereq == null || sim.tech.contains(node.prereq)
            val afford = sim.globalBarren >= node.cost
            p.color = if (owned) Color.rgb(40, 60, 44) else cPanel
            canvas.drawRoundRect(rect, dp(8f), dp(8f), p)
            pText.color = cText; pText.textSize = dp(16f)
            canvas.drawText(node.label, dp(28f), yy + dp(24f), pText)
            pText.textSize = dp(12f); pText.color = cDim
            val sub = when {
                owned -> "freigeschaltet"
                !preOk -> "benoetigt: ${Simulation.TECHS.first { it.id == node.prereq }.label}"
                else -> "Kosten: ${node.cost.toInt()} Barren"
            }
            canvas.drawText(sub, dp(28f), yy + dp(42f), pText)
            if (!owned) {
                val kw = dp(96f); val kh = dp(34f)
                val kr = RectF(W - dp(28f) - kw, yy + (bh - kh) / 2, W - dp(28f), yy + (bh + kh) / 2)
                drawButton(canvas, Btn(kr, "buy_${node.id}", "Kaufen", preOk && afford, false, cAccent))
                buttons.add(Btn(kr, "buy_${node.id}", "Kaufen", preOk && afford))
            }
            yy += bh + dp(8f)
        }
        val cr = RectF(W / 2f - dp(70f), H - dp(60f), W / 2f + dp(70f), H - dp(20f))
        drawButton(canvas, Btn(cr, "close", "Schliessen", true, false, cAccent))
        buttons.add(Btn(cr, "close", "Schliessen"))
    }

    private fun drawStat(canvas: Canvas) {
        p.color = Color.argb(238, 20, 17, 14)
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        pText.color = cAccent; pText.textSize = dp(22f)
        canvas.drawText("Statistik", dp(16f), dp(40f), pText)
        pText.color = cText; pText.textSize = dp(16f)
        var yy = dp(80f)
        val lines = listOf(
            "Produktionswert:  ${fmt(sim.produktionswertPerMin)} /min",
            "Barren gesamt:    ${fmt(sim.globalBarren)}   (${oneDec(sim.barrenPerMin)}/min)",
            "Platten gesamt:   ${fmt(sim.globalPlatten)}   (${oneDec(sim.plattenPerMin)}/min)",
            "Strom:            ${fmt(sim.powerSupply)} / ${fmt(sim.powerDemand)} (Angebot/Nachfrage)",
            "Maschinen gebaut: ${machineCount()}",
            "Tech erforscht:   ${sim.tech.size} / ${Simulation.TECHS.size}"
        )
        for (l in lines) { canvas.drawText(l, dp(16f), yy, pText); yy += dp(30f) }
        pText.color = cDim; pText.textSize = dp(13f)
        yy += dp(10f)
        canvas.drawText("Tipp: gelber Punkt = Nachschub fehlt, roter Balken = Verschleiss.", dp(16f), yy, pText)
        yy += dp(20f)
        canvas.drawText("Ofen braucht ~1,3 Bohrer Nachschub, Presse ~1,4 Oefen.", dp(16f), yy, pText)

        buttons.removeAll { it.id == "close" }
        val cr = RectF(W / 2f - dp(70f), H - dp(60f), W / 2f + dp(70f), H - dp(20f))
        drawButton(canvas, Btn(cr, "close", "Schliessen", true, false, cAccent))
        buttons.add(Btn(cr, "close", "Schliessen"))
    }

    private fun drawReport(canvas: Canvas) {
        val rep = report ?: return
        p.color = Color.argb(245, 20, 17, 14)
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        pText.color = cAccent; pText.textSize = dp(22f)
        canvas.drawText("Offline-Report", dp(16f), dp(40f), pText)
        pText.color = cText; pText.textSize = dp(15f)
        val away = fmtDur(rep.elapsedSeconds)
        val simd = fmtDur(rep.simSeconds)
        canvas.drawText("Du warst $away weg (simuliert: $simd, Cap 8h).", dp(16f), dp(66f), pText)
        pText.color = cGood
        canvas.drawText("+ ${fmt(rep.barrenGained)} Barren     + ${fmt(rep.plattenGained)} Platten", dp(16f), dp(90f), pText)

        pText.color = cText; pText.textSize = dp(14f)
        canvas.drawText("Zeitleiste:", dp(16f), dp(120f), pText)
        var yy = dp(142f)
        if (rep.events.isEmpty()) {
            pText.color = cDim
            canvas.drawText("Alles lief durch – keine Ausfaelle.", dp(24f), yy, pText)
        } else {
            pText.textSize = dp(13f)
            for (e in rep.events) {
                if (yy > H - dp(90f)) break
                pText.color = cDim
                canvas.drawText(fmtDur(e.timeSec), dp(24f), yy, pText)
                pText.color = cText
                canvas.drawText(e.text, dp(96f), yy, pText)
                yy += dp(22f)
            }
        }
        buttons.removeAll { it.id == "close" }
        val cr = RectF(W / 2f - dp(90f), H - dp(64f), W / 2f + dp(90f), H - dp(22f))
        drawButton(canvas, Btn(cr, "close", "Weiterspielen", true, false, cAccent))
        buttons.add(Btn(cr, "close", "Weiterspielen"))
    }

    private fun drawButton(canvas: Canvas, b: Btn) {
        p.color = when {
            !b.enabled -> Color.rgb(48, 44, 40)
            b.active -> (if (b.color != 0) b.color else cAccent)
            else -> Color.rgb(58, 52, 46)
        }
        canvas.drawRoundRect(b.rect, dp(7f), dp(7f), p)
        if (b.active) {
            p.color = cAccent; p.style = Paint.Style.STROKE; p.strokeWidth = dp(2f)
            canvas.drawRoundRect(b.rect, dp(7f), dp(7f), p)
            p.style = Paint.Style.FILL
        }
        pTextC.color = when {
            !b.enabled -> cDim
            b.active -> Color.rgb(24, 20, 16)
            else -> cText
        }
        pTextC.textSize = dp(13f)
        canvas.drawText(b.label, b.rect.centerX(), b.rect.centerY() + dp(5f), pTextC)
    }

    // ---------------- Eingabe ----------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        val x = event.x; val y = event.y

        // Buttons zuerst (Overlays haben Vorrang)
        for (b in buttons.reversed()) {
            if (b.rect.contains(x, y)) {
                if (b.enabled) handleButton(b.id)
                return true
            }
        }

        if (screen != Screen.GAME) return true

        // Gittertreffer
        if (x >= gridLeft && x < gridLeft + gridSide && y >= gridTop && y < gridTop + gridSide) {
            val c = ((x - gridLeft) / cell).toInt().coerceIn(0, sim.n - 1)
            val r = ((y - gridTop) / cell).toInt().coerceIn(0, sim.n - 1)
            handleCell(r, c)
        } else {
            selR = -1; selC = -1
        }
        return true
    }

    private fun handleButton(id: String) {
        when {
            id == "tech" -> { screen = if (screen == Screen.TECH) Screen.GAME else Screen.TECH; selR = -1 }
            id == "stat" -> { screen = if (screen == Screen.STAT) Screen.GAME else Screen.STAT; selR = -1 }
            id == "close" -> { screen = Screen.GAME; report = null }
            id == "repair_sel" -> { sim.grid[selR][selC]?.let { sim.repair(it) } }
            id.startsWith("buy_") -> { sim.buyTech(id.removePrefix("buy_")) }
            id.startsWith("tool_") -> {
                tool = Tool.valueOf(id.removePrefix("tool_"))
                selR = -1; selC = -1
            }
        }
        invalidate()
    }

    private fun handleCell(r: Int, c: Int) {
        val m = sim.grid[r][c]
        when (tool) {
            Tool.INSPECT -> { selR = r; selC = c }
            Tool.REPARIEREN -> { if (m != null) sim.repair(m); selR = r; selC = c }
            Tool.ABRISS -> { if (m != null && m.type != MType.REAKTOR) { sim.grid[r][c] = null; selR = -1 } }
            else -> {
                val t = toolMachine(tool)
                if (t != null && m == null && sim.canBuild(t)) {
                    sim.grid[r][c] = Machine(t)
                    selR = r; selC = c
                }
            }
        }
        invalidate()
    }

    private fun toolMachine(t: Tool): MType? = when (t) {
        Tool.BOHRER -> MType.BOHRER
        Tool.OFEN -> MType.OFEN
        Tool.PRESSE -> MType.PRESSE
        Tool.GENERATOR -> MType.GENERATOR
        Tool.LAGER -> MType.LAGER
        Tool.DROHNE -> MType.DROHNE
        else -> null
    }

    private fun machineCount(): Int {
        var n = 0
        for (r in 0 until sim.n) for (c in 0 until sim.n) if (sim.grid[r][c] != null && sim.grid[r][c]!!.type != MType.REAKTOR) n++
        return n
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        running = false
        handler.removeCallbacks(loop)
        persist()
    }

    // ---------------- Format-Helfer ----------------

    private fun fmt(v: Double): String {
        val a = if (v < 0) 0.0 else v
        return when {
            a >= 1_000_000 -> oneDec(a / 1_000_000) + "M"
            a >= 1_000 -> oneDec(a / 1_000) + "k"
            else -> a.roundToInt().toString()
        }
    }

    private fun oneDec(v: Double): String = ((v * 10).roundToInt() / 10.0).toString()

    private fun fmtDur(sec: Int): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) "${h}h ${m}m" else if (m > 0) "${m}m ${s}s" else "${s}s"
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        val ar = Color.red(a); val ag = Color.green(a); val ab = Color.blue(a)
        val br = Color.red(b); val bg = Color.green(b); val bb = Color.blue(b)
        return Color.rgb(
            (ar + (br - ar) * tt).toInt(),
            (ag + (bg - ag) * tt).toInt(),
            (ab + (bb - ab) * tt).toInt()
        )
    }
}
