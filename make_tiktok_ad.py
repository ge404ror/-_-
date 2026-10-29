from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math, os, subprocess

W, H, FPS, SECONDS = 720, 1280, 18, 24
OUT = os.path.join(os.path.dirname(__file__), "LastFrequency_TikTok.mp4")
FRAMES = os.path.join(os.path.dirname(__file__), "_promo_frames")
os.makedirs(FRAMES, exist_ok=True)
FONT = "C:/Windows/Fonts/arial.ttf"
BOLD = "C:/Windows/Fonts/arialbd.ttf"

def font(size, bold=False): return ImageFont.truetype(BOLD if bold else FONT, size)
def mix(a,b,t): return tuple(round(x+(y-x)*t) for x,y in zip(a,b))
def ease(x): return max(0,min(1,x))**2*(3-2*max(0,min(1,x)))

def centered(draw, text, y, f, color, stroke=0):
    box=draw.textbbox((0,0),text,font=f,stroke_width=stroke)
    draw.text(((W-(box[2]-box[0]))/2,y),text,font=f,fill=color,stroke_width=stroke,stroke_fill=(0,0,0,180))

def card(draw, box, title, sub, accent, icon="♪", progress=.42):
    x1,y1,x2,y2=box
    draw.rounded_rectangle(box, radius=28, fill=(28,20,25,242), outline=(92,47,59,255), width=2)
    draw.rounded_rectangle((x1+20,y1+20,x1+108,y1+108),radius=20,fill=accent)
    centered_local=draw.textbbox((0,0),icon,font=font(43,True))
    draw.text((x1+64-(centered_local[2]-centered_local[0])/2,y1+35),icon,font=font(43,True),fill=(255,255,255))
    draw.text((x1+128,y1+27),title,font=font(24,True),fill=(255,246,248))
    draw.text((x1+128,y1+63),sub,font=font(17),fill=(197,166,175))
    py=y2-27; draw.rounded_rectangle((x1+24,py,x2-24,py+5),radius=4,fill=(82,63,71))
    draw.rounded_rectangle((x1+24,py,x1+24+(x2-x1-48)*progress,py+5),radius=4,fill=accent)

