import json, subprocess, concurrent.futures as cf, urllib.request, os
dirs = """caveat caveatbrush marckscript badscript neucha amaticsc pacifico lobster kellyslab pangolin shantellsans greatvibes
comfortaa comforter comforterbrush kurale underdog gabriela ruslandisplay poiretone balsamiqsans pattaya kleeone playpensans
dancingscript kaushanscript yesevaone pressstart2p unbounded manrope nunito cormorantgaramond russoone philosopher rubik marmelad jura
montserratalternates alice rubikwetpaint rubikdoodleshadow lobstertwo sacramento ballet mansalva handlee bellota didactgothic
inter opensans jetbrainsmono ibmplexmono vt323 orbitron silkscreen pixelifysans tiny5 sixtyfour oi ruslandisplay podkova forum
oranienbaum prostoone seymourone sofiasans exo2 tektur geologica onest golostext ubuntu firasans firacode spacemono
cuprum ptsans ptmono neuchasans amatic yanonekaffeesatz kellyslab stalinistone bad yesteryear parisienne allura alexbrush
greatvibes playwritepl playwritecz playwriteit playwritees caveat""".split()
dirs = sorted(set(dirs))
def ls(d):
    for lic in ("ofl","apache","ufl"):
        r = subprocess.run(["gh","api",f"repos/google/fonts/contents/{lic}/{d}"],capture_output=True,text=True)
        if r.returncode==0:
            return d, lic, [x["name"] for x in json.loads(r.stdout)]
    return d, None, []
os.makedirs("raw",exist_ok=True)
res={}
with cf.ThreadPoolExecutor(16) as ex:
    for d,lic,files in ex.map(ls,dirs):
        if not lic: print("MISSING",d); continue
        ttfs=[f for f in files if f.endswith(".ttf") and "Italic" not in f]
        res[d]={"license":lic,"files":ttfs,"licfile":[f for f in files if f in("OFL.txt","LICENSE.txt","UFL.txt")]}
def dl(args):
    d,f,lic=args
    url=f"https://raw.githubusercontent.com/google/fonts/main/{lic}/{d}/{f}"
    os.makedirs(f"raw/{d}",exist_ok=True)
    urllib.request.urlretrieve(url,f"raw/{d}/{f}")
    return f
jobs=[(d,f,v["license"]) for d,v in res.items() for f in v["files"]+v["licfile"]+["METADATA.pb"]]
with cf.ThreadPoolExecutor(16) as ex:
    list(ex.map(dl,jobs))
json.dump(res,open("index.json","w"),indent=1)
print(len(res),"families")
