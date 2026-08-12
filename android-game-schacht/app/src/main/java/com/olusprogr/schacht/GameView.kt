package com.olusprogr.schacht

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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

    private enum class Screen { MENU, GAME, TECH, STAT, REPORT }
    private var screen = Screen.MENU

    private val saveStore = SaveStore(context)
    private var currentSlot: String? = null
    private var menuArmedDelete: String? = null   // Slot, dessen Loeschen bestaetigt werden muss
    private var menuSlots: List<SlotInfo> = emptyList()   // gecachte Liste fuers Menue
    private fun refreshMenu() { menuSlots = saveStore.slots() }

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
    private var downInGrid = false

    private val audio = Audio()

    // Pinch-Zoom auf der Karte (nur im Spiel).
    private val scaleDetector = android.view.ScaleGestureDetector(
        context,
        object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: android.view.ScaleGestureDetector): Boolean {
                if (screen != Screen.GAME) return false
                val bc = baseCell()
                val oldCell = bc * zoom
                val gx = (d.focusX - (gridLeft + panX)) / oldCell
                val gy = (d.focusY - (gridTop + panY)) / oldCell
                zoom = (zoom * d.scaleFactor).coerceIn(zoomMin, zoomMax)
                val newCell = bc * zoom
                panX = d.focusX - gridLeft - gx * newCell
                panY = d.focusY - gridTop - gy * newCell
                moved = true
                invalidate()
                return true
            }
        }
    )

    // Ein-Finger-Scrollen ueber die Standard-GestureDetector-API (robust).
    private val gestureDetector = android.view.GestureDetector(
        context,
        object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (screen == Screen.GAME && downInGrid && !scaleDetector.isInProgress) {
                    panX -= dx
                    panY -= dy
                    moved = true
                    invalidate()
                    return true
                }
                return false
            }
        }
    )

    private val prefs = context.getSharedPreferences("schacht_save", Context.MODE_PRIVATE)

    // Farben (heller, waermer, farbiger)
    private val cBg = Color.rgb(52, 46, 39)
    private val cPanel = Color.rgb(72, 62, 53)
    private val cPanelHi = Color.rgb(96, 83, 70)
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
        MType.PROSPEKTOR -> Color.rgb(96, 200, 210)
        MType.WINDRAD -> Color.rgb(214, 220, 230)
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pSprite = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }

    // Echte Boden-Kacheln (Pixel-Texturen, CC0/selbst erstellt)
    private val pTile = Paint().apply { isFilterBitmap = false; isDither = false; isAntiAlias = false }
    private val srcTile = Rect(0, 0, 32, 32)
    private val dstTile = RectF()
    private val pBlade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(232, 236, 244); style = Paint.Style.FILL }
    private val bladePath = android.graphics.Path()
    private lateinit var bmpWater0: Bitmap
    private lateinit var bmpWater1: Bitmap
    private lateinit var grassUnknown: Bitmap
    private lateinit var grassTiles: Array<Bitmap>
    private lateinit var decoPines: Array<Bitmap>
    private lateinit var decoLeafs: Array<Bitmap>
    private lateinit var decoRock: Bitmap
    private lateinit var decoBush: Bitmap
    private lateinit var machBmp: Array<Bitmap>   // 64x64 Maschinen-Sprites
    private val decoDst = RectF()
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cText; textAlign = Paint.Align.LEFT }
    private val pTextC = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cText; textAlign = Paint.Align.CENTER }

    private var W = 0
    private var H = 0
    private var dens = context.resources.displayMetrics.density
    private fun dp(v: Float) = v * dens

    // Layout: gridLeft/gridTop + gridW/gridH = rechteckiges Karten-Fenster.
    private var gridLeft = 0f
    private var gridTop = 0f
    private var cell = 0f          // effektive (gezoomte) Zellgroesse
    private var gridW = 0f
    private var gridH = 0f
    private var headerH = 0f
    private var paletteTop = 0f
    private var paletteH = 0f

    // Zoom & Verschiebung der (grossen) Karte
    private val visibleAt1 = 9f   // ~9 Chunks quer bei Zoom 1
    private val zoomMin = 0.7f
    private val zoomMax = 2.4f
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var vLeft = 0f         // effektiver Ursprung (mit Pan)
    private var vTop = 0f
    private var lastPanX = 0f
    private var lastPanY = 0f
    private var needCenter = true

    private fun baseCell() = gridW / visibleAt1

    private fun reactorRC(): Pair<Int, Int> {
        for (r in 0 until sim.n) for (c in 0 until sim.n)
            if (sim.grid[r][c]?.type == MType.REAKTOR) return r to c
        return sim.startR to sim.startC
    }

    private fun updateView() {
        zoom = zoom.coerceIn(zoomMin, zoomMax)
        cell = baseCell() * zoom
        val content = sim.n * cell
        if (needCenter && gridW > 0f) {
            val (rr, cc) = reactorRC()
            panX = gridW / 2f - (cc + 0.5f) * cell
            panY = gridH / 2f - (rr + 0.5f) * cell
            needCenter = false
        }
        panX = if (content <= gridW) (gridW - content) / 2f else panX.coerceIn(gridW - content, 0f)
        panY = if (content <= gridH) (gridH - content) / 2f else panY.coerceIn(gridH - content, 0f)
        vLeft = gridLeft + panX
        vTop = gridTop + panY
    }

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

    // Aufsteigende "+Geld"-Zahlen beim Abbauen von Hindernissen.
    private class Rise(val r: Int, val c: Int, val text: String, val start: Float)
    private val rises = ArrayList<Rise>()

    // Stein-/Boden-Farben fuer den Hintergrund
    private val cGroundBase = Color.rgb(43, 39, 35)
    private val cGroundDark = Color.rgb(33, 30, 26)
    private val cGroundLite = Color.rgb(55, 50, 44)
    private val cGroundRust = Color.rgb(61, 37, 30)

    private val buildOrder = listOf(
        MType.BOHRER, MType.OFEN, MType.PROSPEKTOR, MType.PRESSE, MType.ASSEMBLER,
        MType.HAENDLER, MType.GENERATOR, MType.WINDRAD, MType.LAGER, MType.DROHNE, MType.VERSTAERKER
    )

    private fun resAbbr(res: Res) = when (res) {
        Res.ROHERZ -> "E"; Res.BARREN -> "B"; Res.PLATTE -> "P"; Res.KOMPONENTE -> "K"
    }
    private fun tierName(t: Int) = I18n.t("tier$t")
    private fun mName(t: MType) = I18n.t("m_" + t.name.lowercase())
    private fun mShort(t: MType) = I18n.t("ms_" + t.name.lowercase())
    private fun tr(k: String) = I18n.t(k)

    private fun drawIcon(canvas: Canvas, list: List<Px>, x: Float, y: Float, size: Float) =
        drawSprite(canvas, list, x, y, size / 12f)

    private fun setLang(l: Lang) {
        I18n.lang = l
        prefs.edit().putInt("lang", l.ordinal).apply()
        audio.click(); invalidate()
    }

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
            if (screen == Screen.GAME || screen == Screen.TECH || screen == Screen.STAT) sim.step(dt)
            animT += dt.toFloat()
            invalidate()
            handler.postDelayed(this, 33)
        }
    }

    init {
        // Quick-Scale (Doppeltipp-Ziehen zum Zoomen) aus: es blockiert sonst
        // das Ein-Finger-Scrollen, weil der Scale-Detektor "in progress" meldet.
        try { scaleDetector.isQuickScaleEnabled = false } catch (_: Exception) { }
        // Boden-Kacheln laden (nearest-neighbor, damit die Pixel scharf bleiben)
        val bo = BitmapFactory.Options().apply { inScaled = false }
        fun ld(id: Int) = BitmapFactory.decodeResource(resources, id, bo)
        bmpWater0 = ld(R.drawable.tile_water_0)
        bmpWater1 = ld(R.drawable.tile_water_1)
        grassUnknown = ld(R.drawable.tile_grass_u)
        grassTiles = arrayOf(
            ld(R.drawable.tile_grass0), ld(R.drawable.tile_grass1),
            ld(R.drawable.tile_grass2), ld(R.drawable.tile_grass3)
        )
        decoPines = arrayOf(ld(R.drawable.deco_pine1), ld(R.drawable.deco_pine2), ld(R.drawable.deco_pine3))
        decoLeafs = arrayOf(ld(R.drawable.deco_leaf1), ld(R.drawable.deco_leaf2))
        decoRock = ld(R.drawable.deco_rock)
        decoBush = ld(R.drawable.deco_bush)
        machBmp = Array(MType.values().size) { i ->
            ld(when (MType.values()[i]) {
                MType.BOHRER -> R.drawable.mach_bohrer
                MType.OFEN -> R.drawable.mach_ofen
                MType.PRESSE -> R.drawable.mach_presse
                MType.ASSEMBLER -> R.drawable.mach_assembler
                MType.GENERATOR -> R.drawable.mach_generator
                MType.LAGER -> R.drawable.mach_lager
                MType.DROHNE -> R.drawable.mach_drohne
                MType.VERSTAERKER -> R.drawable.mach_verstaerker
                MType.REAKTOR -> R.drawable.mach_reaktor
                MType.HAENDLER -> R.drawable.mach_haendler
                MType.PROSPEKTOR -> R.drawable.mach_prospektor
                MType.WINDRAD -> R.drawable.mach_windrad
            })
        }
        I18n.lang = try { Lang.values()[prefs.getInt("lang", Lang.EN.ordinal)] } catch (_: Exception) { Lang.EN }
        audio.setMusicVol(prefs.getInt("musicVol", 50) / 100f)
        audio.setSfxVol(prefs.getInt("sfxVol", 33) / 100f)
        // Start immer im Hauptmenue mit der Slot-Auswahl.
        sim.newGame()
        screen = Screen.MENU
        refreshMenu()
        lastNanos = System.nanoTime()
        handler.post(loop)
        audio.startMusic()
    }

    fun pauseAudio() = audio.pause()
    fun resumeAudio() = audio.resume()

    /** Gesamte Slot-Datenbank als JSON (fuer Backup-Export). */
    fun exportSave(): String { persist(); return saveStore.exportAll() }

    /** Spielstaende aus einem Backup ersetzen; danach zurueck ins Menue. */
    fun importSave(json: String): Boolean {
        val ok = saveStore.importAll(json)
        if (ok) {
            currentSlot = null; menuArmedDelete = null
            refreshMenu(); screen = Screen.MENU; invalidate()
        }
        return ok
    }

    fun persist() {
        val slot = currentSlot ?: return
        try {
            saveStore.saveState(slot, sim.toJson(System.currentTimeMillis()))
        } catch (_: Exception) { }
    }

    /** Einen gespeicherten Slot laden und (bei Bedarf) Offline-Fortschritt zeigen. */
    private fun openSlot(id: String) {
        val blob = saveStore.loadState(id)
        currentSlot = id
        report = null
        needCenter = true
        if (blob == null) { sim.newGame(); screen = Screen.GAME; return }
        try {
            val savedT = sim.fromJson(blob)
            val elapsed = ((System.currentTimeMillis() - savedT) / 1000L).toInt()
            if (savedT > 0 && elapsed > 60) {
                report = sim.runOffline(elapsed)
                screen = Screen.REPORT
            } else screen = Screen.GAME
        } catch (_: Exception) {
            sim.newGame(); screen = Screen.GAME
        }
    }

    private fun startNewSlot() {
        sim.newGame()
        currentSlot = saveStore.createSlot(sim.toJson(System.currentTimeMillis()))
        report = null
        needCenter = true
        screen = Screen.GAME
    }

    private fun setMusicVol(v: Int) {
        audio.setMusicVol(v / 100f)
        prefs.edit().putInt("musicVol", v).apply()
        audio.click(); invalidate()
    }
    private fun setSfxVol(v: Int) {
        audio.setSfxVol(v / 100f)
        prefs.edit().putInt("sfxVol", v).apply()
        audio.click(); invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        W = w; H = h
        headerH = dp(78f)
        val margin = dp(8f)
        // Palette unten (5 Spalten, 2 Reihen), Buttons wieder ~50% hoeher
        val palRows = 2
        val palBh = dp(66f); val palGap = dp(5f)
        paletteH = palRows * palBh + (palRows - 1) * palGap + dp(8f)
        paletteTop = H - paletteH
        gridLeft = margin
        gridTop = headerH + dp(4f)
        gridW = w - 2 * margin
        gridH = paletteTop - gridTop - dp(4f)        // Karten-Fenster fuellt fast alles
        cell = gridW / visibleAt1
        needCenter = true
    }

    // ---------------- Rendering ----------------

    override fun onDraw(canvas: Canvas) {
        buttons.clear()
        updateView()   // setzt cell/vLeft/vTop aus Zoom & Pan
        canvas.drawColor(cBg)
        if (screen == Screen.MENU) { drawMenu(canvas); return }
        drawHeader(canvas)
        drawGrid(canvas)
        if (screen == Screen.GAME) drawRises(canvas)
        if (screen == Screen.GAME) {
            if (selR >= 0 && sim.grid[selR][selC] != null) drawDetail(canvas) else drawPalette(canvas)
        }
        when (screen) {
            Screen.TECH -> drawTech(canvas)
            Screen.STAT -> drawStat(canvas)
            Screen.REPORT -> drawReport(canvas)
            else -> { }
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

        pText.textAlign = Paint.Align.LEFT

        // Titel: Geld hervorgehoben mit Muenz-Icon
        drawIcon(canvas, Sprites.ICON_GELD, dp(9f), dp(11f), dp(18f))
        pText.textSize = dp(20f); pText.color = cResGeld
        val moneyStr = fmt(sim.money)
        canvas.drawText(moneyStr, dp(32f), dp(27f), pText)
        val moneyW = pText.measureText(moneyStr)
        pText.textSize = dp(13f); pText.color = cGood
        canvas.drawText("+${fmt(sim.moneyPerMin)}/min", dp(32f) + moneyW + dp(10f), dp(27f), pText)

        // Bestand inkl. Lager-Inhalten (mit Pixel-Icons)
        pText.textSize = dp(14f); pText.color = cText
        drawIcon(canvas, Sprites.ICON_BARREN, dp(9f), dp(39f), dp(15f))
        canvas.drawText(fmt(sim.availableBarren()), dp(28f), dp(48f), pText)
        drawIcon(canvas, Sprites.ICON_PLATTE, dp(110f), dp(39f), dp(15f))
        canvas.drawText(fmt(sim.availablePlatten()), dp(128f), dp(48f), pText)
        drawIcon(canvas, Sprites.ICON_KOMP, dp(206f), dp(39f), dp(15f))
        canvas.drawText(fmt(sim.availableKomponente()), dp(226f), dp(48f), pText)

        val powOk = sim.powerDemand <= sim.powerSupply + 1e-6
        val rest = sim.powerSupply - sim.powerDemand
        drawIcon(canvas, Sprites.ICON_STROM, dp(9f), dp(61f), dp(15f))
        pText.color = if (powOk) cGood else cBad
        canvas.drawText("${tr("reststrom")} ${if (rest < 0) "-" + fmt(-rest) else fmt(rest)}", dp(28f), dp(70f), pText)
        pText.color = cDim
        canvas.drawText("${fmt(sim.powerSupply)}/${fmt(sim.powerDemand)}", dp(170f), dp(70f), pText)

        val bw = dp(84f); val bh = dp(30f)
        val tR = RectF(W - dp(12f) - bw, dp(8f), W - dp(12f), dp(8f) + bh)
        val sR = RectF(W - dp(12f) - bw, dp(42f), W - dp(12f), dp(42f) + bh)
        drawButton(canvas, Btn(tR, "tech", tr("tech"), true, screen == Screen.TECH, cAccent))
        drawButton(canvas, Btn(sR, "stat", tr("stat"), true, screen == Screen.STAT, cAccent))
        buttons.add(Btn(tR, "tech", "Tech"))
        buttons.add(Btn(sR, "stat", "Statistik"))
    }

    private fun drawGrid(canvas: Canvas) {
        val an = sim.areaN()
        canvas.save()
        canvas.clipRect(gridLeft, gridTop, gridLeft + gridW, gridTop + gridH)
        // nur den sichtbaren Ausschnitt zeichnen (grosse Welt)
        val c0 = (((gridLeft - vLeft) / cell).toInt() - 1).coerceIn(0, an - 1)
        val c1 = (((gridLeft + gridW - vLeft) / cell).toInt() + 1).coerceIn(0, an - 1)
        val r0 = (((gridTop - vTop) / cell).toInt() - 1).coerceIn(0, an - 1)
        val r1 = (((gridTop + gridH - vTop) / cell).toInt() + 1).coerceIn(0, an - 1)
        // Pass 1: Boden + Deko
        for (r in r0..r1) for (c in c0..c1) {
            drawGround(canvas, r, c, vLeft + c * cell, vTop + r * cell)
        }
        // Pass 2: Maschinen (eine Reihe tiefer mitnehmen, wegen Gebaeuden die nach oben ragen)
        val mr1 = (r1 + 1).coerceIn(0, an - 1)
        for (r in r0..mr1) for (c in c0..c1) {
            val m = sim.grid[r][c] ?: continue
            drawMachine(canvas, m, vLeft + c * cell, vTop + r * cell)
        }
        if (screen == Screen.GAME) drawFlows(canvas)
        if (selR >= 0 && selR < an && selC < an && screen == Screen.GAME) {
            val sm = sim.grid[selR][selC]
            val fh = sm?.h ?: 1; val fw = sm?.w ?: 1
            p.color = cAccent; p.style = Paint.Style.STROKE; p.strokeWidth = dp(3f)
            canvas.drawRect(vLeft + selC * cell + 1, vTop + (selR - fh + 1) * cell + 1,
                vLeft + (selC + fw) * cell - 1, vTop + (selR + 1) * cell - 1, p)
            p.style = Paint.Style.FILL
        }
        canvas.restore()
    }

    // Welchen Rohstoff gibt ein Produzent aus / will ein Verbraucher.
    private fun offersRes(t: MType): Int = when (t) {
        MType.BOHRER -> Res.ROHERZ.ordinal
        MType.OFEN -> Res.BARREN.ordinal
        MType.PRESSE -> Res.PLATTE.ordinal
        MType.ASSEMBLER -> Res.KOMPONENTE.ordinal
        else -> -1
    }
    private fun wantsRes(t: MType): Int = when (t) {
        MType.OFEN, MType.GENERATOR -> Res.ROHERZ.ordinal
        MType.PRESSE -> Res.BARREN.ordinal
        MType.ASSEMBLER -> Res.PLATTE.ordinal
        else -> -1
    }

    /**
     * Fluesse werden direkt aus dem Gitter abgeleitet (Nachbarschaft + Aktivitaet),
     * nicht aus den winzigen Pro-Tick-Transfers. So werden alle Ketten sichtbar:
     * Bohrer->Ofen, Ofen->Presse, Presse->Assembler, sowie Zufuhr in die Lager.
     */
    private fun drawFlows(canvas: Canvas) {
        val an = sim.areaN()
        val half = cell / 2f
        val isz = (cell * 0.26f).coerceAtLeast(dp(9f))   // kleiner als vorher
        val dirs = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))
        for (r in 0 until an) for (c in 0 until an) {
            val cm = sim.grid[r][c] ?: continue
            val isLager = cm.type == MType.LAGER
            val want = wantsRes(cm.type)
            if (want < 0 && !isLager) continue
            for (dir in dirs) {
                val pr = r + dir[0]; val pc = c + dir[1]
                if (pr !in 0 until an || pc !in 0 until an) continue
                val pm = sim.grid[pr][pc] ?: continue
                val off = offersRes(pm.type)
                if (off < 0) continue
                val res = if (isLager) off else want
                if (!isLager && off != want) continue
                // nur zeichnen, wenn tatsaechlich Material fliesst
                val consuming = if (isLager) pm.output[res] > 0.3 else cm.util > 0.03
                val producing = pm.util > 0.03 || pm.output[res] > 0.2
                if (!consuming || !producing) continue

                val sx = vLeft + pc * cell + half
                val sy = vTop + pr * cell + half
                val ex = vLeft + c * cell + half
                val ey = vTop + r * cell + half
                val icon = Sprites.iconForRes(res)
                val base = animT * 0.6f + (pr * 3 + pc + res) * 0.31f   // langsamer
                val t = base % 1f
                val cx = sx + (ex - sx) * t
                val cy = sy + (ey - sy) * t
                drawIcon(canvas, icon, cx - isz / 2f, cy - isz / 2f, isz)
            }
        }
    }

    // Terrain-Farben: heller Surf, Sandstrand, feuchter Sand
    private val cSurf = Color.rgb(206, 244, 244)
    private val cSand = Color.rgb(232, 214, 156)
    private val cSandWet = Color.rgb(206, 184, 128)

    private fun landSafe(r: Int, c: Int): Boolean =
        r in 0 until sim.n && c in 0 until sim.n && sim.isLand(r, c)

    /** Zeichnet einen Karten-Chunk aus echten Bild-Kacheln + prozeduraler Kueste/Fog. */
    private fun drawGround(canvas: Canvas, r: Int, c: Int, x: Float, y: Float) {
        val u = cell / 8f
        dstTile.set(x, y, x + cell, y + cell)

        if (!sim.isLand(r, c)) {
            // --- Wasser: zwei Kachel-Frames sanft abwechselnd ---
            val wb = if ((animT * 2f).toInt() and 1 == 0) bmpWater0 else bmpWater1
            canvas.drawBitmap(wb, srcTile, dstTile, pTile)
            return
        }

        // --- Land: Gras-Kachel je nach Scan/Reichtum ---
        val surveyed = sim.isSurveyed(r, c)
        val tier = sim.richness(r, c)
        val gb = if (!surveyed) grassUnknown else grassTiles[tier]
        canvas.drawBitmap(gb, srcTile, dstTile, pTile)

        if (!surveyed) {
            // dezenter Punkt signalisiert "unbekannt"
            pSprite.color = Color.argb(90, 40, 46, 36)
            canvas.drawRect(x + 3.5f * u, y + 3.5f * u, x + 4.5f * u, y + 4.5f * u, pSprite)
        } else if (tier == 3) {
            // reicher Boden funkelt dezent
            val tw = kotlin.math.sin(animT * 2.6 + (r * 1.7 + c)) * 0.5 + 0.5
            if (tw > 0.7) {
                val g = cell / 16f
                pSprite.color = Color.argb(220, 255, 250, 220)
                canvas.drawRect(x + 5 * g, y + 6 * g, x + 6 * g, y + 7 * g, pSprite)
            }
        }

        // --- Kueste: Sandstrand (Surf -> feuchter Sand -> trockener Sand) ---
        val t = cell * 0.22f
        val a = t * 0.22f; val b = t * 0.55f
        if (!landSafe(r - 1, c)) {
            pSprite.color = cSurf; canvas.drawRect(x, y, x + cell, y + a, pSprite)
            pSprite.color = cSandWet; canvas.drawRect(x, y + a, x + cell, y + b, pSprite)
            pSprite.color = cSand; canvas.drawRect(x, y + b, x + cell, y + t, pSprite)
        }
        if (!landSafe(r + 1, c)) {
            pSprite.color = cSurf; canvas.drawRect(x, y + cell - a, x + cell, y + cell, pSprite)
            pSprite.color = cSandWet; canvas.drawRect(x, y + cell - b, x + cell, y + cell - a, pSprite)
            pSprite.color = cSand; canvas.drawRect(x, y + cell - t, x + cell, y + cell - b, pSprite)
        }
        if (!landSafe(r, c - 1)) {
            pSprite.color = cSurf; canvas.drawRect(x, y, x + a, y + cell, pSprite)
            pSprite.color = cSandWet; canvas.drawRect(x + a, y, x + b, y + cell, pSprite)
            pSprite.color = cSand; canvas.drawRect(x + b, y, x + t, y + cell, pSprite)
        }
        if (!landSafe(r, c + 1)) {
            pSprite.color = cSurf; canvas.drawRect(x + cell - a, y, x + cell, y + cell, pSprite)
            pSprite.color = cSandWet; canvas.drawRect(x + cell - b, y, x + cell - a, y + cell, pSprite)
            pSprite.color = cSand; canvas.drawRect(x + cell - t, y, x + cell - b, y + cell, pSprite)
        }

        drawDeco(canvas, r, c, x, y)
    }

    private fun decoHash(r: Int, c: Int): Int {
        var h = (r * 92837111) xor (c * 689287499) xor 0x9E3779B
        h = h xor (h ushr 15); h *= -0x7ee3623b; h = h xor (h ushr 13)
        return h and 0x7fffffff
    }

    /** Deko (Baeume/Felsen/Buesche) je Biom – Kategorie kommt aus der Sim, Variante/Groesse random. */
    private fun drawDeco(canvas: Canvas, r: Int, c: Int, x: Float, y: Float) {
        val t = sim.decoType(r, c)
        if (t == 0) return
        val h = decoHash(r, c)
        val bmp: Bitmap = when (t) {
            1 -> decoPines[(h ushr 5) % decoPines.size]
            2 -> decoLeafs[(h ushr 5) % decoLeafs.size]
            3 -> decoRock
            else -> decoBush
        }
        val maxDim = kotlin.math.max(bmp.width, bmp.height).toFloat()
        val sizeF = 0.70f + ((h ushr 11) % 26) / 100f       // 0.70..0.95 zufaellige Groesse
        val scale = cell * sizeF / maxDim
        val dw = bmp.width * scale; val dh = bmp.height * scale
        val jitter = ((h ushr 8) % 5 - 2) * (cell * 0.04f)
        val dx = x + (cell - dw) / 2f + jitter
        val dy = y + cell - dh - cell * 0.05f
        decoDst.set(dx, dy, dx + dw, dy + dh)
        canvas.drawBitmap(bmp, null, decoDst, pTile)
    }

    /** Aufsteigende, ausblendende "+Geld"-Zahlen ueber abgebauten Feldern. */
    private fun drawRises(canvas: Canvas) {
        if (rises.isEmpty()) return
        canvas.save()
        canvas.clipRect(gridLeft, gridTop, gridLeft + gridW, gridTop + gridH)
        val it = rises.iterator()
        while (it.hasNext()) {
            val rr = it.next()
            val age = animT - rr.start
            if (age > 0.9f) { it.remove(); continue }
            val cx = vLeft + (rr.c + 0.5f) * cell
            val cy = vTop + (rr.r + 0.4f) * cell - age * dp(46f)
            val al = (255 * (1f - age / 0.9f)).toInt().coerceIn(0, 255)
            pTextC.textSize = dp(17f)
            pTextC.color = Color.argb((al * 0.75f).toInt().coerceIn(0, 255), 18, 18, 18)
            canvas.drawText(rr.text, cx + dp(1f), cy + dp(1f), pTextC)
            pTextC.color = Color.argb(al, 246, 214, 96)
            canvas.drawText(rr.text, cx, cy, pTextC)
        }
        canvas.restore()
    }

    private fun drawSprite(canvas: Canvas, list: List<Px>, x: Float, y: Float, s: Float) {
        for (px in list) {
            pSprite.color = px.c
            canvas.drawRect(x + px.x * s, y + px.y * s, x + (px.x + px.w) * s, y + (px.y + px.h) * s, pSprite)
        }
    }

    /** Windrad (2 Zellen hoch): Turm-Bitmap + live rotierende Rotorblaetter. */
    private fun drawWindrad(canvas: Canvas, x: Float, topY: Float) {
        dstTile.set(x, topY, x + cell, topY + 2f * cell)
        canvas.drawBitmap(machBmp[MType.WINDRAD.ordinal], null, dstTile, pTile)
        val hx = x + cell * 0.5f
        val hy = topY + cell * 0.44f          // Nabe im oberen Zellbereich
        val bl = cell * 0.92f
        val ang = animT * 80f                 // Grad/s
        val wRoot = cell * 0.055f; val wTip = cell * 0.012f
        for (k in 0 until 3) {
            canvas.save()
            canvas.rotate(ang + k * 120f, hx, hy)
            bladePath.reset()
            bladePath.moveTo(hx - wRoot, hy)
            bladePath.lineTo(hx + wRoot, hy)
            bladePath.lineTo(hx + wTip, hy + bl)
            bladePath.lineTo(hx - wTip, hy + bl)
            bladePath.close()
            canvas.drawPath(bladePath, pBlade)
            canvas.restore()
        }
        p.color = Color.rgb(52, 56, 64)
        canvas.drawCircle(hx, hy, cell * 0.06f, p)
    }

    private fun drawMachine(canvas: Canvas, m: Machine, x: Float, y: Float) {
        // mehrzellige Gebaeude: Anker unten, Sprite ragt nach oben
        val topY = y - (m.h - 1) * cell
        if (m.type == MType.WINDRAD) { drawWindrad(canvas, x, topY); return }

        val hasWear = m.type != MType.REAKTOR && m.type != MType.LAGER &&
            m.type != MType.HAENDLER && m.type != MType.PROSPEKTOR && m.type != MType.WINDRAD
        val pad = cell * 0.08f

        // Basis-Sprite (64x64 Bild-Kachel)
        dstTile.set(x, topY, x + m.w * cell, y + cell)
        canvas.drawBitmap(machBmp[m.type.ordinal], null, dstTile, pTile)

        // Arbeits-Status: blinkende LED oben rechts, wenn die Maschine laeuft
        val working = m.util > 0.02 ||
            m.type == MType.REAKTOR ||
            (m.type == MType.LAGER && (m.output[0] + m.output[1] + m.output[2] + m.output[3]) > 0.5)
        if (working) {
            val blink = (0.45f + 0.55f * (0.5f + 0.5f * kotlin.math.sin(animT * 6f)))
            val ledA = (200 * blink).toInt().coerceIn(0, 255)
            val ld = cell * 0.06f
            pSprite.color = Color.argb(ledA, 190, 255, 150)
            canvas.drawRect(x + cell * 0.74f, y + cell * 0.12f, x + cell * 0.74f + ld, y + cell * 0.12f + ld, pSprite)
        }

        // Verschleiss: braun-roter Schleier bei niedrigem Zustand
        if (hasWear) {
            if (m.condition < 50) {
                pSprite.color = Color.argb(70, 96, 54, 30)
                canvas.drawRect(x + pad, y + pad, x + cell - pad, y + cell - pad, pSprite)
            }
            if (m.condition < 20) {
                val fl = (0.5f + 0.5f * kotlin.math.sin(animT * 9f))
                pSprite.color = Color.argb((90 * fl).toInt().coerceIn(0, 255), 220, 40, 20)
                canvas.drawRect(x + pad, y + pad, x + cell - pad, y + cell - pad, pSprite)
            }
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
        val cols = 6
        val margin = dp(8f)
        val gap = dp(5f)
        val bw = (W - 2 * margin - (cols - 1) * gap) / cols
        val bh = dp(66f)
        for ((i, t) in buildOrder.withIndex()) {
            val col = i % cols
            val row = i / cols
            val x = margin + col * (bw + gap)
            val yy = paletteTop + dp(6f) + row * (bh + gap)
            val rect = RectF(x, yy, x + bw, yy + bh)
            val used = sim.typeCount[t.ordinal]
            val max = sim.maxCount(t)
            val full = used >= max
            val unlocked = sim.canBuild(t) && !full
            val (cres, camt) = sim.buildCost(t)
            val afford = sim.available(cres) >= camt
            val sub: String
            val subCol: Int
            when {
                !sim.canBuild(t) -> { sub = tr("tech_needed"); subCol = cDim }
                full -> { sub = "$used/$max ${tr("full")}"; subCol = cBad }
                else -> { sub = "${camt.toInt()}${resAbbr(cres)} $used/$max"; subCol = if (afford) cDim else cBad }
            }
            val active = buildTool == t
            val mc = mColor(t)
            drawButton(canvas, Btn(rect, "build_${t.name}", mShort(t), unlocked, active, mc, sub, subCol, mc))
            buttons.add(Btn(rect, "build_${t.name}", mShort(t), unlocked))
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
        // eigenes, hoeheres Overlay unten (unabhaengig von der kompakten Palette)
        val top = H - dp(214f)
        p.color = cPanel
        canvas.drawRect(0f, top, W.toFloat(), H.toFloat(), p)
        p.color = cPanelHi
        canvas.drawRect(0f, top, W.toFloat(), top + dp(2f), p)

        pText.color = cAccent; pText.textSize = dp(18f)
        canvas.drawText("${mName(m.type)}  (${selR + 1},${selC + 1})", dp(12f), top + dp(24f), pText)

        pText.color = cText; pText.textSize = dp(14f)
        var yy = top + dp(48f)
        canvas.drawText("${tr("condition")} ${m.condition.roundToInt()}%     ${tr("util")} ${(m.util * 100).roundToInt()}%", dp(12f), yy, pText)
        yy += dp(22f)
        val io = when (m.type) {
            MType.BOHRER -> {
                val floorTxt = if (sim.isSurveyed(selR, selC)) {
                    val tier = sim.richness(selR, selC)
                    "${tierName(tier)} (x${Simulation.ORE_MULT[tier]})"
                } else tr("unscanned")
                "${tr("out")} ${oneDec(m.output[0])} ${tr("roherz")}   ${tr("floor")}: $floorTxt"
            }
            MType.PROSPEKTOR -> "${tr("scans")} (${tr("radius")} ${sim.scanRadius()})"
            MType.WINDRAD -> "${tr("provides")} ${Simulation.WIND_POWER.toInt()} ${tr("strom")} (${tr("wind")})"
            MType.OFEN -> "${tr("in")} ${oneDec(m.input[0])} ${tr("roherz")}   ${tr("out")} ${oneDec(m.output[1])} ${tr("barren")}"
            MType.PRESSE -> "${tr("in")} ${oneDec(m.input[1])} ${tr("barren")}   ${tr("out")} ${oneDec(m.output[2])} ${tr("platten")}"
            MType.ASSEMBLER -> "${tr("in")} ${oneDec(m.input[2])} ${tr("platten")}   ${tr("out")} ${oneDec(m.output[3])} ${tr("komp")}"
            MType.HAENDLER -> "${tr("sells_comp")} (${oneDec(sim.componentPrice())}${tr("per_piece")})"
            MType.GENERATOR -> "${tr("fuel")} ${oneDec(m.input[0])} ${tr("roherz")}  ->  +${Simulation.GEN_POWER.toInt()} ${tr("strom")}"
            MType.LAGER -> "${tr("buffer")} ${oneDec(m.output[0])}E ${oneDec(m.output[1])}B ${oneDec(m.output[2])}P ${oneDec(m.output[3])}K"
            MType.VERSTAERKER -> "${tr("boosts")} (+${(Simulation.BOOST_PER * 100).toInt()}%)"
            MType.REAKTOR -> "${tr("provides")} ${Simulation.REAKTOR_POWER.toInt()} ${tr("strom")} (${tr("fixed")})"
            MType.DROHNE -> "${tr("repairs")} (${Simulation.DROHNE_RATE.toInt()}%/s)"
        }
        canvas.drawText(io, dp(12f), yy, pText)
        yy += dp(22f)

        // Engpass-Ampel: zeigt in Klartext, warum die Maschine (nicht) laeuft
        val code = sim.bottleneck(m, selR, selC)
        val dotR = dp(5f); val dotCx = dp(17f); val dotCy = yy - dp(4f)
        p.color = bnColor(code)
        canvas.drawCircle(dotCx, dotCy, dotR, p)
        p.color = cGridLine; p.style = Paint.Style.STROKE; p.strokeWidth = dp(1f)
        canvas.drawCircle(dotCx, dotCy, dotR, p); p.style = Paint.Style.FILL
        pText.color = bnColor(code); pText.textSize = dp(14f)
        canvas.drawText(bnLabel(code), dp(30f), yy, pText)
        yy += dp(22f)

        pText.color = cDim; pText.textSize = dp(14f)
        val (sres, samt) = sim.buildCost(m.type)
        if (m.type != MType.REAKTOR) {
            val refund = samt * 0.5 * (m.condition / 100.0)
            canvas.drawText("${tr("poweruse")} ${m.type.power.toInt()}     ${tr("sellvalue")} +${oneDec(refund)} ${resAbbr(sres)}", dp(12f), yy, pText)
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
            drawButton(canvas, Btn(rSell, "sell_sel", "${tr("sell")} +$refund ${resAbbr(sres)}", true, false, cBad))
            drawButton(canvas, Btn(rRep, "repair_sel", "${tr("repair")} ${Simulation.REPAIR_COST.toInt()} B", repEnabled, false, cAccent))
            buttons.add(Btn(rSell, "sell_sel", "Verkaufen"))
            buttons.add(Btn(rRep, "repair_sel", "Reparieren", repEnabled))
        } else if (canSell) {
            val rSell = RectF(margin, by, W - margin, by + bh)
            drawButton(canvas, Btn(rSell, "sell_sel", "${tr("sell")} +$refund ${resAbbr(sres)}", true, false, cBad))
            buttons.add(Btn(rSell, "sell_sel", "Verkaufen"))
        }
    }

    private fun bnLabel(code: Int) = when (code) {
        1 -> tr("bn_input"); 2 -> tr("bn_output"); 3 -> tr("bn_power")
        4 -> tr("bn_dead"); 5 -> tr("bn_soil"); else -> tr("bn_ok")
    }

    private fun bnColor(code: Int) = when (code) {
        0 -> cGood
        3, 4 -> cBad
        else -> cWarn
    }

    private fun drawTech(canvas: Canvas) {
        p.color = Color.argb(246, 58, 50, 42)
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
            val cur = if (node.costRes != null) resAbbr(node.costRes) else tr("geld")
            p.color = if (l > 0) Color.rgb(46, 62, 50) else cPanel
            canvas.drawRoundRect(rect, dp(8f), dp(8f), p)

            pText.color = cText; pText.textSize = dp(15f)
            val label = tr(node.id)
            val title = if (node.maxLevel > 1) "$label  (${tr("level")} $l/${node.maxLevel})" else label
            canvas.drawText(title, dp(24f), yy + dp(20f), pText)
            pText.textSize = dp(12f); pText.color = cDim
            val fx = techEffect(node.id)
            val sub = when {
                maxed -> tr("maxed")
                !preOk -> "${tr("requires")}: ${tr(node.prereq!!)}"
                node.maxLevel > 1 -> "$fx  ·  ${tr("next")}: ${cost.toInt()} $cur"
                else -> "${tr("cost")}: ${cost.toInt()} $cur" + (if (fx.isNotEmpty()) "  ·  $fx" else "")
            }
            canvas.drawText(sub, dp(24f), yy + dp(38f), pText)

            val kw = dp(92f); val kh = dp(34f)
            val kr = RectF(W - dp(24f) - kw, yy + (bh - kh) / 2, W - dp(24f), yy + (bh + kh) / 2)
            val kLabel = if (maxed) tr("max") else if (node.maxLevel > 1) tr("buy_level") else tr("buy")
            val kEnabled = !maxed && preOk && afford
            drawButton(canvas, Btn(kr, "buy_${node.id}", kLabel, kEnabled, false, cAccent))
            if (!maxed) buttons.add(Btn(kr, "buy_${node.id}", kLabel, kEnabled))
        }

        // Kopf- und Fussleiste maskieren gescrollte Inhalte
        p.color = Color.rgb(58, 50, 42)
        canvas.drawRect(0f, 0f, W.toFloat(), startY, p)
        canvas.drawRect(0f, bandBottom, W.toFloat(), H.toFloat(), p)
        pText.color = cAccent; pText.textSize = dp(22f)
        canvas.drawText(tr("techtree"), dp(16f), dp(38f), pText)
        pText.color = cDim; pText.textSize = dp(13f)
        canvas.drawText("${tr("geld")} ${fmt(sim.money)}  ·  B ${fmt(sim.availableBarren())}  ·  P ${fmt(sim.availablePlatten())}", dp(16f), dp(58f), pText)

        val cr = RectF(W / 2f - dp(70f), H - dp(56f), W / 2f + dp(70f), H - dp(16f))
        drawButton(canvas, Btn(cr, "close", tr("close"), true, false, cAccent))
        buttons.add(Btn(cr, "close", "Schliessen"))
    }

    private fun techEffect(id: String): String = when (id) {
        "t_assembler" -> tr("tf_assembler"); "t_haendler" -> tr("tf_haendler"); "t_boost" -> tr("tf_boost")
        "t_wind" -> tr("tf_wind")
        "t_diag" -> tr("tf_diag")
        "t_bspeed", "t_ospeed", "t_pspeed", "t_aspeed" -> tr("tf_speed")
        "t_wert" -> tr("tf_wert"); "t_scan" -> tr("tf_scan"); "t_takt" -> tr("tf_takt")
        "t_robust" -> tr("tf_robust"); "t_lift" -> tr("tf_lift"); "t_power" -> tr("tf_power")
        else -> ""
    }

    private fun drawStat(canvas: Canvas) {
        p.color = Color.argb(246, 58, 50, 42)
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        pText.color = cAccent; pText.textSize = dp(22f)
        canvas.drawText(tr("stat_title"), dp(16f), dp(40f), pText)
        pText.color = cText; pText.textSize = dp(15f)
        var yy = dp(74f)
        val lines = listOf(
            "${tr("s_money")}: ${fmt(sim.money)}   (+${fmt(sim.moneyPerMin)}/min)",
            "${tr("s_barren")}: ${fmt(sim.availableBarren())}  (${oneDec(sim.barrenPerMin)}/min)",
            "${tr("s_platten")}: ${fmt(sim.availablePlatten())}  (${oneDec(sim.plattenPerMin)}/min)",
            "${tr("s_komp")}: ${fmt(sim.availableKomponente())}  (${oneDec(sim.komponentenPerMin)}/min)",
            "${tr("s_strom")}: ${fmt(sim.powerSupply)} / ${fmt(sim.powerDemand)}",
            "${tr("s_area")}: ${sim.areaN()} x ${sim.areaN()}",
            "${tr("s_machines")}: ${machineCount()}",
            "${tr("s_upgrades")}: ${sim.tech.values.sum()}"
        )
        for (l in lines) { canvas.drawText(l, dp(16f), yy, pText); yy += dp(25f) }

        // Produktions-Auslastung je Typ (geglaettet) + groesster Engpass
        yy += dp(6f)
        pText.color = cAccent; pText.textSize = dp(16f)
        canvas.drawText(tr("s_prod_title"), dp(16f), yy, pText)
        yy += dp(22f)
        val producers = listOf(MType.BOHRER, MType.OFEN, MType.PRESSE, MType.ASSEMBLER, MType.HAENDLER)
        pText.textSize = dp(13f)
        for (t in producers) {
            val i = t.ordinal
            val cnt = sim.typeCount[i]
            if (cnt == 0) continue
            val u = sim.typeUtil[i].coerceIn(0.0, 1.0)
            pText.color = cText
            canvas.drawText("${mName(t)} x$cnt", dp(20f), yy + dp(11f), pText)
            val barL = dp(150f); val barR = W - dp(70f); val barY = yy + dp(3f); val barH = dp(11f)
            p.color = cGridLine
            canvas.drawRect(barL, barY, barR, barY + barH, p)
            p.color = when { u >= 0.66 -> cGood; u >= 0.33 -> cWarn; else -> cBad }
            canvas.drawRect(barL, barY, barL + (barR - barL) * u.toFloat(), barY + barH, p)
            pText.color = cDim; pText.textAlign = Paint.Align.RIGHT
            canvas.drawText("${(u * 100).roundToInt()}%", W - dp(16f), yy + dp(11f), pText)
            pText.textAlign = Paint.Align.LEFT
            yy += dp(20f)
        }
        val bn = bottleneckSummary()
        pText.color = cDim; pText.textSize = dp(13f)
        yy += dp(2f)
        canvas.drawText("${tr("s_bottleneck")}: $bn", dp(16f), yy, pText)

        yy += dp(20f)
        pText.color = cDim; pText.textSize = dp(12f)
        canvas.drawText(tr("hint_floor"), dp(16f), yy, pText)
        yy += dp(16f)
        canvas.drawText(tr("hint_trade"), dp(16f), yy, pText)

        val margin = dp(12f)
        // Sprachauswahl DE / EN / PL
        pText.color = cText; pText.textSize = dp(14f)
        canvas.drawText("${tr("lang")}:", dp(16f), yy + dp(30f), pText)
        val lw = (W - 2 * margin - dp(80f) - 2 * dp(6f)) / 3f
        val ly = yy + dp(16f); val lh = dp(34f)
        val langs = listOf(Lang.DE to "DE", Lang.EN to "EN", Lang.PL to "PL")
        for ((i, lv) in langs.withIndex()) {
            val lx = dp(80f) + margin + i * (lw + dp(6f))
            val lr = RectF(lx, ly, lx + lw, ly + lh)
            drawButton(canvas, Btn(lr, "lang_${lv.first.name}", lv.second, true, I18n.lang == lv.first, cAccent))
            buttons.add(Btn(lr, "lang_${lv.first.name}", "lang"))
        }

        // Ton-Einstellungen: Musik + Effekte, je vier Stufen
        var sy = ly + lh + dp(12f)
        sy = drawSoundRow(canvas, "snd_music", (audio.musicVol * 100).roundToInt(), "mvol", sy, margin)
        sy = drawSoundRow(canvas, "snd_sfx", (audio.sfxVol * 100).roundToInt(), "svol", sy, margin)

        // Hauptmenue
        val menuR = RectF(margin, H - dp(104f), W - margin, H - dp(104f) + dp(38f))
        drawButton(canvas, Btn(menuR, "to_menu", tr("to_menu"), true, false, cAccent))
        buttons.add(Btn(menuR, "to_menu", "menu"))

        // Reset + Schliessen
        val by = H - dp(58f); val bh = dp(40f)
        val half = (W - 3 * margin) / 2f
        val rReset = RectF(margin, by, margin + half, by + bh)
        val rClose = RectF(margin * 2 + half, by, margin * 2 + half * 2, by + bh)
        drawButton(canvas, Btn(rReset, "reset", if (resetArmed) tr("reset_confirm") else tr("reset"), true, resetArmed, cBad))
        drawButton(canvas, Btn(rClose, "close", tr("close"), true, false, cAccent))
        buttons.add(Btn(rReset, "reset", "reset"))
        buttons.add(Btn(rClose, "close", "Schliessen"))
    }

    private val sndLevels = intArrayOf(0, 33, 66, 100)

    private fun nearestLevel(cur: Int): Int {
        var bi = 0; var bd = Int.MAX_VALUE
        for (i in sndLevels.indices) { val d = kotlin.math.abs(sndLevels[i] - cur); if (d < bd) { bd = d; bi = i } }
        return bi
    }

    /** Eine Ton-Zeile (Label + 4 Stufen-Buttons); gibt das neue Y zurueck. */
    private fun drawSoundRow(canvas: Canvas, labelKey: String, cur: Int, idPrefix: String, y: Float, margin: Float): Float {
        pText.color = cText; pText.textSize = dp(14f); pText.textAlign = Paint.Align.LEFT
        canvas.drawText(tr(labelKey), dp(16f), y + dp(2f), pText)
        val by = y + dp(8f); val bh = dp(34f)
        val gap = dp(6f)
        val bw = (W - 2 * margin - 3 * gap) / 4f
        val lbls = listOf(tr("snd_off"), tr("snd_low"), tr("snd_mid"), tr("snd_high"))
        val active = nearestLevel(cur)
        for (i in 0 until 4) {
            val bx = margin + i * (bw + gap)
            val r = RectF(bx, by, bx + bw, by + bh)
            drawButton(canvas, Btn(r, "${idPrefix}_${sndLevels[i]}", lbls[i], true, active == i, cAccent))
            buttons.add(Btn(r, "${idPrefix}_${sndLevels[i]}", "snd"))
        }
        return by + bh + dp(12f)
    }

    private fun drawReport(canvas: Canvas) {
        val rep = report ?: return
        p.color = Color.argb(248, 58, 50, 42)
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        pText.color = cAccent; pText.textSize = dp(22f)
        canvas.drawText(tr("report_title"), dp(16f), dp(40f), pText)
        pText.color = cText; pText.textSize = dp(15f)
        val away = fmtDur(rep.elapsedSeconds)
        val simd = fmtDur(rep.simSeconds)
        canvas.drawText("${tr("away")}: $away  ·  ${tr("sim")}: $simd  ·  Cap 8h", dp(16f), dp(66f), pText)
        pText.color = cGood
        canvas.drawText("+ ${fmt(rep.moneyGained)} ${tr("geld")}   + ${fmt(rep.barrenGained)} ${tr("barren")}   + ${fmt(rep.plattenGained)} ${tr("platten")}", dp(16f), dp(90f), pText)

        pText.color = cText; pText.textSize = dp(14f)
        canvas.drawText("${tr("timeline")}:", dp(16f), dp(120f), pText)
        var yy = dp(142f)
        if (rep.events.isEmpty()) {
            pText.color = cDim
            canvas.drawText(tr("all_ran"), dp(24f), yy, pText)
        } else {
            pText.textSize = dp(13f)
            for (e in rep.events) {
                if (yy > H - dp(90f)) break
                pText.color = cDim
                canvas.drawText(fmtDur(e.timeSec), dp(24f), yy, pText)
                pText.color = cText
                val evText = "${mName(e.mType)} (${e.r + 1},${e.c + 1}): ${if (e.dead) tr("ev_dead") else tr("ev_starved")}"
                canvas.drawText(evText, dp(96f), yy, pText)
                yy += dp(22f)
            }
        }
        val cr = RectF(W / 2f - dp(90f), H - dp(64f), W / 2f + dp(90f), H - dp(22f))
        drawButton(canvas, Btn(cr, "close", tr("continue"), true, false, cAccent))
        buttons.add(Btn(cr, "close", "Weiterspielen"))
    }

    private fun drawMenu(canvas: Canvas) {
        // Kopfbanner
        p.color = cPanel
        canvas.drawRect(0f, 0f, W.toFloat(), dp(104f), p)
        p.color = cAccent
        canvas.drawRect(0f, dp(102f), W.toFloat(), dp(104f), p)
        pText.textAlign = Paint.Align.LEFT
        pText.color = cAccent; pText.textSize = dp(34f)
        canvas.drawText("SCHACHT", dp(16f), dp(52f), pText)
        pText.color = cDim; pText.textSize = dp(15f)
        canvas.drawText(tr("choose_save"), dp(16f), dp(82f), pText)

        val margin = dp(12f)
        val slots = menuSlots
        var yy = dp(124f)
        val cardH = dp(74f)
        val maxY = H - dp(150f)   // Platz fuer Backup-Zeile + Neues Spiel
        for (s in slots) {
            if (yy + cardH > maxY) break
            val rect = RectF(margin, yy, W - margin, yy + cardH)
            p.color = cPanel
            canvas.drawRoundRect(rect, dp(10f), dp(10f), p)
            p.color = Color.argb(60, 255, 255, 255)
            canvas.drawRect(rect.left + dp(8f), rect.top + dp(2f), rect.right - dp(8f), rect.top + dp(3.5f), p)
            // farbiger Streifen links
            p.color = cAccent
            canvas.drawRect(rect.left + dp(4f), rect.top + dp(10f), rect.left + dp(8f), rect.bottom - dp(10f), p)

            pText.color = cText; pText.textSize = dp(19f)
            canvas.drawText(s.name, margin + dp(18f), yy + dp(28f), pText)
            pText.color = cDim; pText.textSize = dp(13f)
            val info = "${tr("geld")} ${fmt(s.money)}  ·  ${s.machines} ${tr("mach_short")}  ·  ${fmtAgo(s.savedAt)}"
            canvas.drawText(info, margin + dp(18f), yy + dp(52f), pText)

            // ganze Karte = weiterspielen
            buttons.add(Btn(rect, "open_${s.id}", s.name))
            // Loeschen rechts (zweistufig)
            val dw = dp(70f); val dh = dp(36f)
            val dr = RectF(W - margin - dw - dp(6f), yy + (cardH - dh) / 2, W - margin - dp(6f), yy + (cardH + dh) / 2)
            val armed = menuArmedDelete == s.id
            drawButton(canvas, Btn(dr, "del_${s.id}", if (armed) tr("del_confirm") else tr("delete"), true, armed, cBad))
            buttons.add(Btn(dr, "del_${s.id}", "del"))
            yy += cardH + dp(8f)
        }
        if (slots.isEmpty()) {
            pText.color = cDim; pText.textSize = dp(15f)
            canvas.drawText(tr("no_saves"), dp(18f), yy + dp(10f), pText)
            yy += dp(28f)
        }

        // Backup speichern / laden (ueberlebt Deinstallation)
        val bkY = H - dp(126f); val bkH = dp(40f)
        val bhalf = (W - 3 * margin) / 2f
        val bSave = RectF(margin, bkY, margin + bhalf, bkY + bkH)
        val bLoad = RectF(margin * 2 + bhalf, bkY, margin * 2 + bhalf * 2, bkY + bkH)
        drawButton(canvas, Btn(bSave, "backup_save", tr("backup_save"), true, false, cAccent))
        drawButton(canvas, Btn(bLoad, "backup_load", tr("backup_load"), true, false, cAccent))
        buttons.add(Btn(bSave, "backup_save", "bs"))
        buttons.add(Btn(bLoad, "backup_load", "bl"))

        // Neues Spiel
        val nr = RectF(margin, H - dp(74f), W - margin, H - dp(74f) + dp(54f))
        drawButton(canvas, Btn(nr, "new_slot", "+ ${tr("new_game")}", true, false, cGood))
        buttons.add(Btn(nr, "new_slot", "new"))
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
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        val slop = dp(8f)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; downScroll = techScroll
                lastPanX = panX; lastPanY = panY; moved = false
                downInGrid = downX >= gridLeft && downX <= gridLeft + gridW &&
                    downY >= gridTop && downY <= gridTop + gridH
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.abs(event.x - downX) > slop || kotlin.math.abs(event.y - downY) > slop) moved = true
                // Karte-Scrollen laeuft ueber gestureDetector.onScroll.
                if (screen == Screen.TECH) {
                    techScroll = (downScroll - (event.y - downY)).coerceIn(0f, techMaxScroll)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!moved && !scaleDetector.isInProgress) handleClick(event.x, event.y)
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
        if (screen == Screen.MENU) { if (menuArmedDelete != null) { menuArmedDelete = null; invalidate() }; return }
        if (screen != Screen.GAME) return
        val an = sim.areaN()
        if (x >= gridLeft && x < gridLeft + gridW && y >= gridTop && y < gridTop + gridH) {
            val c = ((x - vLeft) / cell).toInt().coerceIn(0, an - 1)
            val r = ((y - vTop) / cell).toInt().coerceIn(0, an - 1)
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
            id.startsWith("mvol_") -> setMusicVol(id.removePrefix("mvol_").toInt())
            id.startsWith("svol_") -> setSfxVol(id.removePrefix("svol_").toInt())
            id == "to_menu" -> { persist(); resetArmed = false; menuArmedDelete = null; selR = -1; refreshMenu(); screen = Screen.MENU; audio.click() }
            id == "new_slot" -> { startNewSlot(); menuArmedDelete = null; audio.place() }
            id == "backup_save" -> { (context as? MainActivity)?.startBackupExport(); audio.click() }
            id == "backup_load" -> { (context as? MainActivity)?.startBackupImport(); audio.click() }
            id.startsWith("open_") -> { openSlot(id.removePrefix("open_")); menuArmedDelete = null; audio.click() }
            id.startsWith("del_") -> {
                val sid = id.removePrefix("del_")
                if (menuArmedDelete != sid) { menuArmedDelete = sid; audio.click() }
                else {
                    saveStore.deleteSlot(sid); menuArmedDelete = null
                    if (currentSlot == sid) currentSlot = null
                    refreshMenu(); audio.sell()
                }
            }
            id.startsWith("lang_") -> { setLang(Lang.valueOf(id.removePrefix("lang_"))) }
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
        val a = sim.anchorOf(r, c)
        if (a != null) {
            selR = a[0]; selC = a[1]; audio.click()   // auch belegte Zelle eines Gebaeudes waehlt den Anker
        } else {
            val t = buildTool
            if (t != null) {
                if (sim.build(t, r, c)) { selR = -1; selC = -1; audio.place() } else audio.error()
            } else {
                // freies Feld ohne Werkzeug: Deko (Baum/Busch/Fels) fuer Geld abbauen
                val earned = sim.harvest(r, c)
                if (earned > 0) { rises.add(Rise(r, c, "+$earned", animT)); audio.sell() }
                else { selR = -1; selC = -1 }
            }
        }
        invalidate()
    }

    /** Zaehlt die Engpass-Ursachen ueber alle Maschinen und benennt die haeufigste. */
    private fun bottleneckSummary(): String {
        val counts = IntArray(6)
        var total = 0
        for (r in 0 until sim.n) for (c in 0 until sim.n) {
            val m = sim.grid[r][c] ?: continue
            if (m.type == MType.REAKTOR || m.type == MType.LAGER || m.type == MType.VERSTAERKER || m.type == MType.DROHNE) continue
            val code = sim.bottleneck(m, r, c)
            counts[code]++
            if (code != 0) total++
        }
        if (total == 0) return tr("bn_none")
        var worst = 1
        for (i in 1 until 6) if (counts[i] > counts[worst]) worst = i
        return "${bnLabel(worst)} (${counts[worst]})"
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

    private fun fmtAgo(millis: Long): String {
        if (millis <= 0L) return tr("ago_new")
        val sec = ((System.currentTimeMillis() - millis) / 1000L).toInt().coerceAtLeast(0)
        return when {
            sec < 60 -> tr("ago_now")
            sec < 3600 -> "${tr("ago_pre")}${sec / 60}m"
            sec < 86400 -> "${tr("ago_pre")}${sec / 3600}h"
            else -> "${tr("ago_pre")}${sec / 86400}d"
        }
    }

    private fun fmtDur(sec: Int): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) "${h}h ${m}m" else if (m > 0) "${m}m ${s}s" else "${s}s"
    }

}
