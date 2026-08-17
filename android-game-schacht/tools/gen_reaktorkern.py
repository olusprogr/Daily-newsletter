"""
Reaktorkern (2x2, 128x128): das Reaktor-Containment-Gebaeude aus dem
AKW-Sprite (gen_reaktor.py), fuer sich alleine und groesser - gleicher
Baustil (Zylinder + Kuppel + Trefoil-Warnsymbol + Lueftung + Tuer + Leiter
+ Warnstreifen-Sockel), damit Level-1-AKW und Level-2-Reaktorkern optisch
zusammengehoeren.
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

# Zylinder-Koerper
bx, body_top, body_bot, bw = 18, 30, 112, 92
rect(bx - 1, body_top - 1, bw + 2, body_bot - body_top + 2, OC)
for y in range(body_top, body_bot):
    hspan(y, bx + bw / 2, bw / 2, CN, CNL, CND)
for rx in range(bx + 8, bx + bw - 6, 14):
    rect(rx, body_top + 6, 1, body_bot - body_top - 12, CND)

# Kuppel (Halbkugel)
domeR = bw / 2 + 2; dcx = bx + bw / 2; dbase = body_top
for y in range(int(dbase - domeR), dbase):
    hgt = dbase - y
    hw = math.sqrt(max(0, domeR * domeR - hgt * hgt))
    rect(dcx - hw - 1, y, 2 * hw + 2, 1, OC)
    hspan(y, dcx, hw, DM, DML, DMD)
rect(dcx - 1, int(dbase - domeR) - 8, 2, 8, C(120, 126, 140))
disc(dcx, int(dbase - domeR) - 9, 3, RED); im.putpixel((int(dcx), int(dbase - domeR) - 10), REDL)

# Strahlen-Warnsymbol (Trefoil)
tx, ty, tr = bx + bw // 2, body_top + 26, 18
for y in range(ty - tr, ty + tr + 1):
    for x in range(tx - tr, tx + tr + 1):
        dx, dy = x - tx, y - ty
        rr = (dx * dx + dy * dy) ** 0.5
        if rr <= tr:
            ang = (math.degrees(math.atan2(dy, dx)) + 90) % 120
            blade = (rr > 5.5 and rr < tr * 0.9 and ang < 52)
            im.putpixel((x, y), OC if (blade or rr <= 4.2) else YEL)
for a in range(0, 360, 3):
    x = int(tx + tr * math.cos(math.radians(a))); y = int(ty + tr * math.sin(math.radians(a)))
    if 0 <= x < S and 0 <= y < S: im.putpixel((x, y), OC)

# Betonband + Lueftungsschlitze
rect(bx + 4, body_top + 52, bw - 8, 3, CND)
for vx in range(bx + 8, bx + bw - 10, 11):
    rect(vx, body_top + 58, 7, 10, C(58, 64, 78)); rect(vx, body_top + 58, 7, 3, C(96, 102, 116))

# Tuer + Warnstreifen
rect(bx + bw // 2 - 8, body_bot - 22, 16, 22, OC); rect(bx + bw // 2 - 7, body_bot - 21, 14, 21, C(60, 64, 74))
for i in range(0, bw - 6, 10): rect(bx + 3 + i, body_bot - 6, 5, 4, YEL); rect(bx + 3 + i + 5, body_bot - 6, 5, 4, OC)

# Leiter
rect(bx + bw - 4, body_top + 8, 1, body_bot - body_top - 12, C(150, 156, 168))
rect(bx + bw + 2, body_top + 8, 1, body_bot - body_top - 12, C(150, 156, 168))
for ly in range(body_top + 8, body_bot - 6, 7): rect(bx + bw - 4, ly, 7, 2, C(150, 156, 168))

im.save(OUT + "/mach_reaktorkern.png"); print("reaktorkern ok")
