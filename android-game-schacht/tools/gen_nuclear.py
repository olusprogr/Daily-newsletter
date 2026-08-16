from PIL import Image, ImageDraw
import math
S=64
OUT="/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"
SP="/tmp/claude-0/-home-user-physik/d83acb4c-7a2c-5dd2-aa53-ab7b5d062310/scratchpad"
A=255
OC=(24,24,30,A)
MDK=(64,68,78,A); MMD=(104,110,122,A); MLT=(150,158,172,A); MHI=(198,204,216,A)
GRY=(158,164,176,A); GRYD=(112,118,130,A)
YEL=(240,200,70,A); YELL=(255,236,150,A); ORG=(238,120,52,A)
CY=(96,206,232,A); CYL=(190,244,250,A); CYD=(46,140,160,A)
PB=(120,126,146,A); PBD=(80,84,100,A); PBL=(160,166,184,A)   # Blei-Farbe (blaugrau)
BLU=(70,150,214,A); BLUL=(150,210,246,A); BLUD=(40,96,150,A) # Wasser
PUR=(168,132,232,A); PURL=(210,180,246,A)

def newimg(): return Image.new("RGBA",(S,S),(0,0,0,0))
def rect(im,x,y,w,h,c):
    x,y,w,h=int(x),int(y),int(w),int(h)
    for dy in range(h):
        for dx in range(w):
            px,py=x+dx,y+dy
            if 0<=px<S and 0<=py<S: im.putpixel((px,py),c)
def panel(im,x,y,w,h,base,light,dark,out=OC):
    rect(im,x-1,y-1,w+2,h+2,out)
    rect(im,x,y,w,h,base)
    rect(im,x,y,w,2,light); rect(im,x,y,2,h,light)
    rect(im,x,y+h-2,w,2,dark); rect(im,x+w-2,y,2,h,dark)
def disc(im,cx,cy,r,c):
    for y in range(int(cy-r),int(cy+r+1)):
        for x in range(int(cx-r),int(cx+r+1)):
            if (x-cx)**2+(y-cy)**2<=r*r and 0<=x<S and 0<=y<S: im.putpixel((x,y),c)
def ring(im,cx,cy,r,c):
    for a in range(0,360,4):
        x=int(cx+r*math.cos(math.radians(a))); y=int(cy+r*math.sin(math.radians(a)))
        if 0<=x<S and 0<=y<S: im.putpixel((x,y),c)
def bolts(im,x,y,w,h,c=MHI):
    for (bx,by) in [(x+2,y+2),(x+w-3,y+2),(x+2,y+h-3),(x+w-3,y+h-3)]:
        if 0<=bx<S and 0<=by<S: im.putpixel((bx,by),c)
def save(im,name):
    im.save(OUT+"/"+name); print("wrote",name)

# ============ TIEFEN-BOHRER (Blei) ============
im=newimg()
panel(im,22,6,20,10,MDK,MMD,PBD)
disc(im,32,4,4,PBL)
rect(im,29,20,6,10,MMD)
tip=[(32,58),(23,32),(41,32)]
im2=ImageDraw.Draw(im)
im2.polygon(tip,fill=PB)
im2.polygon([(32,58),(27,36),(37,36)],fill=PBL)
for i,yy in enumerate(range(34,54,4)):
    off=3 if i%2==0 else -3
    im2.line([(32-6+off,yy),(32+6+off,yy+2)],fill=PBD,width=2)
# Blei-Streifen-Markierung (grau-blau statt Feuer)
rect(im,26,44,12,3,MHI)
save(im,"mach_bleibohrer.png")

