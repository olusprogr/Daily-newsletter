from PIL import Image
import random, os
W=H=32
OUT="/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"
def new_img(base): return Image.new("RGBA",(W,H),base+(255,))
def px(im,x,y,c): im.putpixel((x%W,y%H),c+(255,))
def rect(im,x,y,w,h,c):
    for dy in range(h):
        for dx in range(w): px(im,x+dx,y+dy,c)
def noise(im,amt,rng):
    p=im.load()
    for y in range(H):
        for x in range(W):
            r,g,b,a=p[x,y]; d=rng.randint(-amt,amt)
            p[x,y]=(max(0,min(255,r+d)),max(0,min(255,g+d)),max(0,min(255,b+d)),255)
def grass(name,base,dark,light,blade,gold=None,seed=1):
    rng=random.Random(seed); im=new_img(base); noise(im,6,rng)
    for _ in range(7):
        x,y=rng.randrange(W),rng.randrange(H); rect(im,x,y,rng.choice([2,3]),rng.choice([1,2]),dark)
    for _ in range(6):
        x,y=rng.randrange(W),rng.randrange(H); rect(im,x,y,1,1,light)
    for _ in range(14):
        x,y=rng.randrange(W),rng.randrange(H); rect(im,x,y,1,rng.choice([2,3]),blade)
    if gold:
        for _ in range(6):
            x,y=rng.randrange(W),rng.randrange(H); px(im,x,y,gold)
    im.save(os.path.join(OUT,name)); print("wrote",name)
# heller als vorher
grass("tile_grass_u.png",(116,132,100),(98,112,84),  (140,156,120),(88,102,76), seed=11)
grass("tile_grass0.png", (150,162,110),(126,138,90), (178,188,132),(112,124,80),seed=12)
grass("tile_grass1.png", (132,190,92), (108,164,72), (172,216,122),(94,152,66), seed=13)
grass("tile_grass2.png", (112,198,92), (88,170,72),  (162,226,128),(78,150,64), seed=14)
grass("tile_grass3.png", (180,190,86), (150,160,64), (226,222,118),(140,150,58),gold=(244,220,110),seed=15)
