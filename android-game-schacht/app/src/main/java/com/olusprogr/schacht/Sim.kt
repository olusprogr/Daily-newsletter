package com.olusprogr.schacht

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.json.JSONArray
import org.json.JSONObject

/** Rohstoffe. Tier 1-3: Roherz -> Barren -> Platte -> Komponente. */
enum class Res { ROHERZ, BARREN, PLATTE, KOMPONENTE }

// Reihenfolge = Save-Ordinal. Neue Typen ans ENDE anhaengen (Save-Kompatibilitaet).
enum class MType(val label: String, val sym: String, val power: Double) {
    BOHRER("Bohrer", "B", 4.0),
    OFEN("Ofen", "O", 6.0),
    PRESSE("Presse", "P", 8.0),
    GENERATOR("Generator", "G", 0.0),
    LAGER("Lager", "L", 0.0),
    DROHNE("Wartungsdrohne", "D", 5.0),
    REAKTOR("Reaktor", "R", 0.0),
    ASSEMBLER("Assembler", "A", 10.0),
    VERSTAERKER("Verstaerker", "V", 6.0),
    HAENDLER("Haendler", "H", 0.0),
    PROSPEKTOR("Prospektor", "S", 2.0),
    WINDRAD("Windrad", "W", 0.0),
    SOLAR("Solarpanel", "So", 0.0),
    FORSCHUNG("Forschungszentrum", "Fz", 12.0)
}

class Machine(var type: MType) {
    var condition = 100.0
    val input = DoubleArray(Res.values().size)
    val output = DoubleArray(Res.values().size)
    var util = 0.0
    var starved = false
    var w = 1     // Grundflaeche (Breite in Zellen)
    var h = 1     // Grundflaeche (Hoehe in Zellen), Anker = unterste Zelle
    // Nur fuer die Drohnen-Station: aktuell angeflogene/reparierte Maschine (-1 = keine).
    var svR = -1
    var svC = -1
    // Drohnen-Station: repariert nur, wenn Guthaben >= dieser Schwelle (per Klick einstellbar).
    var moneyGate = 0.0
}

data class OfflineEvent(val timeSec: Int, val dead: Boolean, val mType: MType, val r: Int, val c: Int)

data class OfflineReport(
    val elapsedSeconds: Int,
    val simSeconds: Int,
    val barrenGained: Double,
    val plattenGained: Double,
    val moneyGained: Double,
    val events: List<OfflineEvent>
)

/**
 * Ein Tech-Knoten. `costRes` bestimmt die Waehrung: BARREN/PLATTE fuer
 * Maschinen-Freischaltungen, null = Geld fuer die eigentlichen Upgrades.
 */
data class TechNode(
    val id: String,
    val label: String,
    val baseCost: Double,
    val growth: Double,
    val maxLevel: Int,
    val effect: String,
    val prereq: String?,
    val costRes: Res?
)

class Simulation {
    val n = 40                       // grosse Welt: 40 x 40 Chunks
    val startR = n / 2
    val startC = n / 2
    val grid = Array(n) { arrayOfNulls<Machine>(n) }
    /** Belegung durch mehrzellige Gebaeude: haelt die Anker-Koordinaten [r,c]. */
    val occ = Array(n) { arrayOfNulls<IntArray>(n) }
    /** Kuehlwasser-Schlauch des Reaktors: geordnete Zellen Reaktor-Kante -> Wasser. */
    val reactorPipe = ArrayList<IntArray>()
    /** Aufgedeckte Chunks (Prospektor). true = Reichtum bekannt. */
    val surveyed = BooleanArray(n * n)
    /** Abgebaute Deko-Felder (Baum/Busch/Fels entfernt). */
    val harvested = BooleanArray(n * n)
    var globalBarren = 0.0
    var globalPlatten = 0.0
    var globalKomponente = 0.0
    var money = 0.0
    var mapSeed = 12345L
    val tech = HashMap<String, Int>()

    var powerSupply = 0.0
    var powerDemand = 0.0
    var researchMult = 1.0           // globaler Produktionsbonus durch Forschungszentren

    // --- Statistik: geglaettete Auslastung je Maschinentyp ---
    val typeUtil = DoubleArray(MType.values().size)
    val typeCount = IntArray(MType.values().size)

    private var emaBarrenPerSec = 0.0
    private var emaPlattePerSec = 0.0
    private var emaKompPerSec = 0.0
    private var emaMoneyPerSec = 0.0
    val barrenPerMin get() = emaBarrenPerSec * 60.0
    val plattenPerMin get() = emaPlattePerSec * 60.0
    val komponentenPerMin get() = emaKompPerSec * 60.0
    val moneyPerMin get() = emaMoneyPerSec * 60.0

