from PIL import Image, ImageDraw
import random, math
S=64; OUT="/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"; A=255
# ---- helper (grass) ----
Wg=Hg=32
def gnew(base): return Image.new("RGBA",(Wg,Hg),base+(255,))
def gpx(im,x,y,c): im.putpixel((x%Wg,y%Hg),c+(255,))
def grect(im,x,y,w,h,c):
    for dy in range(h):
        for dx in range(w): gpx(im,x+dx,y+dy,c)
def gnoise(im,amt,rng):
    p=im.load()
    for y in range(Hg):
        for x in range(Wg):
            r,g,b,a=p[x,y]; d=rng.randint(-amt,amt)
            p[x,y]=(max(0,min(255,r+d)),max(0,min(255,g+d)),max(0,min(255,b+d)),255)
def grass(name,base,dark,light,blade,seed=1):
    rng=random.Random(seed); im=gnew(base); gnoise(im,6,rng)
    for _ in range(7):
        x,y=rng.randrange(Wg),rng.randrange(Hg); grect(im,x,y,rng.choice([2,3]),rng.choice([1,2]),dark)
    for _ in range(6):
        x,y=rng.randrange(Wg),rng.randrange(Hg); grect(im,x,y,1,1,light)
    for _ in range(14):
        x,y=rng.randrange(Wg),rng.randrange(Hg); grect(im,x,y,1,rng.choice([2,3]),blade)
    im.save(OUT+"/"+name); print("wrote",name)
# ungescannt: heller (vorher 116,132,100)
grass("tile_grass_u.png",(150,166,128),(128,144,108),(176,192,152),(120,138,102),seed=11)

# ---- Generator neu (Blitz voll sichtbar, kein schwarzer Fleck) ----
OUT_C=(24,24,30,A); MDK=(64,68,78,A); MMD=(104,110,122,A); MLT=(150,158,172,A); MHI=(198,204,216,A)
YEL=(248,206,74,A); YELL=(255,238,160,A)
def rect(im,x,y,w,h,c):
    x,y,w,h=int(x),int(y),int(w),int(h)
    for dy in range(h):
        for dx in range(w):
            if 0<=x+dx<S and 0<=y+dy<S: im.putpixel((x+dx,y+dy),c)
def panel(im,x,y,w,h,base,light,dark,out=OUT_C):
    rect(im,x-1,y-1,w+2,h+2,out); rect(im,x,y,w,h,base)
    rect(im,x,y,w,2,light); rect(im,x,y,2,h,light); rect(im,x,y+h-2,w,2,dark); rect(im,x+w-2,y,2,h,dark)
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
im=Image.new("RGBA",(S,S),(0,0,0,0)); d=ImageDraw.Draw(im)
panel(im,10,12,44,40,(52,56,66,A),MMD,(28,30,36,A))
for vy in range(15,23,3):
    rect(im,16,vy,32,2,(30,32,40,A)); rect(im,16,vy,32,1,(20,22,28,A))
# Emblem-Platte, innen hell genug -> Blitz voll sichtbar
disc(im,32,37,14,OUT_C); disc(im,32,37,13,(58,62,72,A)); disc(im,32,37,11,(74,78,90,A)); ring(im,32,37,13,MMD,2)
# Blitz komplett innerhalb der hellen Platte
bolt=[(36,28),(28,37),(33,37),(28,46),(39,36),(33,36)]
d.polygon(bolt, fill=YEL)
d.line(bolt+[bolt[0]], fill=(70,52,10,A), width=1)   # duenner Umriss, kein Klecks
d.line([(35,30),(30,37)], fill=YELL, width=2)         # Glanz
rect(im,10,49,5,4,MHI); rect(im,49,49,5,4,MHI)        # Terminals
d.line([(13,53),(18,57),(24,55)], fill=(30,30,36,A), width=2)
d.line([(51,53),(46,57),(40,55)], fill=(30,30,36,A), width=2)
disc(im,47,16,2,(90,240,130,A)); im.putpixel((46,15),(220,255,220,A))
bolts(im,10,12,44,40)
im.save(OUT+"/mach_generator.png"); print("generator ok")
