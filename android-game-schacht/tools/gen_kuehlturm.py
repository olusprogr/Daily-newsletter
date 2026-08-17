from PIL import Image, ImageDraw
S=64
OUT="/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"
A=255
OC=(24,24,30,A)
CONC=(150,148,142,A); CONCD=(112,110,104,A); CONCL=(190,188,182,A)
YEL=(240,200,70,A)
STEAM=(240,244,248,190); STEAML=(255,255,255,140)

def newimg(): return Image.new("RGBA",(S,S),(0,0,0,0))
def rect(im,x,y,w,h,c):
    x,y,w,h=int(x),int(y),int(w),int(h)
    for dy in range(h):
        for dx in range(w):
            px,py=x+dx,y+dy
            if 0<=px<S and 0<=py<S: im.putpixel((px,py),c)
def save(im,name):
    im.save(OUT+"/"+name); print("wrote",name)

im=newimg()
d=ImageDraw.Draw(im)
# Fundament mit Warnstreifen
rect(im,8,54,48,6,CONCD)
for i,gx in enumerate(range(8,56,6)):
    rect(im,gx,59,6,2, YEL if i%2==0 else OC)
# Kuehlturm-Silhouette (Hyperboloid: schmale Taille)
tower=[(14,54),(23,28),(19,14),(45,14),(41,28),(50,54)]
d.polygon(tower, fill=CONC, outline=OC)
hi=[(14,54),(23,28),(19,14),(27,14),(23,28),(19,54)]
d.polygon(hi, fill=CONCL)
sh=[(45,14),(41,28),(50,54),(41,54),(37,28),(39,14)]
d.polygon(sh, fill=CONCD)
# Ringstruktur (Beton-Fugen)
for ry in (24,34,44):
    d.line([(15,ry),(49,ry)], fill=OC, width=1)
# Kuehlturm-Muendung oben (dunkler Ring)
d.ellipse([19,10,45,18], outline=OC, fill=(70,74,84,A))
d.ellipse([23,12,41,17], fill=(30,32,38,A))
# Aufsteigender Dampf
for i,(sx,sy,r) in enumerate([(28,10,5),(35,5,6),(31,-1,7),(38,-6,6)]):
    d.ellipse([sx-r,sy-r,sx+r,sy+r], fill=STEAM if i%2==0 else STEAML)
save(im,"mach_kuehlturm.png")
