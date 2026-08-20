"""
Level 4 (High-Tech): alle acht Maschinen-Sprites.

Baustil bewusst identisch zum Rest (panel()/bolts()/disc()/Warnstreifen, harte
1px-Outlines, Licht von oben links), aber mit einer eigenen Farbfamilie:
Reinraum-Weiss + Silizium-Silber + Glas-Cyan + Gold + Platinengruen statt des
Messing/Petrolgruen der Petrochemie-Stufe. So erkennt man auf einen Blick, in
welchem Unternehmens-Level man ist, ohne dass die Fabrik stilistisch
auseinanderfaellt.

Groessen entsprechen footprint() in Sim.kt:
  Sandmine/Seltenerd/Reinstwasser/Dotierwerk/Satellitenwerk  1x1 -> 64x64
  Waferfabrik                                                1x2 -> 64x128
  Chipfabrik                                                 2x2 -> 128x128
  Startrampe                                                 2x3 -> 128x192
"""
from PIL import Image, ImageDraw
import math

OUT = "/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"
A = 255
# --- gemeinsame Industrie-Palette (wie gen_nuclear.py / gen_petro.py) ---
OC  = (24, 24, 30, A)
MDK = (64, 68, 78, A); MMD = (104, 110, 122, A); MLT = (150, 158, 172, A); MHI = (198, 204, 216, A)
YEL = (240, 200, 70, A); YELL = (255, 236, 150, A); OCN = (30, 30, 34, A)
RED = (232, 76, 64, A); REDL = (255, 150, 130, A)
BLU = (70, 150, 214, A); BLUL = (150, 210, 246, A); BLUD = (40, 96, 150, A)
CONC = (86, 88, 96, A); CONCD = (60, 62, 70, A)
ORG = (244, 140, 48, A); ORGL = (255, 206, 120, A)
# --- High-Tech-Familie ---
WHT = (228, 233, 240, A); WHTL = (250, 252, 255, A); WHTD = (182, 189, 202, A)   # Reinraum-Weiss
SIL = (172, 183, 198, A); SILL = (214, 223, 236, A); SILD = (118, 128, 146, A)   # Silizium-Silber
GLS = (104, 194, 208, A); GLSL = (182, 240, 246, A); GLSD = (52, 128, 148, A)    # Glas/Cyan
GLD = (226, 184, 78, A); GLDL = (250, 222, 146, A); GLDD = (158, 122, 42, A)     # Gold
GRN = (62, 150, 104, A); GRNL = (112, 202, 150, A); GRND = (32, 94, 64, A)       # Platinengruen
VIO = (154, 110, 212, A); VIOL = (208, 172, 246, A); VIOD = (96, 62, 148, A)     # Seltene Erden
SND = (224, 202, 142, A); SNDL = (246, 234, 196, A); SNDD = (176, 152, 98, A)    # Quarzsand

# ---------------------------------------------------------------- Helfer
class Canvas:
    def __init__(self, w, h):
        self.W, self.H = w, h
        self.im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.im)
    def px(self, x, y, c):
        if 0 <= x < self.W and 0 <= y < self.H: self.im.putpixel((int(x), int(y)), c)
    def rect(self, x, y, w, h, c):
        x, y, w, h = int(round(x)), int(round(y)), int(round(w)), int(round(h))
        for dy in range(h):
            for dx in range(w): self.px(x + dx, y + dy, c)
    def panel(self, x, y, w, h, base, light, dark, out=OC):
        self.rect(x - 1, y - 1, w + 2, h + 2, out)
        self.rect(x, y, w, h, base)
        self.rect(x, y, w, 2, light); self.rect(x, y, 2, h, light)
        self.rect(x, y + h - 2, w, 2, dark); self.rect(x + w - 2, y, 2, h, dark)
    def bolts(self, x, y, w, h, c=MHI):
        for (bx, by) in [(x+2, y+2), (x+w-3, y+2), (x+2, y+h-3), (x+w-3, y+h-3)]:
            self.px(bx, by, c)
    def disc(self, cx, cy, r, c):
        for y in range(int(cy-r), int(cy+r+1)):
            for x in range(int(cx-r), int(cx+r+1)):
                if (x-cx)**2 + (y-cy)**2 <= r*r: self.px(x, y, c)
    def ring(self, cx, cy, r, c):
        for a in range(0, 360, 2):
            self.px(int(cx + r*math.cos(math.radians(a))), int(cy + r*math.sin(math.radians(a))), c)
    def cyl(self, x, y, w, h, base, light, dark):
        self.rect(x - 1, y - 1, w + 2, h + 2, OC)
        self.rect(x, y, w, h, base)
        self.rect(x, y, max(1, int(w*0.22)), h, light)
        self.rect(x + w - max(1, int(w*0.28)), y, max(1, int(w*0.28)), h, dark)
    def hazard(self, x, y, w, h, step=6):
        for i in range(0, int(w), step*2):
            self.rect(x+i, y, min(step, w-i), h, YEL)
            self.rect(x+i+step, y, min(step, max(0, w-i-step)), h, OCN)
    def base(self, x, y, w, h=10):
        self.rect(x, y, w, h, CONCD); self.rect(x, y, w, 2, CONC)
        for i in range(int(x)+3, int(x+w)-2, 8): self.px(i, y+h-3, MHI)
    def save(self, name):
        self.im.save(OUT + "/" + name); print("wrote", name, self.im.size)

