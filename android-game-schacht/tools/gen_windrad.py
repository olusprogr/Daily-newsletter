from PIL import Image
W,H=64,128; OUT="/home/user/physik/android-game-schacht/app/src/main/res/drawable-nodpi"; A=255
OUT_C=(30,32,38,A); TW=(210,214,222,A); TWD=(168,172,182,A); TWL=(238,240,246,A)
NAC=(196,200,210,A); NACD=(150,154,164,A)
def rect(im,x,y,w,h,c):
    for dy in range(h):
        for dx in range(w):
            if 0<=x+dx<W and 0<=y+dy<H: im.putpixel((x+dx,y+dy),c)
im=Image.new("RGBA",(W,H),(0,0,0,0))
# Fundament
rect(im,24,120,16,6,OUT_C); rect(im,26,121,12,4,(120,124,132,A))
# Turm (leicht verjuengt nach oben), Nabe bei y~28
for y in range(30,122):
    t=(y-30)/(122-30)
    hw=int(2+ t*3)          # Halbbreite 2..5
    cx=32
    rect(im,cx-hw,y,hw*2,1,TW)
    rect(im,cx-hw,y,1,1,TWL); rect(im,cx+hw-1,y,1,1,TWD)
# Gondel (Nacelle) am oberen Ende
rect(im,25,24,16,9,OUT_C); rect(im,26,25,14,7,NAC); rect(im,26,25,14,2,TWL); rect(im,26,30,14,2,NACD)
# Nabe (Hub) vorn
rect(im,29,26,6,6,OUT_C); rect(im,30,27,4,4,TWD)
im.save(OUT+"/mach_windrad.png"); print("windrad ok", im.size)
