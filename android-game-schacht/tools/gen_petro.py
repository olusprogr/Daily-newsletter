"""
Level 3 (Petrochemie): alle acht Maschinen-Sprites.

Baustil bewusst identisch zum Rest (panel()/bolts()/disc()/Warnstreifen, harte
1px-Outlines, Licht von oben links), aber mit einer eigenen Farbfamilie: warmes
Messing/Kupfer + Petrolgruen + Oelschwarz statt des Blaugrau/Uran-Gruen der
Kernkraft-Stufe. So erkennt man auf einen Blick, in welchem Unternehmens-Level
man sich befindet, ohne dass die Fabrik stilistisch auseinanderfaellt.

Groessen entsprechen footprint() in Sim.kt:
  Oelbohrturm/Gasbohrer/Seewasser/Gaswaesche/Polymerwerk  1x1 -> 64x64
  Destillation                                            1x2 -> 64x128
  Cracker                                                 2x2 -> 128x128
  Raffinerie                                              2x3 -> 128x192
"""
from PIL import Image, ImageDraw
import math

OUT = "/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"
A = 255
# --- gemeinsame Industrie-Palette (wie gen_nuclear.py) ---
OC  = (24, 24, 30, A)
MDK = (64, 68, 78, A); MMD = (104, 110, 122, A); MLT = (150, 158, 172, A); MHI = (198, 204, 216, A)
YEL = (240, 200, 70, A); YELL = (255, 236, 150, A); OCN = (30, 30, 34, A)
RED = (232, 76, 64, A); REDL = (255, 150, 130, A)
BLU = (70, 150, 214, A); BLUL = (150, 210, 246, A); BLUD = (40, 96, 150, A)
CONC = (86, 88, 96, A); CONCD = (60, 62, 70, A)
# --- Petro-Familie ---
OIL  = (38, 30, 24, A); OILL = (74, 58, 44, A)          # Rohoel
BRS  = (186, 142, 66, A); BRSL = (232, 196, 118, A); BRSD = (128, 94, 40, A)  # Messing
CU   = (176, 106, 60, A); CUL = (222, 156, 104, A)      # Kupfer
PET  = (96, 140, 128, A); PETL = (150, 190, 176, A); PETD = (58, 92, 84, A)   # Petrolgruen
FLM  = (244, 140, 48, A); FLML = (255, 206, 120, A)     # Flamme
CRM  = (226, 218, 200, A)                                # Granulat-Creme

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
        """Blechplatte mit Outline und Licht von oben links."""
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
        for a in range(0, 360, 3):
            self.px(int(cx + r*math.cos(math.radians(a))), int(cy + r*math.sin(math.radians(a))), c)
    def cyl(self, x, y, w, h, base, light, dark):
        """Stehender Zylinder: Rundung ueber vertikale Licht-/Schattenbaender."""
        self.rect(x - 1, y - 1, w + 2, h + 2, OC)
        self.rect(x, y, w, h, base)
        self.rect(x, y, max(1, int(w*0.22)), h, light)
        self.rect(x + w - max(1, int(w*0.28)), y, max(1, int(w*0.28)), h, dark)
    def hazard(self, x, y, w, h, step=6):
        """Gelb-schwarzer Warnstreifen (Sockelband)."""
        for i in range(0, int(w), step*2):
            self.rect(x+i, y, min(step, w-i), h, YEL)
            self.rect(x+i+step, y, min(step, max(0, w-i-step)), h, OCN)
    def base(self, x, y, w, h=10):
        """Betonsockel mit Bolzenreihe."""
        self.rect(x, y, w, h, CONCD); self.rect(x, y, w, 2, CONC)
        for i in range(int(x)+3, int(x+w)-2, 8): self.px(i, y+h-3, MHI)
    def save(self, name):
        self.im.save(OUT + "/" + name); print("wrote", name, self.im.size)

def lattice(c, cx, top, bot, hw_top, hw_bot, col=MLT, cold=MDK, rungs=6):
    """Fachwerk-Turm (Bohrturm-Optik) zwischen zwei Breiten."""
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