    companion object {
        const val REAKTOR_POWER = 25.0
        const val GEN_POWER = 20.0
        const val GEN_FUEL = 0.1
        const val BOHRER_RATE = 0.5
        const val OFEN_RATE = 0.34
        const val PRESSE_RATE = 0.25
        const val ASSEMBLER_RATE = 0.15
        const val LIFT = 3.0
        const val IN_CAP = 10.0
        const val OUT_CAP = 20.0
        const val LAGER_CAP = 120.0
        const val REPAIR_COST = 5.0
        const val DROHNE_RATE = 5.0      // Basis-Reparaturtempo am aktuellen Ziel (%/s)
        const val DROHNE_R = 3           // Basis-Reichweite der Station (Chebyshev-Radius)
        const val DROHNE_REPAIR_COST_PER = 0.1  // Geld je repariertem Zustands-% (Drohne)
        const val DROHNE_GATE_STEP = 25.0       // Schrittweite fuer das Reparatur-Limit
        const val BOOST_PER = 0.20
        const val COMPONENT_PRICE = 8.0
        const val HAENDLER_SELL = 2.0
        const val OFFLINE_CAP = 8 * 3600
        const val START_BARREN = 35.0
        const val START_MONEY = 30.0     // Startgeld, um erste Chunks/Hindernisse zu bezahlen

        const val LAND_THRESH = 0.46     // Schwelle Land/Wasser aus dem Rauschen
        const val SCAN_R = 4             // (Alt) Prospektor-Radius – Prospektor entfernt
        const val CHUNK_COST = 5.0       // Geld, um einen Chunk freizuschalten (1 Klick)
        const val WIND_POWER = 16.0      // Strom je Windrad (ohne Brennstoff)
        const val SOLAR_POWER = 10.0     // Strom je Solarpanel (ohne Brennstoff)
        const val RESEARCH_BOOST = 0.08  // globaler Produktionsbonus je Forschungszentrum

        const val HOSE_MAX = 5           // max. Schlauchlaenge Reaktor -> Wasser

        // Grundflaeche je Typ (Breite, Hoehe in Zellen). Anker = untere linke Zelle.
        fun footprint(t: MType): Pair<Int, Int> = when (t) {
            MType.WINDRAD -> Pair(1, 2)
            MType.REAKTOR -> Pair(3, 3)
            MType.FORSCHUNG -> Pair(2, 1)
            else -> Pair(1, 1)
        }

        // Kosten fuers ENTFERNEN eines Hindernisses (Geld): keine, Nadelbaum, Laubbaum, Fels, Busch
        val OBSTACLE_COST = intArrayOf(0, 8, 8, 14, 4)

        // Bodenreichtum je Feld -> Ausbeute-Faktor des Bohrers
        val ORE_MULT = doubleArrayOf(0.0, 0.6, 1.0, 1.7)

        val TECHS = listOf(
            // Maschinen-Freischaltungen (mit Rohstoffen bezahlt)
            TechNode("t_lager", "Lager freischalten", 15.0, 1.0, 1, "", null, Res.BARREN),
            TechNode("t_presse", "Presse freischalten", 20.0, 1.0, 1, "", null, Res.BARREN),
            TechNode("t_gen", "Generator freischalten", 30.0, 1.0, 1, "", null, Res.BARREN),
            TechNode("t_drohne", "Wartungsdrohne freischalten", 40.0, 1.0, 1, "", "t_gen", Res.BARREN),
            TechNode("t_assembler", "Assembler freischalten", 25.0, 1.0, 1, "Platte -> Komponente", "t_presse", Res.PLATTE),
            TechNode("t_haendler", "Haendler freischalten", 20.0, 1.0, 1, "Komponenten -> Geld", "t_assembler", Res.PLATTE),
            TechNode("t_wind", "Windrad freischalten", 28.0, 1.0, 1, "Strom aus Wind", "t_gen", Res.BARREN),
            TechNode("t_solar", "Solarpanel freischalten", 24.0, 1.0, 1, "Strom aus Sonne", "t_gen", Res.BARREN),
            TechNode("t_research", "Forschungszentrum freischalten", 30.0, 1.0, 1, "boostet alle Maschinen", "t_assembler", Res.PLATTE),
            // Upgrades (mit Geld bezahlt)
            TechNode("t_bspeed", "Bohrer-Tempo", 25.0, 1.3, 20, "+8%/Stufe", null, null),
            TechNode("t_ospeed", "Ofen-Tempo", 35.0, 1.3, 20, "+8%/Stufe", null, null),
            TechNode("t_pspeed", "Presse-Tempo", 45.0, 1.3, 20, "+8%/Stufe", "t_presse", null),
            TechNode("t_aspeed", "Assembler-Tempo", 55.0, 1.3, 20, "+8%/Stufe", "t_assembler", null),
            TechNode("t_wert", "Komponenten-Preis", 60.0, 1.4, 10, "+25%/Stufe", "t_assembler", null),
            TechNode("t_takt", "Fabrik-Takt (alle Maschinen)", 50.0, 1.35, 20, "+5%/Stufe", null, null),
            TechNode("t_robust", "Robustheit (weniger Verschleiss)", 40.0, 1.3, 10, "-5%/Stufe", null, null),
            TechNode("t_lift", "Lift-Tempo", 35.0, 1.3, 10, "+10%/Stufe", null, null),
            TechNode("t_power", "Reaktor-Leistung", 45.0, 1.3, 20, "+5 Strom/Stufe", null, null),
            // Drohnen-Upgrades (mit Geld bezahlt)
            TechNode("t_drohne_rep", "Drohnen-Reparatur", 40.0, 1.35, 15, "+20%/Stufe", "t_drohne", null),
            TechNode("t_drohne_speed", "Drohnen-Fluggeschwindigkeit", 35.0, 1.3, 10, "+20%/Stufe", "t_drohne", null),
            TechNode("t_drohne_range", "Drohnen-Reichweite", 60.0, 1.6, 3, "+1 Feld/Stufe", "t_drohne", null)
        )

        val UNLOCK = mapOf(
            MType.PRESSE to "t_presse",
            MType.GENERATOR to "t_gen",
            MType.LAGER to "t_lager",
            MType.DROHNE to "t_drohne",
            MType.ASSEMBLER to "t_assembler",
            MType.HAENDLER to "t_haendler",
            MType.WINDRAD to "t_wind",
            MType.SOLAR to "t_solar",
            MType.FORSCHUNG to "t_research"
        )

        // Hoechstzahl je platzierbarem Typ, damit die Karte nicht zuwuchert.
        val MAX_COUNT = mapOf(
            MType.BOHRER to 30,
            MType.OFEN to 24,
            MType.PRESSE to 16,
            MType.ASSEMBLER to 12,
            MType.GENERATOR to 16,
            MType.LAGER to 16,
            MType.DROHNE to 8,
            MType.HAENDLER to 8,
            MType.WINDRAD to 8,
            MType.SOLAR to 12,
            MType.FORSCHUNG to 4
        )

        // Baukosten: (Rohstoff, Menge). Presse=Barren, Assembler=Platten usw.
        val BUILD_COST = mapOf(
            MType.BOHRER to Pair(Res.BARREN, 5.0),
            MType.OFEN to Pair(Res.BARREN, 8.0),
            MType.PRESSE to Pair(Res.BARREN, 12.0),
            MType.GENERATOR to Pair(Res.BARREN, 10.0),
            MType.LAGER to Pair(Res.BARREN, 8.0),
            MType.WINDRAD to Pair(Res.BARREN, 14.0),
            MType.SOLAR to Pair(Res.BARREN, 10.0),
            MType.FORSCHUNG to Pair(Res.PLATTE, 20.0),
            MType.ASSEMBLER to Pair(Res.PLATTE, 10.0),
            MType.HAENDLER to Pair(Res.PLATTE, 12.0),
            MType.DROHNE to Pair(Res.PLATTE, 8.0)
        )
    }

    fun newGame() {
        for (r in 0 until n) for (c in 0 until n) { grid[r][c] = null; occ[r][c] = null }
        surveyed.fill(false)
        harvested.fill(false)
        globalBarren = START_BARREN
        globalPlatten = 0.0
        globalKomponente = 0.0
        money = START_MONEY
        mapSeed = System.nanoTime() xor 0x5DEECE66DL
        tech.clear()
        placeReactor()
    }

    private fun rawWater(r: Int, c: Int) = r in 0 until n && c in 0 until n && !rawLand(r, c)

    /** Von einem 3x3-Block (oben-links R,C) den kuerzesten Schlauch zum Wasser suchen. */
    private fun walkHose(sr: Int, sc: Int, dr: Int, dc: Int): Triple<List<IntArray>, IntArray, IntArray>? {
        val path = ArrayList<IntArray>()
        var r = sr; var c = sc; var steps = 0
        while (steps <= HOSE_MAX) {
            if (r !in 0 until n || c !in 0 until n) return null
            if (rawWater(r, c)) return Triple(path.toList(), intArrayOf(r, c), intArrayOf(sr - dr, sc - dc))
            if (!rawLand(r, c) || !cellFree(r, c)) return null
            path.add(intArrayOf(r, c)); r += dr; c += dc; steps++
        }
        return null
    }
    private fun hoseFor(R: Int, C: Int): Triple<List<IntArray>, IntArray, IntArray>? {
        var best: Triple<List<IntArray>, IntArray, IntArray>? = null
        val cand = listOf(
            walkHose(R + 1, C + 3, 0, 1),   // rechts
            walkHose(R + 1, C - 1, 0, -1),  // links
            walkHose(R + 3, C + 1, 1, 0),   // unten
            walkHose(R - 1, C + 1, -1, 0)   // oben
        )
        for (h in cand) if (h != null && (best == null || h.first.size < best!!.first.size)) best = h
        return best
    }

