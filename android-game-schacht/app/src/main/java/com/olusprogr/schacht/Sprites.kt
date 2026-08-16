package com.olusprogr.schacht

/**
 * Pixel-Art-Sprites im 32x32-Raster, als Liste farbiger Rechtecke (wie die
 * SVG-<rect>-Assets). Werden in GameView direkt auf das Canvas gezeichnet und
 * auf die Zellgroesse skaliert (Antialiasing aus = harte Pixelkanten).
 */
class Px(val x: Int, val y: Int, val w: Int, val h: Int, val c: Int)

// Palette (ARGB)
private val OUT = 0xFF1A1A1A.toInt()
private val DK2 = 0xFF2A2A2A.toInt()
private val MDK = 0xFF444444.toInt()
private val MMD = 0xFF6A6A6A.toInt()
private val MM2 = 0xFF8A8A8A.toInt()
private val MLT = 0xFF9A9A9A.toInt()
private val MHI = 0xFFB0B0B0.toInt()
private val RUSTB = 0xFF3D251E.toInt()
private val RUSTL = 0xFF5A3A2E.toInt()
private val ORG = 0xFFFF6600.toInt()
private val YEL = 0xFFFFCC00.toInt()
private val CYA = 0xFF00FFFF.toInt()
private val CYL = 0xFFAAFFFF.toInt()

// Verschleiss-Overlay-Farben mit eingebackenem Alpha
private val RA = 0xD98B4513.toInt()   // Rost ~0.85
private val RC = 0xE63D251E.toInt()   // Rostkern ~0.9
private val SM1 = 0x8C6A6A6A.toInt()  // Rauch ~0.55
private val SM2 = 0x669A9A9A.toInt()  // Rauch hell ~0.4

// Icon-Farben
private val STEEL = 0xFFBCC2CC.toInt()
private val BLUE = 0xFF68A8D8.toInt()
private val BLUEL = 0xFFA9D6F2.toInt()

object Sprites {

    val BOHRER = listOf(
        Px(6, 4, 20, 10, OUT),
        Px(7, 5, 18, 8, MMD),
        Px(7, 5, 18, 1, MLT),
        Px(7, 12, 18, 1, MDK),
        Px(9, 8, 14, 2, ORG),
        Px(9, 6, 1, 1, YEL), Px(22, 6, 1, 1, YEL),
        Px(11, 14, 10, 4, OUT),
        Px(12, 14, 8, 3, MDK),
        Px(12, 18, 8, 1, MLT), Px(12, 19, 8, 1, MM2),
        Px(13, 20, 6, 1, MLT), Px(13, 21, 6, 1, MM2),
        Px(14, 22, 4, 1, MLT), Px(14, 23, 4, 1, MM2),
        Px(15, 24, 2, 1, MLT), Px(15, 25, 2, 1, MM2),
        Px(15, 26, 2, 1, MLT), Px(15, 27, 1, 1, MLT), Px(16, 27, 1, 1, MM2),
        Px(13, 19, 1, 1, RUSTB), Px(16, 20, 1, 1, RUSTB), Px(14, 21, 1, 1, RUSTB),
        Px(16, 22, 1, 1, RUSTB), Px(15, 23, 1, 1, RUSTB)
    )

    val OFEN = listOf(
        Px(20, 1, 5, 6, OUT),
        Px(21, 2, 3, 5, RUSTB),
        Px(21, 1, 3, 1, ORG),
        Px(4, 6, 24, 23, OUT),
        Px(5, 7, 22, 21, MDK),
        Px(5, 7, 22, 1, MMD),
        Px(5, 27, 22, 1, DK2),
        Px(8, 12, 16, 13, OUT),
        Px(9, 13, 14, 11, RUSTB),
        Px(10, 15, 12, 8, ORG),
        Px(12, 17, 8, 4, YEL),
        Px(12, 14, 2, 1, ORG), Px(16, 13, 2, 2, ORG), Px(19, 14, 2, 1, ORG),
        Px(6, 8, 1, 1, MM2), Px(25, 8, 1, 1, MM2), Px(6, 25, 1, 1, MM2), Px(25, 25, 1, 1, MM2),
        Px(6, 29, 4, 2, OUT), Px(22, 29, 4, 2, OUT)
    )

