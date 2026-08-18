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

    private enum class Screen { MENU, GAME, TECH, STAT, REPORT, COMPANY, LOADING }
    private var screen = Screen.MENU

    private val saveStore = SaveStore(context)
    private var currentSlot: String? = null
    private var menuArmedDelete: String? = null   // Slot, dessen Loeschen bestaetigt werden muss
    private var menuSlots: List<SlotInfo> = emptyList()   // gecachte Liste fuers Menue
    private fun refreshMenu() { menuSlots = saveStore.slots() }

    // Zuschauer-Modus: anderes Unternehmen (Spielstand-Slot) ansehen, ohne etwas daran
    // aendern zu koennen. viewOnlyId = die Slot-ID, die gerade betrachtet wird (null =
    // normales eigenes Spiel). ownSnapshot haelt den EIGENEN Spielstand fest, waehrend
    // "sim" voruebergehend mit den fremden Daten befuellt ist, damit persist() niemals
    // versehentlich die fremden Daten in den eigenen Slot schreibt.
    private var viewOnlyId: String? = null
    private var ownSnapshot: String? = null
    private val viewOnly: Boolean get() = viewOnlyId != null

    private fun enterViewOnly(id: String) {
        if (id == currentSlot) return
        val blob = saveStore.loadState(id) ?: return
        if (viewOnlyId == null) ownSnapshot = sim.toJson(System.currentTimeMillis())
        try {
            sim.fromJson(blob)
            viewOnlyId = id
            selR = -1; selC = -1; buildTool = null
            needCenter = true
            screen = Screen.GAME
            audio.click()
        } catch (_: Exception) { audio.error() }
    }

    private fun exitViewOnly() {
        val snap = ownSnapshot
        viewOnlyId = null; ownSnapshot = null
        if (snap != null) { try { sim.fromJson(snap) } catch (_: Exception) { } }
        selR = -1; selC = -1; buildTool = null
        needCenter = true
        audio.click()
    }

    private var selR = -1
    private var selC = -1
    private var report: OfflineReport? = null
    private var resetArmed = false
    private var sellArmed = false

    private var techScroll = 0f
    private var techMaxScroll = 0f
    private var statScroll = 0f
    private var statMaxScroll = 0f
    private var downX = 0f
    private var downY = 0f
    private var downScroll = 0f
    private var downStatScroll = 0f
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
    private val cResForsch = Color.rgb(186, 158, 244)

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
        MType.SOLAR -> Color.rgb(96, 150, 220)
        MType.FORSCHUNG -> Color.rgb(150, 130, 224)
        MType.BLEIBOHRER -> Color.rgb(140, 146, 166)
        MType.WASSERPUMPE -> Color.rgb(90, 170, 224)
        MType.ZENTRIFUGE -> Color.rgb(120, 210, 232)
        MType.BLEIPRESSE -> Color.rgb(110, 116, 136)
        MType.BRENNSTABWERK -> Color.rgb(236, 196, 88)
        MType.REAKTORKERN -> Color.rgb(96, 190, 236)
        MType.KUEHLTURM -> Color.rgb(180, 178, 172)
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
    private lateinit var bmpOilRig: Bitmap        // 192x192, 3x3 dekorative Oel-Bohrinsel
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
    // Bau-Palette ein-/ausklappbar ueber einen schwebenden Button (drawPaletteToggle);
    // Zustand wird gemerkt, damit er nach Neustart erhalten bleibt.
    private var paletteCollapsed = false

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
        // Ab Level 2 gibt es keinen Reaktor mehr - dann auf die Plattform zentrieren.
        if (sim.hasPlatform()) return (sim.platformR0 + sim.platformR1) / 2 to (sim.platformC0 + sim.platformC1) / 2
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

    // Button-Druck-Feedback (Juice): gedrueckte Button-ID + Zeitpunkt.
    private var pressedBtn: String? = null

    // Bau-Staubwolken (kurzer Effekt beim Platzieren).
    private class Puff(val r: Int, val c: Int, val start: Float)
    private val puffs = ArrayList<Puff>()

    // Aufsteigende "+Geld"-Zahlen beim Abbauen von Hindernissen.
    private class Rise(val r: Int, val c: Int, val text: String, val start: Float)
    private val rises = ArrayList<Rise>()

    // Stein-/Boden-Farben fuer den Hintergrund
    private val cGroundBase = Color.rgb(43, 39, 35)
    private val cGroundDark = Color.rgb(33, 30, 26)
    private val cGroundLite = Color.rgb(55, 50, 44)
    private val cGroundRust = Color.rgb(61, 37, 30)

    private val buildOrderLvl1 = listOf(
        MType.BOHRER, MType.OFEN, MType.PRESSE, MType.ASSEMBLER,
        MType.HAENDLER, MType.GENERATOR, MType.WINDRAD, MType.SOLAR,
        MType.LAGER, MType.DROHNE, MType.FORSCHUNG
    )
    private val buildOrderLvl2 = listOf(
        MType.BOHRER, MType.BLEIBOHRER, MType.WASSERPUMPE, MType.ZENTRIFUGE, MType.BLEIPRESSE, MType.BRENNSTABWERK,
        MType.REAKTORKERN, MType.KUEHLTURM, MType.HAENDLER, MType.GENERATOR, MType.WINDRAD, MType.SOLAR,
        MType.LAGER, MType.DROHNE, MType.FORSCHUNG
    )
    /** Bau-Palette haengt vom Unternehmens-Level ab ("komplett andere placeable items" ab Level 2). */
    private fun buildOrderFor(level: Int): List<MType> = if (level >= 2) buildOrderLvl2 else buildOrderLvl1
    private val paletteCols = 6

    private fun resAbbr(res: Res) = when (res) {
        Res.ROHERZ -> "E"; Res.BARREN -> "B"; Res.PLATTE -> "P"; Res.KOMPONENTE -> "K"
        Res.WASSER -> "W"; Res.BLEI -> "Pb"; Res.DAMPF -> "D"; Res.STROM -> "St"
    }
    private fun tierName(t: Int) = I18n.t("tier$t")
    private fun mName(t: MType): String {
        if (t == MType.BOHRER && sim.companyLevel >= 2) return tr("m_uranbohrer")
        return I18n.t("m_" + t.name.lowercase())
    }
    private fun mShort(t: MType): String {
        if (t == MType.BOHRER && sim.companyLevel >= 2) return tr("ms_uranbohrer")
        return I18n.t("ms_" + t.name.lowercase())
    }
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
        bmpOilRig = ld(R.drawable.deco_oilrig)
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
                MType.SOLAR -> R.drawable.mach_solar
                MType.FORSCHUNG -> R.drawable.mach_research
                MType.BLEIBOHRER -> R.drawable.mach_bleibohrer
                MType.WASSERPUMPE -> R.drawable.mach_wasserpumpe
                MType.ZENTRIFUGE -> R.drawable.mach_zentrifuge
                MType.BLEIPRESSE -> R.drawable.mach_bleipresse
                MType.BRENNSTABWERK -> R.drawable.mach_brennstabwerk
                MType.REAKTORKERN -> R.drawable.mach_reaktorkern
                MType.KUEHLTURM -> R.drawable.mach_kuehlturm
            })
        }
        I18n.lang = try { Lang.values()[prefs.getInt("lang", Lang.EN.ordinal)] } catch (_: Exception) { Lang.EN }
        audio.setMusicVol(prefs.getInt("musicVol", 50) / 100f)
        audio.setSfxVol(prefs.getInt("sfxVol", 33) / 100f)
        paletteCollapsed = prefs.getBoolean("paletteCollapsed", false)
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
        // Im Zuschauer-Modus enthaelt "sim" gerade FREMDE Firmendaten - niemals in den
        // eigenen Slot schreiben, egal von wo persist() aufgerufen wird.
        if (viewOnly) return
        val slot = currentSlot ?: return
        try {
            saveStore.saveState(slot, sim.toJson(System.currentTimeMillis()))
        } catch (_: Exception) { }
    }

    // Fortschritt (0..1) des Offline-Nachrechnens, waehrend Screen.LOADING aktiv ist.
    private var loadingProgress = 0f

    /**
     * Einen gespeicherten Slot laden und (bei Bedarf) Offline-Fortschritt zeigen. Das
     * Nachrechnen laesst sich bei langer Abwesenheit spuerbar hinziehen - laeuft daher
     * auf einem Hintergrund-Thread, waehrend Screen.LOADING jede Eingabe blockiert und
     * einen Ladebalken zeigt (kommt aus sim.runOffline()s onProgress-Callback).
     */
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
                loadingProgress = 0f
                screen = Screen.LOADING
                invalidate()
                Thread {
                    val result = sim.runOffline(elapsed) { p ->
                        handler.post { loadingProgress = p; invalidate() }
                    }
                    handler.post {
                        report = result
                        screen = Screen.REPORT
                        invalidate()
                    }
                }.start()
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

    /**
     * Palettenhoehe (Zeilenzahl) haengt von der Anzahl der Bau-Items im aktuellen Level
     * ab - Level 2 hat mehr Maschinentypen als Level 1 und braucht daher mehr Zeilen,
     * sonst fallen die letzten Kacheln (z.B. Lager/Drohne/Forschung) aus dem festen
     * 2-Zeilen-Raster heraus und sind unsichtbar/nicht antippbar. Wird jeden Frame neu
     * berechnet (nicht nur bei onSizeChanged), damit ein Level-Aufstieg MITTEN in einer
     * laufenden Sitzung (ohne Bildschirm-Resize) sofort die richtige Zeilenzahl bekommt.
     */
    private fun updatePaletteLayout() {
        val items = buildOrderFor(sim.companyLevel).size
        val palRows = ((items + paletteCols - 1) / paletteCols).coerceAtLeast(1)
        val palBh = dp(66f); val palGap = dp(5f)
        // Eingeklappt braucht die Palette selbst GAR KEINEN Platz mehr (der Auf-/Zuklapp-
        // Button schwebt separat ueber der Karte, siehe drawPaletteToggle()).
        paletteH = if (paletteCollapsed) 0f else palRows * palBh + (palRows - 1) * palGap + dp(8f)
        paletteTop = H - paletteH
    }

    /**
     * Kartenfenster-Hoehe: haengt davon ab, was GERADE unten gezeigt wird (Palette,
     * Detail-Panel einer Maschine oder das Infrastruktur-Ausbau-Panel) - nicht mehr
     * pauschal von der Palette allein. Sonst blieb (wenn z.B. ein kuerzeres Detail-Panel
     * statt der Palette gezeigt wurde) eine unnoetige graue Luecke zwischen Karte und
     * Panel stehen, weil die Karte weiterhin nur bis zur (oft hoeheren) Paletten-Kante
     * geclippt wurde.
     */
    private fun updateGridViewport() {
        val bottomTop = if (screen != Screen.GAME) H.toFloat() else when {
            selR >= 0 && sim.grid[selR][selC] != null -> H - dp(214f) - detailExtraH(sim.grid[selR][selC]!!)
            selR >= 0 && sim.canExpandInfra(selR, selC) -> expandPanelTop()
            else -> paletteTop
        }
        gridH = bottomTop - gridTop - dp(4f)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        W = w; H = h
        headerH = dp(96f)
        val margin = dp(8f)
        gridLeft = margin
        gridTop = headerH + dp(4f)
        gridW = w - 2 * margin
        updatePaletteLayout()
        cell = gridW / visibleAt1
        needCenter = true
    }

    // ---------------- Rendering ----------------

    override fun onDraw(canvas: Canvas) {
        buttons.clear()
        if (W > 0 && H > 0) { updatePaletteLayout(); updateGridViewport() }   // an aktuellen Zustand anpassen
        updateView()   // setzt cell/vLeft/vTop aus Zoom & Pan
        canvas.drawColor(cBg)
        if (screen == Screen.MENU) { drawMenu(canvas); return }
        // Waehrend des Offline-Nachrechnens (Hintergrund-Thread) NICHT auf sim.* zugreifen.
        if (screen == Screen.LOADING) { drawLoading(canvas); return }
        drawHeader(canvas)
        drawGrid(canvas)
        if (screen == Screen.GAME) { drawPuffs(canvas); drawRises(canvas) }
        if (screen == Screen.GAME) drawPowerPulse(canvas)
        if (screen == Screen.GAME) {
            when {
                selR >= 0 && sim.grid[selR][selC] != null -> drawDetail(canvas)
                selR >= 0 && sim.canExpandInfra(selR, selC) -> drawExpandPanel(canvas)
                else -> { drawPalette(canvas); drawPaletteToggle(canvas) }
            }
        }
        when (screen) {
            Screen.COMPANY -> drawCompany(canvas)
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

    // Helle, kontrastreiche Ressourcenfarben fuer die Top-Bar
    private val cValSilver = Color.rgb(214, 220, 232)
    private val cValCyan = Color.rgb(120, 200, 234)
    private val cValPurple = Color.rgb(196, 168, 244)

    private fun drawHeader(canvas: Canvas) {
        // Konsolen-Panel (skeuomorph): Grund + heller Grat oben + Nietenreihe + Akzentkante unten
        p.color = cPanel
        canvas.drawRect(0f, 0f, W.toFloat(), headerH, p)
        p.color = cPanelHi
        canvas.drawRect(0f, 0f, W.toFloat(), dp(2f), p)
        p.color = Color.argb(60, 0, 0, 0)
        canvas.drawRect(0f, headerH - dp(4f), W.toFloat(), headerH - dp(2f), p)
        p.color = cAccent
        canvas.drawRect(0f, headerH - dp(2f), W.toFloat(), headerH, p)
        // Nieten
        p.color = Color.argb(90, 210, 216, 228)
        var rx = dp(6f)
        while (rx < W - dp(100f)) { canvas.drawCircle(rx, dp(5f), dp(1.1f), p); rx += dp(16f) }

        pText.textAlign = Paint.Align.LEFT

        // Reihe 1: Geld gross mit Muenz-Icon + Einkommen klein
        drawIcon(canvas, Sprites.ICON_GELD, dp(9f), dp(9f), dp(18f))
        pText.textSize = dp(20f); pText.color = cResGeld
        val moneyStr = fmt(sim.money)
        canvas.drawText(moneyStr, dp(32f), dp(25f), pText)
        val moneyW = pText.measureText(moneyStr)
        pText.textSize = dp(12f); pText.color = cGood
        canvas.drawText("+${fmt(sim.moneyPerMin)}/min", dp(32f) + moneyW + dp(8f), dp(25f), pText)

        // Forschungswaehrung als Chip rechts (vor den Menue-Buttons)
        val fStr = "◆ ${fmt(sim.research)}"
        pText.textSize = dp(16f)
        val fW = pText.measureText(fStr)
        pText.textAlign = Paint.Align.RIGHT
        pText.color = cResForsch
        canvas.drawText(fStr, W - dp(102f), dp(24f), pText)
        pText.textAlign = Paint.Align.LEFT

        // Reihe 2: Rohstoffe hell & kontrastreich
        pText.textSize = dp(14f)
        drawIcon(canvas, Sprites.iconForRes(Res.BARREN.ordinal, sim.companyLevel), dp(9f), dp(37f), dp(15f))
        pText.color = cValSilver; canvas.drawText(fmt(sim.availableBarren()), dp(28f), dp(47f), pText)
        drawIcon(canvas, Sprites.iconForRes(Res.PLATTE.ordinal, sim.companyLevel), dp(108f), dp(37f), dp(15f))
        pText.color = cValCyan; canvas.drawText(fmt(sim.availablePlatten()), dp(127f), dp(47f), pText)
        drawIcon(canvas, if (sim.companyLevel >= 2) Sprites.ICON_STROM else Sprites.ICON_KOMP, dp(196f), dp(37f), dp(15f))
        pText.color = cValPurple
        canvas.drawText(fmt(if (sim.companyLevel >= 2) sim.availableStrom() else sim.availableKomponente()), dp(215f), dp(47f), pText)

        // Reihe 3: Strom-Konsole (Chip, gruen=Ueberschuss / rot=Mangel)
        val powOk = sim.powerDemand <= sim.powerSupply + 1e-6
        val rest = sim.powerSupply - sim.powerDemand
        drawIcon(canvas, Sprites.ICON_STROM, dp(9f), dp(59f), dp(15f))
        val chip = RectF(dp(28f), dp(57f), dp(150f), dp(74f))
        p.color = if (powOk) Color.argb(55, 90, 200, 120) else Color.argb(70, 224, 84, 72)
        canvas.drawRoundRect(chip, dp(4f), dp(4f), p)
        p.color = if (powOk) cGood else cBad; p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.3f)
        canvas.drawRoundRect(chip, dp(4f), dp(4f), p); p.style = Paint.Style.FILL
        pText.color = if (powOk) cGood else cBad
        canvas.drawText("${tr("reststrom")} ${if (rest < 0) "-" + fmt(-rest) else "+" + fmt(rest)}", dp(34f), dp(70f), pText)
        pText.color = cDim; pText.textSize = dp(12f)
        canvas.drawText("${fmt(sim.powerSupply)}/${fmt(sim.powerDemand)}", dp(158f), dp(70f), pText)

        // --- Firmenleiste: Level + Fortschritt zum Verkaufsziel (oder, im Zuschauer-
        // Modus, ein deutlicher Hinweis + direkter Rueckweg zur eigenen Firma) ---
        run {
            val cy0 = dp(78f); val cy1 = dp(93f)
            if (viewOnly) {
                val cRect = RectF(0f, cy0 - dp(2f), W.toFloat(), cy1 + dp(1f))
                p.color = Color.argb(90, 224, 84, 72)
                canvas.drawRect(cRect, p)
                pText.textSize = dp(11.5f); pText.textAlign = Paint.Align.LEFT; pText.color = cBad
                canvas.drawText("👁 ${tr("viewonly_badge")}", dp(8f), cy1 - dp(3f), pText)
                pText.textAlign = Paint.Align.RIGHT; pText.color = cText
                canvas.drawText("◂ ${tr("exit_viewonly")}", W - dp(8f), cy1 - dp(3f), pText)
                pText.textAlign = Paint.Align.LEFT
                buttons.add(Btn(cRect, "exit_viewonly", "exit"))
                return@run
            }
            val ready = sim.canSellCompany()
            val pulse = 0.5f + 0.5f * kotlin.math.sin(animT * 4f)
            val barL = dp(96f); val barR = W - dp(96f)
            p.color = Color.argb(70, 0, 0, 0)
            canvas.drawRect(0f, cy0 - dp(2f), W.toFloat(), cy1 + dp(1f), p)
            pText.textSize = dp(11.5f); pText.textAlign = Paint.Align.LEFT
            pText.color = if (ready) cAccent else cText
            canvas.drawText("LVL ${sim.companyLevel} · ${tr("lvl" + sim.companyLevel.coerceAtMost(4))}", dp(8f), cy1 - dp(3f), pText)
            // Fortschrittsbalken
            val prog = sim.levelProgress().toFloat()
            p.color = cGridLine; canvas.drawRect(barL, cy0 + dp(1f), barR, cy1 - dp(3f), p)
            p.color = if (ready) Color.argb((160 + 95 * pulse).toInt().coerceIn(0, 255), 246, 200, 98)
                      else Color.rgb(96, 170, 220)
            canvas.drawRect(barL, cy0 + dp(1f), barL + (barR - barL) * prog, cy1 - dp(3f), p)
            pText.textAlign = Paint.Align.RIGHT
            pText.color = if (ready) cAccent else cDim
            val label = if (ready) "▸ ${tr("sell_company")}" else "${fmt(sim.money)} / ${fmt(sim.levelGoal())}"
            canvas.drawText(label, W - dp(8f), cy1 - dp(3f), pText)
            pText.textAlign = Paint.Align.LEFT
            if (screen == Screen.GAME) {
                val cRect = RectF(0f, cy0 - dp(2f), W.toFloat(), cy1 + dp(1f))
                buttons.add(Btn(cRect, "company", "company"))
            }
        }

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
        drawOilRig(canvas)
        // Kuehlschlauch (unter den Maschinen)
        if (screen == Screen.GAME) drawHose(canvas)
        if (screen == Screen.GAME) drawWasserpumpePipes(canvas)
        // Pass 2: Maschinen (Rand erweitern: Gebaeude ragen bis 2 Zellen nach oben/rechts)
        val mr1 = (r1 + 2).coerceIn(0, an - 1)
        val mc0 = (c0 - 2).coerceIn(0, an - 1)
        for (r in r0..mr1) for (c in mc0..c1) {
            val m = sim.grid[r][c] ?: continue
            drawMachine(canvas, m, vLeft + c * cell, vTop + r * cell)
        }
        // Drohnen ueber allen Maschinen (fliegen zwischen Station und Zielen)
        if (screen == Screen.GAME) drawDrones(canvas)
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

    /**
     * Oel-Bohrinsel (3x3, rein dekorativ): steht auf offenem Wasser, kein Machine-
     * Objekt, keine Interaktion - nur ein "Spoiler" fuers naechste Unternehmens-Level.
     */
    private fun drawOilRig(canvas: Canvas) {
        if (!sim.hasOilRig()) return
        val x = vLeft + sim.oilRigC0 * cell
        val y = vTop + sim.oilRigR0 * cell
        val w = (sim.oilRigC1 - sim.oilRigC0 + 1) * cell
        val h = (sim.oilRigR1 - sim.oilRigR0 + 1) * cell
        dstTile.set(x, y, x + w, y + h)
        canvas.drawBitmap(bmpOilRig, null, dstTile, pTile)
    }

    // Welchen Rohstoff gibt ein Produzent aus / will ein Verbraucher.
    private fun offersRes(t: MType): Int = when (t) {
        MType.BOHRER -> Res.ROHERZ.ordinal
        MType.OFEN -> Res.BARREN.ordinal
        MType.PRESSE -> Res.PLATTE.ordinal
        MType.ASSEMBLER -> Res.KOMPONENTE.ordinal
        MType.BLEIBOHRER -> Res.BLEI.ordinal
        MType.WASSERPUMPE -> Res.WASSER.ordinal
        MType.ZENTRIFUGE -> Res.BARREN.ordinal
        MType.BLEIPRESSE -> Res.PLATTE.ordinal
        MType.BRENNSTABWERK -> Res.KOMPONENTE.ordinal
        MType.REAKTORKERN -> Res.DAMPF.ordinal
        MType.KUEHLTURM -> Res.STROM.ordinal
        else -> -1
    }
    // IntArray statt Einzelwert: manche Maschinen wollen ZWEI Rohstoffe gleichzeitig
    // (Zentrifuge: Uranerz+Wasser; Brennstabwerk: Angereichertes Uran+Blei-Verkleidung) -
    // mit nur einem Rueckgabewert wurde die zweite Zulieferung nie als Fluss angezeigt.
    private fun wantsRes(t: MType): IntArray = when (t) {
        MType.OFEN, MType.GENERATOR -> intArrayOf(Res.ROHERZ.ordinal)
        MType.PRESSE -> intArrayOf(Res.BARREN.ordinal)
        MType.ASSEMBLER -> intArrayOf(Res.PLATTE.ordinal)
        MType.ZENTRIFUGE -> intArrayOf(Res.ROHERZ.ordinal, Res.WASSER.ordinal)
        MType.BLEIPRESSE -> intArrayOf(Res.BLEI.ordinal)
        MType.BRENNSTABWERK -> intArrayOf(Res.BARREN.ordinal, Res.PLATTE.ordinal)
        MType.REAKTORKERN -> intArrayOf(Res.KOMPONENTE.ordinal)
        MType.KUEHLTURM -> intArrayOf(Res.DAMPF.ordinal)
        else -> IntArray(0)
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
        for (r in 0 until an) for (c in 0 until an) {
            val cm = sim.grid[r][c] ?: continue
            val isLager = cm.type == MType.LAGER
            val wants = wantsRes(cm.type)
            if (wants.isEmpty() && !isLager) continue
            // Kanten der GESAMTEN Grundflaeche (nicht nur der Anker-Zelle) - sonst
            // zeigt ein 2x2/2x3-Gebaeude (Reaktorkern, Kuehlturm) nur an einer Seite
            // einen Materialfluss an.
            val rTop = r - (cm.h - 1); val rBot = r
            val cLeft = c; val cRight = c + (cm.w - 1)
            val edges = ArrayList<IntArray>()
            for (cc in cLeft..cRight) { edges.add(intArrayOf(rTop - 1, cc)); edges.add(intArrayOf(rBot + 1, cc)) }
            for (rr in rTop..rBot) { edges.add(intArrayOf(rr, cLeft - 1)); edges.add(intArrayOf(rr, cRight + 1)) }
            for (edge in edges) {
                val pr = edge[0]; val pc = edge[1]
                if (pr !in 0 until an || pc !in 0 until an) continue
                val pa = sim.anchorOf(pr, pc) ?: continue
                val pm = sim.grid[pa[0]][pa[1]] ?: continue
                // Welche Rohstoffe koennte dieser Nachbar liefern? Ein Lager liefert (anders
                // als ein normaler Produzent mit genau einem festen Ausstoss) JEDEN gerade
                // gelagerten Rohstoff - sonst wuerde nur der Zufluss INS Lager animiert,
                // nie der Abfluss AUS dem Lager zu einem Abnehmer.
                val offerCandidates: IntArray = if (pm.type == MType.LAGER) {
                    if (isLager) IntArray(Res.values().size) { it } else wants
                } else {
                    val off = offersRes(pm.type)
                    if (off < 0 || (!isLager && off !in wants)) continue
                    intArrayOf(off)
                }
                for (res in offerCandidates) {
                    // Nur zeichnen, wenn wirklich etwas fliesst: eine vom Lager blockierte
                    // Sorte NIE zeigen, sonst nur wenn die Quelle etwas anzubieten hat UND
                    // das Ziel es auch annimmt (Lager: nicht blockiert reicht; sonst muss
                    // das Ziel gerade aktiv laufen). Winzige Epsilons statt fester "Mindest-
                    // mengen" (0.2/0.03) - bei knappen Ketten (z.B. Brennstabsatz/Dampf,
                    // wenn Wasser der Engpass ist) blieb sonst auch ein echter, aber kleiner
                    // Fluss unsichtbar, weil er nie ueber die willkuerliche Schwelle kam.
                    if (isLager && !cm.acceptRes[res]) continue
                    val hasSupply = pm.output[res] > 1e-6 || pm.util > 1e-6
                    val isAccepted = isLager || cm.util > 1e-6
                    if (!hasSupply || !isAccepted) continue

                    val sx = vLeft + pa[1] * cell + half
                    val sy = vTop + pa[0] * cell + half
                    val ex = vLeft + c * cell + half
                    val ey = vTop + r * cell + half
                    val icon = Sprites.iconForRes(res, sim.companyLevel)
                    val base = animT * 0.6f + (pr * 3 + pc + res) * 0.31f   // langsamer
                    val t = base % 1f
                    val cx = sx + (ex - sx) * t
                    val cy = sy + (ey - sy) * t
                    drawIcon(canvas, icon, cx - isz / 2f, cy - isz / 2f, isz)
                }
            }
        }
    }

    // Terrain-Farben: heller Surf, Sandstrand, feuchter Sand
    private val cSurf = Color.rgb(202, 240, 246)
    private val cSand = Color.rgb(236, 220, 164)
    private val cSandWet = Color.rgb(198, 186, 130)

    private fun landSafe(r: Int, c: Int): Boolean =
        r in 0 until sim.n && c in 0 until sim.n && sim.isLand(r, c)

    /** Zeichnet einen Karten-Chunk aus echten Bild-Kacheln + prozeduraler Kueste/Fog. */
    /**
     * Level-2-Plattform: schlichter metallischer Kern (grosse, ruhige Deckplatten statt
     * Nieten pro Kachel) mit einem durchgehenden gelb-schwarzen Warnrand nur an der
     * echten Aussenkante. Der Streifen alternierst nach absoluter Gitter-Koordinate,
     * damit er nahtlos ueber die ganze Kante durchlaeuft (keine Naht pro Kachel).
     */
    private fun drawPlatformTile(canvas: Canvas, r: Int, c: Int, x: Float, y: Float) {
        val metal = Color.rgb(126, 132, 143); val metalSeam = Color.rgb(100, 106, 117)
        p.color = metal; canvas.drawRect(x, y, x + cell, y + cell, p)
        // Grosse, ruhige Deckplatten-Fugen alle 3 Zellen statt Nieten pro Kachel.
        p.color = metalSeam
        val lw = (cell * 0.035f).coerceAtLeast(1f)
        if (((r - sim.platformR0) % 3 + 3) % 3 == 0) canvas.drawRect(x, y, x + cell, y + lw, p)
        if (((c - sim.platformC0) % 3 + 3) % 3 == 0) canvas.drawRect(x, y, x + lw, y + cell, p)
        // Gelb-schwarzer Warnrand an den AUSSENkanten der GESAMTEN Industrieflaeche
        // (feste Plattform + spielerausgebaute Erweiterungen) - ein Nachbar-Check statt
        // eines festen Rechtecks, damit der Rand immer nahtlos ums tatsaechliche Areal
        // (inkl. hineingegrabener Kanal-"Loecher") herumlaeuft, egal wie es gewachsen ist.
        val t = cell * 0.16f
        if (!isIndustrialFloor(r - 1, c)) drawHazardStrip(canvas, x, y, cell, t, c)
        if (!isIndustrialFloor(r + 1, c)) drawHazardStrip(canvas, x, y + cell - t, cell, t, c)
        if (!isIndustrialFloor(r, c - 1)) drawHazardStrip(canvas, x, y, t, cell, r)
        if (!isIndustrialFloor(r, c + 1)) drawHazardStrip(canvas, x + cell - t, y, t, cell, r)
    }
    /** Plattform ODER spielerausgebaute Erweiterung, aber KEIN dort gegrabener Kanal. */
    private fun isIndustrialFloor(r: Int, c: Int): Boolean =
        !sim.isExpandedCanal(r, c) && ((sim.hasPlatform() && sim.isPlatform(r, c)) || sim.isExpandedPlatform(r, c))
    private fun drawHazardStrip(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, coord: Int) {
        p.color = if (coord % 2 == 0) Color.rgb(244, 196, 32) else Color.rgb(26, 26, 30)
        canvas.drawRect(x, y, x + w, y + h, p)
    }

    private fun drawGround(canvas: Canvas, r: Int, c: Int, x: Float, y: Float) {
        val u = cell / 8f
        dstTile.set(x, y, x + cell, y + cell)

        if (sim.isExpandedCanal(r, c)) {
            // Kuenstlicher Kanal: wie Wasser, mit einem Betonrand nur an den Seiten, wo
            // wirklich Land/AKW-Flaeche angrenzt (nicht zum offenen Meer oder zum
            // naechsten Kanal-Glied hin, sonst zerschneidet der Rand einen ganzen Fluss
            // aus mehreren Kanal-Feldern in lauter einzelne Kaestchen). Muss VOR der
            // Plattform-Pruefung kommen - ein Kanal kann auch mitten in der Plattform sein.
            val wb = if ((animT * 2f).toInt() and 1 == 0) bmpWater0 else bmpWater1
            canvas.drawBitmap(wb, srcTile, dstTile, pTile)
            p.color = Color.argb(190, 150, 150, 158)
            val bw2 = cell * 0.1f
            if (sim.isLand(r - 1, c)) canvas.drawRect(x, y, x + cell, y + bw2, p)
            if (sim.isLand(r + 1, c)) canvas.drawRect(x, y + cell - bw2, x + cell, y + cell, p)
            if (sim.isLand(r, c - 1)) canvas.drawRect(x, y, x + bw2, y + cell, p)
            if (sim.isLand(r, c + 1)) canvas.drawRect(x + cell - bw2, y, x + cell, y + cell, p)
            return
        }

        if ((sim.hasPlatform() && sim.isPlatform(r, c)) || sim.isExpandedPlatform(r, c)) {
            drawPlatformTile(canvas, r, c, x, y)
            return
        }

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
    /** Kurze Bau-Staubwolke beim Platzieren. */
    private fun drawPuffs(canvas: Canvas) {
        if (puffs.isEmpty()) return
        canvas.save()
        canvas.clipRect(gridLeft, gridTop, gridLeft + gridW, gridTop + gridH)
        val it = puffs.iterator()
        while (it.hasNext()) {
            val pf = it.next()
            val age = animT - pf.start
            if (age > 0.5f) { it.remove(); continue }
            val prog = age / 0.5f
            val cx = vLeft + (pf.c + 0.5f) * cell
            val cy = vTop + (pf.r + 0.85f) * cell
            val al = (170 * (1f - prog)).toInt().coerceIn(0, 255)
            p.color = Color.argb(al, 210, 200, 180)
            for (k in 0 until 5) {
                val ang = k * 1.2566f
                val dist = cell * (0.10f + 0.42f * prog)
                val rad = cell * (0.16f - 0.10f * prog)
                canvas.drawCircle(cx + kotlin.math.cos(ang) * dist, cy - kotlin.math.sin(ang) * dist * 0.5f, rad, p)
            }
        }
        canvas.restore()
    }

    /** Roter Rand-Puls bei Stromknappheit (statt Text auf dem Raster). */
    private fun drawPowerPulse(canvas: Canvas) {
        if (sim.powerDemand <= sim.powerSupply + 1e-6) return
        val pulse = 0.5f + 0.5f * kotlin.math.sin(animT * 4f)
        val a = (70 * pulse).toInt().coerceIn(0, 255)
        val th = dp(6f)
        p.color = Color.argb(a, 230, 70, 60)
        canvas.drawRect(gridLeft, gridTop, gridLeft + gridW, gridTop + th, p)
        canvas.drawRect(gridLeft, gridTop + gridH - th, gridLeft + gridW, gridTop + gridH, p)
        canvas.drawRect(gridLeft, gridTop, gridLeft + th, gridTop + gridH, p)
        canvas.drawRect(gridLeft + gridW - th, gridTop, gridLeft + gridW, gridTop + gridH, p)
    }

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

    /** Kuehlwasser-Schlauch des Reaktors: Rohr entlang der Zellen + animierter Wasserfluss. */
    private fun drawHose(canvas: Canvas) {
        val pipe = sim.reactorPipe
        if (pipe.size < 2) return
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until pipe.size - 1) {
            val a = pipe[i]; val b = pipe[i + 1]
            val ax = vLeft + (a[1] + 0.5f) * cell; val ay = vTop + (a[0] + 0.5f) * cell
            val bx = vLeft + (b[1] + 0.5f) * cell; val by = vTop + (b[0] + 0.5f) * cell
            p.color = cGridLine; p.strokeWidth = cell * 0.18f; canvas.drawLine(ax, ay, bx, by, p)
            p.color = Color.rgb(120, 126, 140); p.strokeWidth = cell * 0.10f; canvas.drawLine(ax, ay, bx, by, p)
            p.color = Color.rgb(176, 182, 194); p.strokeWidth = cell * 0.035f
            canvas.drawLine(ax, ay - cell * 0.03f, bx, by - cell * 0.03f, p)
        }
        // animierter Wasserfluss vom Wasser zum Reaktor
        p.style = Paint.Style.FILL
        val segs = pipe.size - 1
        for (k in 0 until 2) {
            val pos = (animT * 0.4f + k * 0.5f) % 1f
            val idx = (1f - pos) * segs
            val si = idx.toInt().coerceIn(0, segs - 1)
            val f = idx - si
            val a = pipe[si]; val b = pipe[si + 1]
            val cx = vLeft + ((a[1] + (b[1] - a[1]) * f) + 0.5f) * cell
            val cy = vTop + ((a[0] + (b[0] - a[0]) * f) + 0.5f) * cell
            p.color = Color.rgb(96, 206, 228)
            canvas.drawCircle(cx, cy, cell * 0.035f, p)
        }
        p.strokeCap = Paint.Cap.BUTT
    }

    /** Ein Rohrsegment im selben Stil wie der Reaktor-Kuehlschlauch (drawHose): dunkler
     *  Aussenrand, mittelgrauer Kern, heller Glanzstreifen oben. */
    private fun drawPipeSeg(canvas: Canvas, ax: Float, ay: Float, bx: Float, by: Float) {
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        p.color = cGridLine; p.strokeWidth = cell * 0.18f; canvas.drawLine(ax, ay, bx, by, p)
        p.color = Color.rgb(120, 126, 140); p.strokeWidth = cell * 0.10f; canvas.drawLine(ax, ay, bx, by, p)
        p.color = Color.rgb(176, 182, 194); p.strokeWidth = cell * 0.035f
        canvas.drawLine(ax, ay - cell * 0.03f, bx, by - cell * 0.03f, p)
        p.strokeCap = Paint.Cap.BUTT
    }

    /** Ein animierter Wassertropfen, der von (ax,ay) nach (bx,by) durch ein Rohrsegment wandert. */
    private fun drawPipeFlow(canvas: Canvas, ax: Float, ay: Float, bx: Float, by: Float, phase: Float) {
        p.style = Paint.Style.FILL
        val pos = phase - kotlin.math.floor(phase)
        p.color = Color.rgb(96, 206, 228)
        canvas.drawCircle(ax + (bx - ax) * pos, ay + (by - ay) * pos, cell * 0.035f, p)
    }

    /**
     * Wasserpumpe: echtes Ansaugrohr zur tatsaechlich angrenzenden Wasserquelle (Meer oder
     * Kanal, welche Richtung auch immer) UND ein Abgaberohr zu einer direkt angrenzenden
     * Zentrifuge, falls vorhanden - beide beruehren sich ueber die Pumpe hinweg, statt wie
     * vorher ein fest eingebackener Stutzen zu sein, der selten zur echten Platzierung passt.
     */
    private fun drawWasserpumpePipes(canvas: Canvas) {
        val an = sim.areaN()
        for (r in 0 until an) for (c in 0 until an) {
            val m = sim.grid[r][c] ?: continue
            if (m.type != MType.WASSERPUMPE) continue
            val px = vLeft + (c + 0.5f) * cell; val py = vTop + (r + 0.5f) * cell
            val wdir = sim.waterNeighborDir(r, c)
            if (wdir != null) {
                val wx = vLeft + (c + wdir[1] + 0.5f) * cell; val wy = vTop + (r + wdir[0] + 0.5f) * cell
                drawPipeSeg(canvas, px, py, wx, wy)
                drawPipeFlow(canvas, wx, wy, px, py, animT * 0.5f)   // Wasser stroemt VON der Quelle ZUR Pumpe
            }
            for (d in arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))) {
                val nr = r + d[0]; val nc = c + d[1]
                if (nr !in 0 until an || nc !in 0 until an) continue
                if (sim.grid[nr][nc]?.type != MType.ZENTRIFUGE) continue
                val zx = vLeft + (nc + 0.5f) * cell; val zy = vTop + (nr + 0.5f) * cell
                drawPipeSeg(canvas, px, py, zx, zy)
                drawPipeFlow(canvas, px, py, zx, zy, animT * 0.5f + 0.5f)   // VON der Pumpe ZUR Zentrifuge
                break
            }
        }
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

    /**
     * Reparaturdrohnen: fliegen von der Station zu beschaedigten Maschinen im Umkreis.
     * Die Flugposition kommt direkt aus der Simulation (Sim.kt flyTowards()) statt aus
     * einer eigenen, potenziell davon abweichenden Interpolation hier - so zeigt die
     * Animation immer exakt, wann wirklich (und woran) repariert wird.
     */
    private fun drawDrones(canvas: Canvas) {
        val nn = sim.n
        for (r in 0 until nn) for (c in 0 until nn) {
            val m = sim.grid[r][c] ?: continue
            if (m.type != MType.DROHNE) continue
            val stX = c + 0.5f; val stY = r + 0.12f            // Ruheplatz ueber der Station
            val px = if (m.flyC >= 0.0) m.flyC.toFloat() else stX
            val py = if (m.flyR >= 0.0) m.flyR.toFloat() else stY
            var tgtX = stX; var tgtY = stY; var repairing = false
            if (m.svR in 0 until nn && m.svC in 0 until nn) {
                val g = sim.grid[m.svR][m.svC]
                if (g != null && g.condition < 99.999) {
                    tgtX = m.svC + 0.5f; tgtY = m.svR + 0.32f; repairing = true
                }
            }
            val arrived = kotlin.math.hypot(tgtX - px, tgtY - py) < 0.12f
            val sx = vLeft + px * cell
            val syBase = vTop + py * cell
            if (sx < gridLeft - cell || sx > gridLeft + gridW + cell ||
                syBase < gridTop - cell || syBase > gridTop + gridH + cell) continue
            val hover = if (arrived) kotlin.math.sin(animT * 3.5f) * cell * 0.03f
                        else kotlin.math.sin(animT * 11f) * cell * 0.012f
            drawDroneSprite(canvas, sx, syBase + hover, repairing && arrived)
        }
    }

    private fun drawDroneSprite(canvas: Canvas, cx: Float, cy: Float, repairing: Boolean) {
        val u = cell
        // Schatten
        pSprite.color = Color.argb(60, 0, 0, 0)
        canvas.drawOval(cx - u * 0.16f, cy + u * 0.24f, cx + u * 0.16f, cy + u * 0.32f, pSprite)
        // Reparatur-Strahl + Funken
        if (repairing) {
            val fl = 0.5f + 0.5f * kotlin.math.sin(animT * 18f)
            pSprite.color = Color.argb((90 * fl).toInt().coerceIn(0, 255), 120, 240, 180)
            bladePath.reset()
            bladePath.moveTo(cx - u * 0.04f, cy + u * 0.08f)
            bladePath.lineTo(cx + u * 0.04f, cy + u * 0.08f)
            bladePath.lineTo(cx + u * 0.10f, cy + u * 0.30f)
            bladePath.lineTo(cx - u * 0.10f, cy + u * 0.30f)
            bladePath.close()
            canvas.drawPath(bladePath, pSprite)
            for (k in 0 until 3) {
                val ph = (animT * 2.2f + k * 0.33f) % 1f
                val fx = cx + (k - 1) * u * 0.09f
                val fy = cy + u * 0.30f - ph * u * 0.18f
                pSprite.color = Color.argb(((1f - ph) * 220).toInt().coerceIn(0, 255), 190, 255, 210)
                canvas.drawRect(fx - u * 0.012f, fy, fx + u * 0.012f, fy + u * 0.03f, pSprite)
            }
        }
        // Arme (X)
        p.color = Color.rgb(40, 44, 52); p.strokeWidth = u * 0.03f; p.style = Paint.Style.STROKE
        canvas.drawLine(cx - u * 0.16f, cy - u * 0.08f, cx + u * 0.16f, cy + u * 0.08f, p)
        canvas.drawLine(cx - u * 0.16f, cy + u * 0.08f, cx + u * 0.16f, cy - u * 0.08f, p)
        p.style = Paint.Style.FILL
        // Rotoren an den 4 Enden
        val rot = floatArrayOf(-0.16f, -0.08f, 0.16f, -0.08f, -0.16f, 0.08f, 0.16f, 0.08f)
        for (k in 0 until 4) {
            val rx = cx + rot[k * 2] * u; val ry = cy + rot[k * 2 + 1] * u
            pSprite.color = Color.argb(55, 180, 210, 235)
            canvas.drawCircle(rx, ry, u * 0.085f, pSprite)
            canvas.save(); canvas.rotate(animT * 720f + k * 40f, rx, ry)
            p.color = Color.rgb(150, 170, 190); p.strokeWidth = u * 0.02f; p.style = Paint.Style.STROKE
            canvas.drawLine(rx - u * 0.08f, ry, rx + u * 0.08f, ry, p)
            p.style = Paint.Style.FILL; canvas.restore()
            p.color = Color.rgb(60, 64, 74); canvas.drawCircle(rx, ry, u * 0.02f, p)
        }
        // Koerper
        p.color = Color.rgb(52, 58, 68)
        canvas.drawRoundRect(cx - u * 0.11f, cy - u * 0.07f, cx + u * 0.11f, cy + u * 0.07f, u * 0.03f, u * 0.03f, p)
        // Scanner-Auge (gruen beim Reparieren, sonst blau)
        p.color = if (repairing) Color.rgb(120, 240, 170) else Color.rgb(90, 190, 240)
        canvas.drawCircle(cx, cy, u * 0.035f, p)
        p.color = Color.argb(180, 255, 255, 255); canvas.drawCircle(cx - u * 0.01f, cy - u * 0.01f, u * 0.012f, p)
    }

    private fun drawMachine(canvas: Canvas, m: Machine, x: Float, y: Float) {
        // mehrzellige Gebaeude: Anker unten, Sprite ragt nach oben
        val topY = y - (m.h - 1) * cell
        if (m.type == MType.WINDRAD) { drawWindrad(canvas, x, topY); return }

        val hasWear = m.type != MType.REAKTOR && m.type != MType.LAGER &&
            m.type != MType.HAENDLER && m.type != MType.PROSPEKTOR && m.type != MType.WINDRAD &&
            m.type != MType.SOLAR
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

        // Bohrer bohrt: nach unten wandernde Glanzbaender (Schnecke dreht sich) + Staub -
        // genauso fuer den Tiefen-Bohrer (Bleibohrer), der bisher keine Animation hatte.
        if (working && (m.type == MType.BOHRER || m.type == MType.BLEIBOHRER)) {
            val cx = x + cell * 0.5f
            for (k in 0 until 2) {
                val phase = (animT * 2.5f + k * 0.5f) % 1f
                val yy = y + cell * (0.48f + 0.44f * phase)
                val hw = cell * (0.12f - 0.09f * phase)
                pSprite.color = Color.argb(210, 240, 246, 255)
                canvas.drawRect(cx - hw, yy, cx + hw, yy + cell * 0.03f, pSprite)
            }
            val dustA = (0.5f + 0.5f * kotlin.math.sin(animT * 7f))
            pSprite.color = Color.argb((120 * dustA).toInt().coerceIn(0, 255), 150, 120, 90)
            canvas.drawRect(x + cell * 0.30f, y + cell * 0.90f, x + cell * 0.40f, y + cell * 0.95f, pSprite)
            canvas.drawRect(x + cell * 0.60f, y + cell * 0.88f, x + cell * 0.70f, y + cell * 0.93f, pSprite)
        }

        // Reaktor: Dampf aus dem breiten Kuehlturm
        if (m.type == MType.REAKTOR) {
            val tx = x + 2.22f * cell
            val tyTop = topY + 0.47f * cell
            for (k in 0 until 4) {
                val ph = (animT * 0.5f + k * 0.25f) % 1f
                val py = tyTop - ph * cell * 1.3f
                val rad = cell * (0.13f + 0.18f * ph)
                val al = (120 * (1f - ph)).toInt().coerceIn(0, 255)
                p.color = Color.argb(al, 236, 240, 246)
                canvas.drawCircle(tx + (k - 1.5f) * cell * 0.15f, py, rad, p)
            }
        }

        // Kuehlturm (2x3): aufsteigende Dampfwolke ueber der Muendung, animiert.
        if (m.type == MType.KUEHLTURM) {
            val tx = x + 1.0f * cell
            val tyTop = topY + 0.5f * cell
            for (k in 0 until 4) {
                val ph = (animT * 0.4f + k * 0.25f) % 1f
                val py = tyTop - ph * cell * 1.8f
                val rad = cell * (0.16f + 0.22f * ph)
                val al = (130 * (1f - ph)).toInt().coerceIn(0, 255)
                p.color = Color.argb(al, 238, 242, 248)
                canvas.drawCircle(tx + (k - 1.5f) * cell * 0.14f, py, rad, p)
            }
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
        if (paletteCollapsed) return   // keine Kacheln zeichnen - der schwebende Auf-/
        // Zuklapp-Button (drawPaletteToggle) reicht als einziges sichtbares Element.
        val cols = paletteCols
        val margin = dp(8f)
        val gap = dp(5f)
        val bw = (W - 2 * margin - (cols - 1) * gap) / cols
        val bh = dp(66f)
        for ((i, t) in buildOrderFor(sim.companyLevel).withIndex()) {
            val col = i % cols
            val row = i / cols
            val x = margin + col * (bw + gap)
            val yy = paletteTop + dp(6f) + row * (bh + gap)
            val rect = RectF(x, yy, x + bw, yy + bh)
            val used = sim.typeCount[t.ordinal]
            val max = sim.maxCount(t)
            val full = used >= max
            val unlocked = sim.canBuild(t) && !full
            drawBuildTile(canvas, rect, t, buildTool == t, used, max, full)
            buttons.add(Btn(rect, "build_${t.name}", mShort(t), unlocked))
        }
    }

    /**
     * Einzelner schwebender Auf-/Zuklapp-Button unten links (statt einer duennen Griff-
     * Leiste ueber die volle Breite, die zu klein zum Treffen war). Sitzt eingeklappt
     * ganz unten auf der Karte, ausgeklappt direkt ueber den Bau-Kacheln - und wird gar
     * nicht erst aufgerufen, wenn statt der Palette ein Detail-/Ausbau-Panel gezeigt wird
     * (siehe onDraw), verschwindet also automatisch, sobald man etwas anderes antippt.
     */
    private fun drawPaletteToggle(canvas: Canvas) {
        val bw = dp(48f); val bh = dp(34f)
        val bx = dp(8f)
        val by = if (paletteCollapsed) H - dp(8f) - bh else paletteTop - dp(8f) - bh
        val r = RectF(bx, by, bx + bw, by + bh)
        p.color = cPanel
        canvas.drawRoundRect(r, dp(8f), dp(8f), p)
        p.color = cPanelHi; p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.5f)
        canvas.drawRoundRect(r, dp(8f), dp(8f), p); p.style = Paint.Style.FILL
        pTextC.color = cText; pTextC.textSize = dp(16f)
        canvas.drawText(if (paletteCollapsed) "▲" else "▼", r.centerX(), r.centerY() + dp(6f), pTextC)
        buttons.add(Btn(r, "palette_toggle", "pt"))
    }

    /** Skeuomorphes Baumodul: Sprite-Icon, Name, Kosten mit Icon, LED-Statusstreifen, Zustaende. */
    private fun drawBuildTile(canvas: Canvas, rect: RectF, t: MType, active: Boolean, used: Int, max: Int, full: Boolean) {
        val pressed = pressedBtn == "build_${t.name}"
        val r = if (pressed) RectF(rect.left, rect.top + dp(1.5f), rect.right, rect.bottom + dp(1.5f)) else rect
        val mc = mColor(t)
        val canB = sim.canBuild(t)
        val moneyB = sim.isMoneyBuilt(t)
        val camt = if (moneyB) sim.moneyBuildCost(t) else sim.buildCost(t).second
        val afford = if (moneyB) sim.money >= camt else sim.available(sim.buildCost(t).first) >= camt
        // Grund + Bevel
        p.color = if (!canB) cBtnSh else if (active) mc else cBtn
        canvas.drawRoundRect(r, dp(8f), dp(8f), p)
        if (canB) {
            p.color = Color.argb(70, 255, 255, 255)
            canvas.drawRect(r.left + dp(6f), r.top + dp(2f), r.right - dp(6f), r.top + dp(3.5f), p)
            p.color = Color.argb(55, 0, 0, 0)
            canvas.drawRect(r.left + dp(6f), r.bottom - dp(3f), r.right - dp(6f), r.bottom - dp(1.5f), p)
        }
        // Eck-Nieten
        p.color = Color.argb(120, 210, 216, 228)
        canvas.drawCircle(r.left + dp(4f), r.top + dp(4f), dp(1.1f), p)
        canvas.drawCircle(r.right - dp(4f), r.top + dp(4f), dp(1.1f), p)
        canvas.drawCircle(r.left + dp(4f), r.bottom - dp(4f), dp(1.1f), p)
        canvas.drawCircle(r.right - dp(4f), r.bottom - dp(4f), dp(1.1f), p)
        // LED-Statusstreifen links (Kategorie)
        val nSeg = 4
        val segH = (r.height() - dp(16f)) / nSeg
        for (k in 0 until nSeg) {
            val yy0 = r.top + dp(8f) + k * segH
            p.color = if (canB) mc else Color.argb(70, Color.red(mc), Color.green(mc), Color.blue(mc))
            canvas.drawRoundRect(RectF(r.left + dp(3.5f), yy0 + dp(1f), r.left + dp(6.5f), yy0 + segH - dp(1f)), dp(1.5f), dp(1.5f), p)
        }
        // Sprite-Icon (aspektgetreu)
        val bmp = machBmp[t.ordinal]
        val box = dp(26f); val ar = bmp.width.toFloat() / bmp.height
        var iw = box; var ih = box
        if (ar > 1f) ih = box / ar else iw = box * ar
        val icx = r.centerX() + dp(2f); val icTop = r.top + dp(5f)
        val dst = RectF(icx - iw / 2, icTop + (box - ih) / 2, icx + iw / 2, icTop + (box + ih) / 2)
        val savedA = pTile.alpha
        pTile.alpha = if (canB) 255 else 90
        canvas.drawBitmap(bmp, null, dst, pTile)
        pTile.alpha = savedA
        // Name
        pTextC.textAlign = Paint.Align.CENTER
        pTextC.textSize = dp(10f); pTextC.color = if (canB) cText else cDim
        canvas.drawText(mShort(t), r.centerX() + dp(2f), r.top + dp(39f), pTextC)
        if (canB) {
            // Kosten: Icon + Betrag als zentrierte Gruppe (eine Zeile)
            val costIcon = when {
                moneyB -> Sprites.ICON_GELD
                sim.buildCost(t).first == Res.PLATTE -> Sprites.ICON_PLATTE
                else -> Sprites.ICON_BARREN
            }
            val amt = "${camt.toInt()}"
            pTextC.textAlign = Paint.Align.LEFT; pTextC.textSize = dp(11f)
            val tw = pTextC.measureText(amt)
            val iw = dp(11f); val gap = dp(2.5f)
            val sx = r.centerX() - (iw + gap + tw) / 2f
            val costBase = r.bottom - dp(15f)
            drawIcon(canvas, costIcon, sx, costBase - dp(9f), iw)
            pTextC.color = if (afford) cText else cBad
            canvas.drawText(amt, sx + iw + gap, costBase, pTextC)
            pTextC.textAlign = Paint.Align.CENTER
            // Anzahl: eigene Zeile darunter (keine Ueberlappung mehr)
            pTextC.color = if (full) cBad else cDim; pTextC.textSize = dp(9.5f)
            canvas.drawText("$used/$max", r.centerX() + dp(1f), r.bottom - dp(3f), pTextC)
        } else {
            pTextC.color = cDim; pTextC.textSize = dp(9.5f)
            canvas.drawText(tr("tech_needed"), r.centerX() + dp(2f), r.bottom - dp(8f), pTextC)
        }
        // Rahmen: aktiv (Akzent), sonst rot wenn nicht baubar (voll/zu teuer)
        if (active) {
            p.color = cAccent; p.style = Paint.Style.STROKE; p.strokeWidth = dp(2f)
            canvas.drawRoundRect(r, dp(8f), dp(8f), p); p.style = Paint.Style.FILL
        } else if (canB && (!afford || full)) {
            p.color = cBad; p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.6f)
            canvas.drawRoundRect(r, dp(8f), dp(8f), p); p.style = Paint.Style.FILL
        }
        // Press-Verdunkelung
        if (pressed) { p.color = Color.argb(45, 0, 0, 0); canvas.drawRoundRect(r, dp(8f), dp(8f), p) }
    }

    private fun shortLabel(t: MType) = when (t) {
        MType.GENERATOR -> "Generat."
        MType.DROHNE -> "Drohne"
        MType.ASSEMBLER -> "Assembl."
        MType.VERSTAERKER -> "Verstaerk."
        MType.HAENDLER -> "Haendler"
        else -> t.label
    }

    /**
     * Zusaetzliche Panelhoehe ueber die Basis (top = H - 214 - extra) hinaus, damit der
     * feste Aktions-Button-Abstand (H - 54) am Ende genau die gleiche kleine Luecke laesst
     * wie bei einer "normalen" Maschine (4 Standardzeilen x 22dp = 88dp) - JEDER Beitrag
     * hier ist die ECHTE Differenz zu diesen 88dp Standard-Inhalt, sonst entsteht (wie
     * beim Lager schon einmal passiert) eine riesige leere Luecke ueber den Buttons.
     */
    private fun detailExtraH(m: Machine): Float {
        var extra = 0f
        if (sim.canUpgradeMachine(m.type)) extra += dp(36f)   // eigene Zeile, +36 ueber die 88dp
        if (m.type == MType.LAGER) {
            // Lager laesst 3 der 4 Standardzeilen weg (Zustand/Auslastung, Puffer-Zeile,
            // Engpass-Ampel = -66dp), zeichnet dafuer die Inhalts-/Filter-Reihe (12dp Kopf +
            // cs hohe Icons + 20dp Luft = 32+cs) UND den "Alles entnehmen"-Button darunter
            // (38dp): netto (32+cs)+38-66 = cs+4. cs haengt von der Bildschirmbreite ab
            // (lagerCellSize()) - MUSS hier exakt mitgerechnet werden, sonst driftet die
            // Panelhoehe wieder auseinander wie zuvor schon zweimal passiert.
            extra += lagerCellSize() + dp(4f)
        }
        if (m.type == MType.DROHNE) extra += dp(36f)   // Reparatur-Limit-Zeile, +36 ueber die 88dp
        return extra
    }

    /** Kantenlaenge einer Lager-Icon-Kachel: 8 Sorten fuellen die volle Panel-Breite
     *  (minus schmalem Rand) aus - von detailExtraH() UND drawDetail() gemeinsam genutzt,
     *  damit beide garantiert denselben Wert verwenden. */
    private fun lagerCellSize(): Float {
        val margin2 = dp(6f); val cgap = dp(4f); val n = Res.values().size
        return (W - 2 * margin2 - (n - 1) * cgap) / n
    }

    private fun drawDetail(canvas: Canvas) {
        val m = sim.grid[selR][selC] ?: return
        // eigenes, hoeheres Overlay unten (unabhaengig von der kompakten Palette) -
        // waechst je nach Maschine (Ausbau-Zeile, Lager-Filter, Drohnen-Reihe).
        val top = H - dp(214f) - detailExtraH(m)
        p.color = cPanel
        canvas.drawRect(0f, top, W.toFloat(), H.toFloat(), p)
        p.color = cPanelHi
        canvas.drawRect(0f, top, W.toFloat(), top + dp(2f), p)

        pText.color = cAccent; pText.textSize = dp(18f)
        canvas.drawText("${mName(m.type)}  (${selR + 1},${selC + 1})", dp(12f), top + dp(24f), pText)

        pText.color = cText; pText.textSize = dp(14f)
        var yy = top + dp(48f)
        // Zustand/Auslastung ist bei einem Lager immer 100%/0% (kein Verschleiss, keine
        // eigene Produktion) - unnoetige Zeile, spart Platz im ohnehin schon vollen Panel.
        if (m.type != MType.LAGER) {
            canvas.drawText("${tr("condition")} ${m.condition.roundToInt()}%     ${tr("util")} ${(m.util * 100).roundToInt()}%", dp(12f), yy, pText)
            yy += dp(22f)
        }
        val oreLabel = if (sim.companyLevel >= 2) tr("uranerz") else tr("roherz")
        val io = when (m.type) {
            MType.BOHRER -> {
                val floorTxt = if (sim.isSurveyed(selR, selC)) {
                    val tier = sim.richness(selR, selC)
                    "${tierName(tier)} (x${Simulation.ORE_MULT[tier]})"
                } else tr("unscanned")
                "${tr("out")} ${oneDec(m.output[0])} $oreLabel   ${tr("floor")}: $floorTxt"
            }
            MType.PROSPEKTOR -> "${tr("scans")} (${tr("radius")} ${sim.scanRadius()})"
            MType.WINDRAD -> {
                val coast = sim.windCoastBonus(selR, selC) > 1.0
                val wp = (Simulation.WIND_POWER * sim.windCoastBonus(selR, selC) * sim.machineUpgradeMult(m)).roundToInt()
                "${tr("provides")} $wp ${tr("strom")} (${if (coast) tr("coast") else tr("wind")})"
            }
            MType.OFEN -> "${tr("in")} ${oneDec(m.input[0])} $oreLabel   ${tr("out")} ${oneDec(m.output[1])} ${tr("barren")}"
            MType.PRESSE -> "${tr("in")} ${oneDec(m.input[1])} ${tr("barren")}   ${tr("out")} ${oneDec(m.output[2])} ${tr("platten")}"
            MType.ASSEMBLER -> "${tr("in")} ${oneDec(m.input[2])} ${tr("platten")}   ${tr("out")} ${oneDec(m.output[3])} ${tr("komp")}"
            MType.HAENDLER -> "${tr(if (sim.companyLevel >= 2) "sells_strom" else "sells_comp")} (${oneDec(sim.componentPrice())}${tr("per_piece")})"
            MType.GENERATOR -> "${tr("fuel")} ${oneDec(m.input[0])} $oreLabel  ->  +${(Simulation.GEN_POWER * sim.machineUpgradeMult(m)).roundToInt()} ${tr("strom")}"
            // Inhalt wird unten als eigene Icon-Reihe gezeigt (siehe die "Lager:
            // Inhalts-Uebersicht"-Zeilen in drawDetail) - hier nur noch der Kopf-Hinweis,
            // keine Buchstaben-Abkuerzungen mehr.
            MType.LAGER -> tr("buffer")
            MType.VERSTAERKER -> "${tr("boosts")} (+${(Simulation.BOOST_PER * 100).toInt()}%)"
            MType.REAKTOR -> "${tr("provides")} ${Simulation.REAKTOR_POWER.toInt()} ${tr("strom")} (${tr("fixed")})"
            MType.SOLAR -> "${tr("provides")} ${(Simulation.SOLAR_POWER * sim.machineUpgradeMult(m)).roundToInt()} ${tr("strom")} (${tr("sun")})"
            MType.FORSCHUNG -> "${tr("produces_research")} +${oneDec(sim.researchRate())}/s"
            MType.DROHNE -> "${tr("repairs")} · R${sim.droneRange()} · ${(sim.droneRepairRate() * sim.machineUpgradeMult(m)).roundToInt()}%/s"
            MType.BLEIBOHRER -> {
                val floorTxt = if (sim.isSurveyed(selR, selC)) {
                    val tier = sim.richness(selR, selC)
                    "${tierName(tier)} (x${Simulation.ORE_MULT[tier]})"
                } else tr("unscanned")
                "${tr("out")} ${oneDec(m.output[Res.BLEI.ordinal])} ${tr("blei")}   ${tr("floor")}: $floorTxt"
            }
            MType.WASSERPUMPE -> "${tr("out")} ${oneDec(m.output[Res.WASSER.ordinal])} ${tr("wasser")}   (${tr("needs_coast")})"
            MType.ZENTRIFUGE -> "${tr("in")} ${oneDec(m.input[Res.ROHERZ.ordinal])} ${tr("uranerz")} + ${oneDec(m.input[Res.WASSER.ordinal])} ${tr("wasser")}   ${tr("out")} ${oneDec(m.output[Res.BARREN.ordinal])} ${tr("angeruran")}"
            MType.BLEIPRESSE -> "${tr("in")} ${oneDec(m.input[Res.BLEI.ordinal])} ${tr("blei")}   ${tr("out")} ${oneDec(m.output[Res.PLATTE.ordinal])} ${tr("bleiverkl")}"
            MType.BRENNSTABWERK -> "${tr("in")} ${oneDec(m.input[Res.BARREN.ordinal])} ${tr("angeruran")} + ${oneDec(m.input[Res.PLATTE.ordinal])} ${tr("bleiverkl")}   ${tr("out")} ${oneDec(m.output[Res.KOMPONENTE.ordinal])} ${tr("brennstab")}"
            MType.REAKTORKERN -> "${tr("in")} ${oneDec(m.input[Res.KOMPONENTE.ordinal])} ${tr("brennstab")}   ${tr("out")} ${oneDec(m.output[Res.DAMPF.ordinal])} ${tr("dampf")}"
            MType.KUEHLTURM -> "${tr("in")} ${oneDec(m.input[Res.DAMPF.ordinal])} ${tr("dampf")}   ${tr("out")} ${oneDec(m.output[Res.STROM.ordinal])} ${tr("netzstrom")}"
        }
        // Fuer ein Lager sagen weder die Puffer-Zeile noch die Engpass-Ampel etwas
        // Nuetzliches aus (kein Rezept, kein "blockiert") - beide weglassen, die neue
        // Inhalts-Uebersicht unten zeigt ohnehin alles Relevante als Icons.
        if (m.type != MType.LAGER) {
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
        }

        pText.color = cDim; pText.textSize = dp(14f)
        val moneyB = sim.isMoneyBuilt(m.type)
        val samt = if (moneyB) sim.moneyBuildCost(m.type) else sim.buildCost(m.type).second
        val curAbbr = if (moneyB) "€" else resAbbr(sim.buildCost(m.type).first)
        if (m.type != MType.REAKTOR) {
            val refund = samt * 0.5 * (m.condition / 100.0)
            canvas.drawText("${tr("poweruse")} ${m.type.power.toInt()}     ${tr("sellvalue")} +${oneDec(refund)} $curAbbr", dp(12f), yy, pText)
        }
        yy += dp(22f)

        // Individuelle Ausbaustufe DIESER Maschine (unabhaengig vom Tech-Baum).
        if (sim.canUpgradeMachine(m.type)) {
            val cost = sim.machineUpgradeCost(m)
            val maxed = m.lvl >= Simulation.MACHINE_UPGRADE_MAXLVL
            val canAfford = sim.money >= cost
            pText.color = cText; pText.textSize = dp(13f)
            val lab = "${tr("upgrade_lvl")} ${m.lvl}" + if (maxed) " · ${tr("upgrade_max")}" else ""
            canvas.drawText(lab, dp(12f), yy + dp(18f), pText)
            if (!maxed) {
                val ur = RectF(W - dp(150f), yy - dp(2f), W - dp(6f), yy + dp(28f))
                drawButton(canvas, Btn(ur, "upgrade_sel", "${tr("upgrade")} ${cost.toInt()}€", canAfford, false, cAccent))
                buttons.add(Btn(ur, "upgrade_sel", "upgrade", canAfford))
            }
            yy += dp(36f)
        }

        // Lager: EINE kompakte Icon-Reihe fuer Inhalt + Annahme-Filter, randnah/volle
        // Breite, damit die Icons so gross wie moeglich sind. Antippen einer Kachel
        // schaltet Annahme/Blockade fuer diesen Rohstoff um (grosser Tap-Bereich, keine
        // winzige Ecke mehr). Entnahme laeuft ueber den eigenen Button darunter, der auf
        // einmal ALLES entnimmt statt jede Sorte einzeln antippen zu muessen.
        if (m.type == MType.LAGER) {
            pText.color = cDim; pText.textSize = dp(11f)
            canvas.drawText(tr("lager_content"), dp(12f), yy + dp(8f), pText)
            val margin2 = dp(6f); val cgap = dp(4f)
            val n = Res.values().size
            val cs = lagerCellSize()
            val cy = yy + dp(12f)
            var cx = margin2
            for (ri in 0 until n) {
                val amt = m.output[ri]
                val has = amt > 0.05
                val on = m.acceptRes[ri]
                val r = RectF(cx, cy, cx + cs, cy + cs)
                p.color = when { !on -> Color.argb(70, 220, 80, 70); has -> Color.argb(60, 120, 190, 230); else -> Color.argb(35, 120, 120, 130) }
                canvas.drawRoundRect(r, dp(4f), dp(4f), p)
                p.color = when { !on -> cBad; has -> cAccent; else -> cGridLine }
                p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.1f)
                canvas.drawRoundRect(r, dp(4f), dp(4f), p); p.style = Paint.Style.FILL
                drawIcon(canvas, Sprites.iconForRes(ri, sim.companyLevel), cx + cs * 0.16f, cy + cs * 0.16f, cs * 0.68f)
                if (!on) { p.color = cBad; p.strokeWidth = dp(1.6f); canvas.drawLine(cx + 2, cy + 2, cx + cs - 2, cy + cs - 2, p) }
                pText.textAlign = Paint.Align.CENTER; pText.textSize = dp(9f)
                pText.color = if (has) cText else cDim
                canvas.drawText(if (has) oneDec(amt) else "-", cx + cs / 2f, cy + cs + dp(11f), pText)
                pText.textAlign = Paint.Align.LEFT
                buttons.add(Btn(r, "lageracc_$ri", "acc"))
                cx += cs + cgap
            }
            // WICHTIG: cs ist dynamisch (haengt von der Bildschirmbreite ab, siehe
            // lagerCellSize()) - yy muss darauf basierend weiterwandern, statt mit einem
            // festen Wert, sonst ueberlappt der Entnahme-Button die Icon-Reihe (auf breiten
            // Bildschirmen wird cs groesser als der frueher fest angenommene Wert).
            yy = cy + cs + dp(20f)
            // Alles entnehmen: kompletten Bestand aller Sorten mit globalem Pool auf einmal
            // in den globalen Bestand ueberfuehren, statt jede Sorte einzeln antippen zu
            // muessen (wird NICHT geloescht, bleibt weiter nutz-/verkaufbar).
            val anyWithdrawable = (0 until n).any { m.output[it] > 0.05 && sim.canWithdrawFromLager(it) }
            val wdR = RectF(margin2, yy, W - margin2, yy + dp(30f))
            drawButton(canvas, Btn(wdR, "lager_wd_all", tr("withdraw_all"), anyWithdrawable, false, cAccent))
            buttons.add(Btn(wdR, "lager_wd_all", "wda", anyWithdrawable))
            yy += dp(38f)
        }

        // Drohnen-Station: Reparatur-Limit einstellen (nur reparieren ab Guthaben >= Limit)
        if (m.type == MType.DROHNE) {
            val y0 = yy - dp(4f); val gh = dp(30f)
            val paused = sim.money < m.moneyGate
            pText.textSize = dp(13f)
            pText.color = if (paused) cWarn else cText
            val lab = "${tr("repair_limit")}: ${m.moneyGate.toInt()} ${tr("geld")}" +
                if (paused) "  (${tr("paused")})" else ""
            canvas.drawText(lab, dp(12f), y0 + dp(20f), pText)
            val step = Simulation.DROHNE_GATE_STEP.toInt()
            val mRect = RectF(W - dp(150f), y0, W - dp(84f), y0 + gh)
            val pRect = RectF(W - dp(76f), y0, W - dp(10f), y0 + gh)
            drawButton(canvas, Btn(mRect, "gate_dn", "− $step", m.moneyGate > 0.0, false, cAccent))
            drawButton(canvas, Btn(pRect, "gate_up", "+ $step", true, false, cAccent))
            buttons.add(Btn(mRect, "gate_dn", "gate_dn", m.moneyGate > 0.0))
            buttons.add(Btn(pRect, "gate_up", "gate_up"))
            yy += dp(36f)
        }

        // Aktions-Buttons unten - schmalerer Rand, damit die Buttons die Breite ausnutzen
        // (statt der vorher breiteren 10dp, die neben dem grosszuegigeren Lager-Rand von
        // nur 6dp unnoetig viel Platz verschenkt haben).
        val margin = dp(6f)
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
            drawButton(canvas, Btn(rSell, "sell_sel", "${tr("sell")} +$refund $curAbbr", true, false, cBad))
            drawButton(canvas, Btn(rRep, "repair_sel", "${tr("repair")} ${Simulation.REPAIR_COST.toInt()} B", repEnabled, false, cAccent))
            buttons.add(Btn(rSell, "sell_sel", "Verkaufen"))
            buttons.add(Btn(rRep, "repair_sel", "Reparieren", repEnabled))
        } else if (canSell) {
            val rSell = RectF(margin, by, W - margin, by + bh)
            drawButton(canvas, Btn(rSell, "sell_sel", "${tr("sell")} +$refund $curAbbr", true, false, cBad))
            buttons.add(Btn(rSell, "sell_sel", "Verkaufen"))
        }
    }

    /**
     * Obere Kante des Infrastruktur-Ausbau-Panels - eigene Funktion (statt inline in
     * drawExpandPanel), damit auch updateGridViewport() weiss, wo die Karte enden muss,
     * OHNE das Panel selbst zu zeichnen (sonst bleibt eine unnoetige graue Luecke
     * zwischen Karte und Panel, wenn die Kartenhoehe noch von der Palette ausging).
     */
    private fun expandPanelTop(): Float {
        val canPlatformHere = sim.canExpandPlatformHere(selR, selC)
        val bh = dp(52f); val gap = dp(10f)
        val rows = if (canPlatformHere) 2 else 1
        return H - dp(58f) - rows * bh - (rows - 1) * gap - dp(6f)
    }

    /**
     * Infrastruktur-Ausbau (Level 2): auf einem leeren, bereits gekauften Landfeld
     * entweder die AKW-Plattform erweitern (bebaubar, ausser Windrad/Solar) oder einen
     * kuenstlichen Wasserkanal graben (nur bei Wasser in der Naehe - Meer ODER ein
     * bereits gegrabener Kanal, damit man ganze Fluesse aneinanderreihen kann; dafuer
     * kein Baugrund mehr - dient nur als schwache Wasserquelle fuer eine Wasserpumpe
     * daneben).
     */
    private fun drawExpandPanel(canvas: Canvas) {
        // Auf einer schon vorhandenen Plattform-Zelle ergibt "Plattform erweitern" keinen
        // Sinn (schon Plattform) - dort nur die Kanal-Option zeigen, Panel entsprechend
        // niedriger. Ein Kanal laesst sich dagegen ueberall graben (auch mitten auf der
        // Plattform).
        val canPlatformHere = sim.canExpandPlatformHere(selR, selC)
        val bh = dp(52f); val gap = dp(10f)
        val top = expandPanelTop()
        p.color = cPanel
        canvas.drawRect(0f, top, W.toFloat(), H.toFloat(), p)
        p.color = cPanelHi
        canvas.drawRect(0f, top, W.toFloat(), top + dp(2f), p)

        pText.color = cAccent; pText.textSize = dp(18f)
        canvas.drawText(tr("expand_title"), dp(12f), top + dp(26f), pText)
        pText.color = cDim; pText.textSize = dp(12.5f)
        canvas.drawText(tr("expand_hint"), dp(12f), top + dp(46f), pText)

        val margin = dp(10f)
        val bw = W - 2 * margin
        var by = top + dp(58f)

        if (canPlatformHere) {
            val platCost = sim.expandPlatformCost()
            val canPlat = sim.money >= platCost
            val r1 = RectF(margin, by, margin + bw, by + bh)
            drawButton(canvas, Btn(r1, "expand_plat", tr("expand_platform"), canPlat, false, cAccent, "${platCost.toInt()}€"))
            buttons.add(Btn(r1, "expand_plat", "ep", canPlat))
            by += bh + gap
        }

        val waterNear = sim.hasWaterAdjacent(selR, selC)
        val canalCost = sim.expandCanalCost()
        val canCanal = waterNear && sim.money >= canalCost
        val r2 = RectF(margin, by, margin + bw, by + bh)
        val canalSub = if (waterNear) "${canalCost.toInt()}€" else tr("expand_needs_water")
        drawButton(canvas, Btn(r2, "expand_canal", tr("expand_canal"), canCanal, false, cAccent, canalSub, if (!waterNear) cBad else 0))
        buttons.add(Btn(r2, "expand_canal", "ec", canCanal))
    }

    private fun bnLabel(code: Int) = when (code) {
        1 -> tr("bn_input"); 2 -> tr("bn_output"); 3 -> tr("bn_power")
        4 -> tr("bn_dead"); 5 -> tr("bn_soil"); 6 -> tr("bn_water"); else -> tr("bn_ok")
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
        // Nur Techs zeigen, die im aktuellen Unternehmens-Level ueberhaupt Sinn ergeben
        // (z.B. keine "Presse freischalten" im Nuklear-Modus, nur "Bleipresse").
        val techs = Simulation.TECHS.filter { sim.techVisible(it.id) }
        val content = techs.size * (bh + gap)
        techMaxScroll = (content - (bandBottom - startY)).coerceAtLeast(0f)
        techScroll = techScroll.coerceIn(0f, techMaxScroll)

        for ((i, node) in techs.withIndex()) {
            val yy = startY + i * (bh + gap) - techScroll
            if (yy + bh < startY || yy > bandBottom) continue
            val rect = RectF(dp(12f), yy, W - dp(12f), yy + bh)
            val l = sim.lvl(node.id)
            val maxed = l >= node.maxLevel
            val prereq = sim.prereqOf(node)
            val preOk = prereq == null || sim.has(prereq)
            val cost = sim.techDisplayCost(node)
            val afford = sim.techAffordable(node)
            val nuclearPaid = sim.companyLevel >= 2 && node.costRes != null
            val cur = if (nuclearPaid) "€" else if (node.costRes != null) resAbbr(node.costRes) else "◆ ${tr("res_short")}"
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
                !preOk -> "${tr("requires")}: ${tr(prereq!!)}"
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
        canvas.drawText("◆ ${fmt(sim.research)}  ·  ${tr("geld")} ${fmt(sim.money)}  ·  B ${fmt(sim.availableBarren())}  ·  P ${fmt(sim.availablePlatten())}", dp(16f), dp(58f), pText)

        val cr = RectF(W / 2f - dp(70f), H - dp(56f), W / 2f + dp(70f), H - dp(16f))
        drawButton(canvas, Btn(cr, "close", tr("close"), true, false, cAccent))
        buttons.add(Btn(cr, "close", "Schliessen"))
    }

    private fun techEffect(id: String): String = when (id) {
        "t_assembler" -> tr("tf_assembler"); "t_haendler" -> tr("tf_haendler"); "t_boost" -> tr("tf_boost")
        "t_wind" -> tr("tf_wind")
        "t_solar" -> tr("tf_solar"); "t_research" -> tr("tf_research")
        "t_diag" -> tr("tf_diag")
        "t_bspeed", "t_ospeed", "t_pspeed", "t_aspeed",
        "t_pbspeed", "t_wpspeed", "t_zfspeed", "t_bpspeed", "t_bwspeed", "t_rkspeed", "t_ktspeed" -> tr("tf_speed")
        "t_wert" -> tr("tf_wert"); "t_scan" -> tr("tf_scan"); "t_takt" -> tr("tf_takt")
        "t_robust" -> tr("tf_robust"); "t_lift" -> tr("tf_lift"); "t_power" -> tr("tf_power")
        "t_drohne_rep" -> tr("tf_drohne_rep"); "t_drohne_speed" -> tr("tf_drohne_speed")
        "t_drohne_range" -> tr("tf_drohne_range"); "t_research_rate" -> tr("tf_research_rate")
        "t_reaktorkern" -> tr("tf_reaktorkern"); "t_kuehlturm" -> tr("tf_kuehlturm")
        else -> ""
    }

    /**
     * Statistik + Einstellungen. Der Inhalt (Ressourcen, Produktion, Hinweise, Sprache,
     * Ton) ist scrollbar zwischen einem festen Kopf (Titel) und Fuss (Hauptmenue,
     * Reset, Schliessen) - so ueberlappt nichts mehr, egal wie viele Maschinentypen
     * (Level 1 oder 2) in der Produktionsliste stehen.
     */
    private fun drawStat(canvas: Canvas) {
        p.color = Color.argb(246, 58, 50, 42)
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)

        val margin = dp(12f)
        val startY = dp(66f)
        val footerH = dp(150f)
        val bandBottom = H - footerH
        fun visible(top: Float, h: Float) = top + h >= startY && top <= bandBottom

        var yy = startY - statScroll
        val nuc = sim.companyLevel >= 2
        val lines = listOf(
            "${tr("s_money")}: ${fmt(sim.money)}   (+${fmt(sim.moneyPerMin)}/min)",
            "${tr(if (nuc) "angeruran" else "s_barren")}: ${fmt(sim.availableBarren())}  (${oneDec(sim.barrenPerMin)}/min)",
            "${tr(if (nuc) "bleiverkl" else "s_platten")}: ${fmt(sim.availablePlatten())}  (${oneDec(sim.plattenPerMin)}/min)",
            "${tr(if (nuc) "netzstrom" else "s_komp")}: ${fmt(if (nuc) sim.availableStrom() else sim.availableKomponente())}  (${oneDec(sim.komponentenPerMin)}/min)",
            "${tr("s_strom")}: ${fmt(sim.powerSupply)} / ${fmt(sim.powerDemand)}",
            "${tr("s_area")}: ${sim.areaN()} x ${sim.areaN()}",
            "${tr("s_machines")}: ${machineCount()}",
            "${tr("s_upgrades")}: ${sim.tech.values.sum()}"
        )
        pText.textSize = dp(15f)
        for (l in lines) {
            if (visible(yy - dp(15f), dp(25f))) { pText.color = cText; canvas.drawText(l, dp(16f), yy, pText) }
            yy += dp(25f)
        }

        // Produktions-Auslastung je Typ (geglaettet) + groesster Engpass
        yy += dp(6f)
        if (visible(yy - dp(16f), dp(22f))) { pText.color = cAccent; pText.textSize = dp(16f); canvas.drawText(tr("s_prod_title"), dp(16f), yy, pText) }
        yy += dp(22f)
        val producers = if (nuc)
            listOf(MType.BOHRER, MType.BLEIBOHRER, MType.WASSERPUMPE, MType.ZENTRIFUGE, MType.BLEIPRESSE, MType.BRENNSTABWERK,
                MType.REAKTORKERN, MType.KUEHLTURM, MType.HAENDLER)
        else listOf(MType.BOHRER, MType.OFEN, MType.PRESSE, MType.ASSEMBLER, MType.HAENDLER)
        pText.textSize = dp(13f)
        for (t in producers) {
            val i = t.ordinal
            val cnt = sim.typeCount[i]
            if (cnt == 0) continue
            if (visible(yy - dp(11f), dp(20f))) {
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
            }
            yy += dp(20f)
        }
        val bn = bottleneckSummary()
        yy += dp(2f)
        if (visible(yy - dp(13f), dp(20f))) { pText.color = cDim; pText.textSize = dp(13f); canvas.drawText("${tr("s_bottleneck")}: $bn", dp(16f), yy, pText) }

        yy += dp(20f)
        pText.textSize = dp(12f)
        for (hint in listOf(tr("hint_floor"), tr("hint_trade"), tr("hint_unlock"))) {
            if (visible(yy - dp(12f), dp(16f))) { pText.color = cDim; canvas.drawText(hint, dp(16f), yy, pText) }
            yy += dp(16f)
        }

        // Sprachauswahl DE / EN / PL
        yy += dp(20f)
        if (visible(yy - dp(14f), dp(20f))) { pText.color = cText; pText.textSize = dp(14f); canvas.drawText("${tr("lang")}:", dp(16f), yy, pText) }
        val lw = (W - 2 * margin - dp(80f) - 2 * dp(6f)) / 3f
        val ly = yy + dp(16f); val lh = dp(34f)
        val langs = listOf(Lang.DE to "DE", Lang.EN to "EN", Lang.PL to "PL")
        if (visible(ly, lh)) {
            for ((i, lv) in langs.withIndex()) {
                val lx = dp(80f) + margin + i * (lw + dp(6f))
                val lr = RectF(lx, ly, lx + lw, ly + lh)
                drawButton(canvas, Btn(lr, "lang_${lv.first.name}", lv.second, true, I18n.lang == lv.first, cAccent))
                buttons.add(Btn(lr, "lang_${lv.first.name}", "lang"))
            }
        }

        // Ton-Einstellungen: Musik + Effekte, je vier Stufen
        var sy = ly + lh + dp(12f)
        sy = drawSoundRow(canvas, "snd_music", (audio.musicVol * 100).roundToInt(), "mvol", sy, margin, startY, bandBottom)
        sy = drawSoundRow(canvas, "snd_sfx", (audio.sfxVol * 100).roundToInt(), "svol", sy, margin, startY, bandBottom)
        yy = sy

        // Zuschauer-Modus: andere eigene Unternehmen (Spielstaende) nur ANSEHEN, ohne
        // etwas daran aendern zu koennen - z.B. um kurz eine zweite Firma zu pruefen.
        val otherSlots = saveStore.slots().filter { it.id != currentSlot }
        if (otherSlots.isNotEmpty()) {
            yy += dp(10f)
            if (visible(yy - dp(14f), dp(20f))) {
                pText.color = cText; pText.textSize = dp(14f)
                canvas.drawText(tr("view_company"), dp(16f), yy, pText)
            }
            yy += dp(4f)
            if (visible(yy, dp(16f))) {
                pText.color = cDim; pText.textSize = dp(11f)
                canvas.drawText(tr("view_company_hint"), dp(16f), yy + dp(11f), pText)
            }
            yy += dp(20f)
            val rowH = dp(36f); val rowGap = dp(6f)
            for (s in otherSlots) {
                if (visible(yy, rowH)) {
                    val rr = RectF(margin, yy, W - margin, yy + rowH)
                    val active = viewOnlyId == s.id
                    drawButton(canvas, Btn(rr, "vo_${s.id}", "${s.name}  ·  ${fmt(s.money)}€", true, active, cAccent))
                    buttons.add(Btn(rr, "vo_${s.id}", "vo"))
                }
                yy += rowH + rowGap
            }
        }

        statMaxScroll = (yy + statScroll - bandBottom).coerceAtLeast(0f)
        statScroll = statScroll.coerceIn(0f, statMaxScroll)

        // Kopf- und Fussbereich ueberdecken (verdeckt gescrollten Inhalt sauber).
        p.color = Color.argb(246, 58, 50, 42)
        canvas.drawRect(0f, 0f, W.toFloat(), startY, p)
        canvas.drawRect(0f, bandBottom, W.toFloat(), H.toFloat(), p)
        pText.color = cAccent; pText.textSize = dp(22f)
        canvas.drawText(tr("stat_title"), dp(16f), dp(40f), pText)

        // Fester Fussbereich: Hauptmenue, Reset, Schliessen.
        val menuR = RectF(margin, H - dp(104f), W - margin, H - dp(104f) + dp(38f))
        drawButton(canvas, Btn(menuR, "to_menu", tr("to_menu"), true, false, cAccent))
        buttons.add(Btn(menuR, "to_menu", "menu"))

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
    /** `bandTop`/`bandBottom`: nur zeichnen/anfassbar machen, wenn innerhalb des sichtbaren (gescrollten) Bereichs. */
    private fun drawSoundRow(
        canvas: Canvas, labelKey: String, cur: Int, idPrefix: String, y: Float, margin: Float,
        bandTop: Float = -1e9f, bandBottom: Float = 1e9f
    ): Float {
        val by = y + dp(8f); val bh = dp(34f)
        if (by + bh >= bandTop && y <= bandBottom) {
            pText.color = cText; pText.textSize = dp(14f); pText.textAlign = Paint.Align.LEFT
            canvas.drawText(tr(labelKey), dp(16f), y + dp(2f), pText)
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
        }
        return by + bh + dp(12f)
    }

    /** Horizontale Scanlines fuer den CRT-/Terminal-Look. */
    private fun drawScanlines(canvas: Canvas) {
        p.color = Color.argb(26, 0, 0, 0)
        var sy = 0f
        while (sy < H) { canvas.drawRect(0f, sy, W.toFloat(), sy + 1f, p); sy += 3f }
    }

    /** Schwarz-gelber Gefahrenstreifen. */
    private fun hazardBar(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        var i = 0f
        while (i < w) {
            p.color = if ((i / h).toInt() % 2 == 0) Color.rgb(238, 198, 70) else Color.rgb(22, 22, 26)
            canvas.drawRect(x + i, y, x + kotlin.math.min(i + h, w), y + h, p)
            i += h
        }
    }

    /** Animierte Fabrik-Kulisse hinter dem Startmenue. */
    private fun drawMenuBackground(canvas: Canvas) {
        p.color = Color.rgb(30, 32, 42); canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        p.color = Color.rgb(22, 24, 32); canvas.drawRect(0f, H * 0.52f, W.toFloat(), H.toFloat(), p)
        val groundY = H * 0.70f
        val tS = dp(38f)
        var gx = 0f
        while (gx < W) { dstTile.set(gx, groundY, gx + tS, groundY + tS); canvas.drawBitmap(grassTiles[1], srcTile, dstTile, pTile); gx += tS }
        p.color = Color.rgb(20, 22, 28); canvas.drawRect(0f, groundY + tS, W.toFloat(), H.toFloat(), p)
        val sz = dp(50f)
        fun place(t: MType, cxFrac: Float, scale: Float) {
            val s = sz * scale; val cx = W * cxFrac
            dstTile.set(cx - s / 2, groundY - s + dp(6f), cx + s / 2, groundY + dp(6f))
            canvas.drawBitmap(machBmp[t.ordinal], null, dstTile, pTile)
        }
        place(MType.BOHRER, 0.12f, 0.85f)
        place(MType.OFEN, 0.30f, 0.95f)
        place(MType.GENERATOR, 0.46f, 0.9f)
        place(MType.REAKTOR, 0.76f, 1.3f)
        // Windrad (rotierend)
        run {
            val hx = W * 0.60f; val hy = groundY - sz * 1.15f
            p.color = Color.rgb(196, 202, 214); canvas.drawRect(hx - dp(2f), hy, hx + dp(2f), groundY, p)
            val ang = animT * 65f
            for (k in 0 until 3) {
                canvas.save(); canvas.rotate(ang + k * 120f, hx, hy)
                bladePath.reset()
                bladePath.moveTo(hx - dp(3f), hy); bladePath.lineTo(hx + dp(3f), hy)
                bladePath.lineTo(hx + dp(1f), hy - dp(28f)); bladePath.lineTo(hx - dp(1f), hy - dp(28f)); bladePath.close()
                canvas.drawPath(bladePath, pBlade); canvas.restore()
            }
            p.color = Color.rgb(52, 56, 64); canvas.drawCircle(hx, hy, dp(3f), p)
        }
        // Dampf ueber Ofen + Reaktor
        for (pair in listOf(W * 0.30f to groundY - sz * 0.95f, W * 0.76f to groundY - sz * 1.3f)) {
            for (k in 0 until 3) {
                val ph = (animT * 0.4f + k * 0.33f) % 1f
                val al = (85 * (1f - ph)).toInt().coerceIn(0, 255)
                p.color = Color.argb(al, 226, 230, 238)
                canvas.drawCircle(pair.first + (k - 1) * dp(5f), pair.second - ph * dp(38f), dp(4f) + ph * dp(6f), p)
            }
        }
        // Ofen-Glut (pulsierend)
        val glow = 0.5f + 0.5f * kotlin.math.sin(animT * 3f)
        p.color = Color.argb((120 * glow).toInt().coerceIn(0, 255), 250, 150, 60)
        canvas.drawCircle(W * 0.30f, groundY - sz * 0.32f, dp(6f), p)
        // Overlay + Scanlines
        p.color = Color.argb(130, 10, 10, 14); canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        drawScanlines(canvas)
    }

    /** Firmen-/Prestige-Screen: Level, Aktien, Dividenden, Verkauf. */
    private fun drawCompany(canvas: Canvas) {
        p.color = Color.rgb(16, 18, 24); canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        hazardBar(canvas, 0f, 0f, W.toFloat(), dp(8f))
        pText.textAlign = Paint.Align.LEFT
        pText.color = cAccent; pText.textSize = dp(22f)
        canvas.drawText(tr("company_title"), dp(16f), dp(44f), pText)
        pText.color = cText; pText.textSize = dp(15f)
        canvas.drawText("LVL ${sim.companyLevel} · ${tr("lvl" + sim.companyLevel.coerceAtMost(4))}", dp(16f), dp(70f), pText)

        // Fortschritt zum Ziel
        var yy = dp(96f)
        pText.color = cDim; pText.textSize = dp(13f)
        canvas.drawText("${tr("goal")}: ${fmt(sim.levelGoal())}", dp(16f), yy, pText)
        yy += dp(10f)
        val bl = dp(16f); val br = W - dp(16f)
        p.color = cGridLine; canvas.drawRect(bl, yy, br, yy + dp(14f), p)
        val ready = sim.canSellCompany()
        p.color = if (ready) cAccent else Color.rgb(96, 170, 220)
        canvas.drawRect(bl, yy, bl + (br - bl) * sim.levelProgress().toFloat(), yy + dp(14f), p)
        pText.color = cText; pText.textSize = dp(12f); pText.textAlign = Paint.Align.CENTER
        canvas.drawText("${fmt(sim.money)} / ${fmt(sim.levelGoal())}", (bl + br) / 2f, yy + dp(11f), pText)
        pText.textAlign = Paint.Align.LEFT
        yy += dp(38f)

        // Kennzahlen
        pText.textSize = dp(14f)
        for (line in listOf(
            "${tr("shares")}: ${fmt(sim.shares)}   (+${((sim.shareBonus() - 1.0) * 100).roundToInt()}% ${tr("value")})",
            "${tr("dividends")}: ${fmt(sim.dividends)}/s",
            "${tr("lvl_bonus")}: x${fmt(sim.companyMult())} ${tr("value")}"
        )) { pText.color = cText; canvas.drawText(line, dp(16f), yy, pText); yy += dp(24f) }

        yy += dp(6f)
        hazardBar(canvas, dp(16f), yy, W - dp(32f), dp(7f)); yy += dp(24f)
        pText.color = cDim; pText.textSize = dp(12f)
        for (line in listOf(tr("sell_keep"), tr("sell_lose"))) { canvas.drawText(line, dp(16f), yy, pText); yy += dp(18f) }
        if (ready) {
            yy += dp(8f)
            pText.color = cGood; pText.textSize = dp(14f)
            canvas.drawText("+ ${fmt(sim.sharesGain())} ${tr("shares")}", dp(16f), yy, pText)
        }

        // Buttons
        val by = H - dp(58f); val bh = dp(42f); val margin = dp(14f)
        val half = (W - 3 * margin) / 2f
        val rSell = RectF(margin, by, margin + half, by + bh)
        val rClose = RectF(margin * 2 + half, by, margin * 2 + half * 2, by + bh)
        val lbl = if (!ready) tr("sale_locked") else if (sellArmed) tr("sell_confirm") else tr("sell_company")
        drawButton(canvas, Btn(rSell, "sell_company", lbl, ready, sellArmed, cAccent))
        drawButton(canvas, Btn(rClose, "close", tr("close"), true, false, cAccent))
        if (ready) buttons.add(Btn(rSell, "sell_company", "sell"))
        buttons.add(Btn(rClose, "close", "close"))
    }

    private fun drawReport(canvas: Canvas) {
        val rep = report ?: return
        val green = Color.rgb(74, 240, 122); val amber = Color.rgb(255, 183, 0); val red = Color.rgb(255, 66, 60)
        // CRT-Terminal-Grund + Metallrahmen + Hazard
        p.color = Color.rgb(8, 16, 10); canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        p.color = Color.rgb(40, 44, 40); canvas.drawRect(0f, 0f, W.toFloat(), dp(6f), p)
        hazardBar(canvas, 0f, dp(6f), W.toFloat(), dp(8f))
        pText.textAlign = Paint.Align.LEFT
        pText.color = amber; pText.textSize = dp(23f)
        canvas.drawText("SCHICHTBERICHT", dp(16f), dp(46f), pText)
        pText.color = green; pText.textSize = dp(14f)
        canvas.drawText("ABWESENHEIT ${fmtDur(rep.elapsedSeconds)}   ·   SIM ${fmtDur(rep.simSeconds)}   ·   CAP 8h", dp(16f), dp(70f), pText)
        p.color = Color.argb(120, 74, 240, 122); canvas.drawRect(dp(16f), dp(80f), W - dp(16f), dp(81f), p)
        // Ertraege
        pText.textSize = dp(16f); pText.color = green
        var yy = dp(108f)
        canvas.drawText("+ ${fmt(rep.moneyGained)}  ${tr("geld").uppercase()}", dp(20f), yy, pText); yy += dp(25f)
        canvas.drawText("+ ${fmt(rep.barrenGained)}  ${tr("barren").uppercase()}", dp(20f), yy, pText); yy += dp(25f)
        canvas.drawText("+ ${fmt(rep.plattenGained)}  ${tr("platten").uppercase()}", dp(20f), yy, pText); yy += dp(30f)
        // Wartungs-Warnung
        val dead = rep.events.count { it.dead }
        if (dead > 0) {
            hazardBar(canvas, dp(16f), yy - dp(14f), W - dp(32f), dp(7f))
            pText.color = red; pText.textSize = dp(15f)
            canvas.drawText("WARNUNG: $dead ${tr("need_service")}", dp(20f), yy + dp(6f), pText); yy += dp(30f)
        }
        // Protokoll
        pText.color = Color.argb(210, 74, 240, 122); pText.textSize = dp(13f)
        canvas.drawText("PROTOKOLL:", dp(20f), yy, pText); yy += dp(20f)
        if (rep.events.isEmpty()) {
            pText.color = Color.argb(150, 74, 240, 122)
            canvas.drawText(tr("all_ran"), dp(28f), yy, pText)
        } else {
            for (e in rep.events) {
                if (yy > H - dp(96f)) break
                pText.color = if (e.dead) red else amber
                canvas.drawText("${fmtDur(e.timeSec)}  ${mName(e.mType)} (${e.r + 1},${e.c + 1})  ${if (e.dead) tr("ev_dead") else tr("ev_starved")}", dp(28f), yy, pText)
                yy += dp(20f)
            }
        }
        drawScanlines(canvas)
        val cr = RectF(dp(24f), H - dp(72f), W - dp(24f), H - dp(20f))
        drawButton(canvas, Btn(cr, "collect", "▸ ${tr("collect")}", true, false, cGood))
        buttons.add(Btn(cr, "collect", "collect"))
    }

    /**
     * Ladebildschirm waehrend das Offline-Nachrechnen auf dem Hintergrund-Thread laeuft.
     * Blockiert jede Eingabe (siehe onTouchEvent/handleClick), zeigt einen echten
     * Fortschrittsbalken statt eines unbestimmten Spinners.
     */
    private fun drawLoading(canvas: Canvas) {
        canvas.drawColor(cBg)
        pText.textAlign = Paint.Align.CENTER
        pText.color = cAccent; pText.textSize = dp(20f)
        canvas.drawText(tr("loading_title"), W / 2f, H / 2f - dp(40f), pText)
        pText.color = cDim; pText.textSize = dp(13f)
        canvas.drawText(tr("loading_hint"), W / 2f, H / 2f - dp(16f), pText)

        val barW = (W - dp(64f)).coerceAtMost(dp(360f)); val barH = dp(18f)
        val bx = (W - barW) / 2f; val by = H / 2f
        p.color = cGridLine; canvas.drawRoundRect(RectF(bx, by, bx + barW, by + barH), dp(4f), dp(4f), p)
        p.color = cAccent
        canvas.drawRoundRect(RectF(bx, by, bx + barW * loadingProgress.coerceIn(0f, 1f), by + barH), dp(4f), dp(4f), p)
        p.color = cPanelHi; p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.5f)
        canvas.drawRoundRect(RectF(bx, by, bx + barW, by + barH), dp(4f), dp(4f), p); p.style = Paint.Style.FILL

        pText.color = cText; pText.textSize = dp(13f)
        canvas.drawText("${(loadingProgress.coerceIn(0f, 1f) * 100).roundToInt()}%", W / 2f, by + barH + dp(24f), pText)
        pText.textAlign = Paint.Align.LEFT

        hazardBar(canvas, 0f, H - dp(7f), W.toFloat(), dp(7f))
    }

    private fun drawMenu(canvas: Canvas) {
        // Animierte Fabrik-Kulisse
        drawMenuBackground(canvas)
        // Kopfbanner (Konsole mit Nieten + Hazard-Kante)
        p.color = Color.argb(238, 40, 42, 52)
        canvas.drawRect(0f, 0f, W.toFloat(), dp(98f), p)
        p.color = cPanelHi; canvas.drawRect(0f, 0f, W.toFloat(), dp(2f), p)
        p.color = Color.argb(90, 210, 216, 228)
        var rx = dp(8f)
        while (rx < W - dp(8f)) { canvas.drawCircle(rx, dp(6f), dp(1.2f), p); rx += dp(18f) }
        hazardBar(canvas, 0f, dp(98f), W.toFloat(), dp(7f))
        pText.textAlign = Paint.Align.LEFT
        pText.color = cAccent; pText.textSize = dp(30f)
        canvas.drawText("DEEP INDUSTRY", dp(16f), dp(48f), pText)
        pText.color = cText; pText.textSize = dp(14f)
        canvas.drawText("⚙ ${tr("choose_save")}", dp(16f), dp(80f), pText)

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
        val pressed = pressedBtn == b.id && b.enabled
        if (pressed) { canvas.save(); canvas.translate(0f, dp(1.5f)) }
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
        if (pressed) {
            p.color = Color.argb(40, 0, 0, 0); canvas.drawRoundRect(b.rect, dp(9f), dp(9f), p)
            canvas.restore()
        }
    }

    // ---------------- Eingabe ----------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Waehrend des Offline-Nachrechnens (Hintergrund-Thread) jede Eingabe blockieren.
        if (screen == Screen.LOADING) return true
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        val slop = dp(8f)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; downScroll = techScroll; downStatScroll = statScroll
                lastPanX = panX; lastPanY = panY; moved = false
                downInGrid = downX >= gridLeft && downX <= gridLeft + gridW &&
                    downY >= gridTop && downY <= gridTop + gridH
                // Button unter dem Finger fuer Druck-Feedback merken
                pressedBtn = buttons.lastOrNull { it.enabled && it.rect.contains(downX, downY) }?.id
                if (pressedBtn != null) invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.abs(event.x - downX) > slop || kotlin.math.abs(event.y - downY) > slop) {
                    moved = true
                    if (pressedBtn != null) { pressedBtn = null; invalidate() }
                }
                // Karte-Scrollen laeuft ueber gestureDetector.onScroll.
                if (screen == Screen.TECH) {
                    techScroll = (downScroll - (event.y - downY)).coerceIn(0f, techMaxScroll)
                    invalidate()
                }
                if (screen == Screen.STAT) {
                    statScroll = (downStatScroll - (event.y - downY)).coerceIn(0f, statMaxScroll)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!moved && !scaleDetector.isInProgress) handleClick(event.x, event.y)
                if (pressedBtn != null) { pressedBtn = null; invalidate() }
                return true
            }
            MotionEvent.ACTION_CANCEL -> { if (pressedBtn != null) { pressedBtn = null; invalidate() }; return true }
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
        // Im Zuschauer-Modus (fremdes Unternehmen ansehen) nur Navigation/Anzeige erlauben -
        // alles was den Spielstand aendern wuerde stumm abweisen. "to_menu" beendet den
        // Zuschauer-Modus zuerst, damit man dort wieder im EIGENEN Spiel landet.
        if (viewOnly && id == "to_menu") exitViewOnly()
        else if (viewOnly) {
            val navAllowed = id == "tech" || id == "stat" || id == "close" || id == "company" ||
                id.startsWith("mvol_") || id.startsWith("svol_") || id.startsWith("lang_") ||
                id == "exit_viewonly" || id.startsWith("vo_") || id == "palette_toggle"
            if (!navAllowed) { audio.error(); invalidate(); return }
        }
        when {
            id == "exit_viewonly" -> exitViewOnly()
            id.startsWith("vo_") -> {
                val sid = id.removePrefix("vo_")
                if (viewOnlyId == sid) exitViewOnly() else enterViewOnly(sid)
            }
            id == "palette_toggle" -> {
                paletteCollapsed = !paletteCollapsed
                prefs.edit().putBoolean("paletteCollapsed", paletteCollapsed).apply()
                audio.click()
            }
            id == "tech" -> { screen = if (screen == Screen.TECH) Screen.GAME else Screen.TECH; selR = -1; resetArmed = false; techScroll = 0f; audio.click() }
            id == "stat" -> { screen = if (screen == Screen.STAT) Screen.GAME else Screen.STAT; selR = -1; resetArmed = false; statScroll = 0f; audio.click() }
            id == "close" -> { screen = Screen.GAME; report = null; resetArmed = false; sellArmed = false; audio.click() }
            id == "company" -> { screen = if (screen == Screen.COMPANY) Screen.GAME else Screen.COMPANY; selR = -1; sellArmed = false; audio.click() }
            id == "sell_company" -> {
                if (!sellArmed) { sellArmed = true; audio.click() }
                else {
                    if (sim.sellCompany()) {
                        persist(); sellArmed = false; buildTool = null; selR = -1; selC = -1
                        needCenter = true; screen = Screen.GAME; audio.buy()
                    } else audio.error()
                }
            }
            id == "collect" -> { screen = Screen.GAME; report = null; resetArmed = false; audio.buy() }
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
            id == "gate_up" -> { if (selR >= 0) { sim.adjustDroneGate(selR, selC, Simulation.DROHNE_GATE_STEP); persist(); audio.click() } }
            id == "gate_dn" -> { if (selR >= 0) { sim.adjustDroneGate(selR, selC, -Simulation.DROHNE_GATE_STEP); persist(); audio.click() } }
            id == "repair_sel" -> { if (selR >= 0) sim.grid[selR][selC]?.let { if (sim.repair(it)) audio.buy() else audio.error() } }
            id == "sell_sel" -> { if (selR >= 0) { sim.sell(selR, selC); selR = -1; selC = -1; audio.sell() } }
            id == "upgrade_sel" -> { if (selR >= 0) { if (sim.upgradeMachine(selR, selC)) audio.buy() else audio.error() } }
            id == "expand_plat" -> {
                if (selR >= 0) {
                    val cost = sim.expandPlatformCost()
                    if (sim.expandToPlatform(selR, selC)) {
                        rises.add(Rise(selR, selC, "-${cost.toInt()}", animT)); selR = -1; selC = -1; audio.buy()
                    } else audio.error()
                }
            }
            id == "expand_canal" -> {
                if (selR >= 0) {
                    val cost = sim.expandCanalCost()
                    if (sim.expandToCanal(selR, selC)) {
                        rises.add(Rise(selR, selC, "-${cost.toInt()}", animT)); selR = -1; selC = -1; audio.buy()
                    } else audio.error()
                }
            }
            id.startsWith("lageracc_") -> {
                if (selR >= 0) sim.grid[selR][selC]?.let { m ->
                    val ri = id.removePrefix("lageracc_").toIntOrNull()
                    if (ri != null && ri in m.acceptRes.indices) { m.acceptRes[ri] = !m.acceptRes[ri]; audio.click() }
                }
            }
            id == "lager_wd_all" -> {
                if (selR >= 0) {
                    var total = 0.0
                    for (ri in Res.values().indices) total += sim.withdrawFromLager(selR, selC, ri)
                    if (total > 1e-9) { rises.add(Rise(selR, selC, "+${oneDec(total)}", animT)); audio.sell() } else audio.error()
                }
            }
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
            invalidate(); return
        }
        // Im Zuschauer-Modus keine Chunks freischalten, Hindernisse entfernen oder bauen -
        // nur bereits platzierte Maschinen ansehen (siehe Anker-Auswahl oben).
        if (viewOnly) { selR = -1; selC = -1; audio.error(); invalidate(); return }
        // 1) Gesperrter Chunk? -> mit Geld freischalten
        if (sim.isLocked(r, c)) {
            val cost = sim.chunkCost().toInt()
            if (sim.freeChunk(r, c)) { rises.add(Rise(r, c, "-$cost", animT)); audio.buy() }
            else audio.error()
            invalidate(); return
        }
        // 2) Hindernis (Baum/Busch/Fels)? -> mit Geld entfernen, dann baubar
        if (sim.hasObstacle(r, c)) {
            val cost = sim.obstacleCost(r, c)
            if (sim.clearObstacle(r, c)) { rises.add(Rise(r, c, "-$cost", animT)); audio.sell() }
            else audio.error()
            invalidate(); return
        }
        // 3) Freies, geraeumtes Feld: bauen (falls Werkzeug), sonst - auf bereits gekauftem
        // Land ab Level 2 - die Infrastruktur-Ausbauwahl anbieten (AKW-Flaeche/Kanal),
        // ansonsten die Auswahl einfach loeschen.
        val t = buildTool
        if (t != null) {
            if (sim.build(t, r, c)) { puffs.add(Puff(r, c, animT)); selR = -1; selC = -1; audio.place() } else audio.error()
        } else if (sim.canExpandInfra(r, c)) {
            selR = r; selC = c; audio.click()
        } else { selR = -1; selC = -1 }
        invalidate()
    }

    /** Zaehlt die Engpass-Ursachen ueber alle Maschinen und benennt die haeufigste. */
    private fun bottleneckSummary(): String {
        val counts = IntArray(7)
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
        for (i in 1 until 7) if (counts[i] > counts[worst]) worst = i
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
        // Waehrend des Offline-Nachrechnens laeuft ein Hintergrund-Thread auf sim.* -
        // hier NICHT gleichzeitig speichern (der Spielstand auf der Platte ist noch der
        // unveraenderte, gerade erst geladene - das reicht, bis der Thread fertig ist).
        if (screen != Screen.LOADING) persist()
        audio.release()
    }

    // ---------------- Format-Helfer ----------------

    private fun fmt(v: Double): String {
        val a = if (v < 0) 0.0 else v
        return when {
            a >= 1e12 -> oneDec(a / 1e12) + "Bio"
            a >= 1e9 -> oneDec(a / 1e9) + "Mrd"
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
