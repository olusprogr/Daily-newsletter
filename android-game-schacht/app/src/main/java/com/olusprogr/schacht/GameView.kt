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

    // Aktives Bau-Werkzeug (null = kein Bauen; Antippen zeigt dann Info).
    private var buildTool: MType? = null

    private enum class Screen { GAME, TECH, STAT, REPORT }
    private var screen = Screen.GAME

    private var selR = -1
    private var selC = -1
    private var report: OfflineReport? = null
    private var resetArmed = false

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
    private var paletteTop = 0f

    private class Btn(
        val rect: RectF,
        val id: String,
        val label: String,
        var enabled: Boolean = true,
        var active: Boolean = false,
        val color: Int = 0,
        val sub: String = "",
        val subColor: Int = 0
    )

    private val buttons = ArrayList<Btn>()

    private val buildOrder = listOf(
        MType.BOHRER, MType.OFEN, MType.PRESSE, MType.GENERATOR, MType.LAGER, MType.DROHNE
    )

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
        paletteTop = gridTop + gridSide + dp(10f)
    }

    // ---------------- Rendering ----------------

    override fun onDraw(canvas: Canvas) {
        buttons.clear()
        canvas.drawColor(cBg)
        drawHeader(canvas)
        drawGrid(canvas)
        if (screen == Screen.GAME) {
            if (selR >= 0 && sim.grid[selR][selC] != null) drawDetail(canvas) else drawPalette(canvas)
        }
        when (screen) {
            Screen.TECH -> drawTech(canvas)
            Screen.STAT -> drawStat(canvas)
            Screen.REPORT -> drawReport(canvas)
            Screen.GAME -> { }
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
        val wear = (m.condition / 100.0).toFloat().coerceIn(0f, 1f)
        val col = blend(base, cCell, 1f - (0.35f + 0.65f * wear))
        p.color = col
        canvas.drawRoundRect(x + pad, y + pad, x + cell - pad, y + cell - pad, cell * 0.12f, cell * 0.12f, p)

        if (m.util > 0.02) {
            p.color = Color.argb((120 + 135 * m.util).toInt().coerceIn(0, 255), 255, 255, 255)
            p.style = Paint.Style.STROKE; p.strokeWidth = dp(2f)
            canvas.drawRoundRect(x + pad, y + pad, x + cell - pad, y + cell - pad, cell * 0.12f, cell * 0.12f, p)
            p.style = Paint.Style.FILL
        }

        pTextC.color = Color.rgb(20, 18, 15)
        pTextC.textSize = cell * 0.42f
        canvas.drawText(m.type.sym, x + cell / 2f, y + cell * 0.6f, pTextC)

        if (m.type != MType.REAKTOR && m.type != MType.LAGER) {
            val bw = cell - 2 * pad
            val by = y + cell - pad - dp(4f)
            p.color = cGridLine
            canvas.drawRect(x + pad, by, x + pad + bw, by + dp(4f), p)
            p.color = when { m.condition < 20 -> cBad; m.condition < 50 -> cWarn; else -> cGood }
            canvas.drawRect(x + pad, by, x + pad + bw * wear, by + dp(4f), p)
        }

        if (m.condition <= 0.0) {
            pTextC.color = cBad; pTextC.textSize = cell * 0.5f
            canvas.drawText("X", x + cell * 0.5f, y + cell * 0.62f, pTextC)
        } else if (m.starved) {
            p.color = cWarn
            canvas.drawCircle(x + cell - pad - dp(5f), y + pad + dp(5f), dp(4f), p)
        }
    }

    private fun drawPalette(canvas: Canvas) {
        val cols = 3
        val margin = dp(10f)
        val gap = dp(6f)
        val bw = (W - 2 * margin - (cols - 1) * gap) / cols
        val bh = dp(52f)
        for ((i, t) in buildOrder.withIndex()) {
            val col = i % cols
            val row = i / cols
            val x = margin + col * (bw + gap)
            val yy = paletteTop + row * (bh + gap)
            val rect = RectF(x, yy, x + bw, yy + bh)
            val unlocked = sim.canBuild(t)
            val cost = sim.buildCost(t)
            val afford = sim.globalBarren >= cost
            val sub: String
            val subCol: Int
            if (!unlocked) { sub = "Tech noetig"; subCol = cDim }
            else { sub = "${cost.toInt()} B"; subCol = if (afford) cDim else cBad }
            val active = buildTool == t
            drawButton(canvas, Btn(rect, "build_${t.name}", shortLabel(t), unlocked, active, 0, sub, subCol))
            buttons.add(Btn(rect, "build_${t.name}", shortLabel(t), unlocked))
        }
    }

    private fun shortLabel(t: MType) = when (t) {
        MType.GENERATOR -> "Generat."
        MType.DROHNE -> "Drohne"
        else -> t.label
    }

    private fun drawDetail(canvas: Canvas) {
        val m = sim.grid[selR][selC] ?: return
        val top = paletteTop
        p.color = cPanel
        canvas.drawRect(0f, top, W.toFloat(), H.toFloat(), p)

        pText.color = cAccent; pText.textSize = dp(18f)
        canvas.drawText("${m.type.label}  (${selR + 1},${selC + 1})", dp(12f), top + dp(24f), pText)

        pText.color = cText; pText.textSize = dp(14f)
        var yy = top + dp(48f)
        canvas.drawText("Zustand ${m.condition.roundToInt()}%     Auslastung ${(m.util * 100).roundToInt()}%", dp(12f), yy, pText)
        yy += dp(22f)
        val io = when (m.type) {
            MType.BOHRER -> "Aus: ${oneDec(m.output[0])} Roherz"
            MType.OFEN -> "Ein ${oneDec(m.input[0])} Roherz   Aus ${oneDec(m.output[1])} Barren"
            MType.PRESSE -> "Ein ${oneDec(m.input[1])} Barren   Aus ${oneDec(m.output[2])} Platten"
            MType.GENERATOR -> "Brennstoff ${oneDec(m.input[0])} Roherz  →  +${Simulation.GEN_POWER.toInt()} Strom"
            MType.LAGER -> "Puffer ${oneDec(m.output[0])}E ${oneDec(m.output[1])}B ${oneDec(m.output[2])}P"
            MType.REAKTOR -> "Liefert ${Simulation.REAKTOR_POWER.toInt()} Strom (fest)"
            MType.DROHNE -> "Repariert Nachbarn (${Simulation.DROHNE_RATE.toInt()}%/s)"
        }
        canvas.drawText(io, dp(12f), yy, pText)
        yy += dp(22f)
        pText.color = cDim
        if (m.type != MType.REAKTOR) {
            val refund = (sim.buildCost(m.type) * 0.5 * (m.condition / 100.0))
            canvas.drawText("Stromverbrauch ${m.type.power.toInt()}     Verkaufswert +${oneDec(refund)} B", dp(12f), yy, pText)
        }

        // Aktions-Buttons unten (ueberdecken keinen Text mehr)
        val margin = dp(10f)
        val by = H - dp(54f)
        val bh = dp(40f)
        val canRepair = m.type != MType.REAKTOR && m.type != MType.LAGER
        val canSell = m.type != MType.REAKTOR
        if (canRepair && canSell) {
            val half = (W - 3 * margin) / 2f
            val rSell = RectF(margin, by, margin + half, by + bh)
            val rRep = RectF(margin * 2 + half, by, margin * 2 + half * 2, by + bh)
            val refund = (sim.buildCost(m.type) * 0.5 * (m.condition / 100.0)).roundToInt()
            drawButton(canvas, Btn(rSell, "sell_sel", "Verkaufen +$refund B", true, false, cBad))
            drawButton(canvas, Btn(rRep, "repair_sel", "Reparieren ${Simulation.REPAIR_COST.toInt()} B",
                m.condition < 99.999 && sim.globalBarren >= Simulation.REPAIR_COST, false, cAccent))
            buttons.add(Btn(rSell, "sell_sel", "Verkaufen"))
            buttons.add(Btn(rRep, "repair_sel", "Reparieren",
                m.condition < 99.999 && sim.globalBarren >= Simulation.REPAIR_COST))
        } else if (canSell) {
            val rSell = RectF(margin, by, W - margin, by + bh)
            val refund = (sim.buildCost(m.type) * 0.5 * (m.condition / 100.0)).roundToInt()
            drawButton(canvas, Btn(rSell, "sell_sel", "Verkaufen +$refund B", true, false, cBad))
            buttons.add(Btn(rSell, "sell_sel", "Verkaufen"))
        }
    }

    private fun drawTech(canvas: Canvas) {
        p.color = Color.argb(242, 20, 17, 14)
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        pText.color = cAccent; pText.textSize = dp(22f)
        canvas.drawText("Tech-Baum", dp(16f), dp(38f), pText)
        pText.color = cDim; pText.textSize = dp(13f)
        canvas.drawText("Upgrades kosten Barren.  Verfuegbar: ${fmt(sim.globalBarren)} B", dp(16f), dp(58f), pText)

        var yy = dp(74f)
        val bh = dp(48f)
        for (node in Simulation.TECHS) {
            val rect = RectF(dp(12f), yy, W - dp(12f), yy + bh)
            val l = sim.lvl(node.id)
            val maxed = l >= node.maxLevel
            val preOk = node.prereq == null || sim.has(node.prereq)
            val cost = sim.nextCost(node)
            val afford = sim.globalBarren >= cost
            p.color = if (l > 0) Color.rgb(40, 55, 44) else cPanel
            canvas.drawRoundRect(rect, dp(8f), dp(8f), p)

            pText.color = cText; pText.textSize = dp(15f)
            val title = if (node.maxLevel > 1) "${node.label}  (Stufe $l/${node.maxLevel})" else node.label
            canvas.drawText(title, dp(24f), yy + dp(20f), pText)
            pText.textSize = dp(12f); pText.color = cDim
            val sub = when {
                maxed -> "voll ausgebaut"
                !preOk -> "benoetigt: ${Simulation.TECHS.first { it.id == node.prereq }.label}"
                node.maxLevel > 1 -> "${node.effect}  ·  naechste Stufe: ${cost.toInt()} B"
                else -> "Kosten: ${cost.toInt()} B" + (if (node.effect.isNotEmpty()) "  ·  ${node.effect}" else "")
            }
            canvas.drawText(sub, dp(24f), yy + dp(38f), pText)

            val kw = dp(92f); val kh = dp(34f)
            val kr = RectF(W - dp(24f) - kw, yy + (bh - kh) / 2, W - dp(24f), yy + (bh + kh) / 2)
            val kLabel = if (maxed) "MAX" else if (node.maxLevel > 1) "Stufe +" else "Kaufen"
            val kEnabled = !maxed && preOk && afford
            drawButton(canvas, Btn(kr, "buy_${node.id}", kLabel, kEnabled, false, cAccent))
            if (!maxed) buttons.add(Btn(kr, "buy_${node.id}", kLabel, kEnabled))
            yy += bh + dp(6f)
        }
        val cr = RectF(W / 2f - dp(70f), H - dp(58f), W / 2f + dp(70f), H - dp(18f))
        drawButton(canvas, Btn(cr, "close", "Schliessen", true, false, cAccent))
        buttons.add(Btn(cr, "close", "Schliessen"))
    }

    private fun drawStat(canvas: Canvas) {
        p.color = Color.argb(242, 20, 17, 14)
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        pText.color = cAccent; pText.textSize = dp(22f)
        canvas.drawText("Statistik", dp(16f), dp(40f), pText)
        pText.color = cText; pText.textSize = dp(16f)
        var yy = dp(80f)
        val lines = listOf(
            "Produktionswert:  ${fmt(sim.produktionswertPerMin)} /min",
            "Barren gesamt:    ${fmt(sim.globalBarren)}   (${oneDec(sim.barrenPerMin)}/min)",
            "Platten gesamt:   ${fmt(sim.globalPlatten)}   (${oneDec(sim.plattenPerMin)}/min)",
            "Strom:            ${fmt(sim.powerSupply)} / ${fmt(sim.powerDemand)}",
            "Maschinen gebaut: ${machineCount()}",
            "Upgrade-Stufen:   ${sim.tech.values.sum()}"
        )
        for (l in lines) { canvas.drawText(l, dp(16f), yy, pText); yy += dp(30f) }
        pText.color = cDim; pText.textSize = dp(13f)
        yy += dp(6f)
        canvas.drawText("Gelber Punkt = Nachschub fehlt, roter Balken = Verschleiss.", dp(16f), yy, pText)
        yy += dp(20f)
        canvas.drawText("Balance-Block: 4 Bohrer : 3 Oefen : 2 Pressen.", dp(16f), yy, pText)

        // Reset-Button (mit Bestaetigung)
        val margin = dp(12f)
        val by = H - dp(58f); val bh = dp(40f)
        val half = (W - 3 * margin) / 2f
        val rReset = RectF(margin, by, margin + half, by + bh)
        val rClose = RectF(margin * 2 + half, by, margin * 2 + half * 2, by + bh)
        drawButton(canvas, Btn(rReset, "reset",
            if (resetArmed) "Wirklich? Erneut tippen" else "Spielstand zuruecksetzen",
            true, resetArmed, cBad))
        drawButton(canvas, Btn(rClose, "close", "Schliessen", true, false, cAccent))
        buttons.add(Btn(rReset, "reset", "reset"))
        buttons.add(Btn(rClose, "close", "Schliessen"))
    }

    private fun drawReport(canvas: Canvas) {
        val rep = report ?: return
        p.color = Color.argb(248, 20, 17, 14)
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
        val txtCol = when {
            !b.enabled -> cDim
            b.active -> Color.rgb(24, 20, 16)
            else -> cText
        }
        if (b.sub.isEmpty()) {
            pTextC.color = txtCol; pTextC.textSize = dp(13f)
            canvas.drawText(b.label, b.rect.centerX(), b.rect.centerY() + dp(5f), pTextC)
        } else {
            pTextC.color = txtCol; pTextC.textSize = dp(14f)
            canvas.drawText(b.label, b.rect.centerX(), b.rect.centerY() - dp(2f), pTextC)
            pTextC.color = if (b.subColor != 0) b.subColor else cDim; pTextC.textSize = dp(11f)
            canvas.drawText(b.sub, b.rect.centerX(), b.rect.centerY() + dp(15f), pTextC)
        }
    }

    // ---------------- Eingabe ----------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        val x = event.x; val y = event.y

        for (b in buttons.reversed()) {
            if (b.rect.contains(x, y)) {
                if (b.enabled) handleButton(b.id)
                return true
            }
        }

        if (screen != Screen.GAME) return true

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
            id == "tech" -> { screen = if (screen == Screen.TECH) Screen.GAME else Screen.TECH; selR = -1; resetArmed = false }
            id == "stat" -> { screen = if (screen == Screen.STAT) Screen.GAME else Screen.STAT; selR = -1; resetArmed = false }
            id == "close" -> { screen = Screen.GAME; report = null; resetArmed = false }
            id == "reset" -> {
                if (!resetArmed) {
                    resetArmed = true
                } else {
                    sim.newGame(); persist(); resetArmed = false
                    buildTool = null; selR = -1; selC = -1; report = null
                    screen = Screen.GAME
                }
            }
            id == "repair_sel" -> { if (selR >= 0) sim.grid[selR][selC]?.let { sim.repair(it) } }
            id == "sell_sel" -> { if (selR >= 0) { sim.sell(selR, selC); selR = -1; selC = -1 } }
            id.startsWith("buy_") -> { sim.buyTech(id.removePrefix("buy_")) }
            id.startsWith("build_") -> {
                val t = MType.valueOf(id.removePrefix("build_"))
                buildTool = if (buildTool == t) null else t
                selR = -1; selC = -1
            }
        }
        invalidate()
    }

    private fun handleCell(r: Int, c: Int) {
        val m = sim.grid[r][c]
        if (m != null) {
            // Belegtes Feld: Info anzeigen (unabhaengig vom Bau-Werkzeug).
            selR = r; selC = c
        } else {
            val t = buildTool
            if (t != null) {
                if (sim.build(t, r, c)) { selR = -1; selC = -1 }
            } else {
                selR = -1; selC = -1
            }
        }
        invalidate()
    }

    private fun machineCount(): Int {
        var k = 0
        for (r in 0 until sim.n) for (c in 0 until sim.n) {
            val m = sim.grid[r][c]
            if (m != null && m.type != MType.REAKTOR) k++
        }
        return k
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