def pipe_h(c, x, y, w, thick=5, col=BRS, light=BRSL, dark=BRSD):
    """Liegendes Rohr mit Glanzkante + Flanschen an den Enden."""
    c.rect(x, y - 1, w, thick + 2, OC)
    c.rect(x, y, w, thick, col)
    c.rect(x, y, w, 1, light); c.rect(x, y + thick - 1, w, 1, dark)
    for fx in (x, x + w - 2):
        c.rect(fx, y - 2, 2, thick + 4, MMD); c.rect(fx, y - 2, 2, 1, MHI)

def pipe_v(c, x, y, h, thick=5, col=BRS, light=BRSL, dark=BRSD):
    c.rect(x - 1, y, thick + 2, h, OC)
    c.rect(x, y, thick, h, col)
    c.rect(x, y, 1, h, light); c.rect(x + thick - 1, y, 1, h, dark)

# ================================================================ 1) OELBOHRTURM
# Pumpjack: Fachwerkbock + Balancier (Pferdekopf) + Gegengewicht + Oelpfuetze.
c = Canvas(64, 64)
c.base(6, 52, 52)
c.hazard(8, 56, 48, 4)
# Fachwerkbock
lattice(c, 26, 18, 52, 4, 11, rungs=5)
# Balancier-Balken
c.rect(8, 15, 40, 5, OC); c.rect(9, 16, 38, 3, MLT); c.rect(9, 16, 38, 1, MHI)
# Pferdekopf vorn (links), leicht nach unten geneigt
c.d.polygon([(8, 14), (2, 20), (2, 27), (9, 22)], fill=MMD, outline=OC)
# Poliertes Hubseil zum Bohrloch
c.rect(4, 27, 2, 21, MHI); c.rect(3, 46, 4, 4, MDK)
# Gegengewicht + Kurbelscheibe hinten (rechts)
c.disc(47, 32, 9, MDK); c.ring(47, 32, 9, OC); c.disc(47, 32, 4, MMD)
c.rect(44, 18, 7, 12, OC); c.rect(45, 19, 5, 10, BRSD)
# Motorhaus
c.panel(50, 40, 11, 12, MDK, MMD, OCN); c.bolts(50, 40, 11, 12)
# Oelpfuetze am Bohrloch
c.rect(2, 50, 10, 3, OIL); c.rect(3, 49, 6, 1, OILL)
c.save("mach_oelbohrturm.png")

# ================================================================ 2) GASBOHRER
# Bohrkopf mit Ventilbaum ("Christmas tree") + abgefackeltem Restgas.
c = Canvas(64, 64)
c.base(6, 52, 52)
c.hazard(8, 56, 48, 4)
# Betonschacht
c.panel(20, 34, 24, 18, MDK, MMD, OCN); c.bolts(20, 34, 24, 18)
# Steigrohr
pipe_v(c, 29, 12, 24, 6, PET, PETL, PETD)
# Ventilbaum: drei Absperrraeder uebereinander
for i, yy in enumerate((14, 22, 30)):
    c.rect(24, yy, 16, 3, OC); c.rect(25, yy, 14, 2, MMD)
    c.disc(22, yy + 1, 3, MHI); c.ring(22, yy + 1, 3, OC)
    c.disc(42, yy + 1, 3, MHI); c.ring(42, yy + 1, 3, OC)
# Fackelrohr seitlich mit kleiner Flamme
pipe_v(c, 50, 22, 24, 4, MMD, MLT, MDK)
c.disc(52, 20, 4, FLM); c.disc(52, 18, 2, FLML)
# Manometer
c.disc(35, 42, 4, MHI); c.ring(35, 42, 4, OC); c.px(35, 40, RED)
c.save("mach_gasbohrer.png")

