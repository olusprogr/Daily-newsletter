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

    fun forType(t: MType): List<Px> = when (t) {
        MType.BOHRER -> BOHRER
        MType.OFEN -> OFEN
        MType.PRESSE -> PRESSE
        MType.GENERATOR -> GENERATOR
        MType.LAGER -> LAGER
        MType.DROHNE -> DROHNE
        MType.REAKTOR -> REAKTOR
    }
}