    val PRESSE = listOf(
        Px(5, 4, 3, 24, OUT), Px(24, 4, 3, 24, OUT),
        Px(5, 4, 1, 24, MMD), Px(24, 4, 1, 24, MMD),
        Px(4, 3, 24, 5, OUT),
        Px(5, 4, 22, 3, MMD),
        Px(5, 4, 22, 1, MLT),
        Px(15, 8, 2, 3, MM2),
        Px(10, 11, 12, 7, OUT),
        Px(11, 12, 10, 5, MMD),
        Px(11, 12, 10, 1, YEL),
        Px(12, 13, 1, 1, YEL), Px(15, 13, 1, 1, YEL), Px(18, 13, 1, 1, YEL),
        Px(13, 18, 6, 1, MLT),
        Px(12, 21, 8, 2, CYA), Px(12, 21, 8, 1, CYL),
        Px(6, 23, 20, 6, OUT),
        Px(7, 24, 18, 4, MDK),
        Px(7, 24, 18, 1, MMD),
        Px(6, 29, 4, 2, OUT), Px(22, 29, 4, 2, OUT)
    )

    val DROHNE = listOf(
        Px(15, 2, 2, 6, OUT), Px(15, 2, 1, 6, MMD), Px(14, 1, 4, 1, YEL),
        Px(6, 11, 6, 1, OUT), Px(20, 11, 6, 1, OUT),
        Px(6, 10, 2, 1, MM2), Px(24, 10, 2, 1, MM2),
        Px(10, 10, 12, 10, OUT),
        Px(11, 11, 10, 8, MDK),
        Px(11, 11, 10, 1, MMD),
        Px(13, 13, 6, 3, CYA), Px(14, 14, 4, 1, CYL),
        Px(12, 17, 2, 1, YEL), Px(18, 17, 2, 1, YEL),
        Px(12, 20, 1, 2, MMD), Px(19, 20, 1, 2, MMD),
        Px(4, 22, 24, 7, OUT),
        Px(5, 23, 22, 5, RUSTB),
        Px(5, 23, 22, 1, RUSTL),
        Px(8, 22, 4, 1, CYA), Px(20, 22, 4, 1, CYA),
        Px(13, 25, 6, 1, YEL)
    )

    // Generator: dunkles Gehaeuse mit gelbem Blitz und Strom-Terminals.
    val GENERATOR = listOf(
        Px(6, 8, 20, 20, OUT),
        Px(7, 9, 18, 18, MDK),
        Px(7, 9, 18, 1, MMD),
        Px(7, 26, 18, 1, DK2),
        Px(9, 12, 14, 1, DK2), Px(9, 15, 14, 1, DK2), Px(9, 24, 14, 1, DK2),
        Px(16, 11, 2, 1, YEL),
        Px(15, 12, 2, 1, YEL),
        Px(14, 13, 3, 1, YEL),
        Px(13, 14, 5, 1, YEL),
        Px(16, 15, 2, 1, YEL),
        Px(15, 16, 2, 1, YEL),
        Px(14, 17, 2, 1, YEL),
        Px(17, 14, 1, 1, ORG), Px(14, 18, 1, 1, ORG),
        Px(8, 11, 1, 1, MM2), Px(23, 11, 1, 1, MM2), Px(8, 25, 1, 1, MM2), Px(23, 25, 1, 1, MM2)
    )

    // Lager: Kiste mit Metallrahmen und diagonalem Kreuzverband.
    val LAGER = listOf(
        Px(5, 7, 22, 21, OUT),
        Px(6, 8, 20, 19, RUSTB),
        Px(6, 8, 20, 2, MMD),
        Px(6, 25, 20, 2, MMD),
        Px(6, 8, 2, 19, MMD),
        Px(24, 8, 2, 19, MMD),
        Px(8, 10, 16, 1, RUSTL),
        Px(8, 11, 2, 2, MM2), Px(10, 13, 2, 2, MM2), Px(12, 15, 2, 2, MM2),
        Px(14, 17, 2, 2, MM2), Px(16, 19, 2, 2, MM2), Px(18, 21, 2, 2, MM2), Px(20, 23, 2, 2, MM2),
        Px(22, 11, 2, 2, MM2), Px(20, 13, 2, 2, MM2), Px(18, 15, 2, 2, MM2),
        Px(14, 19, 2, 2, MM2), Px(12, 21, 2, 2, MM2), Px(10, 23, 2, 2, MM2)
    )