# ================================================================ 3) SEEWASSER
# Seewasser-Einlass: Schoepfwerk mit Rechen, Saugkorb und Schieber.
c = Canvas(64, 64)
c.base(4, 52, 56)
c.hazard(6, 56, 52, 4)
# Pumpenhaus
c.panel(16, 24, 32, 26, MDK, MMD, OCN); c.bolts(16, 24, 32, 26)
# Bullauge mit Wasser
c.disc(32, 34, 9, MDK); c.disc(32, 34, 7, BLUD); c.disc(32, 34, 4, BLU)
c.px(29, 31, BLUL); c.px(30, 31, BLUL)
# Schieber-Handrad oben
c.rect(29, 12, 6, 13, MMD); c.rect(29, 12, 2, 13, MLT)
c.ring(32, 11, 6, MHI); c.ring(32, 11, 7, OC); c.ring(32, 11, 5, MDK)
for a in (0, 60, 120, 180, 240, 300):
    c.d.line([(32, 11), (32 + 6*math.cos(math.radians(a)), 11 + 6*math.sin(math.radians(a)))], fill=MLT, width=1)
c.disc(32, 11, 2, MHI)
# Einlaufrechen links (Gitterstaebe im Wasser)
c.rect(2, 30, 14, 16, BLUD)
for gx in range(3, 16, 3): c.rect(gx, 30, 1, 16, MLT)
c.rect(2, 30, 14, 1, MHI)
# Saugrohr vom Rechen ins Haus
pipe_h(c, 8, 44, 10, 5, PET, PETL, PETD)
# Druckrohr rechts hinaus
pipe_h(c, 47, 36, 15, 5, PET, PETL, PETD)
c.save("mach_seewasser.png")

# ================================================================ 4) GASWAESCHE
# Waschkolonne: zwei gekoppelte Behaelter, Spruehkopf, Tropfenabscheider.
c = Canvas(64, 64)
c.base(4, 52, 56)
c.hazard(6, 56, 52, 4)
# Hauptbehaelter (Waescher) + kleiner Nebenbehaelter
c.cyl(12, 14, 22, 38, PET, PETL, PETD)
c.rect(11, 12, 24, 3, OC); c.rect(12, 12, 22, 2, PETL)     # Deckelflansch
c.cyl(40, 26, 15, 26, MMD, MLT, MDK)
c.rect(39, 24, 17, 3, OC); c.rect(40, 24, 15, 2, MHI)
# Fuellstandsglas im Waescher
c.rect(20, 22, 4, 24, OC); c.rect(21, 23, 2, 22, BLUD); c.rect(21, 34, 2, 11, BLU)
# Spruehduesen (drei Tropfen im Inneren angedeutet)
for sy in (26, 32, 38):
    c.px(28, sy, BLUL); c.px(29, sy + 1, BLUL)
# Verbindungsrohr oben zwischen den Behaeltern
pipe_h(c, 33, 30, 9, 4, BRS, BRSL, BRSD)
# Rohgas-Eintritt unten links, Rein-Additive-Austritt rechts
pipe_h(c, 2, 44, 11, 5, MMD, MLT, MDK)
pipe_h(c, 54, 40, 9, 5, BRS, BRSL, BRSD)
# Warnschild + Manometer
c.rect(15, 18, 8, 3, YEL); c.rect(15, 18, 8, 1, YELL)
c.disc(47, 34, 3, MHI); c.ring(47, 34, 3, OC); c.px(47, 32, RED)
c.save("mach_gaswaesche.png")

# ================================================================ 5) POLYMERWERK
# Reaktorkessel mit Ruehrwerk + Granulat-Trichter + Foerderband.
c = Canvas(64, 64)
c.base(4, 52, 56)
c.hazard(6, 56, 52, 4)
# Kessel
c.cyl(10, 18, 28, 30, BRS, BRSL, BRSD)
c.rect(9, 16, 30, 3, OC); c.rect(10, 16, 28, 2, BRSL)
c.bolts(10, 18, 28, 30, MHI)
# Ruehrwerks-Motor oben
c.panel(16, 6, 16, 11, MDK, MMD, OCN)
for fx in range(18, 31, 3): c.rect(fx, 7, 1, 9, MLT)   # Kuehlrippen
c.disc(34, 11, 4, MMD); c.ring(34, 11, 4, OC); c.px(34, 11, MHI)  # Riemenscheibe
c.rect(23, 17, 3, 8, MHI)                    # Ruehrerwelle
c.rect(19, 25, 11, 2, MLT)                   # Ruehrblatt
# Schauglas mit Schmelze
c.disc(24, 34, 7, MDK); c.disc(24, 34, 5, CU); c.px(22, 32, CUL)
# Granulat-Trichter rechts
c.d.polygon([(42, 20), (60, 20), (54, 36), (48, 36)], fill=MMD, outline=OC)
c.rect(48, 36, 6, 6, MDK); c.rect(48, 36, 6, 1, MLT)
# Granulat-Kuegelchen fallen heraus
for (gx, gy) in ((49, 43), (52, 45), (50, 47), (53, 48)):
    c.px(gx, gy, CRM); c.px(gx + 1, gy, CRM)