def lattice(c, cx, top, bot, hw_top, hw_bot, col=MLT, cold=MDK, rungs=6):
    for yy in range(int(top), int(bot)):
        t = (yy - top) / max(1, (bot - top))
        hw = hw_top + (hw_bot - hw_top) * t
        c.px(cx - hw, yy, col); c.px(cx - hw - 1, yy, OC)
        c.px(cx + hw, yy, col); c.px(cx + hw + 1, yy, OC)
    for i in range(rungs):
        y0 = top + i * (bot - top) / rungs; y1 = y0 + (bot - top) / rungs
        t0 = (y0 - top) / (bot - top); t1 = (y1 - top) / (bot - top)
        h0 = hw_top + (hw_bot - hw_top) * t0; h1 = hw_top + (hw_bot - hw_top) * t1
        c.d.line([(cx - h0, y0), (cx + h1, y1)], fill=cold, width=1)
        c.d.line([(cx + h0, y0), (cx - h1, y1)], fill=cold, width=1)
        c.d.line([(cx - h1, y1), (cx + h1, y1)], fill=cold, width=1)

def pipe_h(c, x, y, w, thick=5, col=SIL, light=SILL, dark=SILD):
    c.rect(x, y - 1, w, thick + 2, OC)
    c.rect(x, y, w, thick, col)
    c.rect(x, y, w, 1, light); c.rect(x, y + thick - 1, w, 1, dark)
    for fx in (x, x + w - 2):
        c.rect(fx, y - 2, 2, thick + 4, MMD); c.rect(fx, y - 2, 2, 1, MHI)

def pipe_v(c, x, y, h, thick=5, col=SIL, light=SILL, dark=SILD):
    c.rect(x - 1, y, thick + 2, h, OC)
    c.rect(x, y, thick, h, col)
    c.rect(x, y, 1, h, light); c.rect(x + thick - 1, y, 1, h, dark)

