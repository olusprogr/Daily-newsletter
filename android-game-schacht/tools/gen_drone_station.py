from PIL import Image
import math
S=64; OUT="/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"
SP="/tmp/claude-0/-home-user-physik/d83acb4c-7a2c-5dd2-aa53-ab7b5d062310/scratchpad"
A=255
def C(r,g,b,a=A): return (r,g,b,a)
OC=C(22,24,30)
MET=C(96,104,120); METL=C(140,148,166); METD=C(62,68,82)
PAD=C(48,54,66); PADL=C(70,78,94)
YEL=C(240,200,70); YELD=C(180,150,50)
CY=C(96,206,232); CYL=C(190,244,250)
GRN=C(120,220,150)
im=Image.new("RGBA",(S,S),(0,0,0,0))
def rect(x,y,w,h,c):
    x,y,w,h=int(round(x)),int(round(y)),int(round(w)),int(round(h))
    for dy in range(h):
        for dx in range(w):
            if 0<=x+dx<S and 0<=y+dy<S: im.putpixel((x+dx,y+dy),c)
def disc(cx,cy,r,c):
    for y in range(int(cy-r),int(cy+r+1)):
        for x in range(int(cx-r),int(cx+r+1)):
            if (x-cx)**2+(y-cy)**2<=r*r and 0<=x<S and 0<=y<S: im.putpixel((x,y),c)
def ring(cx,cy,r,c,w=1):
    for a in range(0,360,3):
        for k in range(w):
            x=int(cx+(r-k)*math.cos(math.radians(a))); y=int(cy+(r-k)*math.sin(math.radians(a)))
            if 0<=x<S and 0<=y<S: im.putpixel((x,y),c)

# ---- Metall-Sockel (perspektivische Platte) ----
rect(6,12,52,46,OC)
rect(8,14,48,42,MET)
rect(8,14,48,3,METL)          # oberer Glanz
rect(8,52,48,4,METD)          # unterer Schatten
# Nietenreihen
for gx in range(12,54,8):
    im.putpixel((gx,17),METD); im.putpixel((gx,53),METL)

# ---- Landeplattform (dunkler Kreis mit Markierung) ----
disc(32,34,20,OC)
disc(32,34,19,PAD)
ring(32,34,19,METD,1)
ring(32,34,13,YELD,1)
# Rotoren-Kreuz-Markierung + "H"
rect(31,20,2,28,PADL)         # senkrechte Markierung
rect(18,33,28,2,PADL)         # waagerechte
# gelbes H (Helipad)
rect(26,27,3,14,YEL); rect(35,27,3,14,YEL); rect(29,32,6,3,YEL)
# Landelichter (4 Ecken der Plattform)
for (lx,ly) in [(15,21),(49,21),(15,47),(49,47)]:
    disc(lx,ly,2,OC); disc(lx,ly,1,YEL)

# ---- Ladesaeule / Antenne oben links ----
rect(10,8,4,10,OC); rect(11,8,2,10,MET); rect(11,8,2,2,METL)
disc(12,6,2,OC); disc(12,6,1,CY)          # Signal-Blinklicht
# kleine Antenne oben rechts
rect(52,6,2,10,METD); disc(53,5,1,GRN)

# ---- Ladekontakt-Symbol in der Mitte (dezent) ----
disc(32,34,4,OC); disc(32,34,3,METD)
rect(31,31,2,6,CY); rect(30,33,4,2,CY)    # kleiner "Blitz"/Kontakt
im.putpixel((32,34),CYL)

im.save(OUT+"/mach_drohne.png"); print("drone station ok")
# Vorschau auf Gras
gr=Image.open(OUT+"/tile_grass1.png").convert("RGBA").resize((64,64),Image.NEAREST)
sh=gr.copy(); sh.alpha_composite(im,(0,0)); sh=sh.resize((256,256),Image.NEAREST)
sh.save(SP+"/drone_station.png")
