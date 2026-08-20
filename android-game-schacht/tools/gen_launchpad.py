"""
Raumhafen (3x3, 192x192): rein dekorativer Spoiler fuers High-Tech-Level -
dieselbe Rolle, die die Oelinsel fuer die Petrochemie hat. Startrampe mit
Servicemast, Rakete, Abgasgraben und Satellitenschuessel; Baustil bleibt der
gleiche (harte Outlines, Warnstreifen, Nieten), Farbwelt aber kuehl/technisch
(Weiss, Stahlblau, Cyan) statt Messing/Oel - so signalisiert die Kulisse
optisch schon "naechste Stufe".
"""
from PIL import Image, ImageDraw
import math
S = 192
OUT = "/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"
A = 255
OC   = (22, 22, 28, A)
MDK  = (58, 62, 72, A); MMD = (100, 106, 120, A); MLT = (152, 160, 176, A); MHI = (206, 212, 226, A)
WHT  = (238, 242, 248, A); WHTD = (196, 202, 214, A)
CY   = (96, 216, 236, A); CYL = (188, 246, 252, A); CYD = (44, 132, 156, A)
YEL  = (244, 206, 74, A); OCN = (28, 28, 32, A)
RED  = (232, 76, 64, A); REDL = (255, 152, 132, A)
FLM  = (250, 168, 60, A); FLML = (255, 226, 150, A)
CONC = (120, 122, 130, A); CONCD = (86, 88, 96, A)

im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(im)

def rect(x, y, w, h, c):
    x, y, w, h = int(round(x)), int(round(y)), int(round(w)), int(round(h))
    for dy in range(h):
        for dx in range(w):
            if 0 <= x+dx < S and 0 <= y+dy < S: im.putpixel((x+dx, y+dy), c)
def panel(x, y, w, h, base, light, dark, out=OC):
    rect(x-1, y-1, w+2, h+2, out); rect(x, y, w, h, base)
    rect(x, y, w, 2, light); rect(x, y, 2, h, light)
    rect(x, y+h-2, w, 2, dark); rect(x+w-2, y, 2, h, dark)
def disc(cx, cy, r, c):
    for y in range(int(cy-r), int(cy+r+1)):
        for x in range(int(cx-r), int(cx+r+1)):
            if (x-cx)**2 + (y-cy)**2 <= r*r and 0 <= x < S and 0 <= y < S: im.putpixel((x, y), c)
def ring(cx, cy, r, c):
    for a in range(0, 360, 2):
        x = int(cx + r*math.cos(math.radians(a))); y = int(cy + r*math.sin(math.radians(a)))
        if 0 <= x < S and 0 <= y < S: im.putpixel((x, y), c)

# --- Betonrampe mit Abgasgraben ---
rect(16, 150, 160, 26, CONCD); rect(16, 150, 160, 3, CONC)
for gx in range(22, 170, 12): rect(gx, 168, 6, 4, MDK)      # Gitterrost
rect(70, 156, 52, 14, OCN); rect(70, 156, 52, 2, MDK)       # Flammgraben
for i, gx in enumerate(range(18, 174, 10)):                  # Warnstreifen-Kante
    rect(gx, 146, 5, 4, YEL if i % 2 == 0 else OCN)

# --- Servicemast (Fachwerk) links neben der Rakete ---
mx = 54
rect(mx-1, 40, 12, 110, OC); rect(mx, 40, 10, 110, MMD); rect(mx, 40, 3, 110, MLT)
for ly in range(46, 148, 10):
    rect(mx-6, ly, 6, 2, MLT)                                # Auslegerarme zur Rakete
    d.line([(mx, ly), (mx+10, ly+10)], fill=MDK, width=1)
rect(mx-2, 34, 14, 7, MDK); rect(mx-2, 34, 14, 2, MLT)      # Kopfplattform
disc(mx+5, 30, 3, RED); im.putpixel((mx+4, 28), REDL)        # Warnlicht

# --- Rakete (mittig) ---
cx = 100
body_top, body_bot = 46, 150
rect(cx-15, body_top-1, 30, body_bot-body_top+1, OC)
rect(cx-14, body_top, 28, body_bot-body_top, WHT)
rect(cx-14, body_top, 8, body_bot-body_top, MHI)            # Licht links
rect(cx+8, body_top, 6, body_bot-body_top, WHTD)            # Schatten rechts
# Spitze
d.polygon([(cx-15, body_top), (cx+15, body_top), (cx, 12)], fill=WHT, outline=OC)
d.polygon([(cx-7, body_top), (cx, body_top), (cx, 20)], fill=MHI)
disc(cx, 8, 3, RED)
# Farbringe + Sichtfenster
rect(cx-14, 62, 28, 5, CYD); rect(cx-14, 62, 28, 2, CY)
rect(cx-14, 118, 28, 5, RED); rect(cx-14, 118, 28, 2, REDL)
disc(cx, 84, 7, OC); disc(cx, 84, 5, CY); disc(cx-2, 82, 2, CYL)
# Nieten-Naht
for by in range(70, 116, 8): im.putpixel((cx+11, by), MMD)
# Finnen
d.polygon([(cx-15, 128), (cx-15, 150), (cx-30, 150), (cx-18, 130)], fill=MMD, outline=OC)
d.polygon([(cx+15, 128), (cx+15, 150), (cx+30, 150), (cx+18, 130)], fill=MDK, outline=OC)
# Triebwerksduesen + Abgasglut im Graben
for nx in (cx-8, cx+8):
    d.polygon([(nx-5, 148), (nx+5, 148), (nx+7, 158), (nx-7, 158)], fill=MDK, outline=OC)
    disc(nx, 160, 5, FLM); disc(nx, 163, 3, FLML)

# --- Satellitenschuessel rechts ---
sx, sy = 152, 108
rect(sx-2, sy, 5, 42, MMD); rect(sx-2, sy, 2, 42, MLT)      # Mast
rect(sx-8, 148, 17, 4, MDK)                                  # Fuss
disc(sx, sy-8, 15, MDK); disc(sx, sy-8, 13, MHI); disc(sx, sy-8, 9, WHT)
ring(sx, sy-8, 15, OC)
rect(sx-1, sy-20, 3, 12, MMD); disc(sx, sy-22, 3, MLT)      # Erreger im Fokus
for a in range(-60, 61, 30):                                 # Streben
    d.line([(sx, sy-8), (sx + 13*math.cos(math.radians(a)), sy-8 + 13*math.sin(math.radians(a)))], fill=MMD, width=1)

# --- Kontrollbunker links unten ---
panel(14, 118, 30, 30, MDK, MMD, OCN)
for wx in (18, 30):
    rect(wx, 126, 9, 8, CY); rect(wx, 126, 9, 3, CYL)
for bx in (17, 40):
    im.putpixel((bx, 121), MHI); im.putpixel((bx, 145), MHI)
rect(14, 114, 30, 4, MMD); rect(14, 114, 30, 1, MLT)        # Flachdach
disc(38, 110, 3, RED)                                        # Antennenlicht
rect(37, 104, 2, 7, MLT)

im.save(OUT + "/deco_launchpad.png"); print("wrote deco_launchpad.png")