# Foerderband unten rechts
c.rect(44, 49, 18, 4, MDK); c.rect(44, 49, 18, 1, MLT)
for bx in range(45, 61, 4): c.px(bx, 51, MHI)
c.save("mach_polymerwerk.png")

# ================================================================ 6) DESTILLATION (1x2, 64x128)
# Hohe Fraktionierkolonne: Boeden/Glockenboeden, Seitenabzuege, Kopfkondensator.
c = Canvas(64, 128)
c.base(6, 116, 52)
c.hazard(8, 120, 48, 4)
# Kolonnenkoerper
c.cyl(20, 20, 24, 96, BRS, BRSL, BRSD)
# Kopfhaube
c.d.polygon([(19, 21), (44, 21), (32, 8)], fill=BRS, outline=OC)
c.rect(30, 4, 5, 6, MMD); c.rect(30, 4, 2, 6, MLT)     # Brueden-Stutzen
# Trennboeden (Fraktionen) - je Boden ein Seitenabzug, abwechselnd links/rechts
for i, by in enumerate(range(30, 112, 12)):
    c.rect(20, by, 24, 2, BRSD); c.rect(20, by, 24, 1, BRSL)
    if i % 2 == 0:
        pipe_h(c, 6, by - 1, 15, 4, MMD, MLT, MDK)
    else:
        pipe_h(c, 44, by - 1, 15, 4, MMD, MLT, MDK)
# Steigleiter rechts
for ly in range(26, 112, 6):
    c.rect(45, ly, 5, 1, MLT)
c.rect(45, 26, 1, 86, MLT); c.rect(49, 26, 1, 86, MLT)
# Schauglas + Warnband unten
c.rect(28, 96, 4, 14, OC); c.rect(29, 97, 2, 12, OIL); c.rect(29, 103, 2, 6, CU)
c.rect(22, 88, 10, 3, YEL); c.rect(22, 88, 10, 1, YELL)
c.save("mach_destillation.png")

# ================================================================ 7) CRACKER (2x2, 128x128)
# Steamcracker: zwei Spaltoefen mit Rohrschlangen + Fackelmast mit echter Flamme.
c = Canvas(128, 128)
c.base(8, 112, 112, 12)
c.hazard(10, 118, 108, 5)
# Ofenblock (breit, massiv)
c.panel(14, 44, 66, 68, MDK, MMD, OCN)
c.bolts(14, 44, 66, 68)
# Zwei Brennkammern mit Sichtfenstern (Glut)
for ox in (22, 52):
    c.rect(ox - 1, 59, 22, 34, OC)
    c.rect(ox, 60, 20, 32, OCN)
    c.rect(ox + 2, 74, 16, 16, FLM); c.rect(ox + 4, 78, 12, 10, FLML)
    for fy in range(62, 74, 4): c.rect(ox + 2, fy, 16, 1, MDK)   # Rohrschlangen davor
# Kamine der Brennkammern
for ox in (28, 58):
    c.rect(ox, 28, 10, 18, MMD); c.rect(ox, 28, 3, 18, MLT); c.rect(ox - 1, 26, 12, 3, OC)
    c.rect(ox, 26, 10, 2, MHI)
