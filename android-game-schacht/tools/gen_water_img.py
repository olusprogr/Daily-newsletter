from PIL import Image, ImageEnhance
import random
SRC="/root/.claude/uploads/d83acb4c-7a2c-5dd2-aa53-ab7b5d062310/48cb527a-1000077105.jpg"
OUT="/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"
SP="/tmp/claude-0/-home-user-physik/d83acb4c-7a2c-5dd2-aa53-ab7b5d062310/scratchpad"

src=Image.open(SRC).convert("RGB")
W,H=src.size

def frame(dx,dy,bright):
    # Quelle rollen -> Funkel-Muster verschiebt sich fuer sanfte Animation
    rolled=Image.new("RGB",(W,H))
    rolled.paste(src,(dx,dy)); rolled.paste(src,(dx-W,dy)); rolled.paste(src,(dx,dy-H)); rolled.paste(src,(dx-W,dy-H))
    im=rolled.resize((32,32),Image.LANCZOS)
    im=ImageEnhance.Brightness(im).enhance(bright)
    return im.convert("RGBA")

frame(0,0,1.00).save(OUT+"/tile_water_0.png"); print("wrote tile_water_0.png")
frame(5,3,1.05).save(OUT+"/tile_water_1.png"); print("wrote tile_water_1.png")

# ---------- Vorschau: Wasser + Kueste (Sand) + Gras ----------
CELL=40
w0=Image.open(OUT+"/tile_water_0.png").convert("RGBA").resize((CELL,CELL),Image.NEAREST)
grass=[Image.open(OUT+"/tile_grass%s.png"%s).convert("RGBA").resize((CELL,CELL),Image.NEAREST)
       for s in ["_u","0","1","2","3"]]
# neue Strand-Farben (muessen zu GameView passen)
cSurf=(202,240,246,255); cSandWet=(198,186,130,255); cSand=(236,220,164,255)
COLS,ROWS=7,7
# Landmaske: kreisrunde Insel
land=[[((c-3.2)**2+(r-3.4)**2)**0.5 < 2.6 for c in range(COLS)] for r in range(ROWS)]
cv=Image.new("RGBA",(COLS*CELL,ROWS*CELL),(0,0,0,255))
def solid(im_cell,col):
    d=Image.new("RGBA",(CELL,CELL),col); return d
for r in range(ROWS):
    for c in range(COLS):
        if not land[r][c]:
            cv.alpha_composite(w0,(c*CELL,r*CELL)); continue
        # Reichtum grob nach Distanz zur Mitte
        d=((c-3.2)**2+(r-3.4)**2)**0.5
        idx=4 if d<0.8 else 3 if d<1.4 else 2 if d<2.0 else 1
        cv.alpha_composite(grass[idx],(c*CELL,r*CELL))
        # Kuesten-Baender wie im Spiel (Surf->nass->trocken), Dicke ~22%
        t=int(CELL*0.22); a=int(t*0.22); b=int(t*0.55)
        def band(x0,y0,x1,y1,col):
            seg=Image.new("RGBA",(max(1,x1-x0),max(1,y1-y0)),col); cv.alpha_composite(seg,(c*CELL+x0,r*CELL+y0))
        if r==0 or not land[r-1][c]:
            band(0,0,CELL,a,cSurf); band(0,a,CELL,b,cSandWet); band(0,b,CELL,t,cSand)
        if r==ROWS-1 or not land[r+1][c]:
            band(0,CELL-a,CELL,CELL,cSurf); band(0,CELL-b,CELL,CELL-a,cSandWet); band(0,CELL-t,CELL,CELL-b,cSand)
        if c==0 or not land[r][c-1]:
            band(0,0,a,CELL,cSurf); band(a,0,b,CELL,cSandWet); band(b,0,t,CELL,cSand)
        if c==COLS-1 or not land[r][c+1]:
            band(CELL-a,0,CELL,CELL,cSurf); band(CELL-b,0,CELL-a,CELL,cSandWet); band(CELL-t,0,CELL-b,CELL,cSand)
# Maschine drauf
rk=Image.open(OUT+"/mach_bohrer.png").convert("RGBA").resize((CELL,CELL),Image.NEAREST)
cv.alpha_composite(rk,(3*CELL,3*CELL))
cv.save(SP+"/water_new_ingame.png"); print("preview ok")
