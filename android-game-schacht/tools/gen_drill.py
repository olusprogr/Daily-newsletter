from PIL import Image, ImageDraw
import math
S=64; OUT="/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"; A=255
OC=(24,24,30,A)
MDK=(64,68,78,A); MMD=(104,110,122,A); MLT=(150,158,172,A); MHI=(200,206,218,A)
STL=(150,158,172,A); STLD=(96,104,118,A); STLL=(210,216,228,A)
ORG=(240,130,40,A); ORGD=(196,86,26,A); YEL=(248,206,74,A)
DIRT=(96,66,42,A); DIRTD=(70,48,30,A)
def rect(im,x,y,w,h,c):
    x,y,w,h=int(round(x)),int(round(y)),int(round(w)),int(round(h))
    for dy in range(h):
        for dx in range(w):
            if 0<=x+dx<S and 0<=y+dy<S: im.putpixel((x+dx,y+dy),c)
def panel(im,x,y,w,h,base,light,dark,out=OC):
    rect(im,x-1,y-1,w+2,h+2,out); rect(im,x,y,w,h,base)
    rect(im,x,y,w,2,light); rect(im,x,y,2,h,light); rect(im,x,y+h-2,w,2,dark); rect(im,x+w-2,y,2,h,dark)
def bolts(im,x,y,w,h,c=MHI):
    for (bx,by) in [(x+2,y+2),(x+w-3,y+2),(x+2,y+h-3),(x+w-3,y+h-3)]: im.putpixel((bx,by),c)
im=Image.new("RGBA",(S,S),(0,0,0,0)); d=ImageDraw.Draw(im)

# --- Boden, in den gebohrt wird ---
rect(im,10,52,44,10,DIRTD)
rect(im,10,52,44,2,DIRT)
for bx in range(12,52,6): rect(im,bx,55,2,2,DIRT)

# --- Stuetzbeine (A-Rahmen) ---
d.line([(20,20),(10,53)], fill=OC, width=4); d.line([(20,20),(10,53)], fill=MMD, width=2)
d.line([(44,20),(54,53)], fill=OC, width=4); d.line([(44,20),(54,53)], fill=MMD, width=2)
rect(im,7,52,8,4,OC); rect(im,49,52,8,4,OC)        # Fuesse

# --- Motorgehaeuse oben ---
panel(im,16,4,32,16,MMD,MLT,MDK)
# Warnstreifen (gelb/schwarz)
for i in range(18,46,6):
    rect(im,i,7,3,4,YEL); rect(im,i+3,7,3,4,(30,30,34,A))
# Kuehlrippen
rect(im,19,13,26,1,MDK); rect(im,19,16,26,1,MDK)
bolts(im,16,4,32,16)
# Getriebe/Kragen
panel(im,24,20,16,6,MDK,MMD,(30,32,38,A))
rect(im,30,20,4,3,MHI)

# --- Antriebswelle ---
rect(im,30,26,4,6,STLD); rect(im,30,26,1,6,STLL)

# --- Foerderschnecke (Auger): konisch, mit Spiral-Flighting ---
top,bot=30,56
for i,yy in enumerate(range(top,bot)):
    t=i/(bot-top)
    hw=8*(1-t)+1.5          # Halbbreite 8 -> 1.5
    cx=32
    rect(im,cx-hw,yy,hw*2,1,STL)
    rect(im,cx-hw,yy,1,1,STLL); rect(im,cx+hw-1,yy,1,1,STLD)
    # Spiralband: eine helle Diagonale, die pro Reihe wandert -> wirkt wie Gewinde
    ph=(i*2)% max(1,int(hw*1.6))
    sx=cx-hw+ph
    rect(im,sx,yy, max(1,int(hw*0.5)),1, STLL)
    # dunkle Gegen-Diagonale
    sx2=cx-hw+ (ph+int(hw))% max(1,int(hw*1.6))
    im.putpixel((min(S-1,max(0,int(sx2))),yy),STLD)
    # orange Schneidkanten alle paar Reihen
    if i%3==0:
        rect(im,cx-hw,yy,2,1,ORG); rect(im,cx+hw-2,yy,2,1,ORGD)
# Bohrspitze
d.polygon([(29,56),(35,56),(32,62)], fill=STL)
d.polygon([(30,57),(34,57),(32,61)], fill=ORG)
rect(im,31,56,2,1,STLL)

im.save(OUT+"/mach_bohrer.png"); print("drill ok")
# Vorschau
sp="/tmp/claude-0/-home-user-physik/d83acb4c-7a2c-5dd2-aa53-ab7b5d062310/scratchpad"
gr=Image.open(OUT+"/tile_grass1.png").convert("RGBA").resize((64,64),Image.NEAREST)
sh=Image.new("RGBA",(64,64),(40,40,46,255)); sh.paste(gr,(0,0)); sh.alpha_composite(im,(0,0))
sh.resize((256,256),Image.NEAREST).save(sp+"/drill.png")
