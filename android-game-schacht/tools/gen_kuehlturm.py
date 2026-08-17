"""
Kuehlturm (2x3, 128x192): der breite Hyperboloid-Kuehlturm aus dem
AKW-Sprite (gen_reaktor.py), fuer sich alleine und groesser, gleicher
Baustil (Beton-Baender, Stuetzstreben-Lattice am Fuss, Warnstreifen-
Plattform). OHNE eingebackenen Dampf - der wird separat als Animation
in GameView.kt darueber gezeichnet.
"""
from PIL import Image, ImageDraw
import math
W, H = 128, 192
OUT = "/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"
A = 255
def C(r, g, b, a=A): return (r, g, b, a)
OC = C(20, 20, 26)
TW = C(178, 184, 198); TWL = C(216, 222, 234); TWD = C(120, 126, 142); TWX = C(88, 94, 110)
YEL = C(248, 206, 74)

im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(im)

def rect(x, y, w, h, c):
    x, y, w, h = int(round(x)), int(round(y)), int(round(w)), int(round(h))
    for dy in range(h):
        for dx in range(w):
            if 0 <= x + dx < W and 0 <= y + dy < H: im.putpixel((x + dx, y + dy), c)

def hspan(y, cx, hw, base, light, dark, edge=None):
    x0 = int(round(cx - hw)); x1 = int(round(cx + hw)); w = x1 - x0
    if w <= 0: return
    rect(x0, y, w, 1, base)
    lw = max(1, int(w * 0.26))
    rect(x0, y, lw, 1, light)
    rect(x1 - max(1, int(w * 0.30)), y, max(1, int(w * 0.30)), 1, dark)
    if edge is not None and 0 <= x0 < W: im.putpixel((max(0, min(W - 1, x0)), y), edge)

# Beton-Plattform + Warnstreifen unten
rect(0, H - 34, W, 34, C(56, 58, 66)); rect(0, H - 34, W, 4, C(78, 80, 90))
for gx in range(4, W, 18): rect(gx, H - 34, 1, 34, C(44, 46, 54))
for gx in range(6, W - 6, 20):
    rect(gx, H - 12, 8, 4, YEL); rect(gx + 8, H - 12, 8, 4, OC)

# Hyperboloid-Kuehlturm, fuellt die Breite fast komplett aus
tcx = W / 2; top = 14; bot = H - 24; rMax = W * 0.46; rWaist = W * 0.27

def trad(y):
    t = (y - top) / (bot - top)
    return rWaist + (rMax - rWaist) * (abs(t - 0.5) * 2) ** 1.5

for y in range(top, bot):
    r = trad(y); rect(tcx - r - 1, y, 2 * r + 2, 1, OC)
for y in range(top, bot):
    r = trad(y)
    hspan(y, tcx, r, TW, TWL, TWD, edge=TWX)
# horizontale Betonbaender
for y in range(top + 8, bot, 16):
    r = trad(y); rect(tcx - r + 1, y, 2 * r - 2, 1, TWD)
# oberer Rand (Muendung, innen dunkel)
rt = trad(top)
rect(tcx - rt, top, 2 * rt, 5, OC); rect(tcx - rt + 3, top + 1, 2 * rt - 6, 4, C(46, 50, 60))
rect(tcx - rt + 3, top + 1, 2 * rt - 6, 1, TWL)
# Stuetzstreben (Lattice) am Fuss
rb = trad(bot - 1)
for i in range(-int(rb), int(rb), 18):
    d.line([(tcx + i, bot - 24), (tcx + i + 16, bot)], fill=OC, width=4)
    d.line([(tcx + i + 16, bot - 24), (tcx + i, bot)], fill=TWD, width=3)
rect(tcx - rb, bot - 3, 2 * rb, 5, C(48, 50, 58))

im.save(OUT + "/mach_kuehlturm.png"); print("kuehlturm ok")
