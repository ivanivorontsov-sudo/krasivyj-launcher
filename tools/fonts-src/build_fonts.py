"""Subset verified fonts to Latin + Cyrillic and write res/font + catalog.
Every font is re-verified (all 66 Russian letters) after subsetting; failure aborts."""
import os, json, io
from fontTools.ttLib import TTFont
from fontTools import subset
from fontTools.varLib import instancer
RU="АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя"
OUT="../../app/src/main/res/font"; os.makedirs(OUT,exist_ok=True)
UNI="U+0020-007E,U+00A0-00FF,U+0131,U+0152-0153,U+02C6,U+02DA,U+02DC,U+2010-2027,U+2030-203A,U+20AC,U+20BD,U+2116,U+2122,U+0400-045F,U+0490-0491"
# id, family dir, file, {weight: axis value or None}, display name, category, designer
H="handwritten"; D="display"; C="clean"; M="mono"; S="serif"
F=[
 ("caveat","caveat","Caveat[wght].ttf",[400],"Caveat",H,"Impallari Type"),
 ("marck_script","marckscript","MarckScript-Regular.ttf",[None],"Marck Script",H,"Denis Masharov"),
 ("bad_script","badscript","BadScript-Regular.ttf",[None],"Bad Script",H,"Gaslight"),
 ("neucha","neucha","Neucha.ttf",[None],"Neucha",H,"Jovanny Lemonad"),
 ("amatic_sc","amaticsc","AmaticSC-Regular.ttf",[None],"Amatic SC",H,"Vernon Adams, Ben Nathan"),
 ("pacifico","pacifico","Pacifico-Regular.ttf",[None],"Pacifico",H,"Vernon Adams, Jacques Le Bailly"),
 ("lobster","lobster","Lobster-Regular.ttf",[None],"Lobster",H,"Impallari Type"),
 ("pangolin","pangolin","Pangolin-Regular.ttf",[None],"Pangolin",H,"Kevin Burke"),
 ("shantell_sans","shantellsans","ShantellSans[BNCE,INFM,SPAC,wght].ttf",[400],"Shantell Sans",H,"Shantell Martin, Arrow Type"),
 ("great_vibes","greatvibes","GreatVibes-Regular.ttf",[None],"Great Vibes",H,"TypeSETit"),
 ("comforter","comforter","Comforter-Regular.ttf",[None],"Comforter",H,"Robert Leuschke"),
 ("balsamiq_sans","balsamiqsans","BalsamiqSans-Regular.ttf",[None],"Balsamiq Sans",H,"Balsamiq, Michael Angeles"),
 ("klee_one","kleeone","KleeOne-Regular.ttf",[None],"Klee One",H,"Fontworks"),
 ("underdog","underdog","Underdog-Regular.ttf",[None],"Underdog",H,"Sorkin Type"),
 ("marmelad","marmelad","Marmelad-Regular.ttf",[None],"Marmelad",H,"Cyreal"),
 ("pattaya","pattaya","Pattaya-Regular.ttf",[None],"Pattaya",H,"Cadson Demak"),
 ("gabriela","gabriela","Gabriela-Regular.ttf",[None],"Gabriela",H,"Eduardo Tunni"),
 ("ruslan_display","ruslandisplay","RuslanDisplay-Regular.ttf",[None],"Ruslan Display",D,"Oleg Pospelov"),
 ("yeseva_one","yesevaone","YesevaOne-Regular.ttf",[None],"Yeseva One",D,"Jovanny Lemonad"),
 ("poiret_one","poiretone","PoiretOne-Regular.ttf",[None],"Poiret One",D,"Denis Masharov"),
 ("cormorant","cormorantgaramond","CormorantGaramond[wght].ttf",[400],"Cormorant Garamond",S,"Christian Thalmann"),
 ("oranienbaum","oranienbaum","Oranienbaum-Regular.ttf",[None],"Oranienbaum",S,"Oleg Pospelov"),
 ("kelly_slab","kellyslab","KellySlab-Regular.ttf",[None],"Kelly Slab",D,"Denis Masharov"),
 ("russo_one","russoone","RussoOne-Regular.ttf",[None],"Russo One",D,"Jovanny Lemonad"),
 ("unbounded","unbounded","Unbounded[wght].ttf",[400],"Unbounded",D,"NaN"),
 ("stalinist_one","stalinistone","StalinistOne-Regular.ttf",[None],"Stalinist One",D,"Alexey Maslov, Jovanny Lemonad"),
 ("press_start","pressstart2p","PressStart2P-Regular.ttf",[None],"Press Start 2P",M,"CodeMan38"),
 ("tiny5","tiny5","Tiny5-Regular.ttf",[None],"Tiny5",M,"Stefan Schmidt"),
 ("jetbrains_mono","jetbrainsmono","JetBrainsMono[wght].ttf",[400],"JetBrains Mono",M,"JetBrains"),
 ("plex_mono","ibmplexmono","IBMPlexMono-Regular.ttf",[None],"IBM Plex Mono",M,"Mike Abbink, Bold Monday"),
 ("inter","inter","Inter[opsz,wght].ttf",[300,400,600],"Inter",C,"Rasmus Andersson"),
 ("open_sans","opensans","OpenSans[wdth,wght].ttf",[300,400,600],"Open Sans",C,"Steve Matteson"),
 ("manrope","manrope","Manrope[wght].ttf",[400],"Manrope",C,"Mikhail Sharanda"),
 ("nunito","nunito","Nunito[wght].ttf",[400],"Nunito",C,"Vernon Adams, Cyreal"),
 ("comfortaa","comfortaa","Comfortaa[wght].ttf",[400],"Comfortaa",C,"Johan Aakerlund"),
 ("golos","golostext","GolosText[wght].ttf",[400],"Golos Text",C,"Paratype"),
 ("jura","jura","Jura[wght].ttf",[400],"Jura",C,"Daniel Johnson"),
 ("exo2","exo2","Exo2[wght].ttf",[400],"Exo 2",C,"Natanael Gama"),
 ("tektur","tektur","Tektur[wdth,wght].ttf",[400],"Tektur",D,"Adam Jagosz"),
 ("philosopher","philosopher","Philosopher-Regular.ttf",[None],"Philosopher",C,"Jovanny Lemonad"),
]
# Fonts whose OFL declares a Reserved Font Name are shipped UNMODIFIED (no subsetting/instancing),
# because an OFL "Modified Version" may not carry the reserved name.
RFN={"marck_script","lobster","underdog","gabriela","ruslan_display","yeseva_one","oranienbaum","kelly_slab","russo_one","stalinist_one","press_start","plex_mono","comfortaa"}
import shutil
cat=[]; total=0
for fid,d,fn,weights,name,catg,designer in F:
    src=f"raw/{d}/{fn}"
    files=[]
    if fid in RFN:
        f=TTFont(src); cmap=f.getBestCmap(); assert all(ord(c) in cmap for c in RU), fid
        rn=f"f_{fid}"; shutil.copyfile(src,f"{OUT}/{rn}.ttf"); sz=os.path.getsize(src); total+=sz
        files.append({"res":rn,"weight":weights[0] or 400,"bytes":sz,"variable":"fvar" in f})
        weights=[]
    for w in weights:
        f=TTFont(src)
        if "fvar" in f:
            axes={a.axisTag:a.defaultValue for a in f["fvar"].axes}
            if w: axes["wght"]=w
            if "opsz" in axes: axes["opsz"]=14 if (w or 400)<500 else 28
            if "wdth" in axes: axes["wdth"]=100
            f=instancer.instantiateVariableFont(f,axes)
        opts=subset.Options(); opts.layout_features=["kern","liga","calt","clig","locl","mark","mkmk","ccmp","rlig","rclt"]
        opts.hinting=False; opts.name_IDs=[0,1,2,3,4,5,6,13,14]; opts.notdef_outline=True; opts.drop_tables+=["DSIG","STAT"]
        s=subset.Subsetter(opts); s.populate(unicodes=subset.parse_unicodes(UNI)); s.subset(f)
        cmap=f.getBestCmap(); miss=[c for c in RU if ord(c) not in cmap]
        assert not miss, (fid,miss)
        rn=f"f_{fid}" + (f"_{w}" if len(weights)>1 else "")
        out=f"{OUT}/{rn}.ttf"; f.save(out); sz=os.path.getsize(out); total+=sz
        files.append({"res":rn,"weight":w or 400,"bytes":sz})
    lic=open(f"raw/{d}/OFL.txt",encoding="utf-8",errors="replace").read() if os.path.exists(f"raw/{d}/OFL.txt") else ""
    copyright_line=next((l.strip() for l in lic.splitlines() if l.strip().lower().startswith("copyright")),"")
    cat.append({"unmodified":fid in RFN,"id":fid,"name":name,"category":catg,"designer":designer,"license":"SIL Open Font License 1.1","copyright":copyright_line,"files":files})
    print(f"{fid:16s} {sum(x['bytes'] for x in files)//1024:5d} KB  {copyright_line[:70]}")
json.dump(cat,open("catalog.json","w"),ensure_ascii=False,indent=1)
print("TOTAL",total//1024,"KB", len(cat),"families")
