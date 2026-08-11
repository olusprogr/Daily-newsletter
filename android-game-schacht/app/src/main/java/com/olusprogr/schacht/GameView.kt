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

    private var techScroll = 0f
    private var techMaxScroll = 0f
    private var downX = 0f
    private var downY = 0f
    private var downScroll = 0f
    private var moved = false

    private val audio = Audio()

    private val prefs = context.getSharedPreferences("schacht_save", Context.MODE_PRIVATE)

    // Farben (heller, waermer, farbiger)
    private val cBg = Color.rgb(38, 33, 28)
    private val cPanel = Color.rgb(60, 52, 44)
    private val cPanelHi = Color.rgb(86, 74, 62)
    private val cCell = Color.rgb(48, 43, 38)
    private val cGridLine = Color.rgb(26, 22, 18)
    private val cText = Color.rgb(244, 238, 228)
    private val cDim = Color.rgb(186, 174, 158)
    private val cAccent = Color.rgb(244, 186, 96)
    private val cGood = Color.rgb(122, 212, 142)
    private val cBad = Color.rgb(234, 104, 94)
    private val cWarn = Color.rgb(240, 204, 98)
    private val cBtn = Color.rgb(80, 70, 60)
    private val cBtnSh = Color.rgb(52, 45, 38)
    // Ressourcen-Farben
    private val cResBarren = Color.rgb(188, 194, 204)
    private val cResPlatte = Color.rgb(104, 168, 216)
    private val cResKomp = Color.rgb(96, 214, 204)
    private val cResGeld = Color.rgb(246, 200, 98)

    private fun mColor(t: MType) = when (t) {
        MType.BOHRER -> Color.rgb(200, 152, 92)
        MType.OFEN -> Color.rgb(234, 122, 72)
        MType.PRESSE -> Color.rgb(98, 152, 202)
        MType.ASSEMBLER -> Color.rgb(120, 202, 162)
        MType.GENERATOR -> Color.rgb(238, 202, 92)
        MType.LAGER -> Color.rgb(158, 138, 114)
        MType.DROHNE -> Color.rgb(110, 202, 172)
        MType.VERSTAERKER -> Color.rgb(154, 134, 232)
        MType.HAENDLER -> Color.rgb(242, 182, 102)
        MType.REAKTOR -> Color.rgb(172, 122, 232)
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pSprite = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
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
        val subColor: Int = 0,
        val tint: Int = 0
    )

    private val buttons = ArrayList<Btn>()

    private var animT = 0f

    // Stein-/Boden-Farben fuer den Hintergrund
    private val cGroundBase = Color.rgb(43, 39, 35)
    private val cGroundDark = Color.rgb(33, 30, 26)
    private val cGroundLite = Color.rgb(55, 50, 44)
    private val cGroundRust = Color.rgb(61, 37, 30)

    private val buildOrder = listOf(
        MType.BOHRER, MType.OFEN, MType.PRESSE, MType.ASSEMBLER, MType.HAENDLER,
        MType.GENERATOR, MType.LAGER, MType.DROHNE, MType.VERSTAERKER
    )

    private fun resAbbr(res: Res) = when (res) {
        Res.ROHERZ -> "E"; Res.BARREN -> "B"; Res.PLATTE -> "P"; Res.KOMPONENTE -> "K"
    }
    private fun tierName(t: Int) = when (t) { 0 -> "leer"; 1 -> "normal"; 2 -> "moderat"; else -> "reich" }

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
            animT += dt.toFloat()
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
        audio.startMusic()
    }

    fun pauseAudio() = audio.pause()
    fun resumeAudio() = audio.resume()

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
        val maxGrid = h - headerH - dp(252f)
        if (gridSide > maxGrid) gridSide = maxGrid
        cell = gridSide / sim.areaN()
        gridLeft = (w - gridSide) / 2f
        gridTop = headerH + dp(6f)
        paletteTop = gridTop + gridSide + dp(10f)
    }

    // ---------------- Rendering ----------------

    override fun onDraw(canvas: Canvas) {
        buttons.clear()
        cell = gridSide / sim.areaN()   // Feldgroesse haengt von der freigeschalteten Flaeche ab
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

    private fun resPip(canvas: Canvas, x: Float, cy: Float, color: Int) {
        val s = dp(9f)
        p.color = cGridLine
        canvas.drawRect(x - dp(1f), cy - s / 2 - dp(1f), x + s + dp(1f), cy + s / 2 + dp(1f), p)
        p.color = color
        canvas.drawRect(x, cy - s / 2, x + s, cy + s / 2, p)
        p.color = Color.argb(110, 255, 255, 255)
        canvas.drawRect(x, cy - s / 2, x + s, cy - s / 2 + dp(2f), p)
    }

    private fun drawHeader(canvas: Canvas) {
        p.color = cPanel
        canvas.drawRect(0f, 0f, W.toFloat(), headerH, p)
        p.color = cPanelHi
        canvas.drawRect(0f, 0f, W.toFloat(), dp(2f), p)
        p.color = cAccent
        canvas.drawRect(0f, headerH - dp(2f), W.toFloat(), headerH, p)

        // Titel: Geld hervorgehoben
        resPip(canvas, dp(12f), dp(20f), cResGeld)
        pText.textAlign = Paint.Align.LEFT
        pText.textSize = dp(20f); pText.color = cResGeld
        val moneyStr = fmt(sim.money)
        canvas.drawText(moneyStr, dp(28f), dp(27f), pText)
        val moneyW = pText.measureText(moneyStr)
        pText.textSize = dp(13f); pText.color = cGood
        canvas.drawText("+${fmt(sim.moneyPerMin)}/min", dp(28f) + moneyW + dp(10f), dp(27f), pText)

        // Bestand inkl. Lager-Inhalten (mit farbigen Pips)
        pText.textSize = dp(14f); pText.color = cText
        resPip(canvas, dp(12f), dp(43f), cResBarren)
        canvas.drawText(fmt(sim.availableBarren()), dp(28f), dp(48f), pText)
        resPip(canvas, dp(112f), dp(43f), cResPlatte)
        canvas.drawText(fmt(sim.availablePlatten()), dp(128f), dp(48f), pText)
        resPip(canvas, dp(210f), dp(43f), cResKomp)
        canvas.drawText(fmt(sim.availableKomponente()), dp(226f), dp(48f), pText)

        val powOk = sim.powerDemand <= sim.powerSupply + 1e-6
        val rest = sim.powerSupply - sim.powerDemand
        pText.color = if (powOk) cGood else cBad
        canvas.drawText("Reststrom ${if (rest < 0) "-" + fmt(-rest) else fmt(rest)}", dp(12f), dp(70f), pText)
        pText.color = cDim
        canvas.drawText("Strom ${fmt(sim.powerSupply)}/${fmt(sim.powerDemand)}", dp(150f), dp(70f), pText)

        val bw = dp(84f); val bh = dp(30f)
        val tR = RectF(W - dp(12f) - bw, dp(8f), W - dp(12f), dp(8f) + bh)
        val sR = RectF(W - dp(12f) - bw, dp(42f), W - dp(12f), dp(42f) + bh)
        drawButton(canvas, Btn(tR, "tech", "Tech", true, screen == Screen.TECH, cAccent))
        drawButton(canvas, Btn(sR, "stat", "Statistik", true, screen == Screen.STAT, cAccent))
        buttons.add(Btn(tR, "tech", "Tech"))
        buttons.add(Btn(sR, "stat", "Statistik"))
    }

    private fun drawGrid(canvas: Canvas) {
        val an = sim.areaN()
        for (r in 0 until an) for (c in 0 until an) {
            val x = gridLeft + c * cell
            val y = gridTop + r * cell
            drawGround(canvas, r, c, x, y)
            val m = sim.grid[r][c]
            if (m != null) drawMachine(canvas, m, x, y)
        }
        if (selR >= 0 && selR < an && selC < an && screen == Screen.GAME) {
            p.color = cAccent; p.style = Paint.Style.STROKE; p.strokeWidth = dp(3f)
            canvas.drawRect(gridLeft + selC * cell + 1, gridTop + selR * cell + 1,
                gridLeft + selC * cell + cell - 1, gridTop + selR * cell + cell - 1, p)
            p.style = Paint.Style.FILL
        }
    }

    /** Zeichnet einen Stein-/Boden-Chunk, eingefaerbt nach Bodenreichtum. */
    private fun drawGround(canvas: Canvas, r: Int, c: Int, x: Float, y: Float) {
        val tier = sim.richness(r, c)
        val base: Int; val dark: Int; val lite: Int; val accent: Int; val edge: Int
        when (tier) {
            0 -> { base = Color.rgb(58, 55, 52); dark = Color.rgb(44, 42, 40); lite = Color.rgb(72, 68, 64); accent = Color.rgb(90, 86, 82); edge = Color.rgb(84, 80, 76) }
            1 -> { base = Color.rgb(43, 39, 35); dark = Color.rgb(33, 30, 26); lite = Color.rgb(55, 50, 44); accent = Color.rgb(90, 55, 40); edge = Color.rgb(72, 52, 42) }
            2 -> { base = Color.rgb(41, 49, 39); dark = Color.rgb(29, 36, 27); lite = Color.rgb(56, 66, 50); accent = Color.rgb(60, 130, 100); edge = Color.rgb(58, 104, 76) }
            else -> { base = Color.rgb(54, 48, 27); dark = Color.rgb(38, 34, 16); lite = Color.rgb(78, 68, 32); accent = Color.rgb(224, 182, 70); edge = Color.rgb(210, 168, 60) }
        }
        pSprite.color = base
        canvas.drawRect(x, y, x + cell, y + cell, pSprite)
        val u = cell / 8f
        val seed = (r * 73856093) xor (c * 19349663)
        fun q(i: Int) = (seed ushr (i * 3)) and 7
        pSprite.color = dark
        canvas.drawRect(x + q(0) * u, y + q(1) * u, x + (q(0) + 1) * u, y + (q(1) + 2) * u, pSprite)
        canvas.drawRect(x + q(2) * u, y + q(3) * u, x + (q(2) + 2) * u, y + (q(3) + 1) * u, pSprite)
        pSprite.color = lite
        canvas.drawRect(x + q(4) * u, y + q(5) * u, x + (q(4) + 1) * u, y + (q(5) + 1) * u, pSprite)
        // Bodenschaetze-Flecken (nicht bei leerem Stein)
        if (tier >= 1) {
            pSprite.color = accent
            canvas.drawRect(x + q(6) * u, y + q(7) * u, x + (q(6) + 1) * u, y + (q(7) + 1) * u, pSprite)
            if (tier >= 2) canvas.drawRect(x + q(1) * u, y + q(6) * u, x + (q(1) + 1) * u, y + (q(6) + 1) * u, pSprite)
        }
        // Reicher Boden funkelt dezent: kleiner Glitzerpunkt, der blinkt
        if (tier == 3) {
            val tw = kotlin.math.sin(animT * 2.6 + (r * 1.7 + c)) * 0.5 + 0.5
            if (tw > 0.7) {
                val g = cell / 16f
                val gx = (q(2) % 5 + 2) * 2f * g
                val gy = (q(5) % 5 + 2) * 2f * g
                pSprite.color = Color.argb(210, 255, 246, 214)
                canvas.drawRect(x + gx, y + gy, x + gx + g, y + gy + g, pSprite)
            }
        }
        // Sanftes Oberlicht (oben heller)
        pSprite.color = Color.argb(26, 255, 255, 255)
        canvas.drawRect(x, y, x + cell, y + cell * 0.16f, pSprite)
        // Chunk-Kante in der Reichtums-Farbe -> jeder Chunk ist markiert
        p.color = edge
        canvas.drawRect(x, y, x + cell, y + 1f, p)
        canvas.drawRect(x, y, x + 1f, y + cell, p)
    }

    private fun drawSprite(canvas: Canvas, list: List<Px>, x: Float, y: Float, s: Float) {
        for (px in list) {
            pSprite.color = px.c
            canvas.drawRect(x + px.x * s, y + px.y * s, x + (px.x + px.w) * s, y + (px.y + px.h) * s, pSprite)
        }
    }

    private fun drawMachine(canvas: Canvas, m: Machine, x: Float, y: Float) {
        val s = cell / 32f
        val hasWear = m.type != MType.REAKTOR && m.type != MType.LAGER && m.type != MType.HAENDLER
        val pad = cell * 0.08f

        // Basis-Sprite
        drawSprite(canvas, Sprites.forType(m.type), x, y, s)

        // Arbeits-Animation: laeuft nur wenn die Maschine wirklich arbeitet
        val working = m.util > 0.02 ||
            m.type == MType.REAKTOR ||
            (m.type == MType.LAGER && (m.output[0] + m.output[1] + m.output[2] + m.output[3]) > 0.5)
        if (working) {
            val frame = ((animT * 6f).toInt()) % 4
            drawSprite(canvas, Sprites.anim(m.type, frame), x, y, s)
        }

        // Verschleiss-Overlays (Rost < 50%, Funken/Rauch < 20%)
        if (hasWear) {
            if (m.condition < 50) drawSprite(canvas, Sprites.RUST, x, y, s)
            if (m.condition < 20) drawSprite(canvas, Sprites.SPARK, x, y, s)
        }

        // dezenter Auslastungs-Rahmen
        if (m.util > 0.02) {
            p.color = Color.argb((60 + 120 * m.util).toInt().coerceIn(0, 255), 255, 255, 255)
            p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.5f)
            canvas.drawRect(x + pad, y + pad, x + cell - pad, y + cell - pad, p)
            p.style = Paint.Style.FILL
        }

        // Zustandsbalken unten
        if (hasWear) {
            val wear = (m.condition / 100.0).toFloat().coerceIn(0f, 1f)
            val bw = cell - 2 * pad
            val by = y + cell - pad - dp(4f)
            p.color = cGridLine
            canvas.drawRect(x + pad, by, x + pad + bw, by + dp(4f), p)
            p.color = when { m.condition < 20 -> cBad; m.condition < 50 -> cWarn; else -> cGood }
            canvas.drawRect(x + pad, by, x + pad + bw * wear, by + dp(4f), p)
        }

        // Marker: Totalausfall / Nachschub fehlt
        if (m.condition <= 0.0) {
            pTextC.color = cBad; pTextC.textSize = cell * 0.5f
            canvas.drawText("X", x + cell * 0.5f, y + cell * 0.62f, pTextC)
        } else if (m.starved) {
            p.color = cWarn
            canvas.drawRect(x + cell - pad - dp(8f), y + pad + dp(1f), x + cell - pad, y + pad + dp(9f), p)
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
            val (cres, camt) = sim.buildCost(t)
            val afford = sim.available(cres) >= camt
            val sub: String
            val subCol: Int
            if (!unlocked) { sub = "Tech noetig"; subCol = cDim }
            else { sub = "${camt.toInt()} ${resAbbr(cres)}"; subCol = if (afford) cDim else cBad }
            val active = buildTool == t
            val mc = mColor(t)
            drawButton(canvas, Btn(rect, "build_${t.name}", shortLabel(t), unlocked, active, mc, sub, subCol, mc))
            buttons.add(Btn(rect, "build_${t.name}", shortLabel(t), unlocked))
        }
    }

    private fun shortLabel(t: MType) = when (t) {
        MType.GENERATOR -> "Generat."
        MType.DROHNE -> "Drohne"
        MType.ASSEMBLER -> "Assembl."
        MType.VERSTAERKER -> "Verstaerk."
        MType.HAENDLER -> "Haendler"
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
            MType.BOHRER -> {
                val tier = sim.richness(selR, selC)
                "Aus ${oneDec(m.output[0])} Roherz   Boden: ${tierName(tier)} (x${Simulation.ORE_MULT[tier]})"
            }
            MType.OFEN -> "Ein ${oneDec(m.input[0])} Roherz   Aus ${oneDec(m.output[1])} Barren"
            MType.PRESSE -> "Ein ${oneDec(m.input[1])} Barren   Aus ${oneDec(m.output[2])} Platten"
            MType.ASSEMBLER -> "Ein ${oneDec(m.input[2])} Platten   Aus ${oneDec(m.output[3])} Komp."
            MType.HAENDLER -> "Verkauft Komponenten -> Geld (${oneDec(sim.componentPrice())}/Stk)"
            MType.GENERATOR -> "Brennstoff ${oneDec(m.input[0])} Roherz  →  +${Simulation.GEN_POWER.toInt()} Strom"
            MType.LAGER -> "Puffer ${oneDec(m.output[0])}E ${oneDec(m.output[1])}B ${oneDec(m.output[2])}P ${oneDec(m.output[3])}K"
            MType.VERSTAERKER -> "Beschleunigt Nachbarn (+${(Simulation.BOOST_PER * 100).toInt()}% je)"
            MType.REAKTOR -> "Liefert ${Simulation.REAKTOR_POWER.toInt()} Strom (fest)"
            MType.DROHNE -> "Repariert Nachbarn (${Simulation.DROHNE_RATE.toInt()}%/s)"
        }
        canvas.drawText(io, dp(12f), yy, pText)
        yy += dp(22f)
        pText.color = cDim
        val (sres, samt) = sim.buildCost(m.type)
        if (m.type != MType.REAKTOR) {
            val refund = samt * 0.5 * (m.condition / 100.0)
            canvas.drawText("Stromverbrauch ${m.type.power.toInt()}     Verkaufswert +${oneDec(refund)} ${resAbbr(sres)}", dp(12f), yy, pText)
        }

        // Aktions-Buttons unten
        val margin = dp(10f)
        val by = H - dp(54f)
        val bh = dp(40f)
        val canRepair = m.type != MType.REAKTOR && m.type != MType.LAGER && m.type != MType.HAENDLER
        val canSell = m.type != MType.REAKTOR
        val refund = (samt * 0.5 * (m.condition / 100.0)).roundToInt()
        val repEnabled = m.condition < 99.999 && sim.availableBarren() >= Simulation.REPAIR_COST
        if (canRepair && canSell) {
            val half = (W - 3 * margin) / 2f
            val rSell = RectF(margin, by, margin + half, by + bh)
            val rRep = RectF(margin * 2 + half, by, margin * 2 + half * 2, by + bh)
            drawButton(canvas, Btn(rSell, "sell_sel", "Verkaufen +$refund ${resAbbr(sres)}", true, false, cBad))
            drawButton(canvas, Btn(rRep, "repair_sel", "Reparieren ${Simulation.REPAIR_COST.toInt()} B", repEnabled, false, cAccent))
            buttons.add(Btn(rSell, "sell_sel", "Verkaufen"))
            buttons.add(Btn(rRep, "repair_sel", "Reparieren", repEnabled))
        } else if (canSell) {
            val rSell = RectF(margin, by, W - margin, by + bh)
            drawButton(canvas, Btn(rSell, "sell_sel", "Verkaufen +$refund ${resAbbr(sres)}", true, false, cBad))
            buttons.add(Btn(rSell, "sell_sel", "Verkaufen"))
        }
    }

    private fun drawTech(canvas: Canvas) {
        p.color = Color.argb(245, 48, 42, 35)
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)

        val bh = dp(48f)
        val gap = dp(6f)
        val startY = dp(66f)
        val bandBottom = H - dp(66f)
        val content = Simulation.TECHS.size * (bh + gap)
        techMaxScroll = (content - (bandBottom - startY)).coerceAtLeast(0f)
        techScroll = techScroll.coerceIn(0f, techMaxScroll)

        for ((i, node) in Simulation.TECHS.withIndex()) {
            val yy = startY + i * (bh + gap) - techScroll
            if (yy + bh < startY || yy > bandBottom) continue
            val rect = RectF(dp(12f), yy, W - dp(12f), yy + bh)
            val l = sim.lvl(node.id)
            val maxed = l >= node.maxLevel
            val preOk = node.prereq == null || sim.has(node.prereq)
            val cost = sim.nextCost(node)
            val afford = sim.techAffordable(node)
            val cur = if (node.costRes != null) resAbbr(node.costRes) else "Geld"
            p.color = if (l > 0) Color.rgb(40, 55, 44) else cPanel
            canvas.drawRoundRect(rect, dp(8f), dp(8f), p)

            pText.color = cText; pText.textSize = dp(15f)
            val title = if (node.maxLevel > 1) "${node.label}  (Stufe $l/${node.maxLevel})" else node.label
            canvas.drawText(title, dp(24f), yy + dp(20f), pText)
            pText.textSize = dp(12f); pText.color = cDim
            val sub = when {
                maxed -> "voll ausgebaut"
                !preOk -> "benoetigt: ${Simulation.TECHS.first { it.id == node.prereq }.label}"
                node.maxLevel > 1 -> "${node.effect}  ·  naechste: ${cost.toInt()} $cur"
                else -> "Kosten: ${cost.toInt()} $cur" + (if (node.effect.isNotEmpty()) "  ·  ${node.effect}" else "")
            }
            canvas.drawText(sub, dp(24f), yy + dp(38f), pText)

            val kw = dp(92f); val kh = dp(34f)
            val kr = RectF(W - dp(24f) - kw, yy + (bh - kh) / 2, W - dp(24f), yy + (bh + kh) / 2)
            val kLabel = if (maxed) "MAX" else if (node.maxLevel > 1) "Stufe +" else "Kaufen"
            val kEnabled = !maxed && preOk && afford
            drawButton(canvas, Btn(kr, "buy_${node.id}", kLabel, kEnabled, false, cAccent))
            if (!maxed) buttons.add(Btn(kr, "buy_${node.id}", kLabel, kEnabled))
        }

        // Kopf- und Fussleiste maskieren gescrollte Inhalte
        p.color = Color.rgb(48, 42, 35)
        canvas.drawRect(0f, 0f, W.toFloat(), startY, p)
        canvas.drawRect(0f, bandBottom, W.toFloat(), H.toFloat(), p)
        pText.color = cAccent; pText.textSize = dp(22f)
        canvas.drawText("Tech-Baum", dp(16f), dp(38f), pText)
        pText.color = cDim; pText.textSize = dp(13f)
        canvas.drawText("Geld ${fmt(sim.money)}  ·  B ${fmt(sim.availableBarren())}  ·  P ${fmt(sim.availablePlatten())}  ·  wischen", dp(16f), dp(58f), pText)

        val cr = RectF(W / 2f - dp(70f), H - dp(56f), W / 2f + dp(70f), H - dp(16f))
        drawButton(canvas, Btn(cr, "close", "Schliessen", true, false, cAccent))
        buttons.add(Btn(cr, "close", "Schliessen"))
    }

    private fun drawStat(canvas: Canvas) {
        p.color = Color.argb(245, 48, 42, 35)
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        pText.color = cAccent; pText.textSize = dp(22f)
        canvas.drawText("Statistik", dp(16f), dp(40f), pText)
        pText.color = cText; pText.textSize = dp(16f)
        var yy = dp(80f)
        val lines = listOf(
            "Geld:             ${fmt(sim.money)}   (+${fmt(sim.moneyPerMin)}/min)",
            "Barren (inkl.Lager): ${fmt(sim.availableBarren())}  (${oneDec(sim.barrenPerMin)}/min)",
            "Platten (inkl.Lager): ${fmt(sim.availablePlatten())}  (${oneDec(sim.plattenPerMin)}/min)",
            "Komponenten:      ${fmt(sim.availableKomponente())}  (${oneDec(sim.komponentenPerMin)}/min)",
            "Strom:            ${fmt(sim.powerSupply)} / ${fmt(sim.powerDemand)}",
            "Sektor-Flaeche:   ${sim.areaN()} x ${sim.areaN()}",
            "Maschinen gebaut: ${machineCount()}",
            "Upgrade-Stufen:   ${sim.tech.values.sum()}"
        )
        for (l in lines) { canvas.drawText(l, dp(16f), yy, pText); yy += dp(27f) }
        pText.color = cDim; pText.textSize = dp(13f)
        yy += dp(4f)
        canvas.drawText("Boden: grau=leer, braun=normal, gruen=moderat, gold=reich.", dp(16f), yy, pText)
        yy += dp(18f)
        canvas.drawText("Komponenten am Haendler -> Geld. Upgrades kosten Geld.", dp(16f), yy, pText)

        // Ton-Schalter
        val margin = dp(12f)
        val tw = W - 2 * margin
        val tr = RectF(margin, yy + dp(14f), margin + tw, yy + dp(14f) + dp(38f))
        drawButton(canvas, Btn(tr, "sound", if (audio.isMuted()) "Ton: AUS" else "Ton: AN", true, !audio.isMuted(), cAccent))
        buttons.add(Btn(tr, "sound", "sound"))

        // Reset + Schliessen
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
        p.color = Color.argb(248, 48, 42, 35)
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        pText.color = cAccent; pText.textSize = dp(22f)
        canvas.drawText("Offline-Report", dp(16f), dp(40f), pText)
        pText.color = cText; pText.textSize = dp(15f)
        val away = fmtDur(rep.elapsedSeconds)
        val simd = fmtDur(rep.simSeconds)
        canvas.drawText("Du warst $away weg (simuliert: $simd, Cap 8h).", dp(16f), dp(66f), pText)
        pText.color = cGood
        canvas.drawText("+ ${fmt(rep.moneyGained)} Geld     + ${fmt(rep.barrenGained)} Barren     + ${fmt(rep.plattenGained)} Platten", dp(16f), dp(90f), pText)

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
        val bg = when {
            !b.enabled -> cBtnSh
            b.active -> if (b.color != 0) b.color else cAccent
            else -> cBtn
        }
        p.color = bg
        canvas.drawRoundRect(b.rect, dp(9f), dp(9f), p)
        // Bevel (heller oben, dunkler unten)
        if (b.enabled) {
            p.color = Color.argb(75, 255, 255, 255)
            canvas.drawRect(b.rect.left + dp(7f), b.rect.top + dp(2f), b.rect.right - dp(7f), b.rect.top + dp(3.5f), p)
            p.color = Color.argb(55, 0, 0, 0)
            canvas.drawRect(b.rect.left + dp(7f), b.rect.bottom - dp(3f), b.rect.right - dp(7f), b.rect.bottom - dp(1.5f), p)
        }
        // Kategorie-Farbstreifen links
        if (b.tint != 0 && !b.active && b.enabled) {
            p.color = b.tint
            canvas.drawRect(b.rect.left + dp(3f), b.rect.top + dp(7f), b.rect.left + dp(6f), b.rect.bottom - dp(7f), p)
        }
        if (b.active) {
            p.color = cAccent; p.style = Paint.Style.STROKE; p.strokeWidth = dp(2f)
            canvas.drawRoundRect(b.rect, dp(9f), dp(9f), p)
            p.style = Paint.Style.FILL
        }
        val txtCol = when {
            !b.enabled -> cDim
            b.active -> Color.rgb(28, 22, 16)
            else -> cText
        }
        if (b.sub.isEmpty()) {
            pTextC.color = txtCol; pTextC.textSize = dp(13f)
            canvas.drawText(b.label, b.rect.centerX(), b.rect.centerY() + dp(5f), pTextC)
        } else {
            pTextC.color = txtCol; pTextC.textSize = dp(13.5f)
            canvas.drawText(b.label, b.rect.centerX(), b.rect.centerY() - dp(2f), pTextC)
            pTextC.color = if (b.subColor != 0) b.subColor else cDim; pTextC.textSize = dp(11f)
            canvas.drawText(b.sub, b.rect.centerX(), b.rect.centerY() + dp(15f), pTextC)
        }
    }

    // ---------------- Eingabe ----------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val slop = dp(8f)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; downScroll = techScroll; moved = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.abs(event.x - downX) > slop || kotlin.math.abs(event.y - downY) > slop) moved = true
                if (screen == Screen.TECH) {
                    techScroll = (downScroll - (event.y - downY)).coerceIn(0f, techMaxScroll)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) handleClick(event.x, event.y)
                return true
            }
            else -> return true
        }
    }

    private fun handleClick(x: Float, y: Float) {
        for (b in buttons.reversed()) {
            if (b.rect.contains(x, y)) {
                if (b.enabled) handleButton(b.id) else audio.error()
                return
            }
        }
        if (screen != Screen.GAME) return
        val an = sim.areaN()
        if (x >= gridLeft && x < gridLeft + gridSide && y >= gridTop && y < gridTop + gridSide) {
            val c = ((x - gridLeft) / cell).toInt().coerceIn(0, an - 1)
            val r = ((y - gridTop) / cell).toInt().coerceIn(0, an - 1)
            handleCell(r, c)
        } else {
            selR = -1; selC = -1
            invalidate()
        }
    }

    private fun handleButton(id: String) {
        when {
            id == "tech" -> { screen = if (screen == Screen.TECH) Screen.GAME else Screen.TECH; selR = -1; resetArmed = false; techScroll = 0f; audio.click() }
            id == "stat" -> { screen = if (screen == Screen.STAT) Screen.GAME else Screen.STAT; selR = -1; resetArmed = false; audio.click() }
            id == "close" -> { screen = Screen.GAME; report = null; resetArmed = false; audio.click() }
            id == "sound" -> { audio.toggleMuted(); audio.click() }
            id == "reset" -> {
                if (!resetArmed) {
                    resetArmed = true; audio.click()
                } else {
                    sim.newGame(); persist(); resetArmed = false
                    buildTool = null; selR = -1; selC = -1; report = null
                    screen = Screen.GAME; audio.sell()
                }
            }
            id == "repair_sel" -> { if (selR >= 0) sim.grid[selR][selC]?.let { if (sim.repair(it)) audio.buy() else audio.error() } }
            id == "sell_sel" -> { if (selR >= 0) { sim.sell(selR, selC); selR = -1; selC = -1; audio.sell() } }
            id.startsWith("buy_") -> { if (sim.buyTech(id.removePrefix("buy_"))) audio.buy() else audio.error() }
            id.startsWith("build_") -> {
                val t = MType.valueOf(id.removePrefix("build_"))
                buildTool = if (buildTool == t) null else t
                selR = -1; selC = -1; audio.click()
            }
        }
        invalidate()
    }

    private fun handleCell(r: Int, c: Int) {
        val m = sim.grid[r][c]
        if (m != null) {
            selR = r; selC = c; audio.click()
        } else {
            val t = buildTool
            if (t != null) {
                if (sim.build(t, r, c)) { selR = -1; selC = -1; audio.place() } else audio.error()
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
        audio.release()
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

}