    /** Reaktor (3x3) an einer Kueste platzieren, moeglichst zentral, mit Kuehlschlauch. */
    fun placeReactor() {
        reactorPipe.clear()
        var bestR = -1; var bestC = -1
        var bestHose: Triple<List<IntArray>, IntArray, IntArray>? = null
        var bestScore = Int.MAX_VALUE
        for (R in 0..n - 3) for (C in 0..n - 3) {
            var ok = true
            var dr = 0
            while (dr < 3 && ok) { var dc = 0; while (dc < 3) { if (!rawLand(R + dr, C + dc) || !cellFree(R + dr, C + dc)) { ok = false; break }; dc++ }; dr++ }
            if (!ok) continue
            val hose = hoseFor(R, C) ?: continue
            val score = kotlin.math.abs(R + 1 - startR) + kotlin.math.abs(C + 1 - startC) + hose.first.size * 3
            if (score < bestScore) { bestScore = score; bestR = R; bestC = C; bestHose = hose }
        }
        if (bestR < 0) {                       // Notfall: in der Mitte erzwingen
            placeReactorAt(startR - 2, startC)
            surveyReactorArea(startR - 2, startC)
            return
        }
        placeReactorAt(bestR, bestC)
        val ar = bestR + 2; val ac = bestC
        val (path, water, edge) = bestHose!!
        reactorPipe.add(edge)                  // Reaktor-Randzelle (nur fuer die Zeichnung)
        for (cell in path) {
            occ[cell[0]][cell[1]] = intArrayOf(ar, ac)
            surveyed[cell[0] * n + cell[1]] = true
            reactorPipe.add(cell)
        }
        reactorPipe.add(water)                 // Wasser-Endpunkt (nicht belegt)
        surveyReactorArea(bestR, bestC)
    }

    private fun placeReactorAt(R: Int, C: Int) {
        val ar = R + 2; val ac = C
        val m = Machine(MType.REAKTOR); m.w = 3; m.h = 3
        grid[ar][ac] = m
        for (dr in 0 until 3) for (dc in 0 until 3) {
            val rr = R + dr; val cc = C + dc
            if (rr in 0 until n && cc in 0 until n && (rr != ar || cc != ac)) occ[rr][cc] = intArrayOf(ar, ac)
        }
    }
    private fun surveyReactorArea(R: Int, C: Int) {
        for (dr in -3..5) for (dc in -3..5) {
            val rr = R + dr; val cc = C + dc
            if (rr in 0 until n && cc in 0 until n) surveyed[rr * n + cc] = true
        }
    }

    fun lvl(id: String): Int = tech[id] ?: 0
    fun has(id: String): Boolean = lvl(id) > 0

    fun areaN(): Int = n

    fun scanRadius(): Int = SCAN_R + lvl("t_scan")

    fun isSurveyed(r: Int, c: Int): Boolean = surveyed[r * n + c]
    private fun markSurveyed(r: Int, c: Int) { surveyed[r * n + c] = true }

    // --- Terrain (Land/Wasser) aus deterministischem Rauschen ---
    private fun hash01(x: Int, y: Int): Double {
        var h = mapSeed xor (x.toLong() * 374761393L) xor (y.toLong() * 668265263L)
        h = (h xor (h ushr 13)) * -0x61c8864680b583ebL
        h = h xor (h ushr 16)
        return ((h ushr 40).toInt() and 0xFFFF) / 65535.0
    }
    private fun smooth(t: Double) = t * t * (3.0 - 2.0 * t)
    private fun valueNoise(x: Double, y: Double): Double {
        val x0 = kotlin.math.floor(x).toInt(); val y0 = kotlin.math.floor(y).toInt()
        val fx = smooth(x - x0); val fy = smooth(y - y0)
        val v00 = hash01(x0, y0); val v10 = hash01(x0 + 1, y0)
        val v01 = hash01(x0, y0 + 1); val v11 = hash01(x0 + 1, y0 + 1)
        val a = v00 + (v10 - v00) * fx
        val b = v01 + (v11 - v01) * fx
        return a + (b - a) * fy
    }
    private fun landValue(r: Int, c: Int): Double {
        var v = 0.0; var amp = 1.0; var freq = 1.0 / 7.0; var norm = 0.0
        for (o in 0 until 3) {
            v += valueNoise(c * freq + 13.7, r * freq + 7.3) * amp
            norm += amp; amp *= 0.5; freq *= 2.0
        }
        return v / norm
    }
    /** Reines Terrain (nur Rauschen), unabhaengig von Bebauung. */
    private fun rawLand(r: Int, c: Int): Boolean =
        r in 0 until n && c in 0 until n && landValue(r, c) > LAND_THRESH

    /** Land, wenn Terrain es sagt – oder eine Maschine/Belegung dort ist. */
    fun isLand(r: Int, c: Int): Boolean {
        if (r !in 0 until n || c !in 0 until n) return false
        if (grid[r][c] != null || occ[r][c] != null) return true
        return landValue(r, c) > LAND_THRESH
    }

    /** Biom 0..3 (Ebene, Wald, Fels, Bluemwiese) – grossflaechig aus Rauschen. */
    fun biome(r: Int, c: Int): Int {
        val v = valueNoise(c / 11.0 + 91.3, r / 11.0 + 47.1)
        return when { v < 0.42 -> 0; v < 0.66 -> 1; v < 0.84 -> 2; else -> 3 }
    }

    private fun decoHash(r: Int, c: Int): Int {
        var h = (r * 92837111) xor (c * 689287499) xor 0x9E3779B
        h = h xor (h ushr 15); h *= -0x7ee3623b; h = h xor (h ushr 13)
        return h and 0x7fffffff
    }
    private fun landAt(r: Int, c: Int) = r in 0 until n && c in 0 until n && isLand(r, c)

    /** Deko-Kategorie: 0 keine, 1 Nadelbaum, 2 Laubbaum, 3 Fels, 4 Busch. */
    fun decoType(r: Int, c: Int): Int {
        if (!landAt(r, c) || grid[r][c] != null || occ[r][c] != null || harvested[r * n + c]) return 0
        if (!landAt(r - 1, c) || !landAt(r + 1, c) || !landAt(r, c - 1) || !landAt(r, c + 1)) return 0
        val h = decoHash(r, c); val pct = h % 100
        return when (biome(r, c)) {
            1 -> if (pct < 46) (if ((h ushr 3) and 1 == 0) 1 else 2) else 0   // Wald
            2 -> if (pct < 15) 3 else if (pct < 27) 4 else 0                  // Fels (halbiert) + Busch
            3 -> if (pct < 26) 4 else 0                                       // Bluemwiese
            else -> if (pct < 8) 4 else 0                                     // Ebene
        }
    }

