"""
Oel-Bohrinsel (3x3, 192x192): rein dekorativer "Spoiler" fuers naechste
Unternehmens-Level (Petrochemie/Oel-Raffinerie) - steht auf Stelzenbeinen
im offenen Wasser, mit Bohrturm, Fackelmast (Flare-Stack mit Flamme),
Kran und Warnstreifen-Deck. Gleicher Baustil wie AKW/Plattform.
"""
from PIL import Image, ImageDraw
import math
S = 192
OUT = "/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"
A = 255
def C(r, g, b, a=A): return (r, g, b, a)
OC = C(20, 20, 26)
STL = C(150, 156, 168); STLL = C(198, 204, 216); STLD = C(96, 104, 118)
DK = C(64, 68, 78); DKD = C(44, 48, 56)
YEL = C(248, 206, 74); OCN = C(30, 30, 34)
RED = C(232, 76, 64); REDL = C(255, 150, 130)
ORG = C(250, 140, 40); ORGL = C(255, 210, 120)
WAT = C(70, 150, 214, 130)

im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(im)

def rect(x, y, w, h, c):
    x, y, w, h = int(round(x)), int(round(y)), int(round(w)), int(round(h))
    for dy in range(h):
        for dx in range(w):
            if 0 <= x + dx < S and 0 <= y + dy < S: im.putpixel((x + dx, y + dy), c)

def disc(cx, cy, r, c):
    for y in range(int(cy - r), int(cy + r + 1)):
        for x in range(int(cx - r), int(cx + r + 1)):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r and 0 <= x < S and 0 <= y < S: im.putpixel((x, y), c)

# --- Wellen-Andeutung ganz unten (Wasser scheint durch, hier nur ein paar Kaemme) ---
for wx in range(0, S, 14):
    rect(wx, 176, 8, 2, C(150, 210, 230, 90))

# --- 4 Stelzenbeine (Lattice), diagonal auf die Deckplattform zulaufend ---
deck_top, deck_bot = 96, 112
legs = [(30, 178, 66, deck_bot), (162, 178, 126, deck_bot), (30, 130, 66, deck_bot), (162, 130, 126, deck_bot)]
for (x0, y0, x1, y1) in [(28, 186, 62, deck_bot), (164, 186, 130, deck_bot), (10, 150, 60, deck_bot), (182, 150, 132, deck_bot)]:
    d.line([(x0, y0), (x1, y1)], fill=OC, width=5)
    d.line([(x0, y0), (x1, y1)], fill=STLD, width=3)
# Querstreben zwischen den Beinen
for yy in (130, 150, 168):
    d.line([(20 + (yy - 130) * 0.3, yy), (172 - (yy - 130) * 0.3, yy)], fill=STLD, width=2)

# --- Deckplattform ---
rect(48, deck_top, 96, deck_bot - deck_top, DK)
rect(48, deck_top, 96, 3, STLL); rect(48, deck_bot - 3, 96, 3, DKD)
rect(47, deck_top - 1, 98, deck_bot - deck_top + 2, OC)
rect(47, deck_top - 1, 98, 2, OC)
# Warnstreifen am Deckrand
for i, gx in enumerate(range(48, 144, 8)):
    rect(gx, deck_bot - 4, 8, 4, YEL if i % 2 == 0 else OCN)

# --- Bohrturm (Lattice-Derrick) mittig auf dem Deck ---
tx = 96; ttop = 20; tbot = deck_top
for yy in range(ttop, tbot):
    t = (yy - ttop) / (tbot - ttop)
    hw = 6 + 20 * t
    rect(tx - hw, yy, 1, 1, STL); rect(tx + hw, yy, 1, 1, STL)
    if yy % 10 == 0:
        d.line([(tx - hw, yy), (tx + hw, yy)], fill=STLD, width=1)
d.line([(tx - 6, ttop), (tx - 26, tbot)], fill=OC, width=3)
d.line([(tx - 6, ttop), (tx - 26, tbot)], fill=STL, width=2)
d.line([(tx + 6, ttop), (tx + 26, tbot)], fill=OC, width=3)
d.line([(tx + 6, ttop), (tx + 26, tbot)], fill=STL, width=2)
# Diagonal-Verstrebungen
for i in range(6):
    yy0 = ttop + i * (tbot - ttop) / 6; yy1 = yy0 + (tbot - ttop) / 6
    t0 = (yy0 - ttop) / (tbot - ttop); t1 = (yy1 - ttop) / (tbot - ttop)
    hw0 = 6 + 20 * t0; hw1 = 6 + 20 * t1
    d.line([(tx - hw0, yy0), (tx + hw1, yy1)], fill=STLD, width=1)
    d.line([(tx + hw0, yy0), (tx - hw1, yy1)], fill=STLD, width=1)
# Krone oben mit rotem Warnlicht
rect(tx - 4, ttop - 6, 8, 6, DK)
disc(tx, ttop - 8, 3, RED); im.putpixel((tx - 1, ttop - 9), REDL)

# --- Fackelmast (Flare-Stack) seitlich mit Flamme ---
fx = 150
rect(fx, 40, 4, deck_top - 40, STLD); rect(fx, 40, 1, deck_top - 40, STLL)
d.line([(fx + 2, deck_top), (fx + 20, deck_top + 4)], fill=OC, width=3)
disc(fx + 2, 36, 7, ORG); disc(fx + 2, 32, 5, ORGL); disc(fx - 2, 40, 4, ORG)

# --- Kranarm ---
rect(50, deck_top - 2, 4, 14, STLD)
d.line([(52, deck_top - 2), (30, deck_top + 6)], fill=OC, width=3)
d.line([(52, deck_top - 2), (30, deck_top + 6)], fill=STL, width=2)

im.save(OUT + "/deco_oilrig.png"); print("oilrig ok")
