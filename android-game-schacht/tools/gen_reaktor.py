from PIL import Image, ImageDraw
import math
S=192; OUT="/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"; A=255
def C(r,g,b,a=A): return (r,g,b,a)
OC=C(20,20,26)
# Beton
CN=C(150,156,168); CNL=C(198,204,216); CND=C(104,110,124); CNX=C(74,80,94)
# Kuppel (leicht blaugrau)
DM=C(126,134,150); DML=C(180,188,204); DMD=C(86,94,110)
# Kuehlturm
TW=C(178,184,198); TWL=C(216,222,234); TWD=C(120,126,142); TWX=C(88,94,110)
# Glut
G_CY=C(96,224,228); G_CYL=C(190,250,250); G_YE=C(252,238,150); G_PU=C(158,120,224); G_PUD=C(110,74,180)
YEL=C(248,206,74); RED=C(240,84,72); REDL=C(255,160,150)
PIPE=C(122,128,142); PIPEL=C(170,176,190); PIPED=C(84,90,104)
WAT=C(70,190,210); WATL=C(150,230,236)
im=Image.new("RGBA",(S,S),(0,0,0,0)); d=ImageDraw.Draw(im)
def rect(x,y,w,h,c):
    x,y,w,h=int(round(x)),int(round(y)),int(round(w)),int(round(h))
    for dy in range(h):
        for dx in range(w):
            if 0<=x+dx<S and 0<=y+dy<S: im.putpixel((x+dx,y+dy),c)
def hspan(y,cx,hw,base,light,dark,edge=None):
    x0=int(round(cx-hw)); x1=int(round(cx+hw)); w=x1-x0
    if w<=0: return
    rect(x0,y,w,1,base)
    lw=max(1,int(w*0.26))
    rect(x0,y,lw,1,light)
    rect(x1-max(1,int(w*0.30)),y,max(1,int(w*0.30)),1,dark)
    if edge is not None: im.putpixel((max(0,min(S-1,x0)),y),edge)
def disc(cx,cy,r,c):
    for y in range(int(cy-r),int(cy+r+1)):
        for x in range(int(cx-r),int(cx+r+1)):
            if (x-cx)**2+(y-cy)**2<=r*r and 0<=x<S and 0<=y<S: im.putpixel((x,y),c)
def ring(cx,cy,r,c,w=1):
    for a in range(0,360,3):
        for k in range(w):
            x=int(cx+(r-k)*math.cos(math.radians(a))); y=int(cy+(r-k)*math.sin(math.radians(a)))
            if 0<=x<S and 0<=y<S: im.putpixel((x,y),c)

# ============ Beton-Plattform unten ============
rect(0,158,192,34,C(56,58,66)); rect(0,158,192,4,C(78,80,90))
for gx in range(4,192,18): rect(gx,158,1,34,C(44,46,54))
for gx in range(10,184,24):
    rect(gx,182,6,3,YEL); rect(gx+6,182,6,3,OC)         # Hazard-Borduere
# Gelaender
for gx in range(4,30,10): rect(gx,150,2,9,C(150,156,168))
rect(4,150,24,2,C(170,176,190))

# ============ Kuehlturm (breiter, Hyperboloid) ============
tcx=142; top=30; bot=168; rMax=42.0; rWaist=25.0
def trad(y):
    t=(y-top)/(bot-top)                    # 0..1
    return rWaist + (rMax-rWaist)*(abs(t-0.5)*2)**1.5
# Umriss (dunkel) zuerst
for y in range(top,bot):
    r=trad(y); rect(tcx-r-1,y,2*r+2,1,OC)
for y in range(top,bot):
    r=trad(y)
    hspan(y,tcx,r,TW,TWL,TWD,edge=TWX)
# horizontale Betonbaender
for y in range(top+6,bot,12):
    r=trad(y); rect(tcx-r+1,y,2*r-2,1,TWD)
# oberer Rand (Innenseite dunkel)
rt=trad(top)
rect(tcx-rt,top,2*rt,4,OC); rect(tcx-rt+2,top+1,2*rt-4,3,C(46,50,60))
rect(tcx-rt+2,top+1,2*rt-4,1,TWL)
# Stuetzstreben (Lattice) am Fuss
rb=trad(bot-1)
for i in range(-int(rb),int(rb),14):
    d.line([(tcx+i,bot-18),(tcx+i+12,bot)], fill=OC, width=3)
    d.line([(tcx+i+12,bot-18),(tcx+i,bot)], fill=TWD, width=2)
rect(tcx-rb,bot-2,2*rb,4,C(48,50,58))     # Fussring

