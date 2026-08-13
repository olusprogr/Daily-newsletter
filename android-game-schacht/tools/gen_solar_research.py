from PIL import Image, ImageDraw
import math
OUT="/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"
SP="/tmp/claude-0/-home-user-physik/d83acb4c-7a2c-5dd2-aa53-ab7b5d062310/scratchpad"
A=255
def C(r,g,b,a=A): return (r,g,b,a)
OC=C(20,22,28)

# ============ SOLARPANEL (64x64) ============
S=64
im=Image.new("RGBA",(S,S),(0,0,0,0)); d=ImageDraw.Draw(im)
def rect(x,y,w,h,c):
    d.rectangle([x,y,x+w-1,y+h-1],fill=c)
FRAME=C(176,182,196); FRAMED=C(110,116,130); FRAMEL=C(214,220,232)
CELL=C(28,58,110); CELLL=C(58,104,176); CELLD=C(18,38,78)
# Schatten am Boden
d.ellipse([14,50,50,60],fill=C(0,0,0,60))
# Stuetzpfosten
rect(28,42,8,14,C(70,76,90)); rect(28,42,3,14,C(100,106,120)); rect(24,55,16,3,C(54,58,70))
# Panel-Rahmen (leicht 3D: obere Kante heller)
rect(6,10,52,34,OC)
rect(7,11,50,32,FRAME)
rect(7,11,50,2,FRAMEL)          # oberer Glanz
rect(7,41,50,2,FRAMED)
# Zellen (4 x 3) mit Rasterlinien
cw=(50-2)//4; ch=(32-2)//3
for r in range(3):
    for c in range(4):
        x=9+c*cw; y=13+r*ch
        rect(x,y,cw-1,ch-1,CELL)
        rect(x,y,cw-1,1,CELLL)          # Zellen-Glanz oben
        rect(x,y+ch-2,cw-1,1,CELLD)
# diagonaler Licht-Glint
d.line([(12,15),(24,13)],fill=C(150,190,240,200),width=2)
d.line([(30,40),(52,16)],fill=C(120,160,220,120),width=1)
im.save(OUT+"/mach_solar.png"); print("solar ok")

# ============ FORSCHUNGSZENTRUM (128x64, 2x1) ============
W2=128; H2=64
rm=Image.new("RGBA",(W2,H2),(0,0,0,0)); dd=ImageDraw.Draw(rm)
def r2(x,y,w,h,c): dd.rectangle([x,y,x+w-1,y+h-1],fill=c)
CN=C(150,156,168); CNL=C(198,204,216); CND=C(104,110,124)
GLASS=C(96,196,214); GLASSD=C(40,120,150); GLASSL=C(180,240,248)
PUR=C(150,120,224); PURL=C(196,170,248)
# Duenner Steinsockel unten
r2(2,54,124,9,C(52,56,66)); r2(2,54,124,2,C(78,82,94))
for gx in range(4,124,14): r2(gx,60,7,3,C(230,196,70)); r2(gx+7,60,7,3,OC)  # Hazard
# Hoeheres, langes Laborgebaeude
r2(6,16,110,40,OC); r2(8,18,106,36,CN); r2(8,18,106,3,CNL); r2(8,51,106,3,CND)
for rx in range(20,114,16): r2(rx,19,1,34,CND)          # vertikale Rippen
# Fensterreihe (oben)
for wx in range(12,110,15):
    r2(wx,24,9,12,GLASSD); r2(wx,24,9,4,GLASS); r2(wx,24,9,1,GLASSL)
# Lueftungsband + Tuer
r2(10,40,104,2,CND)
r2(58,40,12,14,OC); r2(59,41,10,13,C(60,64,74)); r2(63,46,2,2,C(150,156,168))
# Satellitenschuessel links auf dem Dach
dd.ellipse([12,3,28,15],fill=C(210,214,224)); dd.ellipse([15,5,25,13],fill=C(150,156,168))
dd.line([(20,9),(28,1)],fill=C(90,96,110),width=2); dd.ellipse([26,0,31,5],fill=C(230,120,90))
# Kleine Glaskuppel mittig-rechts auf dem Dach + kleines Atom
cx=90; base=16; R=11
r2(cx-R,base-1,2*R,3,C(70,74,86))                       # Kuppelsockel
dd.pieslice([cx-R,base-R,cx+R,base+R],180,360,fill=GLASSD)
dd.pieslice([cx-R,base-R,cx+R,base+R],180,360,outline=GLASS)
ay=base-5                                               # Atom-Zentrum in der Kuppel
for ang in (0,60,120):                                  # 3 gedrehte Ellipsen (klein)
    layer=Image.new("RGBA",(W2,H2),(0,0,0,0)); ld=ImageDraw.Draw(layer)
    ld.ellipse([cx-8,ay-3,cx+8,ay+3],outline=PURL,width=1)
    layer=layer.rotate(ang,center=(cx,ay))
    rm.alpha_composite(layer)
dd.ellipse([cx-2,ay-2,cx+2,ay+2],fill=PUR); dd.ellipse([cx-1,ay-2,cx,ay-1],fill=PURL)
# Antenne mit Blinklicht (rechtes Dachende)
r2(110,7,2,10,C(120,126,140)); dd.ellipse([108,3,113,8],fill=C(120,240,170))
rm.save(OUT+"/mach_research.png"); print("research ok")

# ---- Vorschau ----
gr=Image.open(OUT+"/tile_grass1.png").convert("RGBA")
def onGrass(img,wcells,hcells):
    cv=Image.new("RGBA",(wcells*64,hcells*64),(0,0,0,255))
    for r in range(hcells):
        for c in range(wcells): cv.alpha_composite(gr.resize((64,64),Image.NEAREST),(c*64,r*64))
    cv.alpha_composite(img,(0,0)); return cv.resize((wcells*192,hcells*192),Image.NEAREST)
prev=Image.new("RGBA",(3*192,192),(0,0,0,255))
prev.alpha_composite(onGrass(im,1,1),(0,0))
prev.alpha_composite(onGrass(rm,2,1),(192,0))
prev.save(SP+"/solar_research.png"); print("preview ok")
