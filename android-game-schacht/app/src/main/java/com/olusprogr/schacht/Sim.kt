package com.olusprogr.schacht

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.json.JSONArray
import org.json.JSONObject

/** Rohstoffe in der vertikalen Scheibe (Tier 1 + 2). */
enum class Res { ROHERZ, BARREN, PLATTE }

/**
 * Maschinentypen. `power` ist der Stromverbrauch pro Sekunde unter Volllast
 * (Reaktor/Generator/Lager verbrauchen keinen Strom).
 */
enum class MType(val label: String, val sym: String, val power: Double) {
    BOHRER("Bohrer", "B", 4.0),
    OFEN("Ofen", "O", 6.0),
    PRESSE("Presse", "P", 8.0),
    GENERATOR("Generator", "G", 0.0),
    LAGER("Lager", "L", 0.0),
    DROHNE("Wartungsdrohne", "D", 5.0),
    REAKTOR("Reaktor", "R", 0.0)
}

/** Eine platzierte Maschine mit Zustand und (kontinuierlichen) Puffern. */
class Machine(var type: MType) {
    var condition = 100.0          // 100 = neu, 0 = Totalausfall
    val input = DoubleArray(3)     // Eingangspuffer, indexiert per Res.ordinal
    val output = DoubleArray(3)    // Ausgangspuffer
    var util = 0.0                 // 0..1, tatsaechliche Auslastung (fuer Animation)
    var starved = false            // wollte laufen, bekam aber keinen Nachschub
}

data class OfflineEvent(val timeSec: Int, val text: String)

data class OfflineReport(
    val elapsedSeconds: Int,
    val simSeconds: Int,
    val barrenGained: Double,
    val plattenGained: Double,
    val events: List<OfflineEvent>
)

/**
 * Ein Tech-Knoten. Einmal-Freischaltungen haben maxLevel=1 und growth=1.
 * Stufen-Upgrades haben maxLevel>1; die Kosten wachsen je Stufe um `growth`.
 */
data class TechNode(
    val id: String,
    val label: String,
    val baseCost: Double,
    val growth: Double,
    val maxLevel: Int,
    val effect: String,
    val prereq: String?
)

/**
 * Tick-basierte Fabriksimulation auf einem 8x8-Gitter.
 *
 * Kern: Maschinen ziehen Rohstoffe aus *direkt angrenzenden* Feldern
 * (Adjazenz). Backpressure entsteht von selbst — laeuft ein Ausgangspuffer
 * voll, staut es sich nach oben; fehlt Nachschub, verhungert die Kette nach
 * unten. Strom wird pro Sektor global bilanziert: reicht die Versorgung
 * nicht, laufen ALLE Verbraucher anteilig langsamer (kein harter Stopp).
 *
 * Dieselbe `step()`-Funktion treibt Echtzeit- und Offline-Simulation. Offline
 * laeuft sie in 1-Sekunden-Schritten bis zum 8h-Cap; Verschleiss faellt nur
 * waehrend tatsaechlich produzierter Zeit an, daher ist 24h Abwesenheit exakt
 * gleich 8h und nie schlechter.
 */
class Simulation {
    val n = 8
    val grid = Array(n) { arrayOfNulls<Machine>(n) }
    var globalBarren = 0.0
    var globalPlatten = 0.0
    val tech = HashMap<String, Int>()   // Tech-Id -> Stufe (0 = nicht gekauft)

    // Anzeigewerte (vom letzten step gesetzt)
    var powerSupply = 0.0
    var powerDemand = 0.0
    private var emaBarrenPerSec = 0.0
    private var emaPlattePerSec = 0.0
    val barrenPerMin get() = emaBarrenPerSec * 60.0
    val plattenPerMin get() = emaPlattePerSec * 60.0
    val produktionswertPerMin get() = plattenPerMin * platteValue() + barrenPerMin * VAL_BARREN

