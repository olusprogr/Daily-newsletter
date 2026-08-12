from PIL import Image
import random, os
OUT="/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"
def img(w,h): return Image.new("RGBA",(w,h),(0,0,0,0))
def plot(im,x,y,c):
    if 0<=x<im.width and 0<=y<im.height: im.putpixel((x,y),c)
def rect(im,x,y,w,h,c):
    for dy in range(h):
        for dx in range(w): plot(im,x+dx,y+dy,c)
A=255
TR=(96,64,36,A); TRD=(70,46,24,A)

def pine(name, nd, ndD, ndL, snow=False, seed=0):
    W,H=24,32; im=img(W,H); cx=W//2; rng=random.Random(seed)
    # Stamm
    rect(im,cx-1,H-7,3,7,TRD); rect(im,cx-1,H-7,2,7,TR)
    # 3 Nadel-Stufen: schmal oben, breit unten (richtig herum!)
    tiers=[(3,10,6),(9,11,9),(16,12,12)]
    for (top,ht,hb) in tiers:
        for i in range(ht):
            w=max(1,int(round(2*hb*(i+1)/ht)))
            x=cx-w//2; y=top+i
            for dx in range(w):
                c=nd
                if dx< max(1,int(w*0.28)): c=ndL
                elif dx>= w-max(1,int(w*0.28)): c=ndD
                # kleine Nadel-Struktur
                if rng.random()<0.10: c=ndD
                plot(im,x+dx,y,c)
        # dunkler Saum am Stufenboden
        yb=top+ht-1; wb=2*hb
        for dx in range(wb):
            if (dx+ (top))%2==0: plot(im,cx-wb//2+dx,yb,ndD)
    # Spitze
    plot(im,cx,tiers[0][0]-1,ndL)
    if snow:
        for (top,ht,hb) in tiers:
            yy=top+1
            for dx in range(-hb//2,hb//2):
                if random.Random(seed+dx).random()<0.5: plot(im,cx+dx,yy,(238,244,250,A))
    im.save(OUT+"/"+name); print("wrote",name)

def leaf(name, cy_c, cyL, cyD, rx=9, ry=8, seed=0):
    W,H=24,28; im=img(W,H); cx=11; cyy=10; rng=random.Random(seed)
    rect(im,cx-1,cyy+ry-1,3,H-(cyy+ry-1),TRD); rect(im,cx-1,cyy+ry-1,2,H-(cyy+ry-1),TR)
    for x in range(W):
        for y in range(H):
            dx=(x-cx)/rx; dy=(y-cyy)/ry
            if dx*dx+dy*dy<=1.0:
                c=cy_c
                s=(x-cx)+(y-cyy)
                if s<-rx*0.5: c=cyL
                elif s> ry*0.6: c=cyD
                plot(im,x,y,c)
    # dunkle Cluster + Highlights fuer Struktur
    for _ in range(7):
        bx=cx+rng.randint(-rx+2,rx-3); by=cyy+rng.randint(-ry+2,ry-2)
        rect(im,bx,by,rng.choice([1,2]),1,cyD)
    for _ in range(5):
        bx=cx+rng.randint(-rx+1,rx-2); by=cyy+rng.randint(-ry+1,0)
        plot(im,bx,by,cyL)
    im.save(OUT+"/"+name); print("wrote",name)

pine("deco_pine1.png",(46,120,60,A),(32,92,48,A),(84,158,96,A),snow=False,seed=1)
pine("deco_pine2.png",(40,104,74,A),(28,80,56,A),(74,146,108,A),snow=False,seed=2)
pine("deco_pine3.png",(60,132,68,A),(40,100,52,A),(110,180,110,A),snow=True,seed=3)
leaf("deco_leaf1.png",(64,150,66,A),(104,186,100,A),(42,110,50,A),rx=9,ry=8,seed=4)
leaf("deco_leaf2.png",(84,160,78,A),(126,196,112,A),(56,120,60,A),rx=8,ry=9,seed=5)

# Fels (unveraendert, passt) + Busch (unveraendert)
def rock():
    im=img(20,16); rock=(120,120,128,A); rockD=(88,88,96,A); rockL=(160,160,168,A); ro=(48,48,54,A)
    for cx in range(2,18):
        for cy in range(4,15):
            dx=(cx-10)/9.0; dy=(cy-11)/6.0
            if dx*dx+dy*dy<=1.0:
                c=rock; s=(cx-10)+(cy-11)
                if s<-4: c=rockL
                elif s>5: c=rockD
                im.putpixel((cx,cy),c)
    for x in range(4,16): im.putpixel((x,14),ro)
    im.save(OUT+"/deco_rock.png"); print("wrote deco_rock.png")
def bush():
    im=img(18,12); b=(74,150,74,A); bl=(112,190,110,A); bd=(50,116,56,A)
    for cx in range(2,16):
        for cy in range(3,11):
            dx=(cx-9)/7.0; dy=(cy-8)/4.0
            if dx*dx+dy*dy<=1.0:
                c=b; s=(cx-9)+(cy-8)
                if s<-3: c=bl
                elif s>4: c=bd
                im.putpixel((cx,cy),c)
    for (fx,fy,fc) in [(5,6,(240,214,96,A)),(12,7,(232,120,140,A)),(9,8,(230,230,240,A))]:
        im.putpixel((fx,fy),fc)
    im.save(OUT+"/deco_bush.png"); print("wrote deco_bush.png")
rock(); bush()
# alte Baum-Dateien entfernen
for old in ["deco_tree.png","deco_tree2.png"]:
    p=OUT+"/"+old
    if os.path.exists(p): os.remove(p); print("removed",old)
