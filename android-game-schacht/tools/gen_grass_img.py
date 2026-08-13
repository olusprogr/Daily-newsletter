from PIL import Image
import random
SRC="/root/.claude/uploads/d83acb4c-7a2c-5dd2-aa53-ab7b5d062310/e601ae3a-1000077151.png"
OUT="/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"
SP="/tmp/claude-0/-home-user-physik/d83acb4c-7a2c-5dd2-aa53-ab7b5d062310/scratchpad"

# Basiskachel: Bild auf 32x32 herunterrechnen (Mittelung -> weiche Grasflaeche)
base=Image.open(SRC).convert("RGB").resize((32,32),Image.LANCZOS)

def tint(im, mul, add=(0,0,0), sat=1.0):
    out=Image.new("RGBA",(32,32),(0,0,0,255)); sp=out.load(); ip=im.load()
    for y in range(32):
        for x in range(32):
            r,g,b=ip[x,y]
            # Saettigung um Graumittel
            gray=(r+g+b)/3.0
            r=gray+(r-gray)*sat; g=gray+(g-gray)*sat; b=gray+(b-gray)*sat
            r=r*mul[0]+add[0]; g=g*mul[1]+add[1]; b=b*mul[2]+add[2]
            sp[x,y]=(max(0,min(255,int(r))),max(0,min(255,int(g))),max(0,min(255,int(b))),255)
    return out

# 5 Reichtums-Stufen
tiles={
 "tile_grass1.png": tint(base,(1.00,1.00,1.00)),                       # normal (Original)
 "tile_grass_u.png":tint(base,(0.78,0.80,0.74),sat=0.55),             # arm/unerforscht: entsaettigt+dunkler
 "tile_grass0.png": tint(base,(0.92,0.95,0.72),add=(6,4,-4),sat=0.85),# oliv
 "tile_grass2.png": tint(base,(0.98,1.08,0.92),add=(4,10,2)),         # heller/gruener
 "tile_grass3.png": tint(base,(1.12,1.05,0.60),add=(18,10,-8)),       # gold (reich)
}
for name,im in tiles.items():
    im.save(OUT+"/"+name); print("wrote",name)

# ---------- In-Game-Vorschau ----------
CELL=40; COLS=6; ROWS=6
tiers=["tile_grass_u.png","tile_grass0.png","tile_grass1.png","tile_grass2.png","tile_grass3.png"]
imgs=[Image.open(OUT+"/"+t).convert("RGBA").resize((CELL,CELL),Image.NEAREST) for t in tiers]
rng=random.Random(7)
# Reichtums-Fleckenkarte (value-noise-artig)
field=[[0.0]*COLS for _ in range(ROWS)]
cx,cy=rng.uniform(1,COLS-1),rng.uniform(1,ROWS-1)
for r in range(ROWS):
    for c in range(COLS):
        d=((c-cx)**2+(r-cy)**2)**0.5
        field[r][c]=max(0.0,1.0-d/4.0)+rng.uniform(-0.15,0.15)
cv=Image.new("RGBA",(COLS*CELL,ROWS*CELL),(0,0,0,255))
for r in range(ROWS):
    for c in range(COLS):
        v=field[r][c]
        idx=0 if v<0.15 else 1 if v<0.35 else 2 if v<0.6 else 3 if v<0.85 else 4
        cv.alpha_composite(imgs[idx],(c*CELL,r*CELL))
# ein paar Deko + eine Maschine drauf
def paste(name,cellx,celly,wcells=1,hcells=1):
    s=Image.open(OUT+"/"+name).convert("RGBA").resize((CELL*wcells,CELL*hcells),Image.NEAREST)
    cv.alpha_composite(s,(cellx*CELL,celly*CELL))
paste("deco_pine2.png",0,0); paste("deco_bush.png",4,1); paste("deco_rock.png",1,4)
paste("mach_bohrer.png",2,2); paste("mach_ofen.png",4,4)
cv.save(SP+"/grass_new_ingame.png"); print("preview ok")

# Streifen der 5 Stufen
strip=Image.new("RGBA",(5*64,64),(0,0,0,255))
for i,t in enumerate(tiers):
    strip.alpha_composite(Image.open(OUT+"/"+t).convert("RGBA").resize((64,64),Image.NEAREST),(i*64,0))
strip.save(SP+"/grass_new_tiers.png"); print("tiers ok")
