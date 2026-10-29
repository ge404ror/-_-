from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math, os, subprocess

W,H,FPS,DURATION=1280,720,24,24
ROOT=os.path.dirname(__file__); FRAMES=os.path.join(ROOT,"_promo_wide_frames")
OUT=os.path.join(ROOT,"LastFrequency_Promo_Wide.mp4")
os.makedirs(FRAMES,exist_ok=True)
REG="C:/Windows/Fonts/arial.ttf"; BOLD="C:/Windows/Fonts/arialbd.ttf"
def f(size,b=False): return ImageFont.truetype(BOLD if b else REG,size)
def ease(x): x=max(0,min(1,x)); return x*x*(3-2*x)
def textc(d,s,y,size,color=(250,247,248),bold=True):
    box=d.textbbox((0,0),s,font=f(size,bold)); d.text(((W-(box[2]-box[0]))/2,y),s,font=f(size,bold),fill=color)
def rr(d,box,r,fill,outline=None,w=1): d.rounded_rectangle(box,radius=r,fill=fill,outline=outline,width=w)

def backdrop(t,accent=(203,22,48)):
    im=Image.new("RGB",(W,H),(5,5,7)); haze=Image.new("RGBA",(W,H),(0,0,0,0)); d=ImageDraw.Draw(haze)
    d.ellipse((W*.25,H*.1,W*.75,H*1.2),fill=(*accent,42))
    haze=haze.filter(ImageFilter.GaussianBlur(110)); im=Image.alpha_composite(im.convert("RGBA"),haze)
    # subtly animated audio waveform
    d=ImageDraw.Draw(im,"RGBA"); cx,cy=W//2,95
    for i in range(52):
        x=70+i*22; amp=5+14*(.5+.5*math.sin(t*4+i*.74))
        d.line((x,cy-amp,x,cy+amp),fill=(*accent,48),width=2)
    return im

