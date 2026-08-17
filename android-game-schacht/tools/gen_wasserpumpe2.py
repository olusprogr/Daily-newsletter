"""
Wasserpumpe v2: deutlich substantiellere Pumpstation statt der duennen
Box mit schwebendem Tropfen - Betonsockel mit Bolzen, zylindrisches
Pumpengehaeuse mit Bullauge (Wasser darin sichtbar), Steigrohr mit
Ventilrad, Ansaug- und Auslassrohr mit flieszendem Wasser (Wellen-Muster),
im selben Detailgrad wie der normale Bohrer.
"""
from PIL import Image, ImageDraw
S = 64
OUT = "/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"
A = 255
OC = (24, 24, 30, A)
MDK = (64, 68, 78, A); MMD = (104, 110, 122, A); MLT = (150, 158, 172, A); MHI = (200, 206, 218, A)
BLU = (70, 150, 214, A); BLUL = (150, 210, 246, A); BLUD = (40, 96, 150, A)
YEL = (240, 200, 70, A); OCN = (30, 30, 34, A)
CONC = (86, 88, 96, A); CONCD = (60, 62, 70, A)

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

def disc(cx, cy, r, c):
    for y in range(int(cy - r), int(cy + r + 1)):
        for x in range(int(cx - r), int(cx + r + 1)):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r and 0 <= x < S and 0 <= y < S: im.putpixel((x, y), c)

# Beton-Fundament
rect(8, 52, 48, 10, CONCD); rect(8, 52, 48, 2, CONC)
for i in range(10, 54, 8): rect(i, 58, 3, 3, MHI)

# Pumpengehaeuse (zylindrisch, mit Warnstreifen-Band)
panel(16, 26, 32, 26, MMD, MLT, MDK)
for i in range(18, 46, 6):
    rect(i, 46, 3, 4, YEL); rect(i + 3, 46, 3, 4, OCN)
bolts(16, 26, 32, 26)
# Bullauge mit Wasser drin
disc(32, 36, 10, MDK); disc(32, 36, 8, BLUD); disc(32, 36, 5, BLU); im.putpixel((29, 33), BLUL)
rect(22, 36, 20, 1, MLT)

# Steigrohr + Kopfstueck mit Ventilrad
rect(29, 8, 6, 20, MMD); rect(29, 8, 2, 20, MLT)
panel(23, 3, 18, 7, MDK, MHI, MMD)
disc(32, 6, 5, MDK); im.putpixel((32, 2), MHI); im.putpixel((28, 6), MHI); im.putpixel((36, 6), MHI)
disc(32, 6, 2, MLT)

# Ansaug (links, Richtung Kueste) + Auslass (rechts) mit Wasserwellen
rect(4, 40, 12, 6, MMD); rect(4, 40, 12, 2, MLT)
for i, yy in enumerate(range(41, 45, 2)): rect(6 + (i % 2) * 2, yy, 6, 1, BLU)
rect(48, 40, 12, 6, MMD); rect(48, 40, 12, 2, MLT)
for i, yy in enumerate(range(41, 45, 2)): rect(50 + (i % 2) * 2, yy, 6, 1, BLU)
# Austretendes Wasser (kleine Tropfen/Wellen unten rechts)
rect(50, 47, 8, 1, BLUL); rect(52, 49, 6, 1, BLU); rect(54, 51, 4, 1, BLUL)

im.save(OUT + "/mach_wasserpumpe.png"); print("wasserpumpe v2 ok")
