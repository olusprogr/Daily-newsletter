from PIL import Image
import os, math
S=64
OUT="/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"
A=255
OUT_C=(24,24,30,A)
# Metall
MDK=(64,68,78,A); MMD=(104,110,122,A); MLT=(150,158,172,A); MHI=(198,204,216,A)
# Braun/Rost
BR=(126,84,46,A); BRD=(92,60,32,A); BRL=(156,110,64,A)
# Akzente
ORG=(240,120,40,A); ORGD=(200,84,26,A); YEL=(248,206,74,A); YELL=(255,236,150,A)
CY=(72,210,214,A); CYL=(160,244,244,A); CYD=(40,150,160,A)
PUR=(158,116,226,A); PURL=(200,170,246,A); PURD=(110,72,180,A)
RED=(214,74,66,A); REDL=(240,150,140,A); WHT=(238,240,246,A)
WOOD=(150,104,58,A); WOODD=(112,74,38,A)

def newimg(): return Image.new("RGBA",(S,S),(0,0,0,0))
def rect(im,x,y,w,h,c):
    x,y,w,h=int(x),int(y),int(w),int(h)
    for dy in range(h):
        for dx in range(w):
            px,py=x+dx,y+dy
            if 0<=px<S and 0<=py<S: im.putpixel((px,py),c)
def panel(im,x,y,w,h,base,light,dark,out=OUT_C):
    rect(im,x-1,y-1,w+2,h+2,out)
    rect(im,x,y,w,h,base)
    rect(im,x,y,w,2,light); rect(im,x,y,2,h,light)
    rect(im,x,y+h-2,w,2,dark); rect(im,x+w-2,y,2,h,dark)
def disc(im,cx,cy,r,c):
    for y in range(int(cy-r),int(cy+r+1)):
        for x in range(int(cx-r),int(cx+r+1)):
            if (x-cx)**2+(y-cy)**2<=r*r and 0<=x<S and 0<=y<S: im.putpixel((x,y),c)
def ring(im,cx,cy,r,c):
    for a in range(0,360,6):
        x=int(cx+r*math.cos(math.radians(a))); y=int(cy+r*math.sin(math.radians(a)))
        if 0<=x<S and 0<=y<S: im.putpixel((x,y),c)
def bolts(im,x,y,w,h,c=MHI):
    for (bx,by) in [(x+2,y+2),(x+w-3,y+2),(x+2,y+h-3),(x+w-3,y+h-3)]:
        im.putpixel((bx,by),c)
def save(im,name): im.save(OUT+"/"+name); print("wrote",name)

# ---------- BOHRER ----------
im=newimg()
panel(im,20,6,24,10,MDK,MMD,BRD)            # Motor oben
rect(im,24,9,16,3,ORG)                       # Warnstreifen
panel(im,14,14,36,22,MMD,MLT,MDK)           # Koerper
rect(im,18,20,28,4,ORG); rect(im,18,20,28,1,YEL)
bolts(im,14,14,36,22)
# Bohrer-Halterung
for i,yy in enumerate(range(36,42)):
    rect(im,24-i,yy,16+2*i,1,MDK)
