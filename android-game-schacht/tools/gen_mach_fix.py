from PIL import Image, ImageDraw
import math
S=64
OUT="/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"
A=255
OUT_C=(24,24,30,A)
MDK=(64,68,78,A); MMD=(104,110,122,A); MLT=(150,158,172,A); MHI=(198,204,216,A)
ORG=(240,120,40,A); ORGD=(200,84,26,A); YEL=(248,206,74,A); YELL=(255,238,160,A)
FIRE0=(120,30,20,A); FIRE1=(230,90,30,A); FIRE2=(250,160,50,A); FIRE3=(255,230,150,A)
BRICK=(96,66,60,A); BRICKD=(74,48,44,A); BRICKL=(120,86,78,A)
def newimg(): return Image.new("RGBA",(S,S),(0,0,0,0))
def rect(im,x,y,w,h,c):
    x,y,w,h=int(x),int(y),int(w),int(h)
    for dy in range(h):
        for dx in range(w):
            px,py=x+dx,y+dy
            if 0<=px<S and 0<=py<S: im.putpixel((px,py),c)
def panel(im,x,y,w,h,base,light,dark,out=OUT_C):
    rect(im,x-1,y-1,w+2,h+2,out); rect(im,x,y,w,h,base)
    rect(im,x,y,w,2,light); rect(im,x,y,2,h,light)
    rect(im,x,y+h-2,w,2,dark); rect(im,x+w-2,y,2,h,dark)
def disc(im,cx,cy,r,c):
    for y in range(int(cy-r),int(cy+r+1)):
        for x in range(int(cx-r),int(cx+r+1)):
            if (x-cx)**2+(y-cy)**2<=r*r and 0<=x<S and 0<=y<S: im.putpixel((x,y),c)
def ring(im,cx,cy,r,c,wdt=1):
    for a in range(0,360,4):
        for k in range(wdt):
            x=int(cx+(r-k)*math.cos(math.radians(a))); y=int(cy+(r-k)*math.sin(math.radians(a)))
            if 0<=x<S and 0<=y<S: im.putpixel((x,y),c)
def bolts(im,x,y,w,h,c=MHI):
    for (bx,by) in [(x+2,y+2),(x+w-3,y+2),(x+2,y+h-3),(x+w-3,y+h-3)]: im.putpixel((bx,by),c)

# ---------------- OFEN ----------------
im=newimg(); d=ImageDraw.Draw(im)
# Schornstein + Rauch
rect(im,43,2,10,14,OUT_C); rect(im,44,3,8,13,MDK); rect(im,44,3,8,2,MMD)
for (sx,sy,r,al) in [(49,3,3,90),(53,0,3,70),(46,0,2,60)]:
    disc(im,sx,sy,r,(180,180,186,al))
# Gehaeuse (Metall) + Ziegel-Innenfeld
panel(im,10,14,44,42,MDK,MMD,(30,30,36,A))
rect(im,14,18,36,34,BRICK)
for by in range(18,52,5):                    # Ziegel-Reihen
    rect(im,14,by,36,1,BRICKD)
for bx in range(14,50,10):
    rect(im,bx,18,1,34,BRICKD); rect(im,bx+5,20,1,34,BRICKD)
rect(im,14,18,36,2,BRICKL)
# Metallrahmen um die Feuertuer
panel(im,19,26,26,24,MDK,MLT,(30,30,36,A))
rect(im,22,29,20,18,(18,14,12,A))            # dunkler Ofenraum
# Flammen (Polygone, mehrschichtig)
d.polygon([(23,47),(28,33),(31,41),(34,31),(37,40),(41,47)], fill=FIRE1)
d.polygon([(26,47),(30,36),(32,42),(35,35),(38,47)], fill=FIRE2)
d.polygon([(29,47),(32,39),(35,47)], fill=FIRE3)
rect(im,22,46,20,1,ORGD)
# Rost-Streben vor dem Feuer
for gx in range(24,42,4): rect(im,gx,30,1,17,(12,10,9,A))
# Details
bolts(im,10,14,44,42)
rect(im,52,30,3,10,MMD); rect(im,53,31,1,8,YEL)   # Temperatur-Anzeige
rect(im,8,54,6,6,OUT_C); rect(im,50,54,6,6,OUT_C) # Fuesse
im.save(OUT+"/mach_ofen.png"); print("ofen ok")

# ---------------- GENERATOR ----------------
im=newimg(); d=ImageDraw.Draw(im)
panel(im,10,12,44,40,(52,56,66,A),MMD,(28,30,36,A))
for vy in range(16,26,3):                     # Kuehlschlitze oben
    rect(im,16,vy,32,2,(30,32,40,A)); rect(im,16,vy,32,1,(20,22,28,A))
# Emblem-Platte
disc(im,32,38,14,OUT_C); disc(im,32,38,13,(40,42,50,A)); ring(im,32,38,13,MMD,2)
# Blitz (sauber, mit Umriss + Glanz)
bolt=[(37,26),(27,39),(33,39),(25,52),(41,35),(34,35)]
d.polygon(bolt, fill=YEL)
d.line(bolt+[bolt[0]], fill=OUT_C, width=2)
d.line([(36,28),(30,37)], fill=YELL, width=2)
# Terminals + Kabel unten
rect(im,10,48,5,5,MHI); rect(im,49,48,5,5,MHI)
d.line([(13,53),(18,58),(24,56)], fill=(30,30,36,A), width=2)
d.line([(51,53),(46,58),(40,56)], fill=(30,30,36,A), width=2)
# Status-LED
disc(im,47,17,2,(90,240,130,A)); im.putpixel((46,16),(220,255,220,A))
bolts(im,10,12,44,40)
im.save(OUT+"/mach_generator.png"); print("generator ok")
