from PIL import Image, ImageDraw
import math
S=64; OUT="/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"; A=255
OUT_C=(24,24,30,A); MDK=(64,68,78,A); MMD=(104,110,122,A); MLT=(150,158,172,A); MHI=(198,204,216,A)
GRN=(74,190,96,A); GRND=(44,150,70,A); GRNL=(150,230,160,A); STL=(120,128,140,A)
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
def bolts(im,x,y,w,h,c=MHI):
    for (bx,by) in [(x+2,y+2),(x+w-3,y+2),(x+2,y+h-3),(x+w-3,y+h-3)]: im.putpixel((bx,by),c)
im=Image.new("RGBA",(S,S),(0,0,0,0)); d=ImageDraw.Draw(im)
# Trichter oben
rect(im,20,6,24,4,MDK); rect(im,22,10,20,3,MMD)
d.polygon([(24,13),(40,13),(35,20),(29,20)], fill=STL)
# Gehaeuse
panel(im,10,20,44,34,(58,62,72,A),MMD,(28,30,36,A))
# Recycling-Emblem (drei gruene Pfeile im Kreis)
cx,cy,r=32,37,12
disc(im,cx,cy,r+1,OUT_C); disc(im,cx,cy,r,(40,44,52,A))
for a0 in (0,120,240):
    pts=[]
    for a in range(a0+8,a0+96,6):
        pts.append((cx+int(r*0.72*math.cos(math.radians(a))), cy+int(r*0.72*math.sin(math.radians(a)))))
    if len(pts)>=2: d.line(pts, fill=GRN, width=3)
    # Pfeilspitze am Ende
    ae=math.radians(a0+96)
    tx,ty=cx+int(r*0.72*math.cos(ae)), cy+int(r*0.72*math.sin(ae))
    d.polygon([(tx,ty),(tx-4,ty-2),(tx-1,ty+4)], fill=GRNL)
disc(im,cx,cy,3,GRND); disc(im,cx,cy,2,GRNL)
bolts(im,10,20,44,34)
rect(im,8,52,6,6,OUT_C); rect(im,50,52,6,6,OUT_C)
im.save(OUT+"/mach_recycler.png"); print("recycler ok")
# Vorschau
sp="/tmp/claude-0/-home-user-physik/d83acb4c-7a2c-5dd2-aa53-ab7b5d062310/scratchpad"
gr=Image.open(OUT+"/tile_grass1.png").convert("RGBA").resize((64,64),Image.NEAREST)
sh=Image.new("RGBA",(64,64),(40,40,46,255)); sh.paste(gr,(0,0)); sh.alpha_composite(im,(0,0))
sh.resize((192,192),Image.NEAREST).save(sp+"/recycler.png")