# Prozessrohre quer ueber den Ofen
pipe_h(c, 6, 50, 76, 6, BRS, BRSL, BRSD)
pipe_v(c, 84, 50, 44, 6, BRS, BRSL, BRSD)
# Fackelmast rechts mit Flamme (das Markenzeichen eines Crackers)
c.rect(100, 20, 6, 92, MMD); c.rect(100, 20, 2, 92, MLT)
lattice(c, 103, 40, 110, 5, 12, MLT, MDK, rungs=6)
c.rect(98, 16, 10, 5, MDK); c.rect(98, 16, 10, 2, MLT)
c.disc(103, 10, 8, FLM); c.disc(103, 6, 5, FLML); c.disc(99, 13, 4, FLM)
c.px(103, 2, YELL)
# Steuerhaus links unten
c.panel(14, 92, 20, 18, MDK, MMD, OCN)
for wx in (17, 25):
    c.rect(wx, 97, 6, 6, BLU); c.rect(wx, 97, 6, 2, BLUL)
c.save("mach_cracker.png")

# ================================================================ 8) RAFFINERIE (2x3, 128x192)
# Endstufe: Tanklager + Kolonne + Verladung. Hoechstes Gebaeude der Petro-Kette.
c = Canvas(128, 192)
c.base(6, 176, 116, 14)
c.hazard(8, 184, 112, 5)
# --- Grosser Lagertank (links), mit Schwimmdach-Ring und Treppe ---
c.cyl(10, 96, 46, 80, MLT, MHI, MMD)
c.d.ellipse([9, 84, 57, 100], fill=MMD, outline=OC)        # gewoelbtes Tankdach
c.d.ellipse([16, 87, 50, 96], fill=MLT, outline=None)      # Glanz auf dem Dach
c.rect(9, 94, 48, 4, OC); c.rect(10, 94, 46, 3, MHI)       # Dachrandkranz
for bx in range(14, 54, 8): c.px(bx, 96, MDK)              # Randnieten
c.rect(14, 112, 38, 3, BRSD); c.rect(14, 138, 38, 3, BRSD)  # Spannringe
# Wendeltreppe (angedeutet)
for i, sy in enumerate(range(102, 172, 8)):
    sx = 10 + (i % 2) * 3
    c.rect(sx - 3, sy, 6, 2, MMD)
c.rect(6, 100, 2, 74, MLT)
# Tank-Beschriftung (Warnband)
c.rect(18, 124, 30, 5, YEL); c.rect(18, 124, 30, 2, YELL)
# --- Kolonne (rechts) ---
c.cyl(78, 40, 26, 136, BRS, BRSL, BRSD)
c.d.polygon([(77, 41), (105, 41), (91, 26)], fill=BRS, outline=OC)
c.rect(88, 20, 6, 8, MMD); c.rect(88, 20, 2, 8, MLT)
for by in range(56, 172, 16):
    c.rect(78, by, 26, 2, BRSD); c.rect(78, by, 26, 1, BRSL)
# Seitenabzuege zur Tankseite
for by in (72, 104, 136):
    pipe_h(c, 58, by - 1, 21, 5, MMD, MLT, MDK)
# Leiter an der Kolonne
c.rect(106, 46, 1, 128, MLT); c.rect(110, 46, 1, 128, MLT)
for ly in range(48, 174, 7): c.rect(106, ly, 5, 1, MLT)
# --- Kopf-Kondensator + Fackel ganz oben rechts ---
c.rect(112, 34, 12, 26, MMD); c.rect(112, 34, 4, 26, MLT); c.rect(111, 32, 14, 3, OC)
c.disc(118, 26, 6, FLM); c.disc(118, 22, 4, FLML)
# --- Verladung unten: Rohrbruecke + Ventile ---
pipe_h(c, 4, 166, 120, 6, PET, PETL, PETD)
# Ventilraeder sitzen MITTIG auf der Rohrbruecke (vorher schwebten sie ueber dem Tank)
for vx in (24, 62, 100):
    c.rect(vx - 1, 162, 3, 5, MMD)
    c.ring(vx, 160, 3, MHI); c.ring(vx, 160, 4, OC)
# Steuerhaus
c.panel(58, 148, 18, 16, MDK, MMD, OCN)
c.rect(61, 152, 5, 5, BLU); c.rect(61, 152, 5, 2, BLUL)
c.rect(68, 152, 5, 5, BLU); c.rect(68, 152, 5, 2, BLUL)
c.save("mach_raffinerie.png")

print("--- Level-3-Sprites fertig ---")
