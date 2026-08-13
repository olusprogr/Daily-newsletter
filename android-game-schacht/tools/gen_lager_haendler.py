from PIL import Image
import math
S=64
OUT="/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"
SP="/tmp/claude-0/-home-user-physik/d83acb4c-7a2c-5dd2-aa53-ab7b5d062310/scratchpad"
A=255
OC=(24,24,30,A)
MDK=(64,68,78,A); MMD=(104,110,122,A); MLT=(150,158,172,A); MHI=(198,204,216,A)
BR=(126,84,46,A); BRD=(92,60,32,A); BRL=(156,110,64,A)
ORG=(240,150,60,A); YEL=(248,206,74,A); YELL=(255,236,150,A); ORGD=(190,120,36,A)
CY=(90,200,214,A); CYL=(170,240,246,A)
PUR=(168,132,232,A); RED=(210,74,66,A); WHT=(238,240,246,A)
WOOD=(150,104,58,A); WOODD=(112,74,38,A); DARKIN=(30,32,40,A)
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
def bolts(im,x,y,w,h,c=MHI):
    for (bx,by) in [(x+2,y+2),(x+w-3,y+2),(x+2,y+h-3),(x+w-3,y+h-3)]:
        if 0<=bx<S and 0<=by<S: im.putpixel((bx,by),c)
def save(im,name): im.save(OUT+"/"+name); print("wrote",name)

# ================= LAGER: offenes Regallager mit Rohstoff-Kisten =================
im=newimg()
panel(im,5,7,54,50,MDK,MMD,OC)                 # Aussenrahmen (Metall)
rect(im,9,13,46,40,DARKIN)                     # dunkles Inneres
for sy in (25,39,51): rect(im,9,sy,46,2,MLT)   # Regalboeden
def crate(x,y,c):
    rect(im,x,y,11,10,OC); rect(im,x+1,y+1,9,8,WOOD)
    rect(im,x+1,y+1,9,3,c)                      # farbiger Inhalt oben
    rect(im,x+1,y+5,9,1,WOODD); im.putpixel((x+2,y+2),(255,255,255,120))
# oberstes Regal (Erz, Barren, Platte)
crate(11,15,ORG); crate(25,15,MHI); crate(39,15,CY)
# mittleres Regal (Komponente, Erz, Barren)
crate(11,29,PUR); crate(25,29,ORG); crate(39,29,MHI)
# unteres Regal (Platte, Komponente)
crate(18,43,CY); crate(32,43,PUR)
rect(im,3,5,58,4,OC); rect(im,5,7,54,2,MLT)    # Dachkante
rect(im,25,1,14,6,OC); rect(im,26,2,12,4,YEL)  # Schild
rect(im,29,2,6,4,OC)                           # Box-Icon auf dem Schild
bolts(im,5,7,54,50,MHI)
save(im,"mach_lager.png")

# ================= HAENDLER: Marktstand mit Markise, Theke, Muenzen =================
im=newimg()
panel(im,8,16,48,40,WOODD,WOOD,OC)             # Rueckwand
for i in range(9,55,4): rect(im,i,18,1,26,WOODD)   # Holzbretter
# Theke
panel(im,5,44,54,12,WOOD,BRL,WOODD); rect(im,5,44,54,2,BRL)
# Pfosten
rect(im,8,15,3,30,WOODD); rect(im,53,15,3,30,WOODD)
# Markise (rot/weiss)
for i in range(6,58,8):
    rect(im,i,6,4,10,RED); rect(im,i+4,6,4,10,WHT)
rect(im,5,4,54,3,OC); rect(im,6,6,52,2,(255,120,110,A))
# Zackenkante der Markise
for i in range(6,56,4):
    col=RED if ((i-6)//4)%2==0 else WHT
    for k in range(3): rect(im,i+k,16+k,4-2*k,1,col)
# Grosses Muenz-/Euro-Schild
disc(im,32,29,10,OC); disc(im,32,29,9,YEL); disc(im,32,29,7,YELL)
rect(im,29,25,2,9,ORGD); rect(im,29,25,6,2,ORGD); rect(im,29,32,6,2,ORGD)   # € Spine+oben+unten
rect(im,27,27,7,2,ORGD); rect(im,27,30,7,2,ORGD)                            # € Mittelbalken
# Muenzstapel auf der Theke
def coin(x,y): disc(im,x,y,4,OC); disc(im,x,y,3,YEL); im.putpixel((x-1,y-1),YELL)
coin(14,43); coin(14,40); coin(50,43); coin(50,40)
# Warenkiste auf der Theke
rect(im,22,46,10,8,OC); rect(im,23,47,8,6,WOOD); rect(im,23,47,8,2,CY)
save(im,"mach_haendler.png")

# ---- Vorschau auf Gras ----
gr=Image.open(OUT+"/tile_grass1.png").convert("RGBA").resize((64,64),Image.NEAREST)
def onGrass(name):
    cv=Image.new("RGBA",(64,64),(0,0,0,255)); cv.alpha_composite(gr);
    cv.alpha_composite(Image.open(OUT+"/"+name).convert("RGBA"))
    return cv.resize((192,192),Image.NEAREST)
prev=Image.new("RGBA",(2*192+20,192),(0,0,0,255))
prev.alpha_composite(onGrass("mach_lager.png"),(0,0))
prev.alpha_composite(onGrass("mach_haendler.png"),(192+20,0))
prev.save(SP+"/lager_haendler.png"); print("preview ok")