    // Reaktor: Gehaeuse mit gluehendem Kern (konzentrische Ringe) und Kuehlrippen.
    val REAKTOR = listOf(
        Px(5, 5, 22, 23, OUT),
        Px(6, 6, 20, 21, DK2),
        Px(6, 6, 20, 1, MMD),
        Px(3, 10, 2, 1, MMD), Px(3, 13, 2, 1, MMD), Px(3, 16, 2, 1, MMD), Px(3, 19, 2, 1, MMD),
        Px(27, 10, 2, 1, MMD), Px(27, 13, 2, 1, MMD), Px(27, 16, 2, 1, MMD), Px(27, 19, 2, 1, MMD),
        Px(11, 11, 10, 10, CYA),
        Px(12, 12, 8, 8, OUT),
        Px(13, 13, 6, 6, CYA),
        Px(14, 14, 4, 4, OUT),
        Px(15, 15, 2, 2, YEL),
        Px(15, 10, 2, 1, YEL), Px(15, 21, 2, 1, YEL), Px(10, 15, 1, 2, YEL), Px(21, 15, 1, 2, YEL),
        Px(11, 11, 1, 1, CYL),
        Px(7, 7, 1, 1, MM2), Px(24, 7, 1, 1, MM2), Px(7, 25, 1, 1, MM2), Px(24, 25, 1, 1, MM2)
    )

    // Assembler: Gehaeuse mit Roboterarm und cyan Komponente im Zentrum.
    val ASSEMBLER = listOf(
        Px(13, 4, 6, 1, OUT), Px(15, 4, 2, 5, MM2),
        Px(12, 8, 2, 1, MM2), Px(18, 8, 2, 1, MM2),
        Px(6, 9, 20, 19, OUT),
        Px(7, 10, 18, 17, MDK),
        Px(7, 10, 18, 1, MMD),
        Px(7, 26, 18, 1, DK2),
        Px(11, 13, 10, 8, OUT),
        Px(12, 14, 8, 6, RUSTB),
        Px(13, 15, 6, 1, CYA), Px(13, 15, 1, 4, CYA), Px(18, 16, 1, 3, CYA),
        Px(15, 16, 2, 2, YEL),
        Px(11, 16, 1, 1, YEL), Px(20, 16, 1, 1, YEL), Px(11, 18, 1, 1, YEL), Px(20, 18, 1, 1, YEL),
        Px(9, 11, 1, 1, CYA), Px(22, 11, 1, 1, CYA)
    )

    // Verstaerker: Spule/Antenne mit cyan Kern und gelbem Aufwaerts-Pfeil.
    val VERSTAERKER = listOf(
        Px(7, 22, 18, 5, OUT),
        Px(8, 23, 16, 3, MDK),
        Px(8, 23, 16, 1, MMD),
        Px(13, 10, 6, 12, OUT),
        Px(14, 11, 4, 11, MM2),
        Px(14, 11, 1, 11, MLT),
        Px(14, 13, 4, 4, CYA),
        Px(15, 14, 2, 2, CYL),
        Px(15, 4, 2, 1, YEL),
        Px(14, 5, 1, 1, YEL), Px(17, 5, 1, 1, YEL),
        Px(13, 6, 1, 1, YEL), Px(18, 6, 1, 1, YEL),
        Px(15, 7, 2, 1, YEL), Px(15, 8, 2, 1, YEL), Px(15, 9, 2, 1, YEL),
        Px(10, 15, 1, 1, CYA), Px(21, 15, 1, 1, CYA)
    )