# Bohrer-Spirale
for i,yy in enumerate(range(42,60)):
    w=max(2,14-int(i*0.7)); x=32-w//2
    rect(im,x,yy,w,1,MLT)
    if i%3==0: rect(im,x,yy,max(1,w//2),1,MHI)
    if i%3==1: rect(im,x+w//2,yy,max(1,w//2),1,MDK)
rect(im,30,58,4,3,OUT_C)
save(im,"mach_bohrer.png")

# ---------- OFEN ----------
im=newimg()
rect(im,44,2,8,14,OUT_C); rect(im,45,3,6,13,MDK); rect(im,45,3,6,2,MMD)  # Schornstein
panel(im,10,14,44,42,MDK,MMD,(30,30,36,A))  # Gehaeuse
# Feuerraum (gluehend)
disc(im,32,36,15,OUT_C); disc(im,32,36,13,BRD); disc(im,32,37,12,ORGD)
disc(im,32,38,9,ORG); disc(im,32,39,6,YEL); disc(im,32,40,3,YELL)
# Rost-Streben
for gx in range(22,44,5): rect(im,gx,30,1,16,(20,16,12,A))
bolts(im,10,14,44,42)
rect(im,8,54,6,6,OUT_C); rect(im,50,54,6,6,OUT_C)  # Fuesse
save(im,"mach_ofen.png")

# ---------- PRESSE ----------
im=newimg()
rect(im,12,10,6,46,OUT_C); rect(im,46,10,6,46,OUT_C)  # Saeulen
rect(im,13,10,4,46,MMD); rect(im,47,10,4,46,MMD)
rect(im,13,10,2,46,MLT); rect(im,47,10,2,46,MLT)
panel(im,8,4,48,10,MDK,MMD,(30,30,36,A))    # Kopfbalken
rect(im,26,14,12,4,MHI)                       # Kolbenstange
panel(im,20,18,24,14,MMD,MLT,MDK)           # Presskopf
rect(im,24,20,16,2,YEL)
panel(im,10,44,44,12,MDK,MMD,(30,30,36,A))  # Basis
rect(im,22,40,20,4,CY); rect(im,22,40,20,1,CYL)   # Platte (Output)
bolts(im,10,44,44,12)
save(im,"mach_presse.png")

# ---------- ASSEMBLER ----------
im=newimg()
panel(im,10,16,44,40,MDK,MMD,(30,30,36,A))
# Roboterarm
rect(im,30,4,4,10,MMD); rect(im,30,4,2,10,MLT)
rect(im,26,12,14,4,MDK); rect(im,38,14,3,10,MMD)
# Kammer
panel(im,20,26,24,20,(40,44,52,A),MMD,(24,26,32,A))
# Cyan-Komponente (Plus/Zahnrad)
disc(im,32,36,7,CYD); disc(im,32,36,5,CY)
rect(im,31,30,2,12,CYL); rect(im,26,35,12,2,CYL)
# LEDs
for lx in (14,50): rect(im,lx,20,2,2,YEL)
bolts(im,10,16,44,40)
save(im,"mach_assembler.png")

# ---------- GENERATOR ----------
im=newimg()
panel(im,12,12,40,40,(46,50,58,A),MMD,(28,30,36,A))
for vy in range(18,48,5): rect(im,17,vy,30,1,(26,28,34,A))
# Blitz
pts=[(34,16),(28,32),(33,32),(30,48),(40,28),(35,28)]
# grober Blitz
rect(im,32,16,3,8,YEL); rect(im,29,24,6,3,YEL); rect(im,31,26,4,10,YEL)
rect(im,31,34,7,3,YEL); rect(im,30,36,4,10,YEL)
rect(im,33,16,1,20,YELL)
# Terminals
rect(im,8,22,4,4,MHI); rect(im,52,22,4,4,MHI)
rect(im,8,34,4,4,MHI); rect(im,52,34,4,4,MHI)
bolts(im,12,12,40,40)
save(im,"mach_generator.png")

# ---------- LAGER ----------
im=newimg()
panel(im,10,12,44,44,BR,BRL,BRD)
rect(im,10,12,44,3,MMD); rect(im,10,53,44,3,MMD)
rect(im,10,12,3,44,MMD); rect(im,51,12,3,44,MMD)
# Kreuzverband
for i in range(0,40,2):
    x=13+i; 
    if 12<x<52: im.putpixel((x,15+i),MLT)
    x2=51-i
    if 12<x2<52: im.putpixel((x2,15+i),MLT)
rect(im,13,15,38,1,BRD)
bolts(im,10,12,44,44,MHI)
save(im,"mach_lager.png")

# ---------- DROHNE ----------
im=newimg()
# Rotorarme
rect(im,8,28,48,3,MDK); rect(im,30,10,3,44,MDK)
for (cx,cy) in [(10,29),(54,29),(31,12),(31,52)]:
    disc(im,cx,cy,5,(60,64,74,120)); disc(im,cx,cy,2,MHI)
# Koerper
panel(im,22,26,20,14,(50,54,64,A),MMD,(30,32,40,A))
disc(im,32,33,4,CYD); disc(im,32,33,3,CY); im.putpixel((31,32),CYL)
rect(im,26,42,2,4,MMD); rect(im,36,42,2,4,MMD)  # Skids
rect(im,22,46,6,2,MDK); rect(im,36,46,6,2,MDK)
rect(im,31,6,2,5,MMD); im.putpixel((31,6),YEL)  # Antenne
save(im,"mach_drohne.png")

# ---------- VERSTAERKER ----------
im=newimg()
panel(im,14,44,36,12,MDK,MMD,(28,30,36,A))    # Basis
panel(im,24,16,16,30,(54,50,74,A),PURL,PURD)  # Turm
for ry in range(20,44,5): rect(im,24,ry,16,1,PURD)
disc(im,32,30,5,PURD); disc(im,32,30,3,CY); im.putpixel((31,29),CYL)
# Pfeil hoch
rect(im,31,6,2,8,YEL); rect(im,28,9,8,2,YEL)
rect(im,29,8,2,2,YEL); rect(im,34,8,2,2,YEL)
rect(im,30,6,4,2,YELL)
save(im,"mach_verstaerker.png")

# ---------- REAKTOR ----------
im=newimg()
panel(im,10,10,44,44,(40,36,54,A),PURL,(26,22,38,A))
for fy in range(16,50,6):    # Kuehlrippen
    rect(im,6,fy,4,3,MMD); rect(im,54,fy,4,3,MMD)
disc(im,32,32,16,OUT_C); disc(im,32,32,14,PURD); disc(im,32,32,11,PUR)
disc(im,32,32,8,CYD); disc(im,32,32,6,CY); disc(im,32,32,3,YEL); disc(im,32,32,2,YELL)
ring(im,32,32,13,PURL)
bolts(im,10,10,44,44)
save(im,"mach_reaktor.png")

# ---------- HAENDLER ----------
im=newimg()
# Markise
for i in range(6,58,8):
    rect(im,i,6,4,8,RED); rect(im,i+4,6,4,8,WHT)
rect(im,6,4,52,2,OUT_C)
rect(im,8,14,2,30,WOODD); rect(im,54,14,2,30,WOODD)  # Pfosten
panel(im,10,40,44,16,WOOD,BRL,WOODD)                 # Theke
# Muenze
disc(im,32,26,9,OUT_C); disc(im,32,26,8,YEL); disc(im,32,26,6,YELL)
rect(im,31,21,2,10,ORGD); rect(im,29,24,6,2,ORGD); rect(im,29,28,6,2,ORGD)
save(im,"mach_haendler.png")

# ---------- PROSPEKTOR ----------
im=newimg()
# Stativ
rect(im,30,26,4,20,MDK)
rect(im,18,52,10,3,OUT_C); rect(im,36,52,10,3,OUT_C)
rect(im,31,44,2,10,MMD); 
for i in range(10):  # Beine
    im.putpixel((30-i,46+i),MDK); im.putpixel((34+i,46+i),MDK)
# Schuessel (Radar, gekippt)
for i,yy in enumerate(range(10,20)):
    w=24-i*2; x=32-w//2+ i
    rect(im,x,yy,w,1,MMD)
    rect(im,x,yy,max(1,w//3),1,MLT)
rect(im,20,10,24,2,MHI)
disc(im,44,10,3,CY); im.putpixel((44,10),CYL)   # Sender
# Pulswellen
ring(im,44,10,7,CYD); 
save(im,"mach_prospektor.png")
print("machines done")