    /** Steht auf dem Feld ein Hindernis (Baum/Busch/Fels), das erst weg muss? */
    fun hasObstacle(r: Int, c: Int): Boolean = decoType(r, c) != 0

    /** Kosten (Geld), um das Hindernis auf dem Feld zu entfernen (0 = keins). */
    fun obstacleCost(r: Int, c: Int): Int = OBSTACLE_COST[decoType(r, c)]

    /** Hindernis entfernen -> kostet Geld. true bei Erfolg. */
    fun clearObstacle(r: Int, c: Int): Boolean {
        val t = decoType(r, c)
        if (t == 0) return false
        if (!spendMoney(OBSTACLE_COST[t].toDouble())) return false
        harvested[r * n + c] = true
        return true
    }

    /** Kosten, um einen Chunk freizuschalten. */
    fun chunkCost(): Double = CHUNK_COST

    /** Ist dieses Land-Feld noch gesperrt (nicht freigeschaltet)? */
    fun isLocked(r: Int, c: Int): Boolean =
        r in 0 until n && c in 0 until n && isLand(r, c) && !isSurveyed(r, c)

    /** Einen gesperrten Chunk per Klick + Geld freischalten. true bei Erfolg. */
    fun freeChunk(r: Int, c: Int): Boolean {
        if (!isLocked(r, c)) return false
        if (!spendMoney(CHUNK_COST)) return false
        markSurveyed(r, c)
        return true
    }

    /** Bodenreichtum 0..3 (leer/normal/moderat/reich), deterministisch je Feld. */
    fun richness(r: Int, c: Int): Int {
        var h = mapSeed xor (r.toLong() * 341873128712L) xor (c.toLong() * 132897987541L)
        h = h xor (h ushr 13); h *= -0x61c8864680b583ebL; h = h xor (h ushr 27)
        val v = ((h ushr 33).toInt() and 0x7fffffff) % 100
        // reicher Boden (Tier 3) halbiert: ~7% statt ~14%
        return when { v < 15 -> 0; v < 58 -> 1; v < 93 -> 2; else -> 3 }
    }
    private fun oreMult(r: Int, c: Int) = if (isLand(r, c)) ORE_MULT[richness(r, c)] else 0.0

    // --- Bestand (inkl. Lager) + Waehrungen ---
    private fun lagerSum(res: Int): Double {
        var s = 0.0
        for (r in 0 until n) for (c in 0 until n) {
            val m = grid[r][c]
            if (m != null && m.type == MType.LAGER) s += m.output[res]
        }
        return s
    }
    private fun globalOf(res: Res): Double = when (res) {
        Res.BARREN -> globalBarren
        Res.PLATTE -> globalPlatten
        Res.KOMPONENTE -> globalKomponente
        Res.ROHERZ -> 0.0
    }
    fun available(res: Res) = globalOf(res) + lagerSum(res.ordinal)
    fun availableBarren() = available(Res.BARREN)
    fun availablePlatten() = available(Res.PLATTE)
    fun availableKomponente() = available(Res.KOMPONENTE)

    private fun addGlobal(res: Res, amt: Double) {
        when (res) {
            Res.BARREN -> globalBarren += amt
            Res.PLATTE -> globalPlatten += amt
            Res.KOMPONENTE -> globalKomponente += amt
            Res.ROHERZ -> {}
        }
    }

    /** Zahlt einen Rohstoff: erst global, dann aus den Lagern. */
    private fun spend(res: Res, amt: Double): Boolean {
        if (available(res) < amt - 1e-9) return false
        var rem = amt
        val g = min(globalOf(res), rem)
        when (res) {
            Res.BARREN -> globalBarren -= g
            Res.PLATTE -> globalPlatten -= g
            Res.KOMPONENTE -> globalKomponente -= g
            Res.ROHERZ -> {}
        }
        rem -= g
        if (rem > 1e-9) {
            for (r in 0 until n) for (c in 0 until n) {
                if (rem <= 1e-9) break
                val m = grid[r][c] ?: continue
                if (m.type != MType.LAGER) continue
                val take = min(m.output[res.ordinal], rem)
                m.output[res.ordinal] -= take; rem -= take
            }
        }
        return true
    }

    private fun spendMoney(amt: Double): Boolean {
        if (money < amt - 1e-9) return false
        money -= amt
        return true
    }

    fun canBuild(t: MType): Boolean = when (t) {
        MType.BOHRER, MType.OFEN -> true
        MType.REAKTOR, MType.VERSTAERKER, MType.PROSPEKTOR -> false
        else -> {
            val u = UNLOCK[t]
            u != null && has(u)
        }
    }

    fun buildCost(t: MType): Pair<Res, Double> = BUILD_COST[t] ?: Pair(Res.BARREN, 0.0)

    /** Reparatur-Limit einer Drohnen-Station um delta verschieben (>= 0). Neuer Wert. */
    fun adjustDroneGate(r: Int, c: Int, delta: Double): Double {
        val a = anchorOf(r, c) ?: return 0.0
        val m = grid[a[0]][a[1]] ?: return 0.0
        if (m.type != MType.DROHNE) return m.moneyGate
        m.moneyGate = (m.moneyGate + delta).coerceAtLeast(0.0)
        return m.moneyGate
    }

    fun maxCount(t: MType): Int = MAX_COUNT[t] ?: Int.MAX_VALUE

    fun count(t: MType): Int {
        var k = 0
        for (r in 0 until n) for (c in 0 until n) if (grid[r][c]?.type == t) k++
        return k
    }

    fun atLimit(t: MType): Boolean = count(t) >= maxCount(t)

    /** Anker (Anker-Zelle) eines Gebaeudes, egal welche belegte Zelle man antippt. */
    fun anchorOf(r: Int, c: Int): IntArray? {
        if (r !in 0 until n || c !in 0 until n) return null
        if (grid[r][c] != null) return intArrayOf(r, c)
        return occ[r][c]
    }
    private fun cellFree(r: Int, c: Int) =
        r in 0 until n && c in 0 until n && grid[r][c] == null && occ[r][c] == null

    fun build(t: MType, r: Int, c: Int): Boolean {
        if (!canBuild(t)) return false
        if (atLimit(t)) return false
        val (fw, fh) = footprint(t)
        // Grundflaeche waechst nach OBEN (Anker = unten): Reihen r-(fh-1)..r
        for (dy in 0 until fh) for (dx in 0 until fw) {
            val rr = r - dy; val cc = c + dx
            if (rr !in 0 until areaN() || cc !in 0 until areaN()) return false
            if (!cellFree(rr, cc)) return false
            if (!isLand(rr, cc)) return false           // Bauen nur auf Land
            if (!isSurveyed(rr, cc)) return false        // Chunk muss freigeschaltet sein
            if (decoType(rr, cc) != 0) return false      // Hindernis muss erst weg
        }
        val (res, amt) = buildCost(t)
        if (!spend(res, amt)) return false
        val m = Machine(t); m.w = fw; m.h = fh
        grid[r][c] = m
        for (dy in 0 until fh) for (dx in 0 until fw) {
            val rr = r - dy; val cc = c + dx
            surveyed[rr * n + cc] = true
            if (dy != 0 || dx != 0) occ[rr][cc] = intArrayOf(r, c)
        }
        return true
    }