    // Haendler: Marktstand mit Markise, Theke und Muenz-Symbol.
    val HAENDLER = listOf(
        Px(6, 6, 20, 1, OUT),
        Px(6, 7, 20, 3, YEL),
        Px(8, 7, 2, 3, ORG), Px(12, 7, 2, 3, ORG), Px(16, 7, 2, 3, ORG), Px(20, 7, 2, 3, ORG),
        Px(6, 10, 20, 1, OUT),
        Px(6, 10, 1, 15, OUT), Px(25, 10, 1, 15, OUT),
        Px(7, 10, 1, 15, MMD), Px(24, 10, 1, 15, MMD),
        Px(14, 12, 4, 5, OUT),
        Px(15, 12, 3, 4, YEL),
        Px(16, 13, 1, 2, ORG),
        Px(15, 12, 1, 1, MHI),
        Px(7, 20, 18, 6, OUT),
        Px(8, 21, 16, 4, RUSTB),
        Px(8, 21, 16, 1, RUSTL),
        Px(10, 19, 3, 1, YEL), Px(11, 18, 1, 1, YEL),
        Px(18, 19, 3, 1, YEL), Px(19, 18, 1, 1, YEL)
    )

    // Prospektor: Scanner-Stativ mit Radarschuessel und cyan Sende-Punkt.
    val PROSPEKTOR = listOf(
        Px(9, 24, 14, 4, OUT),
        Px(10, 25, 12, 2, MDK),
        Px(10, 25, 12, 1, MMD),
        Px(15, 12, 2, 12, OUT), Px(15, 12, 1, 12, MMD),
        Px(10, 6, 12, 3, OUT),
        Px(11, 7, 10, 2, MM2), Px(11, 7, 10, 1, MLT),
        Px(15, 4, 2, 2, CYA),
        Px(9, 9, 1, 1, CYL), Px(22, 9, 1, 1, CYL),
        Px(9, 27, 3, 2, OUT), Px(20, 27, 3, 2, OUT)
    )

    // Rost-Overlay fuer Zustand < 50%.
    val RUST = listOf(
        Px(3, 4, 3, 1, RA), Px(2, 5, 2, 2, RA), Px(5, 6, 1, 1, RA),
        Px(26, 3, 3, 1, RA), Px(27, 4, 2, 2, RA),
        Px(10, 8, 1, 1, RA), Px(18, 9, 1, 1, RA),
        Px(6, 20, 3, 2, RA), Px(8, 19, 1, 1, RA),
        Px(23, 22, 3, 2, RA), Px(25, 24, 2, 1, RA),
        Px(14, 27, 4, 1, RA), Px(12, 28, 3, 2, RA),
        Px(3, 5, 1, 1, RC), Px(28, 4, 1, 1, RC), Px(7, 21, 1, 1, RC),
        Px(24, 23, 1, 1, RC), Px(13, 28, 1, 1, RC)
    )

    // Funken/Rauch-Overlay fuer Zustand < 20%.
    val SPARK = listOf(
        Px(9, 6, 3, 2, SM1), Px(8, 4, 2, 2, SM1), Px(10, 2, 2, 2, SM1), Px(7, 1, 1, 1, SM1),
        Px(21, 7, 3, 2, SM1), Px(22, 5, 2, 2, SM1), Px(21, 3, 2, 2, SM1),
        Px(9, 4, 1, 1, SM2), Px(22, 5, 1, 1, SM2),
        Px(15, 14, 1, 1, YEL), Px(18, 12, 1, 1, YEL), Px(12, 16, 1, 1, YEL),
        Px(20, 17, 1, 1, YEL), Px(16, 19, 1, 1, YEL),
        Px(17, 13, 1, 1, ORG), Px(13, 15, 1, 1, ORG), Px(19, 16, 1, 1, ORG),
        Px(14, 18, 1, 1, ORG), Px(21, 14, 1, 1, ORG)
    )