def window(c, x, y, w, h, glow=GLS, glowl=GLSL, glowd=GLSD, cols=0):
    """Reinraum-Fensterband: dunkle Fassung, leuchtendes Glas, Sprossen."""
    c.rect(x - 1, y - 1, w + 2, h + 2, OC)
    c.rect(x, y, w, h, glowd)
    c.rect(x, y, w, max(1, h // 2), glow)
    c.rect(x, y, w, 1, glowl)
    if cols:
        step = max(3, w // cols)
        for i in range(step, w, step): c.rect(x + i, y, 1, h, MDK)

# ================================================================ 1) SANDMINE
# Quarzsand-Mine: Schneckenbohrer im Sandtrichter. Zentrale Achse bleibt frei,
# damit die GameView-Bohranimation (nach unten wandernde Glanzbaender) sitzt.
c = Canvas(64, 64)
c.base(4, 52, 56)
c.hazard(6, 56, 52, 4)
# Sandhaufen links und rechts vom Schacht
c.d.polygon([(2, 52), (12, 40), (22, 52)], fill=SND, outline=OC)
c.d.polygon([(4, 52), (12, 43), (18, 52)], fill=SNDL)
c.d.polygon([(44, 52), (54, 42), (62, 52)], fill=SND, outline=OC)
c.d.polygon([(48, 52), (54, 45), (59, 52)], fill=SNDL)
# Trichter (Sandbunker) oben
c.d.polygon([(8, 6), (56, 6), (42, 26), (22, 26)], fill=SND, outline=OC)
c.rect(9, 7, 46, 4, SNDL)
c.rect(22, 22, 20, 4, SNDD)
for gx, gy in [(16, 12), (26, 14), (36, 11), (46, 15), (31, 18), (41, 19)]:
    c.px(gx, gy, SNDL); c.px(gx + 1, gy + 1, SNDD)
# Trichterrahmen
c.rect(7, 5, 50, 2, MMD); c.rect(7, 5, 50, 1, MHI)
# Schneckenbohrer (Mast) - schmal, damit die Animation daneben liegt
c.rect(27, 26, 10, 26, OC)
c.rect(28, 26, 8, 26, MMD); c.rect(28, 26, 2, 26, MLT)
for i in range(0, 26, 4):
    c.d.line([(28, 26 + i), (35, 26 + i + 3)], fill=SNDL, width=1)
    c.d.line([(28, 26 + i + 2), (35, 26 + i + 5)], fill=SNDD, width=1)
# Bohrkopf
c.d.polygon([(27, 50), (37, 50), (32, 58)], fill=SIL, outline=OC)
c.px(31, 53, SILL)
# Antrieb rechts
c.panel(44, 30, 14, 16, MDK, MMD, OCN); c.bolts(44, 30, 14, 16)
c.rect(47, 34, 8, 4, GLS); c.rect(47, 34, 8, 1, GLSL)
# Foerderband nach links
pipe_h(c, 4, 44, 22, 4, SIL, SILL, SILD)
c.save("mach_sandmine.png")

# ================================================================ 2) SELTENERD
# Seltenerd-Mine: Foerdergeruest mit Seilscheibe ueber dem Schacht, violette
# Kristalladern am Fuss.
c = Canvas(64, 64)
c.base(4, 52, 56)
c.hazard(6, 56, 52, 4)
# Foerdergeruest
lattice(c, 32, 12, 50, 5, 13, col=MHI, cold=MLT, rungs=7)
# Seilscheibe (das Erkennungszeichen einer Mine)
c.disc(32, 12, 8, MMD); c.ring(32, 12, 8, OC); c.ring(32, 12, 6, MDK)
for a in range(0, 360, 60):
    c.d.line([(32, 12), (32 + 7*math.cos(math.radians(a)), 12 + 7*math.sin(math.radians(a)))], fill=MHI, width=1)
c.disc(32, 12, 3, MLT); c.ring(32, 12, 3, OC); c.px(31, 11, MHI)
# Foerderseil in den Schacht
c.rect(31, 20, 2, 28, MHI)
# Schachtmund
c.rect(22, 46, 20, 7, OC); c.rect(23, 47, 18, 5, VIOD)
c.rect(23, 47, 18, 1, VIO)
# Violette Kristalladern links/rechts
for (bx, by, hh) in [(6, 40, 12), (12, 44, 8), (50, 42, 10), (56, 45, 7)]:
    c.d.polygon([(bx, by + hh), (bx + 3, by), (bx + 6, by + hh)], fill=VIO, outline=OC)
    c.d.line([(bx + 3, by + 1), (bx + 2, by + hh - 1)], fill=VIOL, width=1)
# Maschinenhaus mit Winde
c.panel(44, 28, 15, 14, MDK, MMD, OCN); c.bolts(44, 28, 15, 14)
c.rect(47, 32, 9, 4, VIOL); c.rect(47, 32, 9, 1, WHTL)
c.save("mach_seltenerd.png")

# ================================================================ 3) REINSTWASSER
# Reinstwasserwerk: drei Umkehrosmose-Saeulen mit Schauglas, Sammelrohr oben,
# Steuerschrank rechts.
c = Canvas(64, 64)
c.base(2, 52, 60)
c.hazard(4, 56, 56, 4)
# Sammelrohr oben
pipe_h(c, 4, 8, 56, 5, SIL, SILL, SILD)
# drei Membransaeulen
for i, x in enumerate((8, 24, 40)):
    c.cyl(x, 18, 12, 34, WHT, WHTL, WHTD)
    c.rect(x - 1, 15, 14, 4, MDK); c.rect(x, 15, 12, 1, MHI)
    pipe_v(c, x + 3, 13, 6, 5, SIL, SILL, SILD)
    # Schauglas mit Reinstwasser
    c.rect(x + 3, 26, 6, 16, OC)
    c.rect(x + 4, 27, 4, 14, GLSD); c.rect(x + 4, 30, 4, 11, GLS)
    c.px(x + 5, 32, GLSL); c.px(x + 6, 36, GLSL)
    # Spannbaender
    c.rect(x, 24, 12, 1, MMD); c.rect(x, 44, 12, 1, MMD)
c.save("mach_reinstwasser.png")

# ================================================================ 4) DOTIERWERK
# Ionenimplanter: Ionenquelle links, Strahlrohr mit violettem Strahl, Analysier-
# magnet, Endstation mit gruenem Substratstapel.
c = Canvas(64, 64)
c.base(2, 52, 60)
c.hazard(4, 56, 56, 4)
# Ionenquelle (Hochspannungskasten mit Isolatoren)
c.panel(3, 14, 16, 22, MDK, MMD, OCN); c.bolts(3, 14, 16, 22)
c.rect(6, 19, 10, 8, VIOD); c.rect(6, 19, 10, 3, VIO); c.px(8, 21, VIOL)
for ix in (5, 10, 15):
    c.rect(ix, 10, 3, 4, MHI); c.rect(ix, 10, 3, 1, WHTL)
# Strahlrohr
pipe_h(c, 19, 22, 24, 8, SIL, SILL, SILD)
c.rect(20, 25, 22, 2, VIOL)
c.rect(20, 25, 22, 1, WHTL)
# Analysiermagnet (grosser Ring mit Spulenwicklung)
c.disc(50, 26, 11, MDK); c.ring(50, 26, 11, OC); c.disc(50, 26, 6, OCN)
for a in range(0, 360, 30):
    c.d.line([(50 + 6*math.cos(math.radians(a)), 26 + 6*math.sin(math.radians(a))),
              (50 + 10*math.cos(math.radians(a)), 26 + 10*math.sin(math.radians(a)))], fill=GLD, width=1)
c.disc(50, 26, 3, VIOL)
# Endstation mit Substratstapel (gruene Platinen)
c.panel(24, 38, 26, 14, MDK, MMD, OCN)
for i, sy in enumerate((40, 44, 48)):
    c.rect(27, sy, 20, 3, GRN); c.rect(27, sy, 20, 1, GRNL); c.rect(27, sy + 2, 20, 1, GRND)
    c.px(29, sy + 1, GLD); c.px(44, sy + 1, GLD)
c.save("mach_dotierwerk.png")

# ================================================================ 5) SATELLITENWERK
# Montagehalle: Portalkran ueber einem Satelliten (Goldfolien-Korpus, blaue
# Solarfluegel, Parabolantenne) auf dem Montagebock.
c = Canvas(64, 64)
c.base(2, 52, 60)
c.hazard(4, 56, 56, 4)
# Hallenrueckwand
c.panel(2, 14, 60, 38, WHTD, WHT, SILD)
for wx in range(6, 58, 10): c.rect(wx, 17, 2, 32, SILD)
# Portalkran (Schiene + Laufkatze + Haken)
c.rect(0, 9, 64, 4, MDK); c.rect(0, 9, 64, 1, MHI)
c.rect(26, 13, 12, 4, MMD); c.rect(26, 13, 12, 1, MHI)
c.rect(31, 17, 2, 5, MHI)
# Parabolantenne
c.disc(32, 24, 6, MHI); c.ring(32, 24, 6, OC); c.disc(32, 24, 4, SILD)
c.rect(31, 22, 2, 3, MDK)
# Solarfluegel links
c.rect(3, 29, 19, 12, OC); c.rect(4, 30, 17, 10, BLUD); c.rect(4, 30, 17, 4, BLU)
for gx in range(7, 21, 4): c.rect(gx, 30, 1, 10, OCN)
c.rect(4, 34, 17, 1, BLUL)
# Solarfluegel rechts
c.rect(42, 29, 19, 12, OC); c.rect(43, 30, 17, 10, BLUD); c.rect(43, 30, 17, 4, BLU)
for gx in range(46, 60, 4): c.rect(gx, 30, 1, 10, OCN)
c.rect(43, 34, 17, 1, BLUL)
# Korpus in Goldfolie
c.panel(24, 28, 16, 20, GLD, GLDL, GLDD); c.bolts(24, 28, 16, 20, WHTL)
c.rect(26, 40, 12, 2, GLDD); c.rect(26, 33, 12, 1, GLDL)
# Duese unten
c.d.polygon([(29, 48), (35, 48), (37, 53), (27, 53)], fill=MDK, outline=OC)
# Montagebock
c.rect(22, 50, 20, 3, MMD); c.rect(22, 50, 20, 1, MHI)
c.save("mach_satellitenwerk.png")

# ================================================================ 6) WAFERFAB (1x2)
# Reinraum-Gebaeude: drei leuchtende Fensterbaender mit sichtbaren Wafer-Scheiben,
# Luefteraufbauten auf dem Dach, Air-Shower-Schleuse am Boden.
c = Canvas(64, 128)
c.base(2, 116, 60, 12)
c.hazard(4, 122, 56, 5)
# Dachaufbauten (Umluftanlage)
c.panel(8, 16, 18, 12, MDK, MMD, OCN); c.bolts(8, 16, 18, 12)
c.panel(34, 10, 20, 18, MDK, MMD, OCN); c.bolts(34, 10, 20, 18)
for dx in range(36, 53, 4): c.rect(dx, 14, 2, 10, MHI)
pipe_h(c, 6, 30, 52, 5, SIL, SILL, SILD)
# Gebaeudekoerper
c.panel(6, 36, 52, 80, WHT, WHTL, WHTD)
# Fensterbaender mit Wafer-Scheiben dahinter
for wy in (44, 64, 84):
    window(c, 12, wy, 40, 13, cols=0)
    for wx in (20, 32, 44):
        c.disc(wx, wy + 6, 4, SIL); c.ring(wx, wy + 6, 4, OC)
        c.disc(wx, wy + 6, 2, SILL); c.px(wx - 1, wy + 4, WHTL)
# Reinraum-Schleuse (Air Shower)
c.panel(20, 100, 24, 16, SIL, SILL, SILD)
c.rect(25, 104, 14, 12, OC); c.rect(26, 105, 12, 11, GLSD)
c.rect(26, 105, 12, 4, GLS); c.rect(26, 105, 12, 1, GLSL)
c.rect(31, 105, 1, 11, MDK)
# Wasser-/Medienanschluss seitlich
pipe_v(c, 2, 90, 26, 5, GLS, GLSL, GLSD)
pipe_h(c, 2, 106, 18, 5, GLS, GLSL, GLSD)
# Warnband ueber der Schleuse
c.hazard(8, 96, 48, 3, 5)
c.save("mach_waferfab.png")

# ================================================================ 7) CHIPFAB (2x2)
# Grosse Fab: Reinraum-Riegel mit goldbeleuchteten Fensterbaendern, zwei
# Abluftttuermen, Rohrbruecke ueber dem Dach und einem grossen IC-Emblem
# auf der Fassade.
c = Canvas(128, 128)
c.base(2, 112, 124, 14)
c.hazard(6, 119, 116, 5)
# Abluftttuerme
c.panel(18, 18, 20, 26, MDK, MMD, OCN); c.bolts(18, 18, 20, 26)
c.rect(16, 14, 24, 5, MMD); c.rect(16, 14, 24, 1, MHI)
c.panel(90, 12, 22, 32, MDK, MMD, OCN); c.bolts(90, 12, 22, 32)
c.rect(88, 8, 26, 5, MMD); c.rect(88, 8, 26, 1, MHI)
# Rohrbruecke ueber dem Dach
pipe_h(c, 6, 46, 116, 6, SIL, SILL, SILD)
for sx in (24, 60, 96): c.rect(sx, 40, 3, 7, MMD)
# Gebaeuderiegel
c.panel(8, 54, 112, 58, WHT, WHTL, WHTD)
# Fensterbaender (obere Reihe cyan = Belichtung, untere gold = Gelblicht-Zone)
window(c, 14, 60, 100, 12, cols=10)
window(c, 14, 78, 44, 12, GLD, GLDL, GLDD, cols=5)
# IC-Emblem auf der Fassade (unverwechselbar: Chip mit Beinchen)
ex, ey = 66, 76
c.rect(ex - 6, ey + 4, 6, 2, GLD); c.rect(ex - 6, ey + 10, 6, 2, GLD); c.rect(ex - 6, ey + 16, 6, 2, GLD)
c.rect(ex + 32, ey + 4, 6, 2, GLD); c.rect(ex + 32, ey + 10, 6, 2, GLD); c.rect(ex + 32, ey + 16, 6, 2, GLD)
c.panel(ex, ey, 32, 22, OCN, MDK, OC)
c.rect(ex + 6, ey + 6, 20, 10, BLUD); c.rect(ex + 6, ey + 6, 20, 4, BLU)
c.rect(ex + 6, ey + 6, 20, 1, BLUL)
c.px(ex + 3, ey + 3, GLD)
# Tor + Verladerampe
c.panel(20, 92, 30, 20, SIL, SILL, SILD)
c.rect(24, 96, 22, 16, OC); c.rect(25, 97, 20, 15, MDK)
for gx in range(25, 45, 4): c.rect(gx, 97, 2, 15, MMD)
c.rect(54, 104, 24, 8, CONCD); c.rect(54, 104, 24, 2, CONC)
# Gruene Substrat-Paletten neben der Rampe
for px_ in (84, 96, 108):
    c.rect(px_, 100, 10, 10, OC); c.rect(px_ + 1, 101, 8, 8, GRN)
    c.rect(px_ + 1, 101, 8, 2, GRNL); c.rect(px_ + 1, 107, 8, 2, GRND)
c.save("mach_chipfab.png")

# ================================================================ 8) STARTRAMPE (2x3)
# Raketenstartrampe: Betonpad mit Flammengraben, Rakete mit Triebwerksduesen,
# Serviceturm mit Plattformen und Schwenkarm, Blitzableitermasten.
c = Canvas(128, 192)
c.base(2, 176, 124, 16)
c.hazard(6, 184, 116, 6)
# Betonpad
c.rect(12, 160, 104, 18, CONCD); c.rect(12, 160, 104, 3, CONC)
for i in range(16, 114, 10): c.px(i, 174, MHI)
# Flammengraben unter der Rakete
c.rect(52, 160, 26, 18, OCN)
c.rect(54, 162, 22, 3, MDK)
# Blitzableitermasten (aussen)
for mx in (10, 118):
    c.rect(mx, 40, 3, 122, MMD); c.rect(mx, 40, 1, 122, MHI)
    c.rect(mx - 1, 34, 5, 6, MDK); c.rect(mx, 30, 3, 5, MHI)
c.d.line([(12, 34), (40, 52)], fill=MDK, width=1)
c.d.line([(119, 34), (92, 52)], fill=MDK, width=1)
# Serviceturm links
c.panel(22, 46, 24, 116, MDK, MMD, OCN)
for py in range(52, 158, 14):
    c.rect(20, py, 28, 3, MMD); c.rect(20, py, 28, 1, MHI)
    for rx in range(21, 47, 4): c.px(rx, py - 2, MLT)
c.rect(30, 46, 2, 116, MLT)
c.rect(38, 46, 2, 116, MLT)
# Schwenkarm zur Rakete
c.rect(46, 76, 12, 6, MMD); c.rect(46, 76, 12, 1, MHI); c.rect(46, 81, 12, 1, MDK)
# Rakete: Korpus
c.cyl(54, 44, 22, 116, WHT, WHTL, WHTD)
# Nasenkegel
c.d.polygon([(65, 14), (54, 46), (76, 46)], fill=WHT, outline=OC)
c.d.polygon([(65, 16), (58, 44), (65, 44)], fill=WHTL)
c.rect(60, 34, 10, 3, RED)
# Zwischenstufe / schwarzes Band + Streifen
c.rect(54, 92, 22, 8, OCN); c.rect(54, 92, 22, 1, MDK)
c.rect(54, 118, 22, 3, RED); c.rect(54, 118, 22, 1, REDL)
# Nutzlast-Logo (kleiner Satellit)
c.rect(61, 60, 8, 8, OC); c.rect(62, 61, 6, 6, GLD); c.rect(62, 61, 6, 2, GLDL)
c.rect(58, 62, 3, 4, BLU); c.rect(69, 62, 3, 4, BLU)
# Finnen
c.d.polygon([(54, 142), (54, 160), (44, 160)], fill=MLT, outline=OC)
c.d.polygon([(76, 142), (76, 160), (86, 160)], fill=MMD, outline=OC)
# Triebwerksduesen
for nx in (57, 65, 73):
    c.d.polygon([(nx - 3, 158), (nx + 3, 158), (nx + 4, 166), (nx - 4, 166)], fill=MDK, outline=OC)
    c.rect(nx - 3, 164, 6, 2, OCN)
# Halteklammern am Fuss
c.rect(48, 154, 8, 5, MMD); c.rect(74, 154, 8, 5, MMD)
# Tanks / Versorgung rechts
c.cyl(96, 128, 16, 32, SIL, SILL, SILD)
c.rect(95, 124, 18, 5, MDK); c.rect(96, 124, 16, 1, MHI)
pipe_h(c, 82, 146, 16, 5, GLS, GLSL, GLSD)
# Warnband am Padrand
c.hazard(12, 156, 40, 4, 5)
c.hazard(78, 156, 38, 4, 5)
c.save("mach_startrampe.png")
