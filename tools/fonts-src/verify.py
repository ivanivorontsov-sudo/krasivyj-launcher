import json, glob, os
from fontTools.ttLib import TTFont
RU = "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя"
EXTRA = "«»—–№…"
LAT = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789:.,!?"
out={}
for d in sorted(os.listdir("raw")):
    for f in sorted(glob.glob(f"raw/{d}/*.ttf")):
        t=TTFont(f, lazy=True); cmap=t.getBestCmap()
        miss_ru=[c for c in RU if ord(c) not in cmap]
        miss_lat=[c for c in LAT if ord(c) not in cmap]
        miss_ex=[c for c in EXTRA if ord(c) not in cmap]
        out[f]={"ru_missing":"".join(miss_ru),"lat_missing":"".join(miss_lat),"extra_missing":"".join(miss_ex),"glyphs":len(cmap),"size":os.path.getsize(f)}
        ok = not miss_ru and not miss_lat
        print(("OK  " if ok else "NO  ")+f, "ru_missing=%d"%len(miss_ru), "lat_missing=%d"%len(miss_lat), "extra_missing="+"".join(miss_ex), os.path.getsize(f)//1024,"KB")
json.dump(out,open("coverage.json","w"),ensure_ascii=False,indent=1)