def logo(d,x,y,size=70,color=(248,241,244)):
    d.ellipse((x-size//2,y-size//2,x+size//2,y+size//2),outline=color,width=max(2,size//17))
    pts=[(x-size*.27,y+size*.2),(x-size*.05,y-size*.24),(x+size*.02,y+size*.12),(x+size*.29,y-size*.16)]
    d.line(pts,fill=color,width=max(3,size//11),joint="curve")

def cover(d,x,y,s):
    rr(d,(x,y,x+s,y+s),14,(56,25,43),(130,48,71),2)
    cols=[(233,46,76),(255,166,88),(113,76,198),(52,153,180)]
    for i,c in enumerate(cols):
        xx=x+7+(i%2)*(s//2-3); yy=y+7+(i//2)*(s//2-3); q=s//2-6
        rr(d,(xx,yy,xx+q,yy+q),8,c)
        d.ellipse((xx+q*.3,yy+q*.3,xx+q*.7,yy+q*.7),outline=(255,246,248),width=2)

def app_window(d,box,accent=(214,32,57)):
    x1,y1,x2,y2=box; rr(d,box,18,(17,17,20,246),(76,72,78),2)
    rr(d,(x1,y1,x2,y1+31),18,(29,28,32,255))
    for i,c in enumerate(((232,70,80),(235,181,68),(80,199,116))): d.ellipse((x1+15+i*18,y1+10,x1+25+i*18,y1+20),fill=c)
    return x1,y1,x2,y2,accent

def screen_search(d,box):
    x1,y1,x2,y2,_=app_window(d,box); d.text((x1+24,y1+48),"Найди следующий трек",font=f(23,True),fill=(255,250,251))
    rr(d,(x1+24,y1+84,x2-24,y1+126),19,(40,38,43),(75,71,77)); d.text((x1+45,y1+94),"⌕  Название трека или исполнитель…",font=f(17),fill=(188,182,190))
    for i,(name,artist) in enumerate([("Track One","Artist One"),("Track Two","Artist Two"),("Track Three","Artist Three")]):
        yy=y1+144+i*44; cover(d,x1+28,yy,32); d.text((x1+72,yy+1),name,font=f(15,True),fill=(245,241,243)); d.text((x1+72,yy+21),artist,font=f(12),fill=(160,155,163))

def screen_lyrics(d,box):
    x1,y1,x2,y2,_=app_window(d,box); cover(d,x1+38,y1+62,190)
    d.text((x1+40,y1+270),"СЕЙЧАС ИГРАЕТ",font=f(11,True),fill=(255,63,86));d.text((x1+40,y1+292),"Твой новый трек",font=f(20,True),fill=(250,246,248));d.text((x1+40,y1+320),"Исполнитель",font=f(14),fill=(176,168,176))
    rr(d,(x1+40,y1+356,x1+230,y1+362),3,(90,83,91));rr(d,(x1+40,y1+356,x1+108,y1+362),3,(230,36,61))
    d.text((x1+300,y1+59),"Я слышу музыку сквозь ночь",font=f(20,True),fill=(155,150,157))
    d.text((x1+300,y1+111),"И каждый звук ведёт вперёд",font=f(22,True),fill=(255,250,251))
    rr(d,(x1+433,y1+110,x1+479,y1+140),5,(83,77,230));d.text((x1+438,y1+113),"звук",font=f(17,True),fill=(255,255,255))
    d.text((x1+300,y1+163),"Пока мелодия со мной",font=f(20,True),fill=(155,150,157))
    d.text((x1+300,y1+218),"Текст песни синхронизируется с музыкой",font=f(14),fill=(144,140,148))

def screen_themes(d,box,t):
    x1,y1,x2,y2,_=app_window(d,box); d.text((x1+25,y1+45),"Выбери оформление",font=f(20,True),fill=(250,246,248))
    opts=[("Bloodline",(218,30,55)),("Maretu",(231,231,231)),("Neon",(112,79,247))]
    for i,(name,c) in enumerate(opts):
        xx=x1+26+i*180; selected=(i==0 if t<.53 else i==2)
        rr(d,(xx,y1+82,xx+158,y1+190),14,(29,27,31),c if selected else (66,63,70),3 if selected else 1)
        rr(d,(xx+8,y1+90,xx+150,y1+160),10,tuple(int(v*.52) for v in c))
        d.text((xx+12,y1+168),name,font=f(15,True),fill=(240,236,240))
    tx=x1+101 if t<.53 else x1+461; ty=y1+130
    d.polygon([(tx,ty),(tx+5,ty+24),(tx+11,ty+15),(tx+22,ty+22),(tx+25,ty+18),(tx+14,ty+10),(tx+22,ty+5)],fill=(255,255,255),outline=(0,0,0))
    if .48<t<.68: d.ellipse((tx-12,ty-7,tx+10,ty+15),outline=(255,255,255),width=2)

def scene(t):
    beats=[(0,3,"platform"),(3,5,"wipe"),(5,8,"brand"),(8,12,"lyrics"),(12,16,"search"),(16,19,"mini"),(19,21.5,"themes"),(21.5,24,"end")]
    a,b,key=next(q for q in beats if q[0]<=t<q[1]); u=(t-a)/(b-a); fade=ease(min(u*4,(1-u)*4))
    accent=(218,27,51)
    if key=="themes": accent=(112,79,247) if u>.53 else (218,30,55)
    im=backdrop(t,accent); d=ImageDraw.Draw(im,"RGBA")
    if key=="platform":
        # music-source marks enter one by one against the dark background
        labels=[("SoundCloud",(255,102,32)),("YouTube Music",(255,45,67)),("Spotify",(32,190,98))]
        for i,(name,c) in enumerate(labels):
            yy=135+i*150; x=270+int((1-ease(u*2.8-i*.55))*100)
            rr(d,(x,yy,x+740,yy+108),24,(18,17,20,220),(45,42,47),1)
            d.ellipse((x+34,yy+20,x+102,yy+88),fill=c)
            d.text((x+134,yy+29),name,font=f(31,True),fill=(250,247,249))
            d.text((x+620,yy+35),"♫",font=f(35,True),fill=(*c,245))
    elif key=="wipe":
        x=int(-80+(W+160)*u); d.rounded_rectangle((x-25,330,x+25,390),radius=25,fill=(255,255,255,255))
        # glowing center line streak
        glow=Image.new("RGBA",(W,H),(0,0,0,0)); gd=ImageDraw.Draw(glow); gd.rounded_rectangle((0,350,W,368),radius=9,fill=(255,255,255,230)); im=Image.alpha_composite(im,glow.filter(ImageFilter.GaussianBlur(22))); d=ImageDraw.Draw(im,"RGBA")
        d.rounded_rectangle((0,356,W,362),radius=4,fill=(255,255,255,210))
    elif key=="brand":
        logo(d,W//2-205,340,90); d.text((W//2-130,287),"LAST FREQUENCY",font=f(55,True),fill=(255,252,253));textc(d,"Музыкальный плеер для твоего потока",372,20,(188,179,185))
    elif key=="lyrics":
        textc(d,"ТЕКСТ ПЕСНИ — В РИТМЕ МУЗЫКИ",74,25,(248,241,245)); screen_lyrics(d,(205,130,1075,620))
    elif key=="search":
        textc(d,"ПОИСК ТРЕКОВ И ПЛЕЙЛИСТОВ",75,25,(248,241,245)); screen_search(d,(245,145,1035,585))
    elif key=="mini":
        textc(d,"ПЛЕЕР ВСЕГДА ПОД РУКОЙ",68,26,(248,241,245))
        # Compact and expanded mini-player examples side-by-side
        rr(d,(75,220,610,430),24,(18,17,20,238),(72,66,74),2);d.text((105,245),"КОМПАКТНЫЙ",font=f(18,True),fill=(193,185,193));cover(d,104,290,82);d.text((205,301),"Трек играет",font=f(24,True),fill=(255,250,251));d.text((205,336),"Исполнитель",font=f(17),fill=(170,164,172));d.text((455,310),"◀  ❚❚  ▶",font=f(22,True),fill=(255,255,255))
        rr(d,(670,220,1205,430),24,(18,17,20,238),(72,66,74),2);d.text((700,245),"РАСШИРЕННЫЙ",font=f(18,True),fill=(193,185,193));cover(d,700,290,82);d.text((800,296),"Обложка · трек",font=f(21,True),fill=(255,250,251));d.text((800,331),"исполнитель и управление",font=f(16),fill=(170,164,172));rr(d,(800,375,1155,381),3,(81,74,83));rr(d,(800,375,958,381),3,(225,37,62))
    elif key=="themes":
        textc(d,"ОДИН КЛИК — ДРУГОЕ НАСТРОЕНИЕ",68,25,(248,241,245));screen_themes(d,(335,185,945,490),u)
    elif key=="end":
        logo(d,W//2-175,335,78);d.text((W//2-112,303),"LAST FREQUENCY",font=f(48,True),fill=(255,252,253));textc(d,"ТВОЯ МУЗЫКА. ТВОЙ СТИЛЬ.",390,23,(210,202,208))
    # Short, soft text/scene fade and fine progress line.
    d=ImageDraw.Draw(im,"RGBA");d.rectangle((0,H-4,int(W*t/DURATION),H),fill=(235,38,64,220))
    return im.convert("RGB")

for i in range(FPS*DURATION): scene(i/FPS).save(os.path.join(FRAMES,f"{i:04d}.jpg"),quality=90)
ffmpeg=os.path.join(ROOT,"ffmpeg","bin","ffmpeg.exe")
cmd=[ffmpeg,"-y","-framerate",str(FPS),"-i",os.path.join(FRAMES,"%04d.jpg"),"-f","lavfi","-i","sine=frequency=110:duration=24:sample_rate=44100","-filter_complex","[1:a]volume=0.02,lowpass=f=500,afade=t=in:st=0:d=1,afade=t=out:st=22:d=2[a]","-map","0:v","-map","[a]","-c:v","libx264","-preset","fast","-crf","20","-pix_fmt","yuv420p","-c:a","aac","-b:a","96k","-shortest","-movflags","+faststart",OUT]
subprocess.run(cmd,check=True); print(OUT)
