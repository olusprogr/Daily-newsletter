from PIL import Image
import os
OUT = "/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"
def img(w,h): return Image.new("RGBA",(w,h),(0,0,0,0))
def rect(im,x,y,w,h,c):
    for dy in range(h):
        for dx in range(w):
            if 0<=x+dx<im.width and 0<=y+dy<im.height: im.putpixel((x+dx,y+dy),c)
A=255
# --- Nadelbaum (Pine), 24x28 ---
im=img(24,28)
trunk=(96,64,36,A); trunkD=(70,46,24,A)
g1=(48,120,54,A); g2=(70,150,70,A); g3=(96,176,92,A); out=(26,54,28,A)
rect(im,10,22,4,6,trunkD); rect(im,10,22,2,6,trunk)
# drei Kegelstufen (Umriss + fuellung + Licht)
def cone(cy, half, base):
    for i,row in enumerate(range(cy, cy+half*2)):
        w = (half*2 - i)
        x = 12 - w//2
        rect(im, x, row, max(1,w), 1, base)
for (cy,half,base) in [(2,6,g1),(8,7,g2),(14,8,g1)]:
    cone(cy,half,base)
# Licht/Schatten Tupfer
rect(im,9,6,1,1,g3); rect(im,8,12,1,1,g3); rect(im,7,18,1,1,g3)
rect(im,15,9,1,1,out); rect(im,16,15,1,1,out)
im.save(OUT+"/deco_tree.png")
# --- Laubbaum (rund), 22x26 ---
im=img(22,26)
rect(im,9,18,4,8,trunkD); rect(im,9,18,2,8,trunk)
canopy=(60,140,64,A); canopyL=(96,178,96,A); canopyD=(40,104,48,A)
pts=[(11,9,8)]
for cy in range(2,18):
    # grober Kreis radius ~8 um (11,9)
    for cx in range(2,20):
        dx=cx-11; dy=cy-9
        if dx*dx+dy*dy<=60:
            im.putpixel((cx,cy),canopy)
# Licht oben links, Schatten unten rechts
for cx in range(2,20):
    for cy in range(2,18):
        dx=cx-11; dy=cy-9
        if dx*dx+dy*dy<=60:
            if dx+dy< -3: im.putpixel((cx,cy),canopyL)
            elif dx+dy> 5: im.putpixel((cx,cy),canopyD)
im.save(OUT+"/deco_tree2.png")
# --- Fels, 20x16 ---
im=img(20,16)
rock=(120,120,128,A); rockD=(88,88,96,A); rockL=(160,160,168,A); ro=(48,48,54,A)
for cx in range(2,18):
    for cy in range(4,15):
        dx=(cx-10)/9.0; dy=(cy-11)/6.0
        if dx*dx+dy*dy<=1.0: im.putpixel((cx,cy),rock)
for cx in range(2,18):
    for cy in range(4,15):
        dx=(cx-10)/9.0; dy=(cy-11)/6.0
        if dx*dx+dy*dy<=1.0:
            if (cx-10)+(cy-11)<-4: im.putpixel((cx,cy),rockL)
            elif (cx-10)+(cy-11)>5: im.putpixel((cx,cy),rockD)
rect(im,4,14,12,1,ro)
im.save(OUT+"/deco_rock.png")
# --- Busch/Blumen, 18x12 ---
im=img(18,12)
b=(64,138,66,A); bl=(96,176,96,A); bd=(44,108,50,A)
for cx in range(2,16):
    for cy in range(3,11):
        dx=(cx-9)/7.0; dy=(cy-8)/4.0
        if dx*dx+dy*dy<=1.0: im.putpixel((cx,cy),b)
rect(im,4,4,1,1,bl); rect(im,11,5,1,1,bl); rect(im,8,4,1,1,bl)
# Bluemchen
for (fx,fy,fc) in [(5,6,(240,214,96,A)),(12,7,(232,120,140,A)),(9,8,(230,230,240,A))]:
    im.putpixel((fx,fy),fc)
im.save(OUT+"/deco_bush.png")
print("deco done")