# ============ WASSERPUMPE ============
im=newimg()
panel(im,16,30,32,20,MDK,MMD,BLUD)   # Pumpengehaeuse
disc(im,32,30,10,MMD); disc(im,32,30,7,BLU); disc(im,32,30,4,BLUL)
rect(im,30,10,4,20,MMD)              # Steigrohr
panel(im,24,4,16,8,MDK,MHI,MMD)      # Kopfstueck
# Wassertropfen-Symbol
im2=ImageDraw.Draw(im)
im2.polygon([(32,14),(37,22),(32,26),(27,22)],fill=BLUL)
# Ausgabeschlauch unten rechts + Wellen
rect(im,44,40,10,6,MMD)
for i,yy in enumerate(range(44,54,3)):
    rect(im,46+ (i%2)*2,yy,6,1,BLU)
bolts(im,16,30,32,20)
save(im,"mach_wasserpumpe.png")

# ============ ZENTRIFUGE ============
im=newimg()
panel(im,14,14,36,36,(40,44,54,A),MHI,MMD)   # Aussenring quadratisch
disc(im,32,32,17,OC); disc(im,32,32,15,MMD); disc(im,32,32,15,None) if False else None
ring(im,32,32,15,MHI)
ring(im,32,32,11,PBL)
disc(im,32,32,6,CYD); disc(im,32,32,4,CY); im.putpixel((30,30),CYL)
# Rotor-Speichen (angedeutete Rotation)
im2=ImageDraw.Draw(im)
for ang in (20,110,200,290):
    x=32+14*math.cos(math.radians(ang)); y=32+14*math.sin(math.radians(ang))
    im2.line([(32,32),(x,y)],fill=MHI,width=2)
bolts(im,14,14,36,36)
save(im,"mach_zentrifuge.png")

# ============ BLEIPRESSE ============
im=newimg()
panel(im,12,38,40,16,MDK,MMD,PBD)   # Basis
panel(im,24,10,16,30,(52,56,66,A),MHI,MMD)  # Presskolben-Tuerme
rect(im,20,26,24,8,PBD); rect(im,20,26,24,2,PBL)  # Presskammer
disc(im,32,8,4,YEL); rect(im,30,8,4,20,MMD)  # Kolbenstange + Gelbwarnung
for gx in range(16,48,8): rect(im,gx,50,4,3,YEL); rect(im,gx+4,50,4,3,OC)
bolts(im,12,38,40,16)
save(im,"mach_bleipresse.png")

# ============ BRENNSTABWERK ============
im=newimg()
panel(im,10,18,44,32,(44,48,58,A),MHI,MMD)   # Werkshalle
for wx in range(16,50,10):
    rect(im,wx,24,6,10,CYD); rect(im,wx,24,6,3,CYL)  # Fenster
rect(im,10,44,44,6,MDK)
# Brennstabbuendel-Symbol mittig
im2=ImageDraw.Draw(im)
for i,dx in enumerate((-6,-2,2,6)):
    rect(im,32+dx-1,34,2,10,PBL if i%2==0 else MHI)
im2.ellipse([24,30,40,36],outline=YEL,width=2)
# Dach mit Antenne/Warnlicht
rect(im,30,10,4,8,MMD); disc(im,32,9,3,YEL)
bolts(im,10,18,44,32)
save(im,"mach_brennstabwerk.png")

# ---- Vorschau ----
gr=Image.open(OUT+"/tile_grass1.png").convert("RGBA").resize((64,64),Image.NEAREST)
def onGrass(name):
    cv=Image.new("RGBA",(64,64),(0,0,0,255)); cv.alpha_composite(gr)
    cv.alpha_composite(Image.open(OUT+"/"+name).convert("RGBA"))
    return cv.resize((160,160),Image.NEAREST)
names=["mach_bleibohrer.png","mach_wasserpumpe.png","mach_zentrifuge.png","mach_bleipresse.png","mach_brennstabwerk.png"]
prev=Image.new("RGBA",(160*5+40,160),(16,17,22,255))
for i,nm in enumerate(names):
    prev.alpha_composite(onGrass(nm),(i*(160+10),0))
prev.save(SP+"/nuclear_machines.png"); print("preview ok")