def playlist_cover(draw, box):
    x1,y1,x2,y2=box; s=x2-x1
    draw.rounded_rectangle(box,radius=22,fill=(47,24,35),outline=(220,24,52),width=2)
    colors=[(255,89,101),(255,174,82),(132,77,192),(46,155,174)]
    for i,c in enumerate(colors):
        x=x1+12+(i%2)*(s//2-5); y=y1+12+(i//2)*(s//2-5); q=s//2-9
        draw.rounded_rectangle((x,y,x+q,y+q),radius=12,fill=c)
        draw.ellipse((x+q*.24,y+q*.2,x+q*.76,y+q*.72),fill=(20,16,23))
        draw.arc((x+q*.33,y+q*.28,x+q*.67,y+q*.62),0,300,fill=(255,240,241),width=4)

def make_frame(t):
    im=Image.new("RGB",(W,H),(10,9,11))
    grad=Image.new("RGBA",(W,H),(0,0,0,0)); gd=ImageDraw.Draw(grad)
    for y in range(H):
        k=1-abs(y-H*.42)/(H*.8); k=max(0,k)
        gd.line((0,y,W,y),fill=(91,10,27,int(78*k)))
    im=Image.alpha_composite(im.convert("RGBA"),grad)
    glow=Image.new("RGBA",(W,H),(0,0,0,0)); g=ImageDraw.Draw(glow)
    g.ellipse((80,45,640,620),fill=(138,10,37,45))
    glow=glow.filter(ImageFilter.GaussianBlur(105)); im=Image.alpha_composite(im,glow)
    d=ImageDraw.Draw(im,"RGBA")
    # Beat-ring motif and subtle sound bars.
    cx,cy=W//2,260
    pulse=1+0.025*math.sin(t*math.pi*2)
    for rr,alpha,width in [(142*pulse,40,2),(126,85,3),(110,35,1)]:
        d.ellipse((cx-rr,cy-rr,cx+rr,cy+rr),outline=(220,26,57,alpha),width=width)
    for i in range(31):
        a=i*math.tau/31
        r1=130; r2=r1+8+20*(.5+.5*math.sin(t*7+i*1.8))
        d.line((cx+math.cos(a)*r1,cy+math.sin(a)*r1,cx+math.cos(a)*r2,cy+math.sin(a)*r2),fill=(227,43,69,120),width=3)
    # Brand mark
    d.ellipse((cx-40,cy-40,cx+40,cy+40),outline=(255,68,90,255),width=4)
    d.line((cx-23,cy+11,cx-3,cy-19,cx+2,cy+5,cx+25,cy-13),fill=(255,232,235,255),width=5,joint="curve")
    # Scene-specific copy/cards, with clean transitions.
    scenes=[
      (0,3,"НАЙДИ СЛЕДУЮЩИЙ ТРЕК","Умный поиск","Поиск треков по названию и исполнителю","search"),
      (3,6,"СОБЕРИ ВСЁ В ОДНОМ МЕСТЕ","Импорт плейлистов","Переноси музыку с разных платформ","import"),
      (6,9,"ТЕКСТ — ПРЯМО ВО ВРЕМЯ ИГРЫ","Сейчас играет","Смотри текст и следи за текущей строкой","lyrics"),
      (9,12,"ОДИН КЛИК — ДРУГАЯ ТЕМА","Настрой под себя","Меняй оформление в пару касаний","themes"),
      (12,15,"МУЗЫКА ВСЕГДА ПОД РУКОЙ","Мини-плеер","Компактный и расширенный режимы","mini"),
      (15,18,"ПОКА ТЫ СЛУШАЕШЬ","Discord Rich Presence","Обложка, трек и прогресс — в статусе","discord"),
      (18,21,"ПОИСКАЙ. НАЙДИ. СЛУШАЙ.","Онлайн и плейлисты","Треки и коллекции — в одном плеере","online"),
      (21,24,"LAST FREQUENCY","ТВОЯ МУЗЫКА. ТВОЙ СТИЛЬ.","Включай и слушай по-своему.","end"),
    ]
    scene=next(s for s in scenes if s[0]<=t<s[1]); start,end,headline,title,sub,kind=scene
    local=(t-start)/(end-start); fade=ease(min(local*4,(1-local)*4))
    y=455+int((1-fade)*24)
    color=(255,255,255,int(255*fade)); accent=(220,24,52)
    if kind=="themes": accent=(52,218,129) if local>.60 else (107,104,255)
    card_y=800+int((1-fade)*38)
    centered(d,headline,y,font(27,True),color)
    centered(d,title,y+59,font(40 if kind!="end" else 45,True),(255,62,83,int(255*fade)))
    centered(d,sub,y+119,font(20), (222,206,211,int(245*fade)))
    if kind in ("online","playlist","lyrics","style","import","themes"):
        content={"online":("Black Knife","Toby Fox  ·  SoundCloud","♪"),"playlist":("Добро и позитив","10 треков  ·  твоя коллекция","♫"),"lyrics":("Сейчас играет","♪  текст песни","♫"),"style":("LAST FREQUENCY","Maretu  ·  мини-плеер  ·  Discord","✦"),"import":("ТВОИ ПЛЕЙЛИСТЫ","Spotify · Apple Music · Яндекс","⇄"),"themes":("ТЕМЫ","Bloodline  ·  Maretu  ·  Neon","✦")}[kind]
        if kind=="import":
            d.rounded_rectangle((42,card_y-8,678,card_y+166),radius=28,fill=(28,20,25,242),outline=(92,47,59,255),width=2)
            playlist_cover(d,(62,card_y+10,194,card_y+142))
            d.text((216,card_y+22),"ТВОИ ПЛЕЙЛИСТЫ",font=font(23,True),fill=(255,246,248))
            d.text((216,card_y+61),"Добро и позитив",font=font(19,True),fill=(255,80,102))
            d.text((216,card_y+94),"Spotify  ·  Apple Music  ·  Яндекс",font=font(14),fill=(197,166,175))
            d.rounded_rectangle((216,card_y+126,632,card_y+132),radius=4,fill=(220,24,52))
        elif kind=="lyrics":
            # Inspired by the user's reference: playback artwork and controls at left,
            # large scrolling lyric lines with one highlighted word at right.
            d.rounded_rectangle((34,card_y-26,686,card_y+190),radius=27,fill=(25,20,24,236),outline=(88,58,67,255),width=2)
            playlist_cover(d,(54,card_y+3,196,card_y+145))
            d.text((54,card_y+151),"СЕЙЧАС ИГРАЕТ",font=font(11,True),fill=(255,67,91))
            d.text((54,card_y+168),"Твой новый трек",font=font(16,True),fill=(255,245,247))
            lines=["Сквозь ночь я слышу музыку", "И каждый звук ведёт вперёд", "Пока мелодия со мной"]
            for j,line in enumerate(lines):
                yy=card_y+4+j*48; col=(162,153,159) if j!=1 else (255,248,250)
                d.text((220,yy),line,font=font(18,True),fill=col)
            d.rounded_rectangle((399,card_y+53,457,card_y+83),radius=5,fill=(74,75,222,220))
            d.text((405,card_y+55),"звук",font=font(17,True),fill=(255,255,255))
            d.rounded_rectangle((54,card_y+215,196,card_y+220),radius=4,fill=(75,62,67))
            d.rounded_rectangle((54,card_y+215,102,card_y+220),radius=4,fill=(255,75,97))
        elif kind=="themes":
            d.rounded_rectangle((52,card_y-14,668,card_y+176),radius=26,fill=(28,20,25,245),outline=(92,47,59,255),width=2)
            choices=[("Bloodline",(218,30,55)),("Maretu",(240,240,240)),("Neon",(125,78,255))]
            for j,(name,c) in enumerate(choices):
                x=72+j*188; selected=(j==0 if local<.58 else j==2)
                d.rounded_rectangle((x,card_y+6,x+166,card_y+111),radius=18,fill=(25,21,26),outline=c if selected else (70,58,64),width=3 if selected else 1)
                d.rounded_rectangle((x+9,card_y+15,x+157,card_y+91),radius=12,fill=tuple(int(v*.48) for v in c))
                d.text((x+14,card_y+117),name,font=font(15,True),fill=(255,245,248))
            # Cursor moves to Neon and clicks; accent and outline change with it.
            target_x=162 if local<.58 else 538; cursor_x=int(162+(538-162)*ease((local-.36)/.3))
            cursor_y=card_y+63
            d.polygon([(cursor_x,cursor_y),(cursor_x+4,cursor_y+23),(cursor_x+9,cursor_y+15),(cursor_x+19,cursor_y+20),(cursor_x+22,cursor_y+16),(cursor_x+12,cursor_y+10),(cursor_x+20,cursor_y+6)],fill=(255,255,255),outline=(10,10,10))
            if .55<local<.72:
                d.ellipse((target_x-9,cursor_y-6,target_x+9,cursor_y+12),outline=(255,255,255,210),width=3)
        else:
            card(d,(56,card_y,664,card_y+145),content[0],content[1],accent,content[2],min(.88,local*.7))
    if kind=="search":
        d.rounded_rectangle((76,790,644,870),radius=30,fill=(35,25,30),outline=(117,51,68),width=2)
        d.ellipse((103,815,126,838),outline=(206,182,190),width=3);d.line((124,836,135,848),fill=(206,182,190),width=3)
        d.text((154,809),"Название трека или исполнитель…",font=font(19),fill=(208,185,193))
        for i,title in enumerate(["Track One — Artist","Track Two — Artist"]):
            yy=884+i*57; d.rounded_rectangle((76,yy,644,yy+48),radius=14,fill=(35,27,32),outline=(75,52,61))
            d.text((98,yy+11),title,font=font(17,True),fill=(245,232,236))
    if kind=="mini":
        d.rounded_rectangle((46,card_y-18,674,card_y+180),radius=24,fill=(25,20,24,240),outline=(88,58,67,255),width=2)
        d.text((72,card_y),"КОМПАКТНЫЙ",font=font(15,True),fill=(205,181,189))
        d.rounded_rectangle((70,card_y+30,650,card_y+88),radius=19,fill=(47,31,38),outline=(107,58,71))
        playlist_cover(d,(82,card_y+38,132,card_y+80))
        d.text((148,card_y+46),"Трек играет",font=font(17,True),fill=(255,246,248))
        d.text((555,card_y+45),"◀   ❚❚   ▶",font=font(15,True),fill=(255,246,248))
        d.text((72,card_y+106),"РАСШИРЕННЫЙ",font=font(15,True),fill=(205,181,189))
        d.rounded_rectangle((70,card_y+136,650,card_y+164),radius=14,fill=(47,31,38),outline=(107,58,71))
        d.text((88,card_y+140),"♫  Обложка   Трек и исполнитель     ◀  ❚❚  ▶",font=font(16,True),fill=(255,246,248))
    if kind=="discord":
        d.rounded_rectangle((52,card_y-8,668,card_y+168),radius=26,fill=(39,40,45,250),outline=(87,88,96,255),width=2)
        d.text((78,card_y+12),"Current activity",font=font(14),fill=(192,193,200))
        playlist_cover(d,(78,card_y+42,205,card_y+151))
        d.text((225,card_y+45),"Listening to LAST FREQUENCY",font=font(16,True),fill=(245,245,248))
        d.text((225,card_y+79),"Track One — Artist",font=font(18,True),fill=(255,255,255))
        d.text((225,card_y+108),"LAST FREQUENCY · YouTube Music",font=font(14),fill=(205,206,212))
        d.rounded_rectangle((225,card_y+140,632,card_y+146),radius=4,fill=(84,85,92))
        d.rounded_rectangle((225,card_y+140,365,card_y+146),radius=4,fill=(88,205,130))
    # Footer progress marks
    d.rounded_rectangle((80,1197,640,1202),radius=4,fill=(54,41,47))
    d.rounded_rectangle((80,1197,80+560*(t/SECONDS),1202),radius=4,fill=accent)
    centered(d,"LAST FREQUENCY",1220,font(15,True),(178,148,157,205))
    return im

for i in range(FPS*SECONDS):
    make_frame(i/FPS).save(os.path.join(FRAMES,f"{i:04d}.png"), optimize=True)

ffmpeg=os.path.join(os.path.dirname(__file__),"ffmpeg","bin","ffmpeg.exe")
cmd=[ffmpeg,"-y","-framerate",str(FPS),"-i",os.path.join(FRAMES,"%04d.png"),"-f","lavfi","-i","sine=frequency=110:duration=24:sample_rate=44100","-filter_complex","[1:a]volume=0.025,lowpass=f=500,afade=t=in:st=0:d=1,afade=t=out:st=22:d=2[a]","-map","0:v","-map","[a]","-c:v","libx264","-preset","fast","-crf","21","-pix_fmt","yuv420p","-c:a","aac","-b:a","96k","-shortest","-movflags","+faststart",OUT]
subprocess.run(cmd,check=True)
print(OUT)