# ============ Reaktor-Containment (kleiner) ============
bx=32; body_top=92; body_bot=158; bw=48
# Zylinder-Koerper
rect(bx-1,body_top-1,bw+2,body_bot-body_top+2,OC)
for y in range(body_top,body_bot):
    hspan(y,bx+bw/2,bw/2,CN,CNL,CND)
# vertikale Rippen
for rx in range(bx+6,bx+bw-4,10): rect(rx,body_top+4,1,body_bot-body_top-8,CND)
# Kuppel (echte Halbkugel)
domeR=bw/2+2; dcx=bx+bw/2; dbase=body_top
for y in range(int(dbase-domeR),dbase):
    hgt=dbase-y
    hw=math.sqrt(max(0,domeR*domeR-hgt*hgt))
    rect(dcx-hw-1,y,2*hw+2,1,OC)
    hspan(y,dcx,hw,DM,DML,DMD)
# Spitze + rotes Warnlicht
rect(dcx-1,int(dbase-domeR)-6,2,6,C(120,126,140))
disc(dcx,int(dbase-domeR)-7,2,RED); im.putpixel((int(dcx),int(dbase-domeR)-8),REDL)
# Strahlen-Warnsymbol (Trefoil) statt Glut
tx=bx+bw//2; ty=body_top+20; tr=13
for y in range(ty-tr,ty+tr+1):
    for x in range(tx-tr,tx+tr+1):
        dx=x-tx; dy=y-ty; rr=(dx*dx+dy*dy)**0.5
        if rr<=tr:
            ang=(math.degrees(math.atan2(dy,dx))+90)%120
            blade = (rr>4 and rr<tr*0.9 and ang<52)
            im.putpixel((x,y), OC if (blade or rr<=3.2) else YEL)
for a in range(0,360,4):
    x=int(tx+tr*math.cos(math.radians(a))); y=int(ty+tr*math.sin(math.radians(a)))
    if 0<=x<S and 0<=y<S: im.putpixel((x,y),OC)
# Betonband + Lueftungsschlitze
rect(bx+3,body_top+38,bw-6,2,CND)
for vx in range(bx+6,bx+bw-8,8):
    rect(vx,body_top+42,5,7,C(58,64,78)); rect(vx,body_top+42,5,2,C(96,102,116))
# Tuer + Hazard
rect(bx+bw//2-6,body_bot-16,12,16,OC); rect(bx+bw//2-5,body_bot-15,10,15,C(60,64,74))
for i in range(0,bw-4,8): rect(bx+2+i,body_bot-4,4,3,YEL); rect(bx+2+i+4,body_bot-4,4,3,OC)
# kleine Leiter
for ly in range(body_top+6,body_bot-4,5): rect(bx+bw-3,ly,4,1,C(150,156,168))
rect(bx+bw-3,body_top+6,1,body_bot-body_top-8,C(150,156,168)); rect(bx+bw+1,body_top+6,1,body_bot-body_top-8,C(150,156,168))

# ============ Rohre Reaktor <-> Turm ============
for py in (112,128):
    rect(bx+bw,py,tcx-int(trad(py))-(bx+bw),7,PIPE)
    rect(bx+bw,py,tcx-int(trad(py))-(bx+bw),2,PIPEL)
    rect(bx+bw,py+5,tcx-int(trad(py))-(bx+bw),2,PIPED)
# Wasser-/Auslass-Stutzen unten mit Ventilrad
rect(bx+bw//2-14,body_bot,28,10,OC); rect(bx+bw//2-12,body_bot+2,24,6,PIPE); rect(bx+bw//2-12,body_bot+2,24,2,PIPEL)
disc(bx+bw//2+16,body_bot+5,5,PIPED); ring(bx+bw//2+16,body_bot+5,5,C(160,166,180)); disc(bx+bw//2+16,body_bot+5,1,PIPEL)
disc(bx+bw//2-16,body_bot+5,3,WAT); im.putpixel((bx+bw//2-16,body_bot+5),WATL)

im.save(OUT+"/mach_reaktor.png"); print("reaktor ok")
sp="/tmp/claude-0/-home-user-physik/d83acb4c-7a2c-5dd2-aa53-ab7b5d062310/scratchpad"
gr=Image.open(OUT+"/tile_grass1.png").convert("RGBA").resize((192,192),Image.NEAREST)
sh=Image.new("RGBA",(192,192),(40,40,46,255)); sh.paste(gr,(0,0)); sh.alpha_composite(im,(0,0))
sh.save(sp+"/reaktor.png")