    /** Verkauf: 50% der Baukosten (im gleichen Rohstoff), skaliert mit Zustand. */
    fun sell(r: Int, c: Int): Double {
        val a = anchorOf(r, c) ?: return 0.0
        val ar = a[0]; val ac = a[1]
        val m = grid[ar][ac] ?: return 0.0
        if (m.type == MType.REAKTOR) return 0.0
        val (res, amt) = buildCost(m.type)
        val refund = amt * 0.5 * (m.condition / 100.0)
        addGlobal(res, refund)
        for (dy in 0 until m.h) for (dx in 0 until m.w) occ[ar - dy][ac + dx] = null
        grid[ar][ac] = null
        return refund
    }

    fun nextCost(node: TechNode): Double =
        kotlin.math.round(node.baseCost * node.growth.pow(lvl(node.id)))

    fun techAffordable(node: TechNode): Boolean {
        val cost = nextCost(node)
        return if (node.costRes != null) available(node.costRes) >= cost else money >= cost
    }

    fun buyTech(id: String): Boolean {
        val node = TECHS.firstOrNull { it.id == id } ?: return false
        val l = lvl(id)
        if (l >= node.maxLevel) return false
        if (node.prereq != null && !has(node.prereq)) return false
        val cost = nextCost(node)
        val ok = if (node.costRes != null) spend(node.costRes, cost) else spendMoney(cost)
        if (!ok) return false
        tech[id] = l + 1
        return true
    }

    /**
     * Engpass-Kennung fuer eine Maschine (Ampel + Ursache):
     * 0=ok, 1=Nachschub fehlt, 2=Ausgang voll, 3=zu wenig Strom, 4=defekt, 5=leerer Boden.
     */
    fun bottleneck(m: Machine, r: Int, c: Int): Int {
        if (m.condition <= 0.0) return 4
        val scale = if (powerDemand <= 0.0) 1.0 else min(1.0, powerSupply / powerDemand)
        when (m.type) {
            MType.BOHRER -> {
                if (oreMult(r, c) <= 0.0) return 5
                if (m.output[Res.ROHERZ.ordinal] >= OUT_CAP - 0.5) return 2
                if (scale < 0.999 && m.util < 0.98) return 3
            }
            MType.OFEN -> {
                if (m.output[Res.BARREN.ordinal] >= OUT_CAP - 0.5) return 2
                if (m.starved) return 1
                if (scale < 0.999 && m.util < 0.98) return 3
            }
            MType.PRESSE -> {
                if (m.output[Res.PLATTE.ordinal] >= OUT_CAP - 0.5) return 2
                if (m.starved) return 1
                if (scale < 0.999 && m.util < 0.98) return 3
            }
            MType.ASSEMBLER -> {
                if (m.output[Res.KOMPONENTE.ordinal] >= OUT_CAP - 0.5) return 2
                if (m.starved) return 1
                if (scale < 0.999 && m.util < 0.98) return 3
            }
            MType.GENERATOR -> {
                if (m.starved) return 1
            }
            MType.HAENDLER -> {
                if (availableKomponente() <= 1e-6 && m.util < 0.5) return 1
            }
            else -> {}
        }
        return 0
    }

    fun repair(m: Machine): Boolean {
        if (m.condition >= 99.999) return false
        if (!spend(Res.BARREN, REPAIR_COST)) return false
        m.condition = 100.0
        return true
    }

    // --- Tech-abhaengige Parameter ---
    private fun globalMult() = (1.0 + 0.05 * lvl("t_takt")) * researchMult
    private fun wearFactor() = max(0.3, 1.0 - 0.05 * lvl("t_robust"))
    private fun liftRate() = LIFT * (1.0 + 0.1 * lvl("t_lift"))
    private fun reactorPower() = REAKTOR_POWER + 5.0 * lvl("t_power")
    fun droneRepairRate() = DROHNE_RATE * (1.0 + 0.20 * lvl("t_drohne_rep"))
    fun droneRange() = DROHNE_R + lvl("t_drohne_range")
    fun droneSpeedMult() = 1.0 + 0.20 * lvl("t_drohne_speed")
    private fun bohrerRate() = BOHRER_RATE * (1.0 + 0.08 * lvl("t_bspeed")) * globalMult()
    private fun ofenRate() = OFEN_RATE * (1.0 + 0.08 * lvl("t_ospeed")) * globalMult()
    private fun presseRate() = PRESSE_RATE * (1.0 + 0.08 * lvl("t_pspeed")) * globalMult()
    private fun assemblerRate() = ASSEMBLER_RATE * (1.0 + 0.08 * lvl("t_aspeed")) * globalMult()
    fun componentPrice() = COMPONENT_PRICE * (1.0 + 0.25 * lvl("t_wert"))

    private fun wearPerSec(t: MType) = when (t) {
        MType.BOHRER -> 1.0 / 60.0
        MType.OFEN -> 1.2 / 60.0
        MType.PRESSE -> 1.5 / 60.0
        MType.ASSEMBLER -> 1.6 / 60.0
        MType.GENERATOR -> 0.8 / 60.0
        MType.DROHNE -> 0.5 / 60.0
        MType.VERSTAERKER -> 0.6 / 60.0
        MType.FORSCHUNG -> 0.7 / 60.0
        else -> 0.0
    }

    private fun wearMult(c: Double) = if (c >= 50.0) 1.0 else max(0.0, c / 50.0)

    private inline fun forEachMachine(block: (Machine, Int, Int) -> Unit) {
        for (r in 0 until n) for (c in 0 until n) {
            val m = grid[r][c]
            if (m != null) block(m, r, c)
        }
    }

