"""
Reaktorkern (2x2, 128x128): das Reaktor-Containment-Gebaeude aus dem
AKW-Sprite (gen_reaktor.py), fuer sich alleine und groesser - gleicher
Baustil (Zylinder + Kuppel + Trefoil-Warnsymbol + Lueftung + Tuer + Leiter
+ Warnstreifen-Sockel), damit Level-1-AKW und Level-2-Reaktorkern optisch
zusammengehoeren.

v2: Kuppel + Warnlicht schmaler/tiefer angesetzt, damit nichts mehr am
oberen Bildrand abgeschnitten wird (v1 liess die Kuppel bei y<0 clippen).
"""
from PIL import Image, ImageDraw
import math
S = 128
OUT = "/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"
A = 255
def C(r, g, b, a=A): return (r, g, b, a)
OC = C(20, 20, 26)
CN = C(150, 156, 168); CNL = C(198, 204, 216); CND = C(104, 110, 124)
DM = C(126, 134, 150); DML = C(180, 188, 204); DMD = C(86, 94, 110)
YEL = C(248, 206, 74); RED = C(240, 84, 72); REDL = C(255, 160, 150)

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
    lw = max(1, int(w * 0.26))
    rect(x0, y, lw, 1, light)
    rect(x1 - max(1, int(w * 0.30)), y, max(1, int(w * 0.30)), 1, dark)

def disc(cx, cy, r, c):
    for y in range(int(cy - r), int(cy + r + 1)):
        for x in range(int(cx - r), int(cx + r + 1)):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r and 0 <= x < S and 0 <= y < S: im.putpixel((x, y), c)

# Sockel mit Warnstreifen
rect(6, 112, S - 12, 12, C(56, 58, 66)); rect(6, 112, S - 12, 3, C(78, 80, 90))
for gx in range(10, S - 10, 16):
    rect(gx, 118, 6, 4, YEL); rect(gx + 6, 118, 6, 4, OC)

# Zylinder-Koerper (schmaler als v1, damit die Kuppel + Warnlicht komplett
# ueber body_top passen, statt am Bildrand (y<0) abgeschnitten zu werden).
bx, body_top, body_bot, bw = 34, 46, 112, 60
rect(bx - 1, body_top - 1, bw + 2, body_bot - body_top + 2, OC)
for y in range(body_top, body_bot):
    hspan(y, bx + bw / 2, bw / 2, CN, CNL, CND)
for rx in range(bx + 6, bx + bw - 4, 10):
    rect(rx, body_top + 4, 1, body_bot - body_top - 8, CND)

# Kuppel (Halbkugel) - domeR ist bewusst klein genug, dass Kuppel + Warnlicht
# komplett im Bild bleiben (dome_top und light_top beide >= 0).
domeR = bw / 2 + 2; dcx = bx + bw / 2; dbase = body_top
for y in range(int(dbase - domeR), dbase):
    hgt = dbase - y
    hw = math.sqrt(max(0, domeR * domeR - hgt * hgt))
    rect(dcx - hw - 1, y, 2 * hw + 2, 1, OC)
    hspan(y, dcx, hw, DM, DML, DMD)
rect(dcx - 1, int(dbase - domeR) - 7, 2, 7, C(120, 126, 140))
disc(dcx, int(dbase - domeR) - 8, 3, RED); im.putpixel((int(dcx) - 1, int(dbase - domeR) - 9), REDL)

# Strahlen-Warnsymbol (Trefoil)
tx, ty, tr = bx + bw // 2, body_top + 22, 14
for y in range(ty - tr, ty + tr + 1):
    for x in range(tx - tr, tx + tr + 1):
        dx, dy = x - tx, y - ty
        rr = (dx * dx + dy * dy) ** 0.5
        if rr <= tr:
            ang = (math.degrees(math.atan2(dy, dx)) + 90) % 120
            blade = (rr > 4.3 and rr < tr * 0.9 and ang < 52)
            im.putpixel((x, y), OC if (blade or rr <= 3.3) else YEL)
for a in range(0, 360, 3):
    x = int(tx + tr * math.cos(math.radians(a))); y = int(ty + tr * math.sin(math.radians(a)))
    if 0 <= x < S and 0 <= y < S: im.putpixel((x, y), OC)

# Betonband + Lueftungsschlitze
rect(bx + 3, body_top + 40, bw - 6, 3, CND)
for vx in range(bx + 6, bx + bw - 8, 9):
    rect(vx, body_top + 44, 6, 8, C(58, 64, 78)); rect(vx, body_top + 44, 6, 2, C(96, 102, 116))

# Tuer + Warnstreifen
rect(bx + bw // 2 - 7, body_bot - 12, 14, 12, OC); rect(bx + bw // 2 - 6, body_bot - 11, 12, 11, C(60, 64, 74))
for i in range(0, bw - 6, 9): rect(bx + 3 + i, body_bot - 4, 4, 3, YEL); rect(bx + 3 + i + 4, body_bot - 4, 4, 3, OC)

# Leiter
rect(bx + bw - 3, body_top + 6, 1, body_bot - body_top - 10, C(150, 156, 168))
rect(bx + bw + 1, body_top + 6, 1, body_bot - body_top - 10, C(150, 156, 168))
for ly in range(body_top + 6, body_bot - 4, 6): rect(bx + bw - 3, ly, 5, 2, C(150, 156, 168))

im.save(OUT + "/mach_reaktorkern.png"); print("reaktorkern v2 ok")
