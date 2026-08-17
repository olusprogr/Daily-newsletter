from PIL import Image, ImageDraw
import math
S=64
OUT="/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"
A=255
OC=(24,24,30,A)
MDK=(64,68,78,A); MMD=(104,110,122,A); MLT=(150,158,172,A); MHI=(198,204,216,A)
YEL=(240,200,70,A); YELL=(255,236,150,A)
CY=(120,206,255,A); CYL=(210,244,255,A); CYD=(40,120,170,A)
ORG=(238,120,52,A); ORGL=(255,182,110,A)

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
def ring(im,cx,cy,r,c,w=1):
    for rr in range(r,r+w):
        for a in range(0,360,3):
            x=int(cx+rr*math.cos(math.radians(a))); y=int(cy+rr*math.sin(math.radians(a)))
            if 0<=x<S and 0<=y<S: im.putpixel((x,y),c)
def bolts(im,x,y,w,h,c=MHI):
    for (bx,by) in [(x+2,y+2),(x+w-3,y+2),(x+2,y+h-3),(x+w-3,y+h-3)]:
        if 0<=bx<S and 0<=by<S: im.putpixel((bx,by),c)
def save(im,name):
    im.save(OUT+"/"+name); print("wrote",name)

# ============ REAKTORKERN: gluehendes Containment-Gefaess ============
im=newimg()
d=ImageDraw.Draw(im)
# Sockel/Fundament mit Warnstreifen
panel(im,10,50,44,10,MDK,MMD,OC)
for i,gx in enumerate(range(12,52,6)):
    rect(im,gx,57,6,2, YEL if i%2==0 else OC)
# Containment-Gehaeuse (sechseckig angedeutet ueber Panel + Fasen)
panel(im,14,16,36,36,(46,50,60,A),MHI,MMD)
d.polygon([(14,16),(24,10),(40,10),(50,16)], fill=(46,50,60,A), outline=OC)   # Dach-Fase
d.polygon([(14,50),(24,56),(40,56),(50,50)], fill=(46,50,60,A), outline=OC)   # Boden-Fase
# Sichtfenster mit gluehendem Kern
disc(im,32,33,15,OC)
disc(im,32,33,13,MMD)
ring(im,32,33,12,MHI)
disc(im,32,33,8,CYD); disc(im,32,33,5,CY); disc(im,32,33,2,CYL)
ring(im,32,33,10,ORG,1)
# Steuerstab-Andeutungen oben
for sx in (24,32,40):
    rect(im,sx-1,10,2,6,MMD)
bolts(im,14,16,36,36)
# Warnsymbol vorne unten
d.polygon([(29,44),(35,44),(32,50)], fill=YELL, outline=OC)
save(im,"mach_reaktorkern.png")