    companion object {
        const val REAKTOR_POWER = 25.0
        const val GEN_POWER = 20.0
        const val GEN_FUEL = 0.1        // Roherz/s als Brennstoff
        const val BOHRER_RATE = 0.5     // Roherz/s
        const val OFEN_RATE = 0.34      // Barren/s (braucht 0.68 Roherz/s)
        const val PRESSE_RATE = 0.25    // Platte/s (braucht 0.5 Barren/s)
        const val LIFT = 3.0            // Einheiten/s in den globalen Bestand
        const val IN_CAP = 10.0
        const val OUT_CAP = 20.0
        const val LAGER_CAP = 120.0
        const val REPAIR_COST = 5.0     // Barren pro Vollreparatur
        const val DROHNE_RATE = 12.0    // Zustand/s an Nachbarn
        const val VAL_BARREN = 3.0
        const val VAL_PLATTE = 10.0
        const val OFFLINE_CAP = 8 * 3600

        val TECHS = listOf(
            // Einmal-Freischaltungen
            TechNode("t_lager", "Lager freischalten", 15.0, 1.0, 1, "", null),
            TechNode("t_presse", "Presse freischalten", 20.0, 1.0, 1, "", null),
            TechNode("t_gen", "Generator freischalten", 30.0, 1.0, 1, "", null),
            TechNode("t_drohne", "Wartungsdrohne freischalten", 80.0, 1.0, 1, "", "t_gen"),
            TechNode("t_diag", "Diagonale Nachbarn", 120.0, 1.0, 1, "8 statt 4 Nachbarn", null),
            // Stufen-Upgrades (mehrfach kaufbar)
            TechNode("t_bspeed", "Bohrer-Tempo", 25.0, 1.3, 20, "+8%/Stufe", null),
            TechNode("t_ospeed", "Ofen-Tempo", 35.0, 1.3, 20, "+8%/Stufe", null),
            TechNode("t_pspeed", "Presse-Tempo", 45.0, 1.3, 20, "+8%/Stufe", "t_presse"),
            TechNode("t_wert", "Platten-Wert", 60.0, 1.4, 10, "+25%/Stufe", "t_presse")
        )

        val UNLOCK = mapOf(
            MType.PRESSE to "t_presse",
            MType.GENERATOR to "t_gen",
            MType.LAGER to "t_lager",
            MType.DROHNE to "t_drohne"
        )

        val BUILD_COST = mapOf(
            MType.BOHRER to 5.0,
            MType.OFEN to 8.0,
            MType.PRESSE to 12.0,
            MType.GENERATOR to 10.0,
            MType.LAGER to 8.0,
            MType.DROHNE to 20.0
        )
        const val START_BARREN = 30.0
    }

    fun newGame() {
        for (r in 0 until n) for (c in 0 until n) grid[r][c] = null
        globalBarren = START_BARREN
        globalPlatten = 0.0
        tech.clear()
        grid[0][0] = Machine(MType.REAKTOR)   // fester Basis-Strom, unzerstoerbar
    }

    fun lvl(id: String): Int = tech[id] ?: 0
    fun has(id: String): Boolean = lvl(id) > 0

    fun canBuild(t: MType): Boolean = when (t) {
        MType.BOHRER, MType.OFEN -> true
        MType.REAKTOR -> false
        else -> has(UNLOCK[t] ?: return false)
    }

    fun buildCost(t: MType): Double = BUILD_COST[t] ?: 0.0

    /** Platziert eine Maschine, falls Feld frei, freigeschaltet und bezahlbar. */
    fun build(t: MType, r: Int, c: Int): Boolean {
        if (grid[r][c] != null) return false
        if (!canBuild(t)) return false
        val cost = buildCost(t)
        if (globalBarren < cost) return false
        globalBarren -= cost
        grid[r][c] = Machine(t)
        return true
    }

    /** Verkauft eine Maschine: 50% der Baukosten, skaliert mit dem Restzustand. */
    fun sell(r: Int, c: Int): Double {
        val m = grid[r][c] ?: return 0.0
        if (m.type == MType.REAKTOR) return 0.0
        val refund = buildCost(m.type) * 0.5 * (m.condition / 100.0)
        globalBarren += refund
        grid[r][c] = null
        return refund
    }

    fun nextCost(node: TechNode): Double =
        kotlin.math.round(node.baseCost * node.growth.pow(lvl(node.id)))

    fun buyTech(id: String): Boolean {
        val node = TECHS.firstOrNull { it.id == id } ?: return false
        val l = lvl(id)
        if (l >= node.maxLevel) return false
        if (node.prereq != null && !has(node.prereq)) return false
        val cost = nextCost(node)
        if (globalBarren < cost) return false
        globalBarren -= cost
        tech[id] = l + 1
        return true
    }

