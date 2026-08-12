from PIL import Image, ImageDraw
import math
S=192; OUT="/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"; A=255
OC=(22,22,28,A)
CON=(150,156,168,A); CONL=(196,202,214,A); COND=(96,102,116,A)   # Beton
DOME=(120,128,142,A); DOMEL=(170,178,192,A); DOMED=(80,88,102,A)
GLOWC=(90,220,224,A); GLOWY=(250,236,150,A); PUR=(158,116,226,A)
TOW=(180,186,198,A); TOWL=(214,220,230,A); TOWD=(120,126,140,A)
BASE=(70,72,82,A); BASE2=(52,54,62,A)
PIPE=(120,126,140,A); PIPED=(80,86,100,A)
def rect(im,x,y,w,h,c):
    x,y,w,h=int(round(x)),int(round(y)),int(round(w)),int(round(h))
    for dy in range(h):
        for dx in range(w):
            if 0<=x+dx<S and 0<=y+dy<S: im.putpixel((x+dx,y+dy),c)
def disc(im,cx,cy,r,c):
    for y in range(int(cy-r),int(cy+r+1)):
        for x in range(int(cx-r),int(cx+r+1)):
            if (x-cx)**2+(y-cy)**2<=r*r and 0<=x<S and 0<=y<S: im.putpixel((x,y),c)
im=Image.new("RGBA",(S,S),(0,0,0,0)); d=ImageDraw.Draw(im)
# Beton-Plattform unten
rect(im,8,150,176,32,BASE2); rect(im,8,150,176,4,BASE); 
for gx in range(8,184,16): rect(im,gx,150,1,32,BASE2)
# ---- Reaktor-Containment (links) ----
cx=64
rect(im,26,74,76,86,OC)
rect(im,28,76,72,82,CON)
rect(im,28,76,4,82,CONL); rect(im,96,76,4,82,COND)
# Kuppel
for yy in range(40,78):
    t=(78-yy)/38.0
    hw=int(38*math.sqrt(max(0,1-(1-t)**2)))
    rect(im,cx-hw,yy,hw*2,1,DOME)
    rect(im,cx-hw,yy,max(1,hw//3),1,DOMEL); rect(im,cx+hw-hw//3,yy,max(1,hw//3),1,DOMED)
rect(im,26,74,76,3,OC)
# Kern-Fenster mit Glut
rect(im,44,96,40,44,OC); rect(im,47,99,34,38,(30,30,38,A))
disc(im,64,118,15,PUR); disc(im,64,118,10,GLOWC); disc(im,64,118,5,GLOWY)
for a in range(0,360,45):
    x=64+int(13*math.cos(math.radians(a))); y=118+int(13*math.sin(math.radians(a))); im.putpixel((x,y),GLOWY)
# Warnstreifen
for i in range(30,98,10): rect(im,i,142,5,4,GLOWY); rect(im,i+5,142,5,4,OC)
# ---- Kuehlturm (rechts, Hyperboloid) ----
tcx=142
for yy in range(46,158):
    t=(yy-46)/(158-46)
    waist=0.5
    # Radius: breit oben & unten, schmal in der Mitte
    r=26 - 10*math.sin(math.pi*t) if False else 16 + 12*abs(t-waist)*2*0.9
    r=int(r)
    rect(im,tcx-r,yy,r*2,1,TOW)
    rect(im,tcx-r,yy,max(1,r//3),1,TOWL); rect(im,tcx+r-r//3,yy,max(1,r//3),1,TOWD)
rect(im,tcx-28,44,56,3,OC)   # oberer Rand
rect(im,tcx-27,46,54,3,(40,44,52,A))  # dunkler Innenrand oben
# Verbindungsrohre Reaktor <-> Turm
rect(im,100,110,14,6,PIPE); rect(im,100,110,14,2,TOWL)
rect(im,100,126,14,6,PIPE); rect(im,100,126,14,2,TOWL)
# Wasser-/Auslass-Rohrstutzen unten (fuer den Schlauch)
rect(im,84,158,24,10,OC); rect(im,86,160,20,6,PIPE); rect(im,86,160,20,2,TOWL)
disc(im,96,164,3,(60,180,200,A))
im.save(OUT+"/mach_reaktor.png"); print("reaktor ok")
sp="/tmp/claude-0/-home-user-physik/d83acb4c-7a2c-5dd2-aa53-ab7b5d062310/scratchpad"
gr=Image.open(OUT+"/tile_grass1.png").convert("RGBA").resize((192,192),Image.NEAREST)
sh=Image.new("RGBA",(192,192),(40,40,46,255)); sh.paste(gr,(0,0)); sh.alpha_composite(im,(0,0))
sh.save(sp+"/reaktor.png")