    private fun neighbors(r: Int, c: Int): List<IntArray> {
        val res = ArrayList<IntArray>(4)
        val d = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))
        for (o in d) {
            val nr = r + o[0]; val nc = c + o[1]
            if (nr in 0 until n && nc in 0 until n) res.add(intArrayOf(nr, nc))
        }
        return res
    }

    private fun boostAt(r: Int, c: Int): Double {
        var k = 0
        for (nb in neighbors(r, c)) if (grid[nb[0]][nb[1]]?.type == MType.VERSTAERKER) k++
        return 1.0 + BOOST_PER * min(k, 3)
    }

    private fun wantedRes(t: MType): IntArray = when (t) {
        MType.OFEN -> intArrayOf(Res.ROHERZ.ordinal)
        MType.PRESSE -> intArrayOf(Res.BARREN.ordinal)
        MType.ASSEMBLER -> intArrayOf(Res.PLATTE.ordinal)
        MType.GENERATOR -> intArrayOf(Res.ROHERZ.ordinal)
        MType.LAGER -> intArrayOf(0, 1, 2, 3)
        else -> IntArray(0)
    }

    private fun offers(t: MType, res: Int): Boolean = when (t) {
        MType.BOHRER -> res == Res.ROHERZ.ordinal
        MType.OFEN -> res == Res.BARREN.ordinal
        MType.PRESSE -> res == Res.PLATTE.ordinal
        MType.ASSEMBLER -> res == Res.KOMPONENTE.ordinal
        MType.LAGER -> true
        else -> false
    }

    private fun wantsToRun(m: Machine, r: Int, c: Int): Boolean = when (m.type) {
        MType.BOHRER -> m.output[Res.ROHERZ.ordinal] < OUT_CAP - 1e-9 && oreMult(r, c) > 0.0
        MType.OFEN -> m.input[Res.ROHERZ.ordinal] > 1e-6 && m.output[Res.BARREN.ordinal] < OUT_CAP - 1e-9
        MType.PRESSE -> m.input[Res.BARREN.ordinal] > 1e-6 && m.output[Res.PLATTE.ordinal] < OUT_CAP - 1e-9
        MType.ASSEMBLER -> m.input[Res.PLATTE.ordinal] > 1e-6 && m.output[Res.KOMPONENTE.ordinal] < OUT_CAP - 1e-9
        MType.DROHNE -> money >= m.moneyGate && damagedInRange(r, c) != null
        MType.VERSTAERKER -> neighbors(r, c).any {
            val g = grid[it[0]][it[1]]?.type
            g == MType.BOHRER || g == MType.OFEN || g == MType.PRESSE || g == MType.ASSEMBLER
        }
        MType.PROSPEKTOR -> hasUnsurveyedInRange(r, c)
        MType.FORSCHUNG -> true          // zieht Strom, solange es steht
        else -> false
    }

    /** Am staerksten beschaedigte Maschine im Umkreis der Station (oder null). */
    private fun damagedInRange(r: Int, c: Int): IntArray? {
        val rad = droneRange()
        var bestR = -1; var bestC = -1; var worst = 99.999
        for (dr in -rad..rad) for (dc in -rad..rad) {
            val rr = r + dr; val cc = c + dc
            if (rr !in 0 until n || cc !in 0 until n) continue
            if (rr == r && cc == c) continue
            val g = grid[rr][cc] ?: continue
            if (g.type == MType.DROHNE) continue
            if (g.condition < worst) { worst = g.condition; bestR = rr; bestC = cc }
        }
        return if (bestR >= 0) intArrayOf(bestR, bestC) else null
    }

    private fun hasUnsurveyedInRange(r: Int, c: Int): Boolean {
        val rad = scanRadius()
        for (dr in -rad..rad) for (dc in -rad..rad) {
            val rr = r + dr; val cc = c + dc
            if (rr in 0 until n && cc in 0 until n && isLand(rr, cc) && !surveyed[rr * n + cc]) return true
        }
        return false
    }

    private fun transfers() {
        forEachMachine { c, r, cc ->
            val wants = wantedRes(c.type)
            if (wants.isEmpty()) return@forEachMachine
            val target = if (c.type == MType.LAGER) c.output else c.input
            val cap = if (c.type == MType.LAGER) LAGER_CAP else IN_CAP
            for (res in wants) {
                var space = cap - target[res]
                if (space <= 1e-9) continue
                for (nb in neighbors(r, cc)) {
                    val nm = grid[nb[0]][nb[1]] ?: continue
                    if (!offers(nm.type, res)) continue
                    val avail = nm.output[res]
                    if (avail <= 1e-9) continue
                    val mv = min(space, avail)
                    nm.output[res] -= mv
                    target[res] += mv
                    space -= mv
                    if (space <= 1e-9) break
                }
            }
        }
    }

    fun step(dt: Double) {
        val ddt = dt.coerceIn(0.0, 2.0)
        if (ddt <= 0.0) return

        transfers()

        var supply = 0.0
        forEachMachine { m, _, _ ->
            if (m.type == MType.REAKTOR) supply += reactorPower()
            if (m.type == MType.GENERATOR && m.input[Res.ROHERZ.ordinal] > 1e-6) supply += GEN_POWER
            if (m.type == MType.WINDRAD) supply += WIND_POWER
            if (m.type == MType.SOLAR) supply += SOLAR_POWER
        }
        var demand = 0.0
        forEachMachine { m, r, c -> if (wantsToRun(m, r, c)) demand += m.type.power }
        val scale = if (demand <= 0.0) 1.0 else min(1.0, supply / demand)
        powerSupply = supply
        powerDemand = demand

        // Forschungszentren: globaler Produktionsbonus (skaliert mit Stromversorgung)
        var forsch = 0
        forEachMachine { m, _, _ -> if (m.type == MType.FORSCHUNG) forsch++ }
        researchMult = 1.0 + RESEARCH_BOOST * forsch * scale

        var barMade = 0.0
        var platMade = 0.0
        var kompMade = 0.0

        forEachMachine { m, r, c ->
            m.starved = false
            val wf = wearFactor()
            when (m.type) {
                MType.BOHRER -> {
                    val nominal = bohrerRate() * ddt * boostAt(r, c) * oreMult(r, c)
                    val want = nominal * scale * wearMult(m.condition)
                    val made = max(0.0, min(want, OUT_CAP - m.output[Res.ROHERZ.ordinal]))
                    m.output[Res.ROHERZ.ordinal] += made
                    if (bohrerRate() > 0) m.condition = max(0.0, m.condition - wearPerSec(MType.BOHRER) * wf * (made / bohrerRate()))
                    m.util = if (nominal > 1e-9) made / nominal else 0.0
                }
                MType.OFEN -> {
                    val nominal = ofenRate() * ddt * boostAt(r, c)
                    val want = nominal * scale * wearMult(m.condition)
                    val byInput = m.input[Res.ROHERZ.ordinal] / 2.0
                    val made = max(0.0, min(want, min(byInput, OUT_CAP - m.output[Res.BARREN.ordinal])))
                    m.input[Res.ROHERZ.ordinal] -= made * 2.0
                    m.output[Res.BARREN.ordinal] += made
                    m.condition = max(0.0, m.condition - wearPerSec(MType.OFEN) * wf * (made / ofenRate()))
                    m.util = if (nominal > 1e-9) made / nominal else 0.0
                    barMade += made
                    if (byInput <= 1e-9 && m.output[Res.BARREN.ordinal] < OUT_CAP - 1e-9) m.starved = true
                }
                MType.PRESSE -> {
                    val nominal = presseRate() * ddt * boostAt(r, c)
                    val want = nominal * scale * wearMult(m.condition)
                    val byInput = m.input[Res.BARREN.ordinal] / 2.0
                    val made = max(0.0, min(want, min(byInput, OUT_CAP - m.output[Res.PLATTE.ordinal])))
                    m.input[Res.BARREN.ordinal] -= made * 2.0
                    m.output[Res.PLATTE.ordinal] += made
                    m.condition = max(0.0, m.condition - wearPerSec(MType.PRESSE) * wf * (made / presseRate()))
                    m.util = if (nominal > 1e-9) made / nominal else 0.0
                    platMade += made
                    if (byInput <= 1e-9 && m.output[Res.PLATTE.ordinal] < OUT_CAP - 1e-9) m.starved = true
                }
                MType.ASSEMBLER -> {
                    val nominal = assemblerRate() * ddt * boostAt(r, c)
                    val want = nominal * scale * wearMult(m.condition)
                    val byInput = m.input[Res.PLATTE.ordinal] / 2.0
                    val made = max(0.0, min(want, min(byInput, OUT_CAP - m.output[Res.KOMPONENTE.ordinal])))
                    m.input[Res.PLATTE.ordinal] -= made * 2.0
                    m.output[Res.KOMPONENTE.ordinal] += made
                    m.condition = max(0.0, m.condition - wearPerSec(MType.ASSEMBLER) * wf * (made / assemblerRate()))
                    m.util = if (nominal > 1e-9) made / nominal else 0.0
                    kompMade += made
                    if (byInput <= 1e-9 && m.output[Res.KOMPONENTE.ordinal] < OUT_CAP - 1e-9) m.starved = true
                }
                MType.GENERATOR -> {
                    val fuel = min(m.input[Res.ROHERZ.ordinal], GEN_FUEL * ddt)
                    m.input[Res.ROHERZ.ordinal] -= fuel
                    if (fuel > 0.0) m.condition = max(0.0, m.condition - wearPerSec(MType.GENERATOR) * wf * (fuel / GEN_FUEL))
                    m.util = if (GEN_FUEL * ddt > 1e-9) fuel / (GEN_FUEL * ddt) else 0.0
                    if (fuel <= 1e-9) m.starved = true
                }
                MType.DROHNE -> {
                    if (money < m.moneyGate) {
                        // Reparatur-Limit nicht erreicht: Drohne pausiert und kehrt heim.
                        m.svR = -1; m.svC = -1; m.util = 0.0
                    } else {
                        val cur = if (m.svR in 0 until n && m.svC in 0 until n) grid[m.svR][m.svC] else null
                        val valid = cur != null && cur.type != MType.DROHNE && cur.condition < 99.999 &&
                            kotlin.math.max(kotlin.math.abs(m.svR - r), kotlin.math.abs(m.svC - c)) <= droneRange()
                        if (!valid) {
                            val next = damagedInRange(r, c)
                            if (next != null) { m.svR = next[0]; m.svC = next[1] } else { m.svR = -1; m.svC = -1 }
                        }
                        val tgt = if (m.svR >= 0) grid[m.svR][m.svC] else null
                        if (tgt != null) {
                            var delta = min(droneRepairRate() * ddt * scale, 100.0 - tgt.condition)
                            // Geld fuer die Reparatur abziehen; nur so viel, wie bezahlbar ist.
                            val maxByMoney = if (DROHNE_REPAIR_COST_PER > 1e-9) money / DROHNE_REPAIR_COST_PER else delta
                            if (maxByMoney < delta) delta = maxByMoney
                            if (delta > 1e-9) {
                                tgt.condition = min(100.0, tgt.condition + delta)
                                money = max(0.0, money - delta * DROHNE_REPAIR_COST_PER)
                                m.condition = max(0.0, m.condition - wearPerSec(MType.DROHNE) * wf * ddt * scale)
                                m.util = 1.0
                            } else m.util = 0.0
                        } else m.util = 0.0
                    }
                }
                MType.VERSTAERKER -> {
                    val active = wantsToRun(m, r, c)
                    if (active) m.condition = max(0.0, m.condition - wearPerSec(MType.VERSTAERKER) * wf * ddt * scale)
                    m.util = if (active) 1.0 else 0.0
                }
                MType.PROSPEKTOR -> {
                    val rad = scanRadius()
                    var revealed = 0
                    for (dr in -rad..rad) for (dc in -rad..rad) {
                        val rr = r + dr; val cc = c + dc
                        if (rr in 0 until n && cc in 0 until n && isLand(rr, cc) && !surveyed[rr * n + cc]) {
                            markSurveyed(rr, cc); revealed++
                        }
                    }
                    m.util = if (revealed > 0) 1.0 else 0.0
                }
                MType.WINDRAD -> { m.util = 1.0 }   // dreht sich immer (Strom aus Wind)
                MType.SOLAR -> { m.util = 1.0 }      // liefert immer (Strom aus Sonne)
                MType.FORSCHUNG -> {
                    m.condition = max(0.0, m.condition - wearPerSec(MType.FORSCHUNG) * wf * ddt * scale)
                    m.util = scale
                }
                MType.LAGER, MType.REAKTOR, MType.HAENDLER -> { m.util = 0.0 }
            }
        }

        transfers()
        forEachMachine { m, _, _ ->
            when (m.type) {
                MType.OFEN -> {
                    val amt = min(liftRate() * ddt, m.output[Res.BARREN.ordinal])
                    m.output[Res.BARREN.ordinal] -= amt; globalBarren += amt
                }
                MType.PRESSE -> {
                    val amt = min(liftRate() * ddt, m.output[Res.PLATTE.ordinal])
                    m.output[Res.PLATTE.ordinal] -= amt; globalPlatten += amt
                }
                MType.ASSEMBLER -> {
                    val amt = min(liftRate() * ddt, m.output[Res.KOMPONENTE.ordinal])
                    m.output[Res.KOMPONENTE.ordinal] -= amt; globalKomponente += amt
                }
                else -> {}
            }
        }

        // Haendler verkaufen Komponenten aus dem globalen Bestand -> Geld
        var soldValue = 0.0
        val price = componentPrice()
        forEachMachine { m, _, _ ->
            if (m.type == MType.HAENDLER) {
                val sold = min(HAENDLER_SELL * ddt, globalKomponente)
                if (sold > 1e-9) {
                    globalKomponente -= sold
                    money += sold * price
                    soldValue += sold * price
                    m.util = 1.0
                } else m.util = 0.0
            }
        }

        val tau = 8.0
        val a = 1.0 - exp(-ddt / tau)
        emaBarrenPerSec += (barMade / ddt - emaBarrenPerSec) * a
        emaPlattePerSec += (platMade / ddt - emaPlattePerSec) * a
        emaKompPerSec += (kompMade / ddt - emaKompPerSec) * a
        emaMoneyPerSec += (soldValue / ddt - emaMoneyPerSec) * a

        // Auslastung je Maschinentyp (geglaettet) fuer die Statistik
        val sumU = DoubleArray(typeUtil.size)
        val cnt = IntArray(typeUtil.size)
        forEachMachine { m, _, _ ->
            val i = m.type.ordinal
            sumU[i] += m.util
            cnt[i]++
        }
        for (i in typeUtil.indices) {
            typeCount[i] = cnt[i]
            val avg = if (cnt[i] > 0) sumU[i] / cnt[i] else 0.0
            typeUtil[i] += (avg - typeUtil[i]) * a
        }
    }

    fun runOffline(elapsedSeconds: Int): OfflineReport {
        val cap = min(elapsedSeconds, OFFLINE_CAP)
        val b0 = globalBarren; val p0 = globalPlatten; val m0 = money
        val events = ArrayList<OfflineEvent>()
        val seenStarve = HashSet<Machine>()
        val seenDead = HashSet<Machine>()
        var t = 0
        while (t < cap) {
            step(1.0)
            forEachMachine { m, r, c ->
                if (m.condition <= 0.0 && !seenDead.contains(m)) {
                    seenDead.add(m)
                    if (events.size < 24) events.add(OfflineEvent(t, true, m.type, r, c))
                }
                if ((m.type == MType.OFEN || m.type == MType.PRESSE || m.type == MType.ASSEMBLER || m.type == MType.GENERATOR) &&
                    m.starved && !seenStarve.contains(m)
                ) {
                    seenStarve.add(m)
                    if (events.size < 24) events.add(OfflineEvent(t, false, m.type, r, c))
                }
            }
            t++
        }
        return OfflineReport(elapsedSeconds, cap, globalBarren - b0, globalPlatten - p0, money - m0, events)
    }

    fun toJson(nowMillis: Long): String {
        val root = JSONObject()
        root.put("t", nowMillis)
        root.put("gb", globalBarren)
        root.put("gp", globalPlatten)
        root.put("gk", globalKomponente)
        root.put("money", money)
        root.put("seed", mapSeed)
        val techObj = JSONObject()
        for ((k, v) in tech) techObj.put(k, v)
        root.put("tech", techObj)
        val cells = JSONArray()
        val rc = Res.values().size
        forEachMachine { m, r, c ->
            val o = JSONObject()
            o.put("r", r); o.put("c", c); o.put("ty", m.type.ordinal); o.put("cond", m.condition)
            if (m.type == MType.DROHNE) o.put("gate", m.moneyGate)
            val ia = JSONArray(); val oa = JSONArray()
            for (k in 0 until rc) { ia.put(m.input[k]); oa.put(m.output[k]) }
            o.put("in", ia); o.put("out", oa)
            cells.put(o)
        }
        root.put("cells", cells)
        // Aufgedeckte Chunks als Liste von Indizes (nur die gesetzten).
        val surv = JSONArray()
        for (i in surveyed.indices) if (surveyed[i]) surv.put(i)
        root.put("surv", surv)
        val harv = JSONArray()
        for (i in harvested.indices) if (harvested[i]) harv.put(i)
        root.put("harv", harv)
        val pipe = JSONArray()
        for (cell in reactorPipe) pipe.put(JSONArray().put(cell[0]).put(cell[1]))
        root.put("pipe", pipe)
        root.put("world", 3)   // Weltformat 3: 3x3-Reaktor + Kuehlschlauch
        return root.toString()
    }

    fun fromJson(s: String): Long {
        val root = JSONObject(s)
        for (r in 0 until n) for (c in 0 until n) { grid[r][c] = null; occ[r][c] = null }
        globalBarren = root.optDouble("gb", 0.0)
        globalPlatten = root.optDouble("gp", 0.0)
        globalKomponente = root.optDouble("gk", 0.0)
        money = root.optDouble("money", 0.0)
        mapSeed = root.optLong("seed", 12345L)
        tech.clear()
        val tv = root.opt("tech")
        if (tv is JSONArray) {
            for (i in 0 until tv.length()) tech[tv.getString(i)] = 1
        } else if (tv is JSONObject) {
            val keys = tv.keys()
            while (keys.hasNext()) { val k = keys.next(); tech[k] = tv.getInt(k) }
        }
        val rc = Res.values().size
        val cells = root.optJSONArray("cells")
        if (cells != null) for (i in 0 until cells.length()) {
            val o = cells.getJSONObject(i)
            val ty = o.getInt("ty")
            if (ty < 0 || ty >= MType.values().size) continue
            val r = o.getInt("r"); val c = o.getInt("c")
            if (r < 0 || r >= n || c < 0 || c >= n) continue
            val m = Machine(MType.values()[ty])
            m.condition = o.optDouble("cond", 100.0)
            m.moneyGate = o.optDouble("gate", 0.0)
            val ia = o.optJSONArray("in"); val oa = o.optJSONArray("out")
            if (ia != null) for (k in 0 until min(rc, ia.length())) m.input[k] = ia.optDouble(k, 0.0)
            if (oa != null) for (k in 0 until min(rc, oa.length())) m.output[k] = oa.optDouble(k, 0.0)
            val (fw, fh) = footprint(m.type); m.w = fw; m.h = fh
            grid[r][c] = m
        }
        // Entfernte Maschinentypen (Verstaerker/Prospektor) aus Alt-Spielstaenden tilgen.
        for (r in 0 until n) for (c in 0 until n) {
            val ty = grid[r][c]?.type
            if (ty == MType.VERSTAERKER || ty == MType.PROSPEKTOR) grid[r][c] = null
        }
        val reactorNew = root.optInt("world", 2) >= 3 && root.has("pipe")
        if (!reactorNew) {
            // altes Format: alten (1x1) Reaktor entfernen -> wird als 3x3 neu platziert
            for (r in 0 until n) for (c in 0 until n) if (grid[r][c]?.type == MType.REAKTOR) grid[r][c] = null
        }
        // Belegung fuer mehrzellige Gebaeude wiederherstellen (Anker = unten links, waechst hoch/rechts).
        forEachMachine { m, r, c ->
            if (m.w > 1 || m.h > 1) {
                for (dy in 0 until m.h) for (dx in 0 until m.w) {
                    val rr = r - dy; val cc = c + dx
                    if (rr in 0 until n && cc in 0 until n && (dy != 0 || dx != 0)) occ[rr][cc] = intArrayOf(r, c)
                }
            }
        }

        // Aufgedeckte Chunks laden.
        surveyed.fill(false)
        val surv = root.optJSONArray("surv")
        if (surv != null) {
            for (i in 0 until surv.length()) {
                val idx = surv.optInt(i, -1)
                if (idx in surveyed.indices) surveyed[idx] = true
            }
        } else {
            // Alter Spielstand ohne Terrain/Fog: alles als aufgedeckt behandeln.
            surveyed.fill(true)
        }
        harvested.fill(false)
        val harv = root.optJSONArray("harv")
        if (harv != null) for (i in 0 until harv.length()) {
            val idx = harv.optInt(i, -1)
            if (idx in harvested.indices) harvested[idx] = true
        }

        // Reaktor-Kuehlschlauch laden (neues Format) bzw. Reaktor neu platzieren (altes Format).
        reactorPipe.clear()
        if (reactorNew) {
            val ra = reactorAnchor()
            val pipe = root.optJSONArray("pipe")
            if (pipe != null) for (i in 0 until pipe.length()) {
                val cellArr = pipe.optJSONArray(i) ?: continue
                val pr = cellArr.optInt(0, -1); val pc = cellArr.optInt(1, -1)
                if (pr in 0 until n && pc in 0 until n) {
                    reactorPipe.add(intArrayOf(pr, pc))
                    if (ra != null && i in 1 until pipe.length() - 1) occ[pr][pc] = intArrayOf(ra[0], ra[1])
                }
            }
        }
        // Reaktor sicherstellen (altes Format -> neuer 3x3-Reaktor + Schlauch).
        if (!hasReactor()) placeReactor()
        return root.optLong("t", 0L)
    }

    private fun hasReactor(): Boolean = reactorAnchor() != null
    private fun reactorAnchor(): IntArray? {
        for (r in 0 until n) for (c in 0 until n) if (grid[r][c]?.type == MType.REAKTOR) return intArrayOf(r, c)
        return null
    }
}
