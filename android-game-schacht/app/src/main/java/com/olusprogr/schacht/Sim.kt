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
    HAENDLER("Haendler", "H", 0.0)
}

class Machine(var type: MType) {
    var condition = 100.0
    val input = DoubleArray(Res.values().size)
    val output = DoubleArray(Res.values().size)
    var util = 0.0
    var starved = false
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
    val n = 12
    val grid = Array(n) { arrayOfNulls<Machine>(n) }
    var globalBarren = 0.0
    var globalPlatten = 0.0
    var globalKomponente = 0.0
    var money = 0.0
    var mapSeed = 12345L
    val tech = HashMap<String, Int>()

    var powerSupply = 0.0
    var powerDemand = 0.0

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
        const val DROHNE_RATE = 12.0
        const val BOOST_PER = 0.20
        const val COMPONENT_PRICE = 8.0
        const val HAENDLER_SELL = 2.0
        const val OFFLINE_CAP = 8 * 3600
        const val START_BARREN = 35.0

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
            TechNode("t_boost", "Verstaerker freischalten", 20.0, 1.0, 1, "beschleunigt Nachbarn", "t_gen", Res.PLATTE),
            // Upgrades (mit Geld bezahlt)
            TechNode("t_bspeed", "Bohrer-Tempo", 25.0, 1.3, 20, "+8%/Stufe", null, null),
            TechNode("t_ospeed", "Ofen-Tempo", 35.0, 1.3, 20, "+8%/Stufe", null, null),
            TechNode("t_pspeed", "Presse-Tempo", 45.0, 1.3, 20, "+8%/Stufe", "t_presse", null),
            TechNode("t_aspeed", "Assembler-Tempo", 55.0, 1.3, 20, "+8%/Stufe", "t_assembler", null),
            TechNode("t_wert", "Komponenten-Preis", 60.0, 1.4, 10, "+25%/Stufe", "t_assembler", null),
            TechNode("t_area", "Flaeche erweitern", 90.0, 1.7, 4, "+1 Reihe & Spalte", null, null),
            TechNode("t_takt", "Fabrik-Takt (alle Maschinen)", 50.0, 1.35, 20, "+5%/Stufe", null, null),
            TechNode("t_robust", "Robustheit (weniger Verschleiss)", 40.0, 1.3, 10, "-5%/Stufe", null, null),
            TechNode("t_lift", "Lift-Tempo", 35.0, 1.3, 10, "+10%/Stufe", null, null),
            TechNode("t_power", "Reaktor-Leistung", 45.0, 1.3, 20, "+5 Strom/Stufe", null, null)
        )

        val UNLOCK = mapOf(
            MType.PRESSE to "t_presse",
            MType.GENERATOR to "t_gen",
            MType.LAGER to "t_lager",
            MType.DROHNE to "t_drohne",
            MType.ASSEMBLER to "t_assembler",
            MType.VERSTAERKER to "t_boost",
            MType.HAENDLER to "t_haendler"
        )

        // Baukosten: (Rohstoff, Menge). Presse=Barren, Assembler=Platten usw.
        val BUILD_COST = mapOf(
            MType.BOHRER to Pair(Res.BARREN, 5.0),
            MType.OFEN to Pair(Res.BARREN, 8.0),
            MType.PRESSE to Pair(Res.BARREN, 12.0),
            MType.GENERATOR to Pair(Res.BARREN, 10.0),
            MType.LAGER to Pair(Res.BARREN, 8.0),
            MType.ASSEMBLER to Pair(Res.PLATTE, 10.0),
            MType.HAENDLER to Pair(Res.PLATTE, 12.0),
            MType.DROHNE to Pair(Res.PLATTE, 8.0),
            MType.VERSTAERKER to Pair(Res.PLATTE, 6.0)
        )
    }

    fun newGame() {
        for (r in 0 until n) for (c in 0 until n) grid[r][c] = null
        globalBarren = START_BARREN
        globalPlatten = 0.0
        globalKomponente = 0.0
        money = 0.0
        mapSeed = System.nanoTime() xor 0x5DEECE66DL
        tech.clear()
        grid[0][0] = Machine(MType.REAKTOR)
    }

    fun lvl(id: String): Int = tech[id] ?: 0
    fun has(id: String): Boolean = lvl(id) > 0

    fun areaN(): Int = min(n, 8 + lvl("t_area"))

    /** Bodenreichtum 0..3 (leer/normal/moderat/reich), deterministisch je Feld. */
    fun richness(r: Int, c: Int): Int {
        var h = mapSeed xor (r.toLong() * 341873128712L) xor (c.toLong() * 132897987541L)
        h = h xor (h ushr 13); h *= -0x61c8864680b583ebL; h = h xor (h ushr 27)
        val v = ((h ushr 33).toInt() and 0x7fffffff) % 100
        return when { v < 15 -> 0; v < 58 -> 1; v < 86 -> 2; else -> 3 }
    }
    private fun oreMult(r: Int, c: Int) = ORE_MULT[richness(r, c)]

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
        MType.REAKTOR -> false
        else -> {
            val u = UNLOCK[t]
            u != null && has(u)
        }
    }

    fun buildCost(t: MType): Pair<Res, Double> = BUILD_COST[t] ?: Pair(Res.BARREN, 0.0)

    fun build(t: MType, r: Int, c: Int): Boolean {
        if (r < 0 || c < 0 || r >= areaN() || c >= areaN()) return false
        if (grid[r][c] != null) return false
        if (!canBuild(t)) return false
        val (res, amt) = buildCost(t)
        if (!spend(res, amt)) return false
        grid[r][c] = Machine(t)
        return true
    }

    /** Verkauf: 50% der Baukosten (im gleichen Rohstoff), skaliert mit Zustand. */
    fun sell(r: Int, c: Int): Double {
        val m = grid[r][c] ?: return 0.0
        if (m.type == MType.REAKTOR) return 0.0
        val (res, amt) = buildCost(m.type)
        val refund = amt * 0.5 * (m.condition / 100.0)
        addGlobal(res, refund)
        grid[r][c] = null
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
    private fun globalMult() = 1.0 + 0.05 * lvl("t_takt")
    private fun wearFactor() = max(0.3, 1.0 - 0.05 * lvl("t_robust"))
    private fun liftRate() = LIFT * (1.0 + 0.1 * lvl("t_lift"))
    private fun reactorPower() = REAKTOR_POWER + 5.0 * lvl("t_power")
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
        MType.DROHNE -> neighbors(r, c).any { grid[it[0]][it[1]]?.let { g -> g.condition < 99.999 } == true }
        MType.VERSTAERKER -> neighbors(r, c).any {
            val g = grid[it[0]][it[1]]?.type
            g == MType.BOHRER || g == MType.OFEN || g == MType.PRESSE || g == MType.ASSEMBLER
        }
        else -> false
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
        }
        var demand = 0.0
        forEachMachine { m, r, c -> if (wantsToRun(m, r, c)) demand += m.type.power }
        val scale = if (demand <= 0.0) 1.0 else min(1.0, supply / demand)
        powerSupply = supply
        powerDemand = demand

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
                    var did = false
                    for (nb in neighbors(r, c)) {
                        val g = grid[nb[0]][nb[1]] ?: continue
                        if (g.condition < 99.999) {
                            g.condition = min(100.0, g.condition + DROHNE_RATE * ddt * scale)
                            did = true
                        }
                    }
                    if (did) m.condition = max(0.0, m.condition - wearPerSec(MType.DROHNE) * wf * ddt * scale)
                    m.util = if (did) 1.0 else 0.0
                }
                MType.VERSTAERKER -> {
                    val active = wantsToRun(m, r, c)
                    if (active) m.condition = max(0.0, m.condition - wearPerSec(MType.VERSTAERKER) * wf * ddt * scale)
                    m.util = if (active) 1.0 else 0.0
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
            val ia = JSONArray(); val oa = JSONArray()
            for (k in 0 until rc) { ia.put(m.input[k]); oa.put(m.output[k]) }
            o.put("in", ia); o.put("out", oa)
            cells.put(o)
        }
        root.put("cells", cells)
        return root.toString()
    }

    fun fromJson(s: String): Long {
        val root = JSONObject(s)
        for (r in 0 until n) for (c in 0 until n) grid[r][c] = null
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
            val m = Machine(MType.values()[o.getInt("ty")])
            m.condition = o.optDouble("cond", 100.0)
            val ia = o.optJSONArray("in"); val oa = o.optJSONArray("out")
            if (ia != null) for (k in 0 until min(rc, ia.length())) m.input[k] = ia.optDouble(k, 0.0)
            if (oa != null) for (k in 0 until min(rc, oa.length())) m.output[k] = oa.optDouble(k, 0.0)
            grid[o.getInt("r")][o.getInt("c")] = m
        }
        if (grid[0][0] == null) grid[0][0] = Machine(MType.REAKTOR)
        return root.optLong("t", 0L)
    }
}