    /** Kleines Arbeits-Overlay (4 Frames), das nur laeuft wenn die Maschine arbeitet. */
    fun anim(t: MType, frame: Int): List<Px> = when (t) {
        MType.BOHRER -> when (frame) {
            0 -> listOf(Px(13, 20, 1, 1, MHI), Px(16, 27, 1, 1, RUSTL))
            1 -> listOf(Px(17, 21, 1, 1, MHI), Px(15, 28, 1, 1, RUSTB))
            2 -> listOf(Px(13, 22, 1, 1, MHI), Px(16, 27, 1, 1, RUSTL))
            else -> listOf(Px(17, 23, 1, 1, MHI), Px(15, 28, 1, 1, RUSTB))
        }
        MType.OFEN -> when (frame) {
            0 -> listOf(Px(13, 11, 1, 1, ORG), Px(16, 10, 1, 2, YEL), Px(19, 11, 1, 1, ORG))
            1 -> listOf(Px(12, 11, 1, 1, YEL), Px(16, 11, 1, 1, ORG), Px(20, 10, 1, 2, ORG))
            2 -> listOf(Px(14, 10, 1, 2, ORG), Px(17, 11, 1, 1, YEL), Px(19, 10, 1, 1, ORG))
            else -> listOf(Px(13, 10, 1, 1, YEL), Px(15, 11, 1, 1, ORG), Px(18, 10, 1, 2, YEL))
        }
        MType.PRESSE -> when (frame) {
            0 -> listOf(Px(15, 19, 2, 1, MHI))
            2 -> listOf(Px(12, 21, 8, 1, CYL))
            else -> emptyList()
        }
        MType.ASSEMBLER -> when (frame) {
            0 -> listOf(Px(15, 16, 2, 2, CYA))
            1 -> listOf(Px(15, 16, 2, 2, YEL), Px(15, 4, 2, 1, MHI))
            2 -> listOf(Px(15, 16, 2, 2, CYL))
            else -> listOf(Px(15, 16, 2, 2, ORG))
        }
        MType.GENERATOR -> when (frame) {
            0 -> listOf(Px(13, 14, 5, 1, MHI))
            1 -> listOf(Px(16, 11, 2, 1, MHI), Px(9, 20, 1, 1, YEL))
            2 -> listOf(Px(14, 17, 2, 1, MHI))
            else -> listOf(Px(23, 13, 1, 1, YEL))
        }
        MType.DROHNE -> when (frame) {
            0 -> listOf(Px(12, 17, 2, 1, CYL))
            1 -> listOf(Px(18, 17, 2, 1, CYL), Px(14, 14, 4, 1, CYL))
            2 -> listOf(Px(12, 17, 2, 1, YEL))
            else -> listOf(Px(18, 17, 2, 1, YEL))
        }
        MType.VERSTAERKER -> when (frame) {
            0 -> listOf(Px(15, 4, 2, 1, MHI), Px(15, 14, 2, 2, CYL))
            1 -> listOf(Px(13, 6, 1, 1, MHI), Px(18, 6, 1, 1, MHI))
            2 -> listOf(Px(15, 7, 2, 3, MHI))
            else -> listOf(Px(14, 13, 4, 4, CYL))
        }
        MType.LAGER -> when (frame) {
            0 -> listOf(Px(11, 9, 1, 1, YEL))
            1 -> listOf(Px(20, 9, 1, 1, YEL))
            2 -> listOf(Px(15, 9, 2, 1, CYA))
            else -> emptyList()
        }
        MType.REAKTOR -> when (frame) {
            0 -> listOf(Px(15, 15, 2, 2, CYL))
            1 -> listOf(Px(15, 15, 2, 2, YEL))
            2 -> listOf(Px(14, 14, 4, 4, CYA))
            else -> listOf(Px(15, 15, 2, 2, CYL))
        }
        MType.HAENDLER -> when (frame) {
            0 -> listOf(Px(15, 12, 1, 1, MHI), Px(10, 18, 1, 1, MHI))
            1 -> listOf(Px(17, 13, 1, 1, MHI), Px(19, 18, 1, 1, MHI))
            2 -> listOf(Px(15, 14, 1, 1, CYL))
            else -> listOf(Px(16, 12, 1, 1, MHI))
        }
        MType.PROSPEKTOR -> when (frame) {
            0 -> listOf(Px(15, 4, 2, 2, CYL))
            1 -> listOf(Px(13, 3, 1, 1, CYA), Px(18, 3, 1, 1, CYA))
            2 -> listOf(Px(11, 7, 10, 1, CYL))
            else -> listOf(Px(15, 4, 2, 2, CYA))
        }
        MType.WINDRAD -> emptyList()
        MType.SOLAR -> emptyList()
        MType.FORSCHUNG -> emptyList()
        MType.BLEIBOHRER -> emptyList()
        MType.WASSERPUMPE -> emptyList()
        MType.ZENTRIFUGE -> emptyList()
        MType.BLEIPRESSE -> emptyList()
        MType.BRENNSTABWERK -> emptyList()
    }

