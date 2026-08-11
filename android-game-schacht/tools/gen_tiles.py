from PIL import Image
import random, os

W = H = 32
OUT = "/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"

def new_img(base):
    im = Image.new("RGBA", (W, H), base + (255,))
    return im

def px(im, x, y, col):
    im.putpixel((x % W, y % H), col + (255,))

def rect(im, x, y, w, h, col):
    for dy in range(h):
        for dx in range(w):
            px(im, x+dx, y+dy, col)

def noise(im, amt, rng):
    p = im.load()
    for y in range(H):
        for x in range(W):
            r,g,b,a = p[x,y]
            d = rng.randint(-amt, amt)
            p[x,y] = (max(0,min(255,r+d)), max(0,min(255,g+d)), max(0,min(255,b+d)), 255)

def grass(name, base, dark, light, blade, gold=None, seed=1):
    rng = random.Random(seed)
    im = new_img(base)
    noise(im, 6, rng)
    for _ in range(7):
        x,y = rng.randrange(W), rng.randrange(H)
        rect(im, x, y, rng.choice([2,3]), rng.choice([1,2]), dark)
    for _ in range(6):
        x,y = rng.randrange(W), rng.randrange(H)
        rect(im, x, y, 1, 1, light)
    for _ in range(14):                     # Grashalme
        x,y = rng.randrange(W), rng.randrange(H)
        rect(im, x, y, 1, rng.choice([2,3]), blade)
    if gold:
        for _ in range(6):
            x,y = rng.randrange(W), rng.randrange(H)
            px(im, x, y, gold)
    im.save(os.path.join(OUT, name))
    print("wrote", name)

def water(name, base, deep, light, foam, shift=0, seed=1):
    rng = random.Random(seed)
    im = new_img(base)
    noise(im, 4, rng)
    for _ in range(6):                      # tiefe Wellen (dunkel)
        x,y = rng.randrange(W), rng.randrange(H)
        rect(im, x+shift, y, rng.choice([3,4]), 1, deep)
    for _ in range(6):                      # Wellenkaemme (hell)
        x,y = rng.randrange(W), rng.randrange(H)
        rect(im, x-shift, y, rng.choice([2,3]), 1, light)
    for _ in range(4):                      # Schaum-Glitzer
        x,y = rng.randrange(W), rng.randrange(H)
        px(im, x+shift, y, foam)
    im.save(os.path.join(OUT, name))
    print("wrote", name)

grass("tile_grass_u.png", (98,112,84),  (82,96,68),   (116,130,98),  (70,84,58),  seed=11)
grass("tile_grass0.png",  (128,140,92), (104,116,74), (152,162,112), (92,104,64), seed=12)
grass("tile_grass1.png",  (110,168,74), (86,140,56),  (152,198,102), (72,124,50), seed=13)
grass("tile_grass2.png",  (92,176,74),  (70,146,56),  (142,210,108), (60,128,52), seed=14)
grass("tile_grass3.png",  (156,166,70), (128,138,50), (208,204,96),  (120,128,46), gold=(240,212,96), seed=15)
water("tile_water_0.png", (74,182,196), (52,150,178), (150,224,224), (214,252,252), shift=0, seed=21)
water("tile_water_1.png", (74,182,196), (52,150,178), (150,224,224), (214,252,252), shift=2, seed=21)
