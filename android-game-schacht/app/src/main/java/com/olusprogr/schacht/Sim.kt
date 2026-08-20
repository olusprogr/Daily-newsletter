package com.olusprogr.schacht

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.json.JSONArray
import org.json.JSONObject

/** Rohstoffe. Tier 1-3: Roherz -> Barren -> Platte -> Komponente. */
enum class Res { ROHERZ, BARREN, PLATTE, KOMPONENTE, WASSER, BLEI, DAMPF, STROM }

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
    FORSCHUNG("Forschungszentrum", "Fz", 12.0),
    BLEIBOHRER("Tiefen-Bohrer", "Pb", 4.0),
    WASSERPUMPE("Wasserpumpe", "Aq", 3.0),
    ZENTRIFUGE("Zentrifuge", "Zf", 9.0),
    BLEIPRESSE("Bleipresse", "Bp", 8.0),
    BRENNSTABWERK("Brennstabwerk", "Bw", 11.0),
    REAKTORKERN("Reaktorkern", "Rk", 12.0),
    KUEHLTURM("Kuehlturm", "Kt", 10.0)
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
    // Nur fuer die Drohnen-Station: aktuelle Flugposition (fraktionale Gitterkoordinaten,
    // Y=Zeile/X=Spalte). -1.0 = noch nicht initialisiert (erster step() setzt sie auf die
    // Ruheposition ueber der Station). Die Reparatur greift erst, wenn diese Position das
    // Ziel wirklich erreicht hat - kein reines Reichweiten-Aura-Heilen mehr.
    var flyR = -1.0
    var flyC = -1.0
    // Drohnen-Station: repariert nur, wenn Guthaben >= dieser Schwelle (per Klick einstellbar).
    var moneyGate = 0.0
    // Individuelle Ausbau-Stufe DIESER Maschine (unabhaengig vom globalen Tech-Baum).
    var lvl = 0
    // Nur fuer Lager: je Rohstoff, ob dieses Lager ihn annimmt (Standard: alle).
    val acceptRes = BooleanArray(Res.values().size) { true }
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
    /** Vom Spieler auf bereits gekauftem Land ausgebaute AKW-Plattform (Level 2). */
    val expandedPlatform = BooleanArray(n * n)
    /** Vom Spieler gegrabener kuenstlicher Wasserkanal (Level 2, an Land, kein echtes Wasser). */
    val expandedCanal = BooleanArray(n * n)
    var globalBarren = 0.0
    var globalPlatten = 0.0
    var globalKomponente = 0.0
    var globalStrom = 0.0     // Level 2: Endprodukt (Kuehlturm), das der Haendler verkauft
    var money = 0.0
    var research = 0.0               // Forschungswaehrung (Forschungszentren) -> Upgrades
    // --- Unternehmens-/Prestige-System ---
    var companyLevel = 1             // 1 Bergbau, 2 Kernkraft, 3 Petrochemie, 4 High-Tech
    var shares = 0.0                 // permanente Aktien aus Firmenverkaeufen
    var dividends = 0.0              // passives Einkommen/s aus verkauften Firmen
    // Level-2-Plattform (bereits errichtet, gelb-schwarzer Rand, metallischer Kern).
    var platformR0 = -1; var platformC0 = -1; var platformR1 = -1; var platformC1 = -1
    // Oel-Bohrinsel (3x3, dekorativ): Spoiler fuers naechste Level (Petrochemie).
    var oilRigR0 = -1; var oilRigC0 = -1; var oilRigR1 = -1; var oilRigC1 = -1
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
        // --- Level 2: Kernkraft ---
        const val BLEIBOHRER_RATE = 0.45
        const val WASSERPUMPE_RATE = 0.6
        const val ZENTRIFUGE_RATE = 0.18
        const val BLEIPRESSE_RATE = 0.22
        const val BRENNSTABWERK_RATE = 0.12
        const val REAKTORKERN_RATE = 0.5     // Brennstabsatz -> Dampf
        const val KUEHLTURM_RATE = 0.45      // Dampf -> Strom (verkaufbar)
        const val LIFT = 3.0
        const val IN_CAP = 10.0
        const val OUT_CAP = 20.0
        const val LAGER_CAP = 120.0
        const val REPAIR_COST = 5.0
        const val DROHNE_RATE = 5.0      // Basis-Reparaturtempo am aktuellen Ziel (%/s)
        const val DROHNE_R = 3           // Basis-Reichweite der Station (Chebyshev-Radius)
        const val DROHNE_REPAIR_COST_PER = 0.1  // Geld je repariertem Zustands-% (Drohne)
        const val DROHNE_GATE_STEP = 25.0       // Schrittweite fuer das Reparatur-Limit
        const val DROHNE_FLY_SPEED = 3.0        // Basis-Fluggeschwindigkeit (Zellen/s)
        const val DROHNE_UPKEEP = 2.0           // Geld/s Betriebskosten je Drohnen-Station
        const val BOOST_PER = 0.20
        const val COMPONENT_PRICE = 8.0
        const val HAENDLER_SELL = 2.0
        const val OFFLINE_CAP = 8 * 3600
        const val START_BARREN = 35.0
        const val START_MONEY = 45.0     // Startgeld: erster Haendler (24) + ein paar Chunks

        const val LAND_THRESH = 0.46     // Schwelle Land/Wasser aus dem Rauschen
        const val SCAN_R = 4             // (Alt) Prospektor-Radius – Prospektor entfernt
        const val CHUNK_COST = 5.0       // Geld, um einen Chunk freizuschalten (1 Klick)
        // Infrastruktur-Ausbau (Level 2): bereits gekauftes Land in AKW-Plattform ODER
        // kuenstlichen Wasserkanal umwandeln - deutlich teurer als jede einzelne Maschine,
        // skaliert daher (wie die uebrigen Level-2-Kosten) mit payAnchor().
        const val EXPAND_PLATFORM_FACTOR = 2.0
        const val EXPAND_CANAL_FACTOR = 1.5
        const val CANAL_WATER_MULT = 0.75       // nur etwas weniger ergiebig als eine echte Quelle
        const val WIND_POWER = 16.0      // Strom je Windrad (ohne Brennstoff)
        const val SOLAR_POWER = 10.0     // Strom je Solarpanel (ohne Brennstoff)
        const val RESEARCH_RATE = 0.1    // Basis-Forschung je Sekunde und Forschungszentrum (Upgrade erhoeht)
        const val WIND_COAST_BONUS = 1.30 // Windrad neben Kueste: +30% Strom

        // Verkaufsziele je Unternehmens-Level (danach x100 pro weiterem Level).
        val LEVEL_GOAL = doubleArrayOf(10_000_000.0, 1_000_000_000.0, 100_000_000_000.0, 10_000_000_000_000.0)
        const val MAX_LEVEL = 4

        const val HOSE_MAX = 5           // max. Schlauchlaenge Reaktor -> Wasser

        // Grundflaeche je Typ (Breite, Hoehe in Zellen). Anker = untere linke Zelle.
        fun footprint(t: MType): Pair<Int, Int> = when (t) {
            MType.WINDRAD -> Pair(1, 2)
            MType.REAKTOR -> Pair(3, 3)
            MType.FORSCHUNG -> Pair(2, 1)
            MType.REAKTORKERN -> Pair(2, 2)
            MType.KUEHLTURM -> Pair(2, 3)
            else -> Pair(1, 1)
        }

        // Kosten fuers ENTFERNEN eines Hindernisses (Geld): keine, Nadelbaum, Laubbaum, Fels, Busch
        val OBSTACLE_COST = intArrayOf(0, 8, 8, 14, 4)

        // Bodenreichtum je Feld -> Ausbeute-Faktor des Bohrers
        val ORE_MULT = doubleArrayOf(0.0, 0.6, 1.0, 1.7)

        val TECHS = listOf(
            // Maschinen-Freischaltungen (mit Rohstoffen bezahlt)
            TechNode("t_lager", "Lager freischalten", 30.0, 1.0, 1, "", null, Res.BARREN),
            TechNode("t_presse", "Presse freischalten", 40.0, 1.0, 1, "", null, Res.BARREN),
            TechNode("t_gen", "Generator freischalten", 60.0, 1.0, 1, "", null, Res.BARREN),
            TechNode("t_drohne", "Wartungsdrohne freischalten", 80.0, 1.0, 1, "", "t_gen", Res.BARREN),
            TechNode("t_assembler", "Assembler freischalten", 50.0, 1.0, 1, "Platte -> Komponente", "t_presse", Res.PLATTE),
            TechNode("t_haendler", "Haendler freischalten", 40.0, 1.0, 1, "Komponenten -> Geld", "t_assembler", Res.PLATTE),
            TechNode("t_wind", "Windrad freischalten", 56.0, 1.0, 1, "Strom aus Wind", "t_gen", Res.BARREN),
            TechNode("t_solar", "Solarpanel freischalten", 48.0, 1.0, 1, "Strom aus Sonne", "t_gen", Res.BARREN),
            TechNode("t_research", "Forschungszentrum freischalten", 60.0, 1.0, 1, "boostet alle Maschinen", "t_assembler", Res.PLATTE),
            // Upgrades (mit Geld bezahlt)
            TechNode("t_bspeed", "Bohrer-Tempo", 50.0, 1.3, 20, "+8%/Stufe", null, null),
            TechNode("t_ospeed", "Ofen-Tempo", 70.0, 1.3, 20, "+8%/Stufe", null, null),
            TechNode("t_pspeed", "Presse-Tempo", 90.0, 1.3, 20, "+8%/Stufe", "t_presse", null),
            TechNode("t_aspeed", "Assembler-Tempo", 110.0, 1.3, 20, "+8%/Stufe", "t_assembler", null),
            TechNode("t_wert", "Komponenten-Preis", 120.0, 1.4, 10, "+25%/Stufe", "t_assembler", null),
            TechNode("t_takt", "Fabrik-Takt (alle Maschinen)", 100.0, 1.35, 20, "+5%/Stufe", null, null),
            TechNode("t_robust", "Robustheit (weniger Verschleiss)", 80.0, 1.3, 10, "-5%/Stufe", null, null),
            TechNode("t_lift", "Lift-Tempo", 70.0, 1.3, 10, "+10%/Stufe", null, null),
            TechNode("t_power", "Reaktor-Leistung", 90.0, 1.3, 20, "+5 Strom/Stufe", null, null),
            // Drohnen-Upgrades (mit Geld bezahlt)
            TechNode("t_drohne_rep", "Drohnen-Reparatur", 80.0, 1.35, 15, "+20%/Stufe", "t_drohne", null),
            TechNode("t_drohne_speed", "Drohnen-Fluggeschwindigkeit", 70.0, 1.3, 10, "+20%/Stufe", "t_drohne", null),
            TechNode("t_drohne_range", "Drohnen-Reichweite", 120.0, 1.6, 3, "+1 Feld/Stufe", "t_drohne", null),
            // Forschungszentrum-Upgrade (mit Forschung bezahlt)
            TechNode("t_research_rate", "Forschungs-Tempo", 60.0, 1.4, 12, "+0.1/s pro Stufe", "t_research", null),
            // --- Level 2: Kernkraft-Freischaltungen (mit Rohstoffen bezahlt) ---
            TechNode("t_wasserpumpe", "Wasserpumpe freischalten", 40.0, 1.0, 1, "Wasser aus der Kueste", null, Res.BARREN),
            TechNode("t_zentrifuge", "Zentrifuge freischalten", 60.0, 1.0, 1, "Uranerz+Wasser -> Angereichertes Uran", "t_wasserpumpe", Res.BARREN),
            TechNode("t_bleipresse", "Bleipresse freischalten", 40.0, 1.0, 1, "Blei -> Blei-Verkleidung", null, Res.BARREN),
            TechNode("t_brennstabwerk", "Brennstabwerk freischalten", 50.0, 1.0, 1, "Fertigt Brennstabsaetze", "t_bleipresse", Res.PLATTE),
            TechNode("t_reaktorkern", "Reaktorkern freischalten", 60.0, 1.0, 1, "Brennstabsatz -> Dampf", "t_brennstabwerk", Res.PLATTE),
            TechNode("t_kuehlturm", "Kuehlturm freischalten", 70.0, 1.0, 1, "Dampf -> Strom", "t_reaktorkern", Res.PLATTE),
            // Level-2-Tempo-Upgrades (mit Forschung bezahlt) - Gegenstuecke zu
            // t_bspeed/t_ospeed/t_pspeed/t_aspeed fuer die Kernkraft-Maschinen.
            TechNode("t_pbspeed", "Tiefen-Bohrer-Tempo", 50.0, 1.3, 20, "+8%/Stufe", "t_gen", null),
            TechNode("t_wpspeed", "Wasserpumpe-Tempo", 60.0, 1.3, 20, "+8%/Stufe", "t_wasserpumpe", null),
            TechNode("t_zfspeed", "Zentrifuge-Tempo", 80.0, 1.3, 20, "+8%/Stufe", "t_zentrifuge", null),
            TechNode("t_bpspeed", "Bleipresse-Tempo", 70.0, 1.3, 20, "+8%/Stufe", "t_bleipresse", null),
            TechNode("t_bwspeed", "Brennstabwerk-Tempo", 100.0, 1.3, 20, "+8%/Stufe", "t_brennstabwerk", null),
            TechNode("t_rkspeed", "Reaktorkern-Tempo", 120.0, 1.3, 20, "+8%/Stufe", "t_reaktorkern", null),
            TechNode("t_ktspeed", "Kuehlturm-Tempo", 130.0, 1.3, 20, "+8%/Stufe", "t_kuehlturm", null),
            // --- Bisher fehlende Techs (beide Level, sofern nicht level-spezifisch) ---
            TechNode("t_ore", "Bohr-Ausbeute", 90.0, 1.35, 15, "+6%/Stufe", null, null),
            TechNode("t_lagercap", "Lager-Kapazitaet", 60.0, 1.3, 10, "+15%/Stufe", "t_lager", null),
            TechNode("t_genpower", "Generator-Leistung", 80.0, 1.3, 10, "+10%/Stufe", "t_gen", null),
            TechNode("t_windpower", "Windrad-Leistung", 90.0, 1.3, 10, "+10%/Stufe", "t_wind", null),
            TechNode("t_solarpower", "Solar-Leistung", 85.0, 1.3, 10, "+10%/Stufe", "t_solar", null),
            // Nur Level 2: Kanaele gibt es erst ab der Kernkraft-Stufe.
            TechNode("t_kanal", "Kanal-Ergiebigkeit", 110.0, 1.4, 5, "+5%/Stufe", "t_wasserpumpe", null)
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
            MType.FORSCHUNG to "t_research",
            MType.WASSERPUMPE to "t_wasserpumpe",
            MType.ZENTRIFUGE to "t_zentrifuge",
            MType.BLEIPRESSE to "t_bleipresse",
            MType.BRENNSTABWERK to "t_brennstabwerk",
            MType.REAKTORKERN to "t_reaktorkern",
            MType.KUEHLTURM to "t_kuehlturm"
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
            MType.FORSCHUNG to 4,
            MType.BLEIBOHRER to 24,
            MType.WASSERPUMPE to 20,
            MType.ZENTRIFUGE to 20,
            MType.BLEIPRESSE to 20,
            MType.BRENNSTABWERK to 20,
            MType.REAKTORKERN to 12,
            MType.KUEHLTURM to 8
        )

        // Baukosten in Rohstoffen: (Rohstoff, Menge).
        val BUILD_COST = mapOf(
            MType.BOHRER to Pair(Res.BARREN, 5.0),
            MType.OFEN to Pair(Res.BARREN, 8.0),
            MType.PRESSE to Pair(Res.BARREN, 12.0),
            MType.GENERATOR to Pair(Res.BARREN, 10.0),
            MType.SOLAR to Pair(Res.BARREN, 10.0),
            MType.FORSCHUNG to Pair(Res.PLATTE, 20.0),
            MType.ASSEMBLER to Pair(Res.PLATTE, 10.0),
            MType.HAENDLER to Pair(Res.PLATTE, 24.0),   // Haendler mit Platten kaufen
            // --- Level 2: Kernkraft ---
            MType.BLEIBOHRER to Pair(Res.BARREN, 6.0),
            MType.WASSERPUMPE to Pair(Res.BARREN, 8.0),
            MType.ZENTRIFUGE to Pair(Res.BARREN, 14.0),
            MType.BLEIPRESSE to Pair(Res.BARREN, 12.0),
            MType.BRENNSTABWERK to Pair(Res.PLATTE, 16.0),
            MType.REAKTORKERN to Pair(Res.PLATTE, 20.0),
            MType.KUEHLTURM to Pair(Res.PLATTE, 24.0)
        )

        // Baukosten in GELD (Turbine/Lager/Drohne), +100% teurer als zuvor.
        val MONEY_BUILD = mapOf(
            MType.WINDRAD to 28.0,    // vorher 14 Barren
            MType.LAGER to 16.0,      // vorher 8 Barren
            MType.DROHNE to 16.0      // vorher 8 Platte
        )

        // Ab Level 2 (Nuklear) wird ALLES mit Geld gekauft, verankert an der Geld/Minute-Rate
        // (Dividende) der zuletzt verkauften Firma: ein fester Bruchteil einer Minute "Gehalt"
        // je Gebaeude. So bleiben Baukosten immer fair (skalieren mit dem Fortschritt) und
        // trotzdem fordernd. Werte per Wirtschafts-Simulation gegengeprueft (kein Soft-Lock).
        val NUCLEAR_COST_FACTOR = mapOf(
            MType.BOHRER to 0.05,
            MType.BLEIBOHRER to 0.06,
            MType.WASSERPUMPE to 0.08,
            MType.GENERATOR to 0.1,
            MType.SOLAR to 0.12,
            MType.LAGER to 0.1,
            MType.WINDRAD to 0.15,
            MType.DROHNE to 0.2,
            MType.BLEIPRESSE to 0.3,
            MType.ZENTRIFUGE to 0.35,
            MType.FORSCHUNG to 0.4,
            MType.HAENDLER to 0.5,
            MType.BRENNSTABWERK to 0.6,
            MType.REAKTORKERN to 0.9,
            MType.KUEHLTURM to 1.0
        )
        // Ab Level 2 werden auch Tech-Freischaltungen (bisher mit Barren/Platte bezahlt) in
        // Geld umgerechnet - sonst entsteht ein Zirkel-Deadlock (z.B. Zentrifuge freischalten
        // braucht Barren, aber nur die Zentrifuge selbst produziert welche).
        val NUCLEAR_TECH_FACTOR = mapOf(
            "t_wasserpumpe" to 0.1,
            "t_zentrifuge" to 0.2,
            "t_bleipresse" to 0.1,
            "t_brennstabwerk" to 0.3,
            "t_reaktorkern" to 0.5,
            "t_kuehlturm" to 0.6
        )
        // Techs, die nur bei companyLevel==1 Sinn ergeben (Gebaeude/Reaktor, die es ab
        // Level 2 nicht mehr gibt) bzw. nur ab companyLevel>=2 (Kernkraft-Freischaltungen).
        // Steuert, was im Tech-Baum je Level ueberhaupt angezeigt wird.
        val LEVEL1_ONLY_TECHS = setOf("t_presse", "t_assembler", "t_ospeed", "t_pspeed", "t_aspeed", "t_power")
        val LEVEL2_ONLY_TECHS = setOf(
            "t_wasserpumpe", "t_zentrifuge", "t_bleipresse", "t_brennstabwerk", "t_reaktorkern", "t_kuehlturm",
            "t_pbspeed", "t_wpspeed", "t_zfspeed", "t_bpspeed", "t_bwspeed", "t_rkspeed", "t_ktspeed",
            "t_kanal"
        )
        // Ab Level 2 zeigen manche weiterhin genutzten Techs urspruenglich auf einen
        // Level-1-only-Prereq ("Assembler freischalten") - hier durch eine erreichbare
        // Kernkraft-Alternative ersetzt, sonst waeren sie fuer immer gesperrt.
        val NUCLEAR_PREREQ_OVERRIDE = mapOf(
            "t_haendler" to "t_zentrifuge",
            "t_research" to "t_wasserpumpe",
            "t_wert" to "t_haendler"
        )

        // --- Individuelle Maschinen-Upgrades (pro platziertem Exemplar, mit Geld bezahlt) ---
        const val MACHINE_UPGRADE_GROWTH = 1.16
        const val MACHINE_UPGRADE_BONUS = 0.12    // +12% Tempo/Ertrag je Stufe
        const val MACHINE_UPGRADE_MAXLVL = 20
        val MACHINE_UPGRADE_BASE = mapOf(
            MType.BOHRER to 18.0, MType.OFEN to 22.0, MType.PRESSE to 28.0, MType.ASSEMBLER to 35.0,
            MType.GENERATOR to 20.0, MType.WINDRAD to 24.0, MType.SOLAR to 22.0, MType.LAGER to 16.0,
            MType.BLEIBOHRER to 20.0, MType.WASSERPUMPE to 22.0, MType.ZENTRIFUGE to 45.0,
            MType.BLEIPRESSE to 36.0, MType.BRENNSTABWERK to 60.0, MType.REAKTORKERN to 90.0, MType.KUEHLTURM to 100.0
        )
        // Nicht upgradebar: keine echten placeable items (auto-platziert / abgeschaltet),
        // oder ein Einzel-Upgrade waere ueberfluessig, weil es dafuer schon den Tech-Baum
        // gibt (Forschungszentrum, Haendler, Drohnen-Station - siehe t_drohne_* Techs).
        val NOT_UPGRADABLE = setOf(MType.REAKTOR, MType.VERSTAERKER, MType.PROSPEKTOR, MType.FORSCHUNG, MType.HAENDLER, MType.DROHNE)
    }

    fun newGame() {
        for (r in 0 until n) for (c in 0 until n) { grid[r][c] = null; occ[r][c] = null }
        surveyed.fill(false)
        harvested.fill(false)
        expandedPlatform.fill(false)
        expandedCanal.fill(false)
        globalBarren = START_BARREN
        globalPlatten = 0.0
        globalKomponente = 0.0
        globalStrom = 0.0
        money = START_MONEY
        research = 0.0
        mapSeed = System.nanoTime() xor 0x5DEECE66DL
        tech.clear()
        companyLevel = 1; shares = 0.0; dividends = 0.0
        placePlatform()
        placeOilRig()
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
        // Kanal geht IMMER vor - auch mitten auf der (sonst immer als Land geltenden)
        // Plattform, sonst liesse sich dort kein Kanal graben.
        if (expandedCanal[r * n + c]) return false
        if (isPlatform(r, c)) return true       // Plattform ist immer Land/bebaubar
        if (expandedPlatform[r * n + c]) return true // vom Spieler ausgebaute Plattform-Flaeche
        return landValue(r, c) > LAND_THRESH
    }
    fun isExpandedPlatform(r: Int, c: Int): Boolean = r in 0 until n && c in 0 until n && expandedPlatform[r * n + c]
    fun isExpandedCanal(r: Int, c: Int): Boolean = r in 0 until n && c in 0 until n && expandedCanal[r * n + c]

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
        if (isPlatform(r, c) || isExpandedPlatform(r, c)) return 0
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

    // --- Infrastruktur-Ausbau (nur Level 2): bereits gekauftes, leeres Land in AKW-
    // Plattform ODER kuenstlichen Wasserkanal umwandeln. ---

    /**
     * Darf an diesem Feld ueberhaupt etwas an der Infrastruktur ausgebaut werden (Basis
     * fuer Plattform-Erweiterung UND Kanal-Grabung)? Ein Kanal laesst sich auch auf der
     * (bereits vorhandenen) AKW-Plattform selbst graben, nicht nur auf freiem Land daneben.
     */
    fun canExpandInfra(r: Int, c: Int): Boolean {
        if (companyLevel < 2) return false
        if (r !in 0 until n || c !in 0 until n) return false
        if (grid[r][c] != null || occ[r][c] != null) return false
        if (isLocked(r, c)) return false
        if (hasObstacle(r, c)) return false
        if (expandedCanal[r * n + c]) return false
        return isLand(r, c)   // muss (noch) Land sein - egal ob normal, Plattform oder Erweiterung
    }

    /** Speziell die Plattform-Erweiterung: nur auf noch normalem Land, keine bereits
     *  vorhandene Plattform "nochmal" erweitern. */
    fun canExpandPlatformHere(r: Int, c: Int): Boolean =
        canExpandInfra(r, c) && !isPlatform(r, c) && !expandedPlatform[r * n + c]

    /**
     * Wasser direkt angrenzend - Pflicht fuer den Kanal. Zaehlt echtes Meer GENAUSO wie
     * ein bereits gegrabener Nachbar-Kanal, damit man Kanaele aneinanderreihen und so
     * richtige kuenstliche Fluesse bauen kann, statt nur direkt am Meer graben zu koennen.
     */
    fun hasWaterAdjacent(r: Int, c: Int): Boolean {
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val rr = r + dr; val cc = c + dc
            if (rr !in 0 until n || cc !in 0 until n) continue
            if (!isLand(rr, cc)) return true   // offenes Meer ODER schon ein Kanal (beide isLand=false)
        }
        return false
    }

    fun expandPlatformCost(): Double = kotlin.math.round(payAnchor() * EXPAND_PLATFORM_FACTOR)
    fun expandCanalCost(): Double = kotlin.math.round(payAnchor() * EXPAND_CANAL_FACTOR)

    /** Feld in ausgebaute AKW-Plattform umwandeln (bebaubar, ausser Windrad/Solar). */
    fun expandToPlatform(r: Int, c: Int): Boolean {
        if (!canExpandPlatformHere(r, c)) return false
        if (!spendMoney(expandPlatformCost())) return false
        expandedPlatform[r * n + c] = true
        return true
    }

    /** Feld zum kuenstlichen Wasserkanal graben (auch mitten auf der Plattform moeglich) -
     *  nicht mehr bebaubar, dafuer (schwache) Wasserquelle. */
    fun expandToCanal(r: Int, c: Int): Boolean {
        if (!canExpandInfra(r, c)) return false
        if (!hasWaterAdjacent(r, c)) return false
        if (!spendMoney(expandCanalCost())) return false
        expandedPlatform[r * n + c] = false   // falls hier vorher Plattform war: sauber ersetzen
        expandedCanal[r * n + c] = true
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
    private fun oreMult(r: Int, c: Int) =
        if (isLand(r, c)) ORE_MULT[richness(r, c)] * (1.0 + 0.06 * lvl("t_ore")) else 0.0

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
        Res.STROM -> globalStrom
        Res.ROHERZ, Res.WASSER, Res.BLEI, Res.DAMPF -> 0.0   // lokale Ressourcen, kein globaler Pool
    }
    fun available(res: Res) = globalOf(res) + lagerSum(res.ordinal)
    fun availableBarren() = available(Res.BARREN)
    fun availablePlatten() = available(Res.PLATTE)
    fun availableKomponente() = available(Res.KOMPONENTE)
    fun availableStrom() = available(Res.STROM)

    private fun addGlobal(res: Res, amt: Double) {
        when (res) {
            Res.BARREN -> globalBarren += amt
            Res.PLATTE -> globalPlatten += amt
            Res.KOMPONENTE -> globalKomponente += amt
            Res.STROM -> globalStrom += amt
            Res.ROHERZ, Res.WASSER, Res.BLEI, Res.DAMPF -> {}
        }
    }

    /** Jede Sorte laesst sich aus einem Lager entnehmen - fuer die vier Sorten mit
     *  globalem Pool (Barren/Platte/Komponente/Strom) wandert der Bestand dorthin (bleibt
     *  also erhalten); fuer die vier rein lokalen Sorten (Roherz/Wasser/Blei/Dampf) gibt
     *  es keinen globalen Pool - die werden schlicht geleert, genau wie jeder Nachschub-
     *  Ueberschuss, den ein Nachfolger nicht schnell genug abnimmt, sonst ohnehin verpufft. */
    fun canWithdrawFromLager(res: Int): Boolean = res in Res.values().indices

    /**
     * Entnimmt den kompletten Bestand eines Rohstoffs aus einem Lager - macht im Lager-
     * Puffer wieder Platz fuer neuen Nachschub (sonst blockiert ein volles Lager irgend-
     * wann die Zulieferer). Bei den vier global gepoolten Sorten bleibt der Bestand
     * erhalten (wandert in den globalen Pool); bei den restlichen vier wird er geleert.
     * Gibt die entnommene Menge zurueck (0 = nichts passiert).
     */
    fun withdrawFromLager(r: Int, c: Int, res: Int): Double {
        val a = anchorOf(r, c) ?: return 0.0
        val m = grid[a[0]][a[1]] ?: return 0.0
        if (m.type != MType.LAGER || !canWithdrawFromLager(res)) return 0.0
        val amt = m.output[res]
        if (amt <= 1e-9) return 0.0
        m.output[res] = 0.0
        addGlobal(Res.values()[res], amt)
        return amt
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
            Res.STROM -> globalStrom -= g
            Res.ROHERZ, Res.WASSER, Res.BLEI, Res.DAMPF -> {}
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

    private fun spendResearch(amt: Double): Boolean {
        if (research < amt - 1e-9) return false
        research -= amt
        return true
    }

    /** Wird dieser Maschinentyp mit Geld statt Rohstoffen gebaut? Ab Level 2: immer. */
    fun isMoneyBuilt(t: MType): Boolean = if (companyLevel >= 2) true else MONEY_BUILD.containsKey(t)
    fun moneyBuildCost(t: MType): Double = if (companyLevel >= 2) nuclearMoneyCost(t) else (MONEY_BUILD[t] ?: 0.0)

    /** Geld/Minute-Anker: die Dividende der zuletzt verkauften Firma, mit Mindestwert. */
    private fun payAnchor(): Double = max(dividends * 60.0, 300.0)

    /**
     * Level-2+-Baukosten: reines Geld, verankert an der Geld/Minute-Rate (Dividende) der
     * zuletzt verkauften Firma. So bleibt jedes Gebaeude immer ein fester Bruchteil einer
     * Minute "Gehalt" der letzten Firma wert - fair (skaliert mit dem Fortschritt) und
     * trotzdem fordernd (nie geschenkt).
     */
    private fun nuclearMoneyCost(t: MType): Double =
        kotlin.math.round(payAnchor() * (NUCLEAR_COST_FACTOR[t] ?: 1.0))

    fun canBuild(t: MType): Boolean = when (t) {
        MType.BOHRER, MType.OFEN, MType.BLEIBOHRER -> true
        MType.REAKTOR, MType.VERSTAERKER, MType.PROSPEKTOR -> false
        else -> {
            val u = UNLOCK[t]
            u != null && has(u)
        }
    }

    // --- Unternehmens-/Prestige-System ---
    /** Geldziel fuer den Verkauf der aktuellen Firma. */
    fun levelGoal(): Double =
        if (companyLevel <= MAX_LEVEL) LEVEL_GOAL[companyLevel - 1]
        else LEVEL_GOAL[MAX_LEVEL - 1] * Math.pow(100.0, (companyLevel - MAX_LEVEL).toDouble())

    /** Fortschritt 0..1 zum Verkaufsziel. */
    fun levelProgress(): Double = (money / levelGoal()).coerceIn(0.0, 1.0)

    /** Firma verkaufsbereit? */
    fun canSellCompany(): Boolean = money >= levelGoal()

    /** Aktien, die der Verkauf jetzt einbringen wuerde. */
    fun sharesGain(): Double {
        if (!canSellCompany()) return 0.0
        return Math.floor(25.0 * companyLevel * Math.sqrt(money / levelGoal()))
    }

    /** Aktien-Bonus auf den Verkaufswert (permanent). */
    fun shareBonus(): Double = 1.0 + 0.04 * shares

    /** Level-Multiplikator auf den Verkaufswert (jedes Level ist wertvoller). */
    fun companyMult(): Double = Math.pow(8.0, (companyLevel - 1).toDouble())

    /**
     * Firma verkaufen: naechstes Level, permanente Aktien, alte Fabrik zahlt
     * ab jetzt Dividende. Karte, Maschinen, Geld, Forschung und Tech werden
     * zurueckgesetzt – Aktien/Dividenden/Level bleiben.
     */
    fun sellCompany(): Boolean {
        if (!canSellCompany()) return false
        shares += sharesGain()
        dividends += levelGoal() * 5e-6      // 10M -> 50/s, 1Mrd -> 5000/s
        companyLevel++
        resetFactory()
        return true
    }

    /**
     * NUR ZUM TESTEN (Debug-Menue): direkt auf ein beliebiges Unternehmens-Level springen,
     * ohne es vorher durchspielen zu muessen. Setzt die Fabrik zurueck (wie ein Verkauf),
     * vergibt aber weder Aktien noch Dividenden. Wird spaeter wieder entfernt.
     */
    fun debugSetLevel(level: Int) {
        companyLevel = level.coerceIn(1, MAX_LEVEL)
        resetFactory()
    }

    fun hasPlatform(): Boolean = platformR0 >= 0
    fun isPlatform(r: Int, c: Int): Boolean =
        hasPlatform() && r in platformR0..platformR1 && c in platformC0..platformC1
    fun isPlatformEdge(r: Int, c: Int): Boolean =
        isPlatform(r, c) && (r == platformR0 || r == platformR1 || c == platformC0 || c == platformC1)

    fun hasOilRig(): Boolean = oilRigR0 >= 0
    fun isOilRig(r: Int, c: Int): Boolean =
        hasOilRig() && r in oilRigR0..oilRigR1 && c in oilRigC0..oilRigC1

    /**
     * Oel-Bohrinsel (3x3, rein dekorativ): ein "Spoiler" fuers naechste Unternehmens-
     * Level (Petrochemie/Oel-Raffinerie) - genau wie der Level-1-Reaktor schon vor dem
     * Kernkraft-Level zu sehen war. Steht auf offenem Wasser, in Sichtweite der
     * Plattform aber nicht direkt angrenzend. Rein visuell, keine Bau-/Spielmechanik.
     */
    private fun placeOilRig() {
        oilRigR0 = -1; oilRigC0 = -1; oilRigR1 = -1; oilRigC1 = -1
        if (companyLevel != 2 || !hasPlatform()) return
        val size = 3
        val pr = (platformR0 + platformR1) / 2; val pc = (platformC0 + platformC1) / 2
        var bestR = -1; var bestC = -1; var bestScore = Int.MAX_VALUE
        for (R in 0..n - size) for (C in 0..n - size) {
            var allWater = true
            for (dr in 0 until size) for (dc in 0 until size) if (!rawWater(R + dr, C + dc)) allWater = false
            if (!allWater) continue
            val cr = R + size / 2; val cc = C + size / 2
            val dist = kotlin.math.abs(cr - pr) + kotlin.math.abs(cc - pc)
            if (dist < 9 || dist > 20) continue   // nicht direkt an der Plattform, aber auch nicht zu weit weg
            val score = kotlin.math.abs(dist - 13)
            if (score < bestScore) { bestScore = score; bestR = R; bestC = C }
        }
        if (bestR < 0) return   // kein passender Wasserfleck gefunden -> einfach weglassen
        oilRigR0 = bestR; oilRigC0 = bestC
        oilRigR1 = bestR + size - 1; oilRigC1 = bestC + size - 1
        for (r in oilRigR0..oilRigR1) for (c in oilRigC0..oilRigC1) surveyed[r * n + c] = true
    }

    /**
     * Level-2-Plattform: ein bereits errichtetes, grosses Baufeld nahe einer
     * Wasserquelle, moeglichst zentral auf der Karte. Wird nur fuer companyLevel==2
     * platziert; alle Zellen gelten sofort als Land, freigeschaltet und bebaubar.
     */
    /**
     * Sucht eine Plattform-Position, die eine "echte" Kueste an genau EINER Seite hat
     * (nie zwei gegenueberliegende Seiten -> sonst wuerde ein Gewaesser durchschnitten
     * werden). `strict` verlangt zusaetzlich, dass das Innere ueberwiegend natuerliches
     * Land ist - sonst saehe es aus, als haette die Plattform ein Stueck See/Fluss
     * abgeschnitten. Gibt den Zentrierungs-Score zurueck oder null bei Ablehnung.
     */
    private fun scorePlatform(R: Int, C: Int, size: Int, strict: Boolean): Int? {
        val sideThresh = size / 3   // mind. 1/3 einer Kante muss Wasser sein: "echte" Kueste
        var wN = 0; var wS = 0; var wE = 0; var wW = 0
        for (i in 0 until size) {
            if (rawWater(R - 1, C + i)) wN++
            if (rawWater(R + size, C + i)) wS++
            if (rawWater(R + i, C - 1)) wW++
            if (rawWater(R + i, C + size)) wE++
        }
        val nSide = wN >= sideThresh; val sSide = wS >= sideThresh
        val wSide = wW >= sideThresh; val eSide = wE >= sideThresh
        if (!nSide && !sSide && !wSide && !eSide) return null   // keine echte Kueste in der Naehe
        if (nSide && sSide) return null                          // wuerde ein Gewaesser durchschneiden
        if (wSide && eSide) return null                          // dito, andere Achse
        if (strict) {
            var waterInside = 0
            for (dr in 0 until size) for (dc in 0 until size) if (rawWater(R + dr, C + dc)) waterInside++
            if (waterInside > size * size / 8) return null       // zu viel Wasser im Innern -> abgelehnt
        }
        val cr = R + size / 2; val cc2 = C + size / 2
        return kotlin.math.abs(cr - startR) + kotlin.math.abs(cc2 - startC)
    }

    /**
     * Level-2-Plattform: ein bereits errichtetes, grosses Baufeld nahe einer Wasserquelle,
     * moeglichst zentral auf der Karte - so platziert, dass sie nie aussieht, als haette sie
     * ein Stueck See/Fluss abgeschnitten (nur eine echte Kuestenseite, ueberwiegend Land im
     * Innern). Wird nur fuer companyLevel==2 platziert; alle Zellen gelten sofort als Land,
     * freigeschaltet und bebaubar.
     */
    private fun placePlatform() {
        platformR0 = -1; platformC0 = -1; platformR1 = -1; platformC1 = -1
        if (companyLevel != 2) return
        val size = 9
        var bestR = -1; var bestC = -1; var bestScore = Int.MAX_VALUE
        for (strict in booleanArrayOf(true, false)) {
            for (R in 0..n - size) for (C in 0..n - size) {
                val sc = scorePlatform(R, C, size, strict) ?: continue
                if (sc < bestScore) { bestScore = sc; bestR = R; bestC = C }
            }
            if (bestR >= 0) break
        }
        if (bestR < 0) {
            // Notfall: erzwinge eine zentrale Position, auch ohne Kuestennaehe.
            bestR = (startR - size / 2).coerceIn(0, n - size)
            bestC = (startC - size / 2).coerceIn(0, n - size)
        }
        platformR0 = bestR; platformC0 = bestC
        platformR1 = bestR + size - 1; platformC1 = bestC + size - 1
        for (r in platformR0..platformR1) for (c in platformC0..platformC1) surveyed[r * n + c] = true
    }

    /** Setzt nur die Fabrik zurueck (Prestige-Daten bleiben erhalten). */
    private fun resetFactory() {
        for (r in 0 until n) for (c in 0 until n) { grid[r][c] = null; occ[r][c] = null }
        surveyed.fill(false); harvested.fill(false)
        expandedPlatform.fill(false); expandedCanal.fill(false)
        globalBarren = START_BARREN; globalPlatten = 0.0; globalKomponente = 0.0; globalStrom = 0.0
        money = START_MONEY; research = 0.0
        mapSeed = System.nanoTime() xor 0x5DEECE66DL
        tech.clear()
        placePlatform()
        placeOilRig()
        // Der fertig gebaute Reaktor gibt es nur beim ersten (Bergbau-)Unternehmen.
        // Ab Level 2 baut man sein eigenes Kraftwerk (Reaktorkern + Kuehlturm) selbst.
        if (companyLevel == 1) placeReactor() else reactorPipe.clear()
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

    fun maxCount(t: MType): Int {
        val base = MAX_COUNT[t] ?: return Int.MAX_VALUE
        return base + 4 * (companyLevel - 1)      // hoehere Level erlauben groessere Fabriken
    }

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
            // Ab Level 2 ist die AKW-Flaeche (Plattform + Erweiterungen) strikt fuer die
            // Industrie reserviert: alles ausser Windrad/Solar NUR dort, Windrad/Solar NUR
            // auf normalem Land daneben (brauchen freien Wind/Himmel).
            if (companyLevel >= 2) {
                val onPlatform = isPlatform(rr, cc) || expandedPlatform[rr * n + cc]
                if (t == MType.WINDRAD || t == MType.SOLAR) {
                    if (onPlatform) return false
                } else if (!onPlatform) return false
            }
        }
        if (isMoneyBuilt(t)) {
            if (!spendMoney(moneyBuildCost(t))) return false
        } else {
            val (res, amt) = buildCost(t)
            if (!spend(res, amt)) return false
        }
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
        val refund: Double
        if (isMoneyBuilt(m.type)) {
            refund = moneyBuildCost(m.type) * 0.5 * (m.condition / 100.0)
            money += refund
        } else {
            val (res, amt) = buildCost(m.type)
            refund = amt * 0.5 * (m.condition / 100.0)
            addGlobal(res, refund)
        }
        for (dy in 0 until m.h) for (dx in 0 until m.w) occ[ar - dy][ac + dx] = null
        grid[ar][ac] = null
        return refund
    }

    fun nextCost(node: TechNode): Double =
        kotlin.math.round(node.baseCost * node.growth.pow(lvl(node.id)))

    /** Ab Level 2 werden Rohstoff-Freischaltungen (costRes!=null) ebenfalls in Geld bezahlt. */
    private fun techMoneyCost(node: TechNode): Double =
        kotlin.math.round(payAnchor() * (NUCLEAR_TECH_FACTOR[node.id] ?: 0.3))

    /** Tatsaechlich angezeigter/zu zahlender Preis (fuer die Tech-Baum-UI). */
    fun techDisplayCost(node: TechNode): Double =
        if (companyLevel >= 2 && node.costRes != null) techMoneyCost(node) else nextCost(node)

    /**
     * Ist diese Tech im aktuellen Unternehmens-Level ueberhaupt relevant? Level-1-Techs
     * fuer Gebaeude, die es ab Level 2 nicht mehr gibt (Presse/Assembler/...), werden im
     * Nuklear-Modus ausgeblendet - und umgekehrt die Kernkraft-Freischaltungen bei Level 1.
     */
    fun techVisible(id: String): Boolean =
        if (companyLevel >= 2) id !in LEVEL1_ONLY_TECHS else id !in LEVEL2_ONLY_TECHS

    /**
     * Effektiver Prereq: ab Level 2 zeigen manche (weiterhin genutzte) Techs urspruenglich
     * auf einen Level-1-only-Prereq (z.B. "Assembler freischalten", das es ab Level 2 nicht
     * mehr gibt) - das wuerde sie fuer immer unerreichbar machen. Hier durch eine sinnvolle
     * Kernkraft-Alternative ersetzt.
     */
    fun prereqOf(node: TechNode): String? =
        if (companyLevel >= 2) (NUCLEAR_PREREQ_OVERRIDE[node.id] ?: node.prereq) else node.prereq

    fun techAffordable(node: TechNode): Boolean {
        if (companyLevel >= 2 && node.costRes != null) return money >= techMoneyCost(node)
        val cost = nextCost(node)
        // costRes==null bedeutet jetzt: mit Forschungswaehrung bezahlt (frueher Geld).
        return if (node.costRes != null) available(node.costRes) >= cost else research >= cost
    }

    fun buyTech(id: String): Boolean {
        val node = TECHS.firstOrNull { it.id == id } ?: return false
        val l = lvl(id)
        if (l >= node.maxLevel) return false
        val prereq = prereqOf(node)
        if (prereq != null && !has(prereq)) return false
        val ok = if (companyLevel >= 2 && node.costRes != null) {
            spendMoney(techMoneyCost(node))
        } else {
            val cost = nextCost(node)
            if (node.costRes != null) spend(node.costRes, cost) else spendResearch(cost)
        }
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
                val avail = if (companyLevel >= 2) availableStrom() else availableKomponente()
                if (avail <= 1e-6 && m.util < 0.5) return 1
            }
            MType.BLEIBOHRER -> {
                if (oreMult(r, c) <= 0.0) return 5
                if (m.output[Res.BLEI.ordinal] >= OUT_CAP - 0.5) return 2
                if (scale < 0.999 && m.util < 0.98) return 3
            }
            MType.WASSERPUMPE -> {
                if (!adjWater(r, c)) return 6
                if (m.output[Res.WASSER.ordinal] >= OUT_CAP - 0.5) return 2
                if (scale < 0.999 && m.util < 0.98) return 3
            }
            MType.ZENTRIFUGE -> {
                if (m.output[Res.BARREN.ordinal] >= OUT_CAP - 0.5) return 2
                if (m.starved) return 1
                if (scale < 0.999 && m.util < 0.98) return 3
            }
            MType.BLEIPRESSE -> {
                if (m.output[Res.PLATTE.ordinal] >= OUT_CAP - 0.5) return 2
                if (m.starved) return 1
                if (scale < 0.999 && m.util < 0.98) return 3
            }
            MType.BRENNSTABWERK -> {
                if (m.output[Res.KOMPONENTE.ordinal] >= OUT_CAP - 0.5) return 2
                if (m.starved) return 1
                if (scale < 0.999 && m.util < 0.98) return 3
            }
            MType.REAKTORKERN -> {
                if (m.output[Res.DAMPF.ordinal] >= OUT_CAP - 0.5) return 2
                if (m.starved) return 1
                if (scale < 0.999 && m.util < 0.98) return 3
            }
            MType.KUEHLTURM -> {
                if (m.output[Res.STROM.ordinal] >= OUT_CAP - 0.5) return 2
                if (m.starved) return 1
                if (scale < 0.999 && m.util < 0.98) return 3
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

    /** Kann DIESER Maschinentyp individuell hochgestuft werden? (nur echte placeable items) */
    fun canUpgradeMachine(t: MType): Boolean = t !in NOT_UPGRADABLE

    /** Geldkosten fuer die naechste individuelle Ausbaustufe dieser Maschine. */
    fun machineUpgradeCost(m: Machine): Double =
        kotlin.math.round((MACHINE_UPGRADE_BASE[m.type] ?: 20.0) * MACHINE_UPGRADE_GROWTH.pow(m.lvl))

    /** Tempo-/Ertrags-Multiplikator aus der individuellen Ausbaustufe dieser Maschine. */
    fun machineUpgradeMult(m: Machine): Double = 1.0 + MACHINE_UPGRADE_BONUS * m.lvl

    /** Eine einzelne platzierte Maschine individuell hochstufen (mit Geld bezahlt). */
    fun upgradeMachine(r: Int, c: Int): Boolean {
        val a = anchorOf(r, c) ?: return false
        val m = grid[a[0]][a[1]] ?: return false
        if (!canUpgradeMachine(m.type)) return false
        if (m.lvl >= MACHINE_UPGRADE_MAXLVL) return false
        if (!spendMoney(machineUpgradeCost(m))) return false
        m.lvl++
        return true
    }

    // --- Tech-abhaengige Parameter ---
    private fun globalMult() = 1.0 + 0.05 * lvl("t_takt")
    private fun wearFactor() = max(0.3, 1.0 - 0.05 * lvl("t_robust"))
    private fun liftRate() = LIFT * (1.0 + 0.1 * lvl("t_lift"))
    private fun reactorPower() = REAKTOR_POWER + 5.0 * lvl("t_power")
    fun lagerCap() = LAGER_CAP * (1.0 + 0.15 * lvl("t_lagercap"))
    fun genPower() = GEN_POWER * (1.0 + 0.10 * lvl("t_genpower"))
    fun windPower() = WIND_POWER * (1.0 + 0.10 * lvl("t_windpower"))
    fun solarPower() = SOLAR_POWER * (1.0 + 0.10 * lvl("t_solarpower"))
    fun droneRepairRate() = DROHNE_RATE * (1.0 + 0.20 * lvl("t_drohne_rep"))
    fun droneRange() = DROHNE_R + lvl("t_drohne_range")
    fun droneSpeedMult() = 1.0 + 0.20 * lvl("t_drohne_speed")
    fun researchRate() = RESEARCH_RATE + 0.1 * lvl("t_research_rate")   // +0.1/s je Stufe

    /** Windrad neben der Kueste (angrenzendes Wasser): +30% Strom, sonst 1.0. */
    fun windCoastBonus(r: Int, c: Int): Double {
        // Windrad ist 1x2 (Anker unten r,c; Kopf r-1,c). Kueste = angrenzendes Wasser.
        for (cell in listOf(intArrayOf(r, c), intArrayOf(r - 1, c))) {
            for (nb in neighbors(cell[0], cell[1])) if (rawWater(nb[0], nb[1])) return WIND_COAST_BONUS
        }
        return 1.0
    }
    private fun bohrerRate() = BOHRER_RATE * (1.0 + 0.08 * lvl("t_bspeed")) * globalMult()
    private fun ofenRate() = OFEN_RATE * (1.0 + 0.08 * lvl("t_ospeed")) * globalMult()
    private fun presseRate() = PRESSE_RATE * (1.0 + 0.08 * lvl("t_pspeed")) * globalMult()
    private fun assemblerRate() = ASSEMBLER_RATE * (1.0 + 0.08 * lvl("t_aspeed")) * globalMult()
    private fun bleibohrerRate() = BLEIBOHRER_RATE * (1.0 + 0.08 * lvl("t_pbspeed")) * globalMult()
    private fun wasserpumpeRate() = WASSERPUMPE_RATE * (1.0 + 0.08 * lvl("t_wpspeed")) * globalMult()
    private fun zentrifugeRate() = ZENTRIFUGE_RATE * (1.0 + 0.08 * lvl("t_zfspeed")) * globalMult()
    private fun bleipresseRate() = BLEIPRESSE_RATE * (1.0 + 0.08 * lvl("t_bpspeed")) * globalMult()
    private fun brennstabwerkRate() = BRENNSTABWERK_RATE * (1.0 + 0.08 * lvl("t_bwspeed")) * globalMult()
    private fun reaktorkernRate() = REAKTORKERN_RATE * (1.0 + 0.08 * lvl("t_rkspeed")) * globalMult()
    private fun kuehlturmRate() = KUEHLTURM_RATE * (1.0 + 0.08 * lvl("t_ktspeed")) * globalMult()
    fun componentPrice() = COMPONENT_PRICE * (1.0 + 0.25 * lvl("t_wert")) * companyMult() * shareBonus()

    private fun wearPerSec(t: MType) = when (t) {
        MType.BOHRER -> 1.0 / 60.0
        MType.OFEN -> 1.2 / 60.0
        MType.PRESSE -> 1.5 / 60.0
        MType.ASSEMBLER -> 1.6 / 60.0
        MType.GENERATOR -> 0.8 / 60.0
        MType.DROHNE -> 0.0   // kein Verschleiss - Drohnen kosten stattdessen Geld/s (DROHNE_UPKEEP)
        MType.VERSTAERKER -> 0.6 / 60.0
        MType.FORSCHUNG -> 0.7 / 60.0
        MType.BLEIBOHRER -> 1.0 / 60.0
        MType.WASSERPUMPE -> 0.8 / 60.0
        MType.ZENTRIFUGE -> 1.2 / 60.0
        MType.BLEIPRESSE -> 1.5 / 60.0
        MType.BRENNSTABWERK -> 1.6 / 60.0
        MType.REAKTORKERN -> 1.8 / 60.0
        MType.KUEHLTURM -> 1.4 / 60.0
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

    /**
     * Alle Zellen direkt AUSSERHALB der Grundflaeche eines (moeglicherweise mehrzelligen)
     * Gebaeudes, dessen Anker bei (r,c) liegt (Anker = unten links, waechst nach oben/
     * rechts um w-1/h-1). Fuer 1x1-Gebaeude identisch zu neighbors(r,c). Ohne das waeren
     * Nachbarn nur an der Anker-Zelle sichtbar - ein 2x2- oder 2x3-Gebaeude (Reaktorkern,
     * Kuehlturm) haette dann an 3 von 4 Seiten "blinde" Kanten fuer den Materialfluss.
     */
    private fun footprintNeighbors(r: Int, c: Int, w: Int, h: Int): List<IntArray> {
        val res = ArrayList<IntArray>()
        val rTop = r - (h - 1); val rBot = r
        val cLeft = c; val cRight = c + (w - 1)
        for (cc in cLeft..cRight) { res.add(intArrayOf(rTop - 1, cc)); res.add(intArrayOf(rBot + 1, cc)) }
        for (rr in rTop..rBot) { res.add(intArrayOf(rr, cLeft - 1)); res.add(intArrayOf(rr, cRight + 1)) }
        return res.filter { it[0] in 0 until n && it[1] in 0 until n }
    }
    /** Wasserpumpe: braucht ein angrenzendes Wasserfeld (Kueste/Fluss), um zu foerdern. */
    fun adjWater(r: Int, c: Int): Boolean =
        neighbors(r, c).any { rawWater(it[0], it[1]) || expandedCanal[it[0] * n + it[1]] }

    /** Richtungs-Offset (dr,dc) des ersten angrenzenden Wasserfeldes (Meer ODER Kanal),
     *  oder null - fuer das Ansaugrohr der Wasserpumpe (GameView.kt), das wirklich bis
     *  zur tatsaechlichen Wasserquelle reicht statt an einer festen Stelle zu kleben. */
    fun waterNeighborDir(r: Int, c: Int): IntArray? {
        val dirs = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))
        for (o in dirs) {
            val rr = r + o[0]; val cc = c + o[1]
            if (rr !in 0 until n || cc !in 0 until n) continue
            if (rawWater(rr, cc) || expandedCanal[rr * n + cc]) return o
        }
        return null
    }

    /** Ergiebigkeit der Wasserversorgung: volle Kraft an echtem Wasser, nur ein Bruchteil
     *  an einem kuenstlich gegrabenen Kanal (weniger effizient als eine echte Quelle). */
    private fun waterEfficiency(r: Int, c: Int): Double {
        if (neighbors(r, c).any { rawWater(it[0], it[1]) }) return 1.0
        if (neighbors(r, c).any { expandedCanal[it[0] * n + it[1]] })
            return min(1.0, CANAL_WATER_MULT + 0.05 * lvl("t_kanal"))
        return 0.0
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
        MType.LAGER -> intArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
        MType.ZENTRIFUGE -> intArrayOf(Res.ROHERZ.ordinal, Res.WASSER.ordinal)
        MType.BLEIPRESSE -> intArrayOf(Res.BLEI.ordinal)
        MType.BRENNSTABWERK -> intArrayOf(Res.BARREN.ordinal, Res.PLATTE.ordinal)
        MType.REAKTORKERN -> intArrayOf(Res.KOMPONENTE.ordinal)
        MType.KUEHLTURM -> intArrayOf(Res.DAMPF.ordinal)
        else -> IntArray(0)
    }

    private fun offers(t: MType, res: Int): Boolean = when (t) {
        MType.BOHRER -> res == Res.ROHERZ.ordinal
        MType.OFEN -> res == Res.BARREN.ordinal
        MType.PRESSE -> res == Res.PLATTE.ordinal
        MType.ASSEMBLER -> res == Res.KOMPONENTE.ordinal
        MType.LAGER -> true
        MType.BLEIBOHRER -> res == Res.BLEI.ordinal
        MType.WASSERPUMPE -> res == Res.WASSER.ordinal
        MType.ZENTRIFUGE -> res == Res.BARREN.ordinal
        MType.BLEIPRESSE -> res == Res.PLATTE.ordinal
        MType.BRENNSTABWERK -> res == Res.KOMPONENTE.ordinal
        MType.REAKTORKERN -> res == Res.DAMPF.ordinal
        MType.KUEHLTURM -> res == Res.STROM.ordinal
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
        MType.BLEIBOHRER -> m.output[Res.BLEI.ordinal] < OUT_CAP - 1e-9 && oreMult(r, c) > 0.0
        MType.WASSERPUMPE -> m.output[Res.WASSER.ordinal] < OUT_CAP - 1e-9 && adjWater(r, c)
        MType.ZENTRIFUGE -> m.input[Res.ROHERZ.ordinal] > 1e-6 && m.input[Res.WASSER.ordinal] > 1e-6 &&
            m.output[Res.BARREN.ordinal] < OUT_CAP - 1e-9
        MType.BLEIPRESSE -> m.input[Res.BLEI.ordinal] > 1e-6 && m.output[Res.PLATTE.ordinal] < OUT_CAP - 1e-9
        MType.BRENNSTABWERK -> m.input[Res.BARREN.ordinal] > 1e-6 && m.input[Res.PLATTE.ordinal] > 1e-6 &&
            m.output[Res.KOMPONENTE.ordinal] < OUT_CAP - 1e-9
        MType.REAKTORKERN -> m.input[Res.KOMPONENTE.ordinal] > 1e-6 && m.output[Res.DAMPF.ordinal] < OUT_CAP - 1e-9
        MType.KUEHLTURM -> m.input[Res.DAMPF.ordinal] > 1e-6 && m.output[Res.STROM.ordinal] < OUT_CAP - 1e-9
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

    /**
     * Bewegt die Flugposition einer Drohne (fraktionale Gitterkoordinaten) einen Schritt
     * Richtung Ziel; Geschwindigkeit skaliert mit Tempo-Tech/Ausbau. Gibt true zurueck,
     * sobald das Ziel diesen Tick erreicht wurde - DAS gattert die eigentliche Reparatur,
     * damit die Drohne wirklich hinfliegen muss statt per Reichweiten-Aura zu heilen.
     */
    private fun flyTowards(m: Machine, tY: Double, tX: Double, ddt: Double): Boolean {
        val dY = tY - m.flyR; val dX = tX - m.flyC
        val dist = kotlin.math.hypot(dY, dX)
        val step = DROHNE_FLY_SPEED * droneSpeedMult() * ddt
        if (dist <= step || dist < 1e-4) { m.flyR = tY; m.flyC = tX; return true }
        m.flyR += dY / dist * step; m.flyC += dX / dist * step
        return false
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
            // Lager-Kapazitaet waechst mit der individuellen Ausbaustufe.
            val cap = if (c.type == MType.LAGER) lagerCap() * machineUpgradeMult(c) else IN_CAP
            // Nachbarn der GESAMTEN Grundflaeche (nicht nur der Anker-Zelle) - sonst
            // waeren mehrzellige Gebaeude (Reaktorkern 2x2, Kuehlturm 2x3, ...) nur von
            // einer Seite aus belieferbar.
            val nbs = footprintNeighbors(r, cc, c.w, c.h)
            for (res in wants) {
                // Lager: je Rohstoff individuell blockierbar (siehe acceptRes-Toggle im UI).
                if (c.type == MType.LAGER && !c.acceptRes[res]) continue
                var space = cap - target[res]
                if (space <= 1e-9) continue
                for (nb in nbs) {
                    // Ueber anchorOf() aufloesen, damit auch mehrzellige LIEFERANTEN
                    // erkannt werden, wenn die Nachbarzelle nicht deren Anker ist.
                    val a = anchorOf(nb[0], nb[1]) ?: continue
                    val nm = grid[a[0]][a[1]] ?: continue
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
        forEachMachine { m, r, c ->
            if (m.type == MType.REAKTOR) supply += reactorPower()
            if (m.type == MType.GENERATOR && m.input[Res.ROHERZ.ordinal] > 1e-6) supply += genPower() * machineUpgradeMult(m)
            if (m.type == MType.WINDRAD) supply += windPower() * windCoastBonus(r, c) * machineUpgradeMult(m)
            if (m.type == MType.SOLAR) supply += solarPower() * machineUpgradeMult(m)
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
                    val mult = machineUpgradeMult(m)
                    val nominal = bohrerRate() * mult * ddt * boostAt(r, c) * oreMult(r, c)
                    val want = nominal * scale * wearMult(m.condition)
                    val made = max(0.0, min(want, OUT_CAP - m.output[Res.ROHERZ.ordinal]))
                    m.output[Res.ROHERZ.ordinal] += made
                    if (bohrerRate() > 0) m.condition = max(0.0, m.condition - wearPerSec(MType.BOHRER) * wf * (made / (bohrerRate() * mult)))
                    m.util = if (nominal > 1e-9) made / nominal else 0.0
                }
                MType.OFEN -> {
                    val mult = machineUpgradeMult(m)
                    val nominal = ofenRate() * mult * ddt * boostAt(r, c)
                    val want = nominal * scale * wearMult(m.condition)
                    val byInput = m.input[Res.ROHERZ.ordinal] / 2.0
                    val made = max(0.0, min(want, min(byInput, OUT_CAP - m.output[Res.BARREN.ordinal])))
                    m.input[Res.ROHERZ.ordinal] -= made * 2.0
                    m.output[Res.BARREN.ordinal] += made
                    m.condition = max(0.0, m.condition - wearPerSec(MType.OFEN) * wf * (made / (ofenRate() * mult)))
                    m.util = if (nominal > 1e-9) made / nominal else 0.0
                    barMade += made
                    if (byInput <= 1e-9 && m.output[Res.BARREN.ordinal] < OUT_CAP - 1e-9) m.starved = true
                }
                MType.PRESSE -> {
                    val mult = machineUpgradeMult(m)
                    val nominal = presseRate() * mult * ddt * boostAt(r, c)
                    val want = nominal * scale * wearMult(m.condition)
                    val byInput = m.input[Res.BARREN.ordinal] / 2.0
                    val made = max(0.0, min(want, min(byInput, OUT_CAP - m.output[Res.PLATTE.ordinal])))
                    m.input[Res.BARREN.ordinal] -= made * 2.0
                    m.output[Res.PLATTE.ordinal] += made
                    m.condition = max(0.0, m.condition - wearPerSec(MType.PRESSE) * wf * (made / (presseRate() * mult)))
                    m.util = if (nominal > 1e-9) made / nominal else 0.0
                    platMade += made
                    if (byInput <= 1e-9 && m.output[Res.PLATTE.ordinal] < OUT_CAP - 1e-9) m.starved = true
                }
                MType.ASSEMBLER -> {
                    val mult = machineUpgradeMult(m)
                    val nominal = assemblerRate() * mult * ddt * boostAt(r, c)
                    val want = nominal * scale * wearMult(m.condition)
                    val byInput = m.input[Res.PLATTE.ordinal] / 2.0
                    val made = max(0.0, min(want, min(byInput, OUT_CAP - m.output[Res.KOMPONENTE.ordinal])))
                    m.input[Res.PLATTE.ordinal] -= made * 2.0
                    m.output[Res.KOMPONENTE.ordinal] += made
                    m.condition = max(0.0, m.condition - wearPerSec(MType.ASSEMBLER) * wf * (made / (assemblerRate() * mult)))
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
                    // Ruheposition ueber der eigenen Station initialisieren (einmalig).
                    if (m.flyR < 0.0) { m.flyR = r + 0.12; m.flyC = c + 0.5 }
                    // Betriebskosten: eine Drohnen-Station kostet laufend Geld statt sich
                    // abzunutzen - man "bedient" sie also mit Geld, nicht mit Reparaturen.
                    money = max(0.0, money - DROHNE_UPKEEP * ddt)
                    if (money < m.moneyGate) {
                        // Reparatur-Limit nicht erreicht: Drohne pausiert und kehrt heim.
                        m.svR = -1; m.svC = -1; m.util = 0.0
                        flyTowards(m, r + 0.12, c + 0.5, ddt)
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
                            // Erst hinfliegen, DANN reparieren - reines Reichweiten-Heilen ganz
                            // ohne echten Flug war der gemeldete Bug ("brauchen nicht mehr zu
                            // fliegen, weil sich alles von selbst repariert").
                            val arrived = flyTowards(m, m.svR + 0.32, m.svC + 0.5, ddt)
                            if (!arrived) {
                                m.util = 0.5
                            } else {
                                var delta = min(droneRepairRate() * machineUpgradeMult(m) * ddt * scale, 100.0 - tgt.condition)
                                // Geld fuer die Reparatur abziehen; nur so viel, wie bezahlbar ist.
                                val maxByMoney = if (DROHNE_REPAIR_COST_PER > 1e-9) money / DROHNE_REPAIR_COST_PER else delta
                                if (maxByMoney < delta) delta = maxByMoney
                                if (delta > 1e-9) {
                                    tgt.condition = min(100.0, tgt.condition + delta)
                                    money = max(0.0, money - delta * DROHNE_REPAIR_COST_PER)
                                    m.util = 1.0
                                } else m.util = 0.0
                            }
                        } else {
                            m.util = 0.0
                            flyTowards(m, r + 0.12, c + 0.5, ddt)
                        }
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
                    // Basis 0.1/s, per Upgrade hoeher; laeuft, solange das Netz nicht tot ist.
                    val active = if (scale > 0.05) 1.0 else 0.0
                    // Kein Einzel-Ausbau mehr fuer Forschungszentren: die Rate kommt
                    // ausschliesslich aus RESEARCH_RATE (0.1/s) + dem Tech t_research_rate.
                    research += researchRate() * ddt * active   // produziert Forschungswaehrung
                    m.condition = max(0.0, m.condition - wearPerSec(MType.FORSCHUNG) * wf * ddt * active)
                    m.util = active
                }
                // --- Level 2: Kernkraft ---
                MType.BLEIBOHRER -> {
                    val mult = machineUpgradeMult(m)
                    val nominal = bleibohrerRate() * mult * ddt * boostAt(r, c) * oreMult(r, c)
                    val want = nominal * scale * wearMult(m.condition)
                    val made = max(0.0, min(want, OUT_CAP - m.output[Res.BLEI.ordinal]))
                    m.output[Res.BLEI.ordinal] += made
                    if (bleibohrerRate() > 0) m.condition = max(0.0, m.condition - wearPerSec(MType.BLEIBOHRER) * wf * (made / (bleibohrerRate() * mult)))
                    m.util = if (nominal > 1e-9) made / nominal else 0.0
                }
                MType.WASSERPUMPE -> {
                    val mult = machineUpgradeMult(m)
                    // An einem kuenstlichen Kanal statt echtem Wasser nur ein Bruchteil des
                    // Ertrags (CANAL_WATER_MULT) - weniger effizient als eine natuerliche Quelle.
                    val nominal = wasserpumpeRate() * mult * ddt * waterEfficiency(r, c)
                    val want = nominal * scale * wearMult(m.condition)
                    val made = max(0.0, min(want, OUT_CAP - m.output[Res.WASSER.ordinal]))
                    m.output[Res.WASSER.ordinal] += made
                    if (wasserpumpeRate() > 0) m.condition = max(0.0, m.condition - wearPerSec(MType.WASSERPUMPE) * wf * (made / (wasserpumpeRate() * mult)))
                    m.util = if (nominal > 1e-9) made / nominal else 0.0
                }
                MType.ZENTRIFUGE -> {
                    val mult = machineUpgradeMult(m)
                    val nominal = zentrifugeRate() * mult * ddt * boostAt(r, c)
                    val want = nominal * scale * wearMult(m.condition)
                    val byOre = m.input[Res.ROHERZ.ordinal] / 3.0
                    val byWater = m.input[Res.WASSER.ordinal]
                    val made = max(0.0, min(want, min(byOre, min(byWater, OUT_CAP - m.output[Res.BARREN.ordinal]))))
                    m.input[Res.ROHERZ.ordinal] -= made * 3.0
                    m.input[Res.WASSER.ordinal] -= made
                    m.output[Res.BARREN.ordinal] += made
                    m.condition = max(0.0, m.condition - wearPerSec(MType.ZENTRIFUGE) * wf * (made / (zentrifugeRate() * mult)))
                    m.util = if (nominal > 1e-9) made / nominal else 0.0
                    barMade += made
                    if ((byOre <= 1e-9 || byWater <= 1e-9) && m.output[Res.BARREN.ordinal] < OUT_CAP - 1e-9) m.starved = true
                }
                MType.BLEIPRESSE -> {
                    val mult = machineUpgradeMult(m)
                    val nominal = bleipresseRate() * mult * ddt * boostAt(r, c)
                    val want = nominal * scale * wearMult(m.condition)
                    val byInput = m.input[Res.BLEI.ordinal] / 2.0
                    val made = max(0.0, min(want, min(byInput, OUT_CAP - m.output[Res.PLATTE.ordinal])))
                    m.input[Res.BLEI.ordinal] -= made * 2.0
                    m.output[Res.PLATTE.ordinal] += made
                    m.condition = max(0.0, m.condition - wearPerSec(MType.BLEIPRESSE) * wf * (made / (bleipresseRate() * mult)))
                    m.util = if (nominal > 1e-9) made / nominal else 0.0
                    platMade += made
                    if (byInput <= 1e-9 && m.output[Res.PLATTE.ordinal] < OUT_CAP - 1e-9) m.starved = true
                }
                MType.BRENNSTABWERK -> {
                    val mult = machineUpgradeMult(m)
                    val nominal = brennstabwerkRate() * mult * ddt * boostAt(r, c)
                    val want = nominal * scale * wearMult(m.condition)
                    val byUran = m.input[Res.BARREN.ordinal] / 2.0
                    val byBlei = m.input[Res.PLATTE.ordinal] / 2.0
                    val made = max(0.0, min(want, min(byUran, min(byBlei, OUT_CAP - m.output[Res.KOMPONENTE.ordinal]))))
                    m.input[Res.BARREN.ordinal] -= made * 2.0
                    m.input[Res.PLATTE.ordinal] -= made * 2.0
                    m.output[Res.KOMPONENTE.ordinal] += made
                    m.condition = max(0.0, m.condition - wearPerSec(MType.BRENNSTABWERK) * wf * (made / (brennstabwerkRate() * mult)))
                    m.util = if (nominal > 1e-9) made / nominal else 0.0
                    // Brennstabsaetze bleiben lokal (kein Lift!) - erst Reaktorkern + Kuehlturm
                    // wandeln sie in Strom um, der global verkauft werden kann.
                    if ((byUran <= 1e-9 || byBlei <= 1e-9) && m.output[Res.KOMPONENTE.ordinal] < OUT_CAP - 1e-9) m.starved = true
                }
                MType.REAKTORKERN -> {
                    val mult = machineUpgradeMult(m)
                    val nominal = reaktorkernRate() * mult * ddt * boostAt(r, c)
                    val want = nominal * scale * wearMult(m.condition)
                    val byBrennstab = m.input[Res.KOMPONENTE.ordinal]
                    val made = max(0.0, min(want, min(byBrennstab, OUT_CAP - m.output[Res.DAMPF.ordinal])))
                    m.input[Res.KOMPONENTE.ordinal] -= made
                    m.output[Res.DAMPF.ordinal] += made
                    m.condition = max(0.0, m.condition - wearPerSec(MType.REAKTORKERN) * wf * (made / (reaktorkernRate() * mult)))
                    m.util = if (nominal > 1e-9) made / nominal else 0.0
                    if (byBrennstab <= 1e-9 && m.output[Res.DAMPF.ordinal] < OUT_CAP - 1e-9) m.starved = true
                }
                MType.KUEHLTURM -> {
                    val mult = machineUpgradeMult(m)
                    val nominal = kuehlturmRate() * mult * ddt * boostAt(r, c)
                    val want = nominal * scale * wearMult(m.condition)
                    val byDampf = m.input[Res.DAMPF.ordinal]
                    val made = max(0.0, min(want, min(byDampf, OUT_CAP - m.output[Res.STROM.ordinal])))
                    m.input[Res.DAMPF.ordinal] -= made
                    m.output[Res.STROM.ordinal] += made
                    m.condition = max(0.0, m.condition - wearPerSec(MType.KUEHLTURM) * wf * (made / (kuehlturmRate() * mult)))
                    m.util = if (nominal > 1e-9) made / nominal else 0.0
                    kompMade += made
                    if (byDampf <= 1e-9 && m.output[Res.STROM.ordinal] < OUT_CAP - 1e-9) m.starved = true
                }
                MType.LAGER, MType.REAKTOR, MType.HAENDLER -> { m.util = 0.0 }
            }
        }

        transfers()
        forEachMachine { m, _, _ ->
            when (m.type) {
                MType.OFEN, MType.ZENTRIFUGE -> {
                    val amt = min(liftRate() * ddt, m.output[Res.BARREN.ordinal])
                    m.output[Res.BARREN.ordinal] -= amt; globalBarren += amt
                }
                MType.PRESSE, MType.BLEIPRESSE -> {
                    val amt = min(liftRate() * ddt, m.output[Res.PLATTE.ordinal])
                    m.output[Res.PLATTE.ordinal] -= amt; globalPlatten += amt
                }
                MType.ASSEMBLER -> {
                    val amt = min(liftRate() * ddt, m.output[Res.KOMPONENTE.ordinal])
                    m.output[Res.KOMPONENTE.ordinal] -= amt; globalKomponente += amt
                }
                MType.KUEHLTURM -> {
                    val amt = min(liftRate() * ddt, m.output[Res.STROM.ordinal])
                    m.output[Res.STROM.ordinal] -= amt; globalStrom += amt
                }
                else -> {}
            }
        }

        // Haendler verkaufen aus dem globalen Bestand -> Geld: Level 1 Komponenten, ab
        // Level 2 Strom (das Endprodukt der Kernkraft-Kette, aus dem Kuehlturm).
        var soldValue = 0.0
        val price = componentPrice()
        val sellNuclear = companyLevel >= 2
        forEachMachine { m, _, _ ->
            if (m.type == MType.HAENDLER) {
                val pool = if (sellNuclear) globalStrom else globalKomponente
                val sold = min(HAENDLER_SELL * machineUpgradeMult(m) * ddt, pool)
                if (sold > 1e-9) {
                    if (sellNuclear) globalStrom -= sold else globalKomponente -= sold
                    money += sold * price
                    soldValue += sold * price
                    m.util = 1.0
                } else m.util = 0.0
            }
        }

        // Dividenden aus verkauften Unternehmen (passives Einkommen)
        if (dividends > 0.0) { val d = dividends * ddt; money += d; soldValue += d }

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

    /**
     * `onProgress` (0..1, optional) wird zwischendurch aufgerufen - fuer eine Ladeanzeige
     * waehrend eines langen Offline-Nachrechnens. Simuliert in 2-Sekunden-Schritten statt
     * 1-Sekunden-Schritten (step() erlaubt maximal dt=2.0) - halbiert die Anzahl der
     * teuren Voll-Gitter-Durchlaeufe (schnelleres Nachrechnen bei sehr langer Abwesenheit).
     */
    fun runOffline(elapsedSeconds: Int, onProgress: ((Float) -> Unit)? = null): OfflineReport {
        val cap = min(elapsedSeconds, OFFLINE_CAP)
        val b0 = globalBarren; val p0 = globalPlatten; val m0 = money
        val events = ArrayList<OfflineEvent>()
        val seenStarve = HashSet<Machine>()
        val seenDead = HashSet<Machine>()
        var t = 0
        val reportEvery = (cap / 80).coerceAtLeast(1)
        var sinceReport = 0
        while (t < cap) {
            val dtNow = min(2.0, (cap - t).toDouble())
            step(dtNow)
            forEachMachine { m, r, c ->
                if (m.condition <= 0.0 && !seenDead.contains(m)) {
                    seenDead.add(m)
                    if (events.size < 24) events.add(OfflineEvent(t, true, m.type, r, c))
                }
                if ((m.type == MType.OFEN || m.type == MType.PRESSE || m.type == MType.ASSEMBLER || m.type == MType.GENERATOR ||
                     m.type == MType.ZENTRIFUGE || m.type == MType.BLEIPRESSE || m.type == MType.BRENNSTABWERK ||
                     m.type == MType.REAKTORKERN || m.type == MType.KUEHLTURM) &&
                    m.starved && !seenStarve.contains(m)
                ) {
                    seenStarve.add(m)
                    if (events.size < 24) events.add(OfflineEvent(t, false, m.type, r, c))
                }
            }
            t += max(1, dtNow.toInt())
            sinceReport += max(1, dtNow.toInt())
            if (onProgress != null && sinceReport >= reportEvery) { onProgress(t.toFloat() / cap); sinceReport = 0 }
        }
        onProgress?.invoke(1f)
        return OfflineReport(elapsedSeconds, cap, globalBarren - b0, globalPlatten - p0, money - m0, events)
    }

    fun toJson(nowMillis: Long): String {
        val root = JSONObject()
        root.put("t", nowMillis)
        root.put("gb", globalBarren)
        root.put("gp", globalPlatten)
        root.put("gk", globalKomponente)
        root.put("gs", globalStrom)
        root.put("money", money)
        root.put("research", research)
        root.put("clvl", companyLevel)
        root.put("shares", shares)
        root.put("divi", dividends)
        if (hasPlatform()) {
            root.put("plat", JSONArray().put(platformR0).put(platformC0).put(platformR1).put(platformC1))
        }
        if (hasOilRig()) {
            root.put("orig", JSONArray().put(oilRigR0).put(oilRigC0).put(oilRigR1).put(oilRigC1))
        }
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
            if (m.lvl > 0) o.put("lvl", m.lvl)
            if (m.type == MType.LAGER && m.acceptRes.any { !it }) {
                val acc = JSONArray()
                for (k in 0 until rc) acc.put(m.acceptRes[k])
                o.put("acc", acc)
            }
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
        val expp = JSONArray()
        for (i in expandedPlatform.indices) if (expandedPlatform[i]) expp.put(i)
        root.put("expp", expp)
        val expc = JSONArray()
        for (i in expandedCanal.indices) if (expandedCanal[i]) expc.put(i)
        root.put("expc", expc)
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
        globalStrom = root.optDouble("gs", 0.0)
        money = root.optDouble("money", 0.0)
        research = root.optDouble("research", 0.0)
        companyLevel = root.optInt("clvl", 1).coerceAtLeast(1)
        shares = root.optDouble("shares", 0.0)
        dividends = root.optDouble("divi", 0.0)
        mapSeed = root.optLong("seed", 12345L)
        // Plattform-Koordinaten laden (braucht mapSeed, falls neu platziert werden muss).
        val plat = root.optJSONArray("plat")
        if (plat != null && plat.length() == 4) {
            platformR0 = plat.optInt(0, -1); platformC0 = plat.optInt(1, -1)
            platformR1 = plat.optInt(2, -1); platformC1 = plat.optInt(3, -1)
        } else if (companyLevel == 2) {
            placePlatform()   // Alt-Speicherstand ohne Plattform-Daten, aber schon Level 2
        } else {
            platformR0 = -1; platformC0 = -1; platformR1 = -1; platformC1 = -1
        }
        // Oel-Bohrinsel (dekorativ) laden bzw. bei Alt-Speicherstaenden nachtraeglich setzen.
        val orig = root.optJSONArray("orig")
        if (orig != null && orig.length() == 4) {
            oilRigR0 = orig.optInt(0, -1); oilRigC0 = orig.optInt(1, -1)
            oilRigR1 = orig.optInt(2, -1); oilRigC1 = orig.optInt(3, -1)
        } else if (companyLevel == 2 && hasPlatform()) {
            placeOilRig()
        } else {
            oilRigR0 = -1; oilRigC0 = -1; oilRigR1 = -1; oilRigC1 = -1
        }
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
            m.lvl = o.optInt("lvl", 0)
            // Typen, die inzwischen nicht mehr einzeln ausbaubar sind (Forschung,
            // Haendler, Drohne), verlieren ihre alte Stufe - sonst wirkte der Bonus
            // aus einem aelteren Spielstand unsichtbar weiter.
            if (!canUpgradeMachine(m.type)) m.lvl = 0
            val acc = o.optJSONArray("acc")
            if (acc != null) for (k in 0 until min(rc, acc.length())) m.acceptRes[k] = acc.optBoolean(k, true)
            val ia = o.optJSONArray("in"); val oa = o.optJSONArray("out")
            if (ia != null) for (k in 0 until min(rc, ia.length())) m.input[k] = ia.optDouble(k, 0.0)
            if (oa != null) for (k in 0 until min(rc, oa.length())) m.output[k] = oa.optDouble(k, 0.0)
            val (fw, fh) = footprint(m.type); m.w = fw; m.h = fh
            grid[r][c] = m
        }
        // Entfernte Maschinentypen (Verstaerker/Prospektor) aus Alt-Spielstaenden tilgen.
        // Der fertig gebaute Reaktor gehoert nur zum ersten (Bergbau-)Unternehmen - in
        // Alt-Spielstaenden ab Level 2 wird er entfernt (man baut ab dort sein eigenes
        // Kraftwerk selbst, mit Reaktorkern + Kuehlturm).
        for (r in 0 until n) for (c in 0 until n) {
            val ty = grid[r][c]?.type
            if (ty == MType.VERSTAERKER || ty == MType.PROSPEKTOR) grid[r][c] = null
            if (ty == MType.REAKTOR && companyLevel >= 2) grid[r][c] = null
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
        expandedPlatform.fill(false)
        val expp = root.optJSONArray("expp")
        if (expp != null) for (i in 0 until expp.length()) {
            val idx = expp.optInt(i, -1)
            if (idx in expandedPlatform.indices) expandedPlatform[idx] = true
        }
        expandedCanal.fill(false)
        val expc = root.optJSONArray("expc")
        if (expc != null) for (i in 0 until expc.length()) {
            val idx = expc.optInt(i, -1)
            if (idx in expandedCanal.indices) expandedCanal[idx] = true
        }

        // Reaktor-Kuehlschlauch laden (neues Format) bzw. Reaktor neu platzieren (altes Format).
        // Nur fuer Level 1 - ab Level 2 gibt es keinen fertig gebauten Reaktor mehr.
        reactorPipe.clear()
        if (reactorNew && companyLevel == 1) {
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
        // Reaktor sicherstellen (altes Format -> neuer 3x3-Reaktor + Schlauch); nur Level 1.
        if (companyLevel == 1 && !hasReactor()) placeReactor()
        return root.optLong("t", 0L)
    }

    private fun hasReactor(): Boolean = reactorAnchor() != null
    private fun reactorAnchor(): IntArray? {
        for (r in 0 until n) for (c in 0 until n) if (grid[r][c]?.type == MType.REAKTOR) return intArrayOf(r, c)
        return null
    }
}