    fun repair(m: Machine): Boolean {
        if (m.condition >= 99.999) return false
        if (globalBarren < REPAIR_COST) return false
        globalBarren -= REPAIR_COST
        m.condition = 100.0
        return true
    }

    // --- Tech-abhaengige Parameter (Stufen-Upgrades) ---
    private fun bohrerRate() = BOHRER_RATE * (1.0 + 0.08 * lvl("t_bspeed"))
    private fun ofenRate() = OFEN_RATE * (1.0 + 0.08 * lvl("t_ospeed"))
    private fun presseRate() = PRESSE_RATE * (1.0 + 0.08 * lvl("t_pspeed"))
    fun platteValue() = VAL_PLATTE * (1.0 + 0.25 * lvl("t_wert"))

    private fun wearPerSec(t: MType) = when (t) {
        MType.BOHRER -> 1.0 / 60.0
        MType.OFEN -> 1.2 / 60.0
        MType.PRESSE -> 1.5 / 60.0
        MType.GENERATOR -> 0.8 / 60.0
        MType.DROHNE -> 0.5 / 60.0
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
        val res = ArrayList<IntArray>(8)
        val diag = has("t_diag")
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            if (!diag && dr != 0 && dc != 0) continue
            val nr = r + dr
            val nc = c + dc
            if (nr in 0 until n && nc in 0 until n) res.add(intArrayOf(nr, nc))
        }
        return res
    }

    private fun wantedRes(t: MType): IntArray = when (t) {
        MType.OFEN -> intArrayOf(Res.ROHERZ.ordinal)
        MType.PRESSE -> intArrayOf(Res.BARREN.ordinal)
        MType.GENERATOR -> intArrayOf(Res.ROHERZ.ordinal)
        MType.LAGER -> intArrayOf(0, 1, 2)
        else -> IntArray(0)
    }

    private fun offers(t: MType, res: Int): Boolean = when (t) {
        MType.BOHRER -> res == Res.ROHERZ.ordinal
        MType.OFEN -> res == Res.BARREN.ordinal
        MType.PRESSE -> res == Res.PLATTE.ordinal
        MType.LAGER -> true
        else -> false
    }

    private fun powerDrawOf(m: Machine): Double = m.type.power

    private fun wantsToRun(m: Machine, r: Int, c: Int): Boolean = when (m.type) {
        MType.BOHRER -> m.output[Res.ROHERZ.ordinal] < OUT_CAP - 1e-9
        MType.OFEN -> m.input[Res.ROHERZ.ordinal] > 1e-6 && m.output[Res.BARREN.ordinal] < OUT_CAP - 1e-9
        MType.PRESSE -> m.input[Res.BARREN.ordinal] > 1e-6 && m.output[Res.PLATTE.ordinal] < OUT_CAP - 1e-9
        MType.DROHNE -> neighbors(r, c).any { grid[it[0]][it[1]]?.let { g -> g.condition < 99.999 } == true }
        else -> false
    }

    /** Adjazenz-Transfer: Verbraucher ziehen aus angrenzenden Anbietern. */
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

    /** Ein Simulationsschritt ueber `dt` Sekunden. */
    fun step(dt: Double) {
        val ddt = dt.coerceIn(0.0, 2.0)
        if (ddt <= 0.0) return

        transfers()

        // Stromversorgung: Basis-Reaktor + Generatoren mit Brennstoff
        var supply = 0.0
        forEachMachine { m, _, _ ->
            if (m.type == MType.REAKTOR) supply += REAKTOR_POWER
            if (m.type == MType.GENERATOR && m.input[Res.ROHERZ.ordinal] > 1e-6) supply += GEN_POWER
        }
        // Nachfrage: alle Verbraucher, die laufen wollen
        var demand = 0.0
        forEachMachine { m, r, c -> if (wantsToRun(m, r, c)) demand += powerDrawOf(m) }
        val scale = if (demand <= 0.0) 1.0 else min(1.0, supply / demand)
        powerSupply = supply
        powerDemand = demand

        var barMade = 0.0
        var platMade = 0.0

        forEachMachine { m, r, c ->
            m.starved = false
            when (m.type) {
                MType.BOHRER -> {
                    val nominal = bohrerRate() * ddt
                    val want = nominal * scale * wearMult(m.condition)
                    val made = max(0.0, min(want, OUT_CAP - m.output[Res.ROHERZ.ordinal]))
                    m.output[Res.ROHERZ.ordinal] += made
                    m.condition = max(0.0, m.condition - wearPerSec(MType.BOHRER) * (made / bohrerRate()))
                    m.util = if (nominal > 1e-9) made / nominal else 0.0
                }
                MType.OFEN -> {
                    val nominal = ofenRate() * ddt
                    val want = nominal * scale * wearMult(m.condition)
                    val byInput = m.input[Res.ROHERZ.ordinal] / 2.0
                    val bySpace = OUT_CAP - m.output[Res.BARREN.ordinal]
                    val made = max(0.0, min(want, min(byInput, bySpace)))
                    m.input[Res.ROHERZ.ordinal] -= made * 2.0
                    m.output[Res.BARREN.ordinal] += made
                    m.condition = max(0.0, m.condition - wearPerSec(MType.OFEN) * (made / ofenRate()))
                    m.util = if (nominal > 1e-9) made / nominal else 0.0
                    barMade += made
                    if (byInput <= 1e-9 && m.output[Res.BARREN.ordinal] < OUT_CAP - 1e-9) m.starved = true
                }
                MType.PRESSE -> {
                    val nominal = presseRate() * ddt
                    val want = nominal * scale * wearMult(m.condition)
                    val byInput = m.input[Res.BARREN.ordinal] / 2.0
                    val bySpace = OUT_CAP - m.output[Res.PLATTE.ordinal]
                    val made = max(0.0, min(want, min(byInput, bySpace)))
                    m.input[Res.BARREN.ordinal] -= made * 2.0
                    m.output[Res.PLATTE.ordinal] += made
                    m.condition = max(0.0, m.condition - wearPerSec(MType.PRESSE) * (made / presseRate()))
                    m.util = if (nominal > 1e-9) made / nominal else 0.0
                    platMade += made
                    if (byInput <= 1e-9 && m.output[Res.PLATTE.ordinal] < OUT_CAP - 1e-9) m.starved = true
                }
                MType.GENERATOR -> {
                    val fuel = min(m.input[Res.ROHERZ.ordinal], GEN_FUEL * ddt)
                    m.input[Res.ROHERZ.ordinal] -= fuel
                    if (fuel > 0.0) m.condition = max(0.0, m.condition - wearPerSec(MType.GENERATOR) * (fuel / GEN_FUEL))
                    m.util = if (GEN_FUEL * ddt > 1e-9) fuel / (GEN_FUEL * ddt) else 0.0
                    if (fuel <= 1e-9) m.starved = true
                }
                MType.DROHNE -> {
                    var did = false
                    for (nb in neighbors(r, c)) {
                        val g = grid[nb[0]][nb[1]] ?: continue
                        if (g.condition < 99.999) {
                            g.condition = min(100.0, g.condition + DROHNE_RATE * ddt * scale)
                            did = true
                        }
                    }
                    if (did) m.condition = max(0.0, m.condition - wearPerSec(MType.DROHNE) * ddt * scale)
                    m.util = if (did) 1.0 else 0.0
                }
                MType.LAGER, MType.REAKTOR -> { m.util = 0.0 }
            }
        }

        // Angrenzende Verbraucher (Lager/Presse) ziehen die eben produzierten
        // Barren/Platten ZUERST — erst danach holt der Lift den Ueberschuss.
        // Sonst wuerde der Lift der Kette das Material wegschnappen und ein
        // Ofen->Lager->Presse-Aufbau bliebe leer.
        transfers()

        // Schacht-Lift: Ueberschuss an Barren/Platten in den globalen Bestand holen
        forEachMachine { m, _, _ ->
            if (m.type == MType.OFEN) {
                val amt = min(LIFT * ddt, m.output[Res.BARREN.ordinal])
                m.output[Res.BARREN.ordinal] -= amt
                globalBarren += amt
            } else if (m.type == MType.PRESSE) {
                val amt = min(LIFT * ddt, m.output[Res.PLATTE.ordinal])
                m.output[Res.PLATTE.ordinal] -= amt
                globalPlatten += amt
            }
        }

        // Produktionswert (gleitender Mittelwert ueber ~8s)
        val tau = 8.0
        val a = 1.0 - exp(-ddt / tau)
        emaBarrenPerSec += (barMade / ddt - emaBarrenPerSec) * a
        emaPlattePerSec += (platMade / ddt - emaPlattePerSec) * a
    }

    /** Simuliert die verstrichene Abwesenheit (bis 8h Cap) und liefert einen Report. */
    fun runOffline(elapsedSeconds: Int): OfflineReport {
        val cap = min(elapsedSeconds, OFFLINE_CAP)
        val b0 = globalBarren
        val p0 = globalPlatten
        val events = ArrayList<OfflineEvent>()
        val seenStarve = HashSet<Machine>()
        val seenDead = HashSet<Machine>()
        var t = 0
        while (t < cap) {
            step(1.0)
            forEachMachine { m, r, c ->
                if (m.condition <= 0.0 && !seenDead.contains(m)) {
                    seenDead.add(m)
                    if (events.size < 24)
                        events.add(OfflineEvent(t, "${m.type.label} (${r + 1},${c + 1}): Totalausfall (0% Zustand)"))
                }
                if ((m.type == MType.OFEN || m.type == MType.PRESSE || m.type == MType.GENERATOR) &&
                    m.starved && !seenStarve.contains(m)
                ) {
                    seenStarve.add(m)
                    if (events.size < 24)
                        events.add(OfflineEvent(t, "${m.type.label} (${r + 1},${c + 1}): Nachschub gestoppt"))
                }
            }
            t++
        }
        return OfflineReport(elapsedSeconds, cap, globalBarren - b0, globalPlatten - p0, events)
    }

    // --- Speichern / Laden (lokal, offline) ---
    fun toJson(nowMillis: Long): String {
        val root = JSONObject()
        root.put("t", nowMillis)
        root.put("gb", globalBarren)
        root.put("gp", globalPlatten)
        val techObj = JSONObject()
        for ((k, v) in tech) techObj.put(k, v)
        root.put("tech", techObj)
        val cells = JSONArray()
        forEachMachine { m, r, c ->
            val o = JSONObject()
            o.put("r", r); o.put("c", c); o.put("ty", m.type.ordinal)
            o.put("cond", m.condition)
            o.put("in", JSONArray(listOf(m.input[0], m.input[1], m.input[2])))
            o.put("out", JSONArray(listOf(m.output[0], m.output[1], m.output[2])))
            cells.put(o)
        }
        root.put("cells", cells)
        return root.toString()
    }

    /** Laedt den Zustand. Rueckgabe: gespeicherter Zeitstempel (ms) oder 0. */
    fun fromJson(s: String): Long {
        val root = JSONObject(s)
        for (r in 0 until n) for (c in 0 until n) grid[r][c] = null
        globalBarren = root.optDouble("gb", 0.0)
        globalPlatten = root.optDouble("gp", 0.0)
        tech.clear()
        val tv = root.opt("tech")
        if (tv is JSONArray) {
            // Altes Format (Set aus Ids) -> jede Freischaltung als Stufe 1
            for (i in 0 until tv.length()) tech[tv.getString(i)] = 1
        } else if (tv is JSONObject) {
            val keys = tv.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                tech[k] = tv.getInt(k)
            }
        }
        val cells = root.optJSONArray("cells")
        if (cells != null) for (i in 0 until cells.length()) {
            val o = cells.getJSONObject(i)
            val ty = MType.values()[o.getInt("ty")]
            val m = Machine(ty)
            m.condition = o.optDouble("cond", 100.0)
            val ia = o.optJSONArray("in"); val oa = o.optJSONArray("out")
            if (ia != null) for (k in 0 until 3) m.input[k] = ia.optDouble(k, 0.0)
            if (oa != null) for (k in 0 until 3) m.output[k] = oa.optDouble(k, 0.0)
            grid[o.getInt("r")][o.getInt("c")] = m
        }
        if (grid[0][0] == null) grid[0][0] = Machine(MType.REAKTOR)
        return root.optLong("t", 0L)
    }
}
