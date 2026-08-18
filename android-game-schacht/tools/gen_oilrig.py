"""
Oel-Bohrinsel (3x3, 192x192): rein dekorativer "Spoiler" fuers naechste
Unternehmens-Level (Petrochemie/Oel-Raffinerie) - steht auf drei dicken,
konischen Stelzenbeinen im offenen Wasser, mit Bohrturm, Kontrollgebaeude
(Fenster + Antenne mit Warnlicht), zwei Kraenen und Warnstreifen-Deck.

v2: komplett neu aufgebaut, angelehnt an ein klassisches Bohrinsel-Icon -
blockige Silhouette statt duenner Diagonal-Stelzen: 3 dicke, sich nach unten
verjuengende Beine statt 4 duenner Lattice-Streben, neues Kontrollgebaeude
mit Fenstern, zweiter Kran ergaenzt (vorher nur einer). Gleicher Baustil wie
AKW/Plattform (hspan-Bänder, Warnstreifen, Nieten-Look).
"""
from PIL import Image, ImageDraw
S = 192
OUT = "/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"
A = 255
def C(r, g, b, a=A): return (r, g, b, a)
OC = C(20, 20, 26)
STL = C(150, 156, 168); STLL = C(198, 204, 216); STLD = C(96, 104, 118)
DK = C(64, 68, 78); DKD = C(44, 48, 56); DKL = C(86, 92, 104)
YEL = C(248, 206, 74); OCN = C(30, 30, 34)
RED = C(232, 76, 64); REDL = C(255, 150, 130)
WIN = C(140, 214, 232); WINL = C(210, 244, 250)

im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(im)

def rect(x, y, w, h, c):
    x, y, w, h = int(round(x)), int(round(y)), int(round(w)), int(round(h))
    for dy in range(h):
        for dx in range(w):
            if 0 <= x + dx < S and 0 <= y + dy < S: im.putpixel((x + dx, y + dy), c)

def hspan(y, cx, hw, base, light, dark):
    x0 = int(round(cx - hw)); x1 = int(round(cx + hw)); w = x1 - x0
    if w <= 0: return
    rect(x0, y, w, 1, base)
    lw = max(1, int(w * 0.28))
    rect(x0, y, lw, 1, light)
    rect(x1 - max(1, int(w * 0.28)), y, max(1, int(w * 0.28)), 1, dark)

def disc(cx, cy, r, c):
    for y in range(int(cy - r), int(cy + r + 1)):
        for x in range(int(cx - r), int(cx + r + 1)):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r and 0 <= x < S and 0 <= y < S: im.putpixel((x, y), c)

# --- Wellen-Andeutung ganz unten ---
for wx in range(0, S, 14):
    rect(wx, 182, 8, 2, C(150, 210, 230, 90))

deck_top, deck_bot = 92, 110
deck_l, deck_r = 30, 162

# --- Drei dicke, konische Stelzenbeine unter dem Deck (statt duenner Diagonalen) ---
for lx in (52, 96, 140):
    top_w, bot_w = 9.0, 17.0
    y0, y1 = deck_bot, 180
    for yy in range(y0, y1):
        t = (yy - y0) / (y1 - y0)
        hw = top_w / 2 + (bot_w - top_w) / 2 * t
        rect(lx - hw - 1, yy, 2 * hw + 2, 1, OC)
        hspan(yy, lx, hw, STL, STLL, STLD)
    rect(lx - bot_w / 2 - 1, y1 - 3, bot_w + 2, 4, DKD)   # Fuss

# --- Deckplattform ---
rect(deck_l - 1, deck_top - 1, deck_r - deck_l + 2, deck_bot - deck_top + 2, OC)
rect(deck_l, deck_top, deck_r - deck_l, deck_bot - deck_top, DK)
rect(deck_l, deck_top, deck_r - deck_l, 3, DKL)
rect(deck_l, deck_bot - 3, deck_r - deck_l, 3, DKD)
for i, gx in enumerate(range(deck_l, deck_r, 8)):
    rect(gx, deck_bot - 4, 8, 4, YEL if i % 2 == 0 else OCN)

# --- Bohrturm (Lattice-Derrick), links auf dem Deck ---
tx = 70; ttop = 18; tbot = deck_top
for yy in range(ttop, tbot):
    t = (yy - ttop) / (tbot - ttop)
    hw = 5 + 17 * t
    rect(tx - hw - 1, yy, 2 * hw + 2, 1, OC)
    rect(tx - hw, yy, 1, 1, STL); rect(tx + hw, yy, 1, 1, STL)
    if yy % 9 == 0:
        d.line([(tx - hw, yy), (tx + hw, yy)], fill=STLD, width=1)
d.line([(tx - 5, ttop), (tx - 22, tbot)], fill=OC, width=3)
d.line([(tx - 5, ttop), (tx - 22, tbot)], fill=STL, width=2)
d.line([(tx + 5, ttop), (tx + 22, tbot)], fill=OC, width=3)
d.line([(tx + 5, ttop), (tx + 22, tbot)], fill=STL, width=2)
for i in range(5):
    yy0 = ttop + i * (tbot - ttop) / 5; yy1 = yy0 + (tbot - ttop) / 5
    t0 = (yy0 - ttop) / (tbot - ttop); t1 = (yy1 - ttop) / (tbot - ttop)
    hw0 = 5 + 17 * t0; hw1 = 5 + 17 * t1
    d.line([(tx - hw0, yy0), (tx + hw1, yy1)], fill=STLD, width=1)
    d.line([(tx + hw0, yy0), (tx - hw1, yy1)], fill=STLD, width=1)
rect(tx - 3, ttop - 5, 6, 5, DK)

# --- Kontrollgebaeude rechts auf dem Deck: blockig mit 3 Fenstern + Antenne ---
bx0, bx1 = 118, 156; btop = 54
rect(bx0 - 1, btop - 1, bx1 - bx0 + 2, deck_top - btop + 1, OC)
rect(bx0, btop, bx1 - bx0, deck_top - btop, DK)
rect(bx0, btop, bx1 - bx0, 3, DKL)
for wx in range(bx0 + 4, bx1 - 6, 10):
    rect(wx, btop + 12, 7, 8, WIN); rect(wx, btop + 12, 7, 2, WINL)
antx = (bx0 + bx1) / 2
rect(antx - 1, btop - 14, 2, 14, C(120, 126, 140))
disc(antx, btop - 16, 3, RED); im.putpixel((int(antx) - 1, btop - 17), REDL)

# --- Zwei Kraene (links + rechts, statt vorher nur einer): Mast + weit ausladender
# Ausleger, gut sichtbar ueber den Deckrand hinaus - wie beim Vorbild-Icon.
def crane(base_x, sign):
    mast_top = deck_top - 34
    rect(base_x - 2, mast_top, 4, deck_top - mast_top, STLD)
    rect(base_x - 2, mast_top, 4, 3, STLL)
    tip_x = base_x + sign * 34; tip_y = mast_top + 6
    d.line([(base_x, mast_top + 2), (tip_x, tip_y)], fill=OC, width=4)
    d.line([(base_x, mast_top + 2), (tip_x, tip_y)], fill=STL, width=2)
    d.line([(tip_x, tip_y), (tip_x - sign * 5, deck_top + 4)], fill=STLD, width=1)

crane(deck_l + 10, -1)
crane(deck_r - 10, 1)

im.save(OUT + "/deco_oilrig.png"); print("oilrig v2 ok")
