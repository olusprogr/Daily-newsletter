"""
Tiefen-Bohrer (Blei) v2: deutlich detaillierter, im selben Stil/Qualitaet
wie der normale Bohrer (gen_drill.py) - A-Rahmen-Tripod, Motorgehaeuse mit
Warnstreifen, Getriebe-Kragen, spiralfoermige Foerderschnecke - aber
schwerer/hoeher gebaut und blaugrau (Blei) statt Stahl gefaerbt, damit er
sich klar vom normalen Bohrer und vom Uranbohrer (gleiches Sprite) abhebt.
"""
from PIL import Image, ImageDraw
S = 64
OUT = "/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"
A = 255
OC = (24, 24, 30, A)
MDK = (64, 68, 78, A); MMD = (104, 110, 122, A); MLT = (150, 158, 172, A); MHI = (200, 206, 218, A)
PB = (120, 126, 146, A); PBD = (80, 84, 100, A); PBL = (168, 174, 194, A)
YEL = (240, 200, 70, A); OCN = (30, 30, 34, A)
DIRT = (96, 66, 42, A); DIRTD = (70, 48, 30, A)

im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(im)

def rect(x, y, w, h, c):
    x, y, w, h = int(round(x)), int(round(y)), int(round(w)), int(round(h))
    for dy in range(h):
        for dx in range(w):
            if 0 <= x + dx < S and 0 <= y + dy < S: im.putpixel((x + dx, y + dy), c)

def panel(x, y, w, h, base, light, dark, out=OC):
    rect(x - 1, y - 1, w + 2, h + 2, out); rect(x, y, w, h, base)
    rect(x, y, w, 2, light); rect(x, y, 2, h, light); rect(x, y + h - 2, w, 2, dark); rect(x + w - 2, y, 2, h, dark)

def bolts(x, y, w, h, c=MHI):
    for (bx, by) in [(x + 2, y + 2), (x + w - 3, y + 2), (x + 2, y + h - 3), (x + w - 3, y + h - 3)]:
        im.putpixel((bx, by), c)

# Boden, tiefes Bohrloch
rect(8, 53, 48, 9, DIRTD); rect(8, 53, 48, 2, DIRT)
for bx in range(10, 54, 6): rect(bx, 56, 2, 2, DIRT)
rect(27, 55, 10, 6, (30, 22, 16, A))   # dunkles Bohrloch (tiefer als beim normalen Bohrer)

# A-Rahmen (steiler/hoeher als der normale Bohrer -> "tiefer" bauend)
d.line([(18, 12), (9, 54)], fill=OC, width=4); d.line([(18, 12), (9, 54)], fill=PBD, width=2)
d.line([(46, 12), (55, 54)], fill=OC, width=4); d.line([(46, 12), (55, 54)], fill=PBD, width=2)
rect(6, 53, 8, 4, OC); rect(50, 53, 8, 4, OC)
# Quer-/Kreuzstrebe (extra Verstrebung -> wirkt schwerer/stabiler)
d.line([(18, 34), (46, 34)], fill=OC, width=3); d.line([(18, 34), (46, 34)], fill=PBD, width=1)

# Motorgehaeuse oben (etwas hoeher als beim normalen Bohrer)
panel(15, 2, 34, 16, PB, PBL, PBD)
for i in range(17, 47, 6):
    rect(i, 5, 3, 4, YEL); rect(i + 3, 5, 3, 4, OCN)
rect(18, 11, 28, 1, PBD); rect(18, 14, 28, 1, PBD)
bolts(15, 2, 34, 16)
# zweite Getriebestufe (macht ihn "schwerer" als den normalen Bohrer)
panel(22, 18, 20, 7, MDK, MMD, OCN)
rect(30, 18, 4, 3, MHI)
panel(26, 25, 12, 5, PBD, PB, OCN)

# Antriebswelle
rect(30, 30, 4, 5, MDK); rect(30, 30, 1, 5, MLT)

# Foerderschnecke (Blei-blaugrau statt Stahl), etwas laenger/schmaler -> "tiefer"
top, bot = 35, 57
for i, yy in enumerate(range(top, bot)):
    t = i / (bot - top)
    hw = 7.5 * (1 - t) + 1.2
    cx = 32
    rect(cx - hw, yy, hw * 2, 1, PB)
    rect(cx - hw, yy, 1, 1, PBL); rect(cx + hw - 1, yy, 1, 1, PBD)
    ph = (i * 2) % max(1, int(hw * 1.6))
    sx = cx - hw + ph
    rect(sx, yy, max(1, int(hw * 0.5)), 1, PBL)
    sx2 = cx - hw + (ph + int(hw)) % max(1, int(hw * 1.6))
    im.putpixel((min(S - 1, max(0, int(sx2))), yy), PBD)
    if i % 3 == 0:
        rect(cx - hw, yy, 2, 1, MHI); rect(cx + hw - 2, yy, 2, 1, MDK)
d.polygon([(29, 57), (35, 57), (32, 63)], fill=PB)
d.polygon([(30, 58), (34, 58), (32, 62)], fill=PBD)
rect(31, 57, 2, 1, PBL)

im.save(OUT + "/mach_bleibohrer.png"); print("bleibohrer v2 ok")