    // --- Ressourcen-Icons im 12x12-Raster ---
    val ICON_GELD = listOf(
        Px(3, 1, 6, 1, OUT), Px(2, 2, 8, 1, OUT),
        Px(1, 3, 1, 6, OUT), Px(10, 3, 1, 6, OUT),
        Px(2, 9, 8, 1, OUT), Px(3, 10, 6, 1, OUT),
        Px(2, 3, 8, 6, YEL), Px(3, 2, 6, 1, YEL), Px(3, 9, 6, 1, YEL),
        Px(3, 3, 2, 2, MHI),
        Px(5, 3, 2, 5, ORG), Px(4, 4, 4, 1, ORG), Px(4, 6, 4, 1, ORG)
    )
    val ICON_BARREN = listOf(
        Px(2, 4, 8, 1, OUT), Px(1, 5, 10, 4, OUT),
        Px(3, 4, 5, 1, STEEL),
        Px(2, 5, 8, 3, MM2), Px(2, 5, 8, 1, MLT), Px(2, 7, 8, 1, MDK)
    )
    val ICON_PLATTE = listOf(
        Px(1, 4, 10, 1, OUT), Px(1, 5, 10, 4, OUT),
        Px(2, 4, 8, 1, STEEL), Px(2, 5, 8, 3, BLUE), Px(2, 5, 8, 1, BLUEL)
    )
    val ICON_KOMP = listOf(
        Px(4, 1, 1, 1, YEL), Px(7, 1, 1, 1, YEL), Px(4, 10, 1, 1, YEL), Px(7, 10, 1, 1, YEL),
        Px(1, 4, 1, 1, YEL), Px(1, 7, 1, 1, YEL), Px(10, 4, 1, 1, YEL), Px(10, 7, 1, 1, YEL),
        Px(2, 2, 8, 8, OUT), Px(3, 3, 6, 6, RUSTB),
        Px(4, 4, 4, 1, CYA), Px(4, 4, 1, 4, CYA), Px(5, 6, 2, 2, YEL)
    )
    val ICON_STROM = listOf(
        Px(6, 1, 2, 1, YEL), Px(5, 2, 2, 1, YEL), Px(4, 3, 3, 1, YEL),
        Px(3, 4, 5, 1, YEL), Px(5, 5, 2, 1, YEL), Px(4, 6, 2, 1, YEL), Px(3, 7, 2, 1, YEL),
        Px(7, 2, 1, 1, ORG), Px(4, 7, 1, 1, ORG)
    )
    // Roherz: grober Steinbrocken mit ein paar Erz-Adern.
    val ICON_ROHERZ = listOf(
        Px(4, 2, 4, 1, OUT), Px(3, 3, 6, 1, OUT),
        Px(2, 4, 1, 4, OUT), Px(9, 4, 1, 4, OUT),
        Px(3, 8, 6, 1, OUT),
        Px(3, 4, 6, 4, RUSTL),
        Px(3, 6, 6, 2, RUSTB),
        Px(4, 4, 2, 1, ORG), Px(6, 5, 1, 1, YEL), Px(5, 6, 1, 1, YEL)
    )

    fun iconForRes(res: Int): List<Px> = when (res) {
        Res.ROHERZ.ordinal -> ICON_ROHERZ
        Res.BARREN.ordinal -> ICON_BARREN
        Res.PLATTE.ordinal -> ICON_PLATTE
        else -> ICON_KOMP
    }

    fun forType(t: MType): List<Px> = when (t) {
        MType.BOHRER -> BOHRER
        MType.OFEN -> OFEN
        MType.PRESSE -> PRESSE
        MType.ASSEMBLER -> ASSEMBLER
        MType.GENERATOR -> GENERATOR
        MType.LAGER -> LAGER
        MType.DROHNE -> DROHNE
        MType.VERSTAERKER -> VERSTAERKER
        MType.REAKTOR -> REAKTOR
        MType.HAENDLER -> HAENDLER
        MType.PROSPEKTOR -> PROSPEKTOR
        MType.WINDRAD -> GENERATOR
        MType.SOLAR -> GENERATOR
        MType.FORSCHUNG -> REAKTOR
        MType.BLEIBOHRER -> BOHRER
        MType.WASSERPUMPE -> GENERATOR
        MType.ZENTRIFUGE -> OFEN
        MType.BLEIPRESSE -> PRESSE
        MType.BRENNSTABWERK -> ASSEMBLER
    }
}
