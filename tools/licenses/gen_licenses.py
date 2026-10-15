#!/usr/bin/env python3
"""1.3.9: builds app/src/main/assets/licenses/ for the in-app «Лицензии» screen.

Fonts are read from the bundled .ttf files themselves (OpenType `name` table: family 16/1, copyright 0,
designer 9, license 13, license URL 14), so the list can never drift from what ships in res/font.
Third-party libraries are listed below by hand (keep in sync with the Gradle dependencies).
Run: python3 tools/licenses/gen_licenses.py
"""
import glob, json, os, re, shutil
from fontTools.ttLib import TTFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "tools", "licenses")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "licenses")

TEXTS = {  # license id -> asset file (full text)
    "OFL-1.1": "OFL-1.1.txt",
    "Apache-2.0": "Apache-2.0.txt",
    "MIT-onnxruntime": "onnxruntime-LICENSE.txt",
    "onnxruntime-notices": "onnxruntime-ThirdPartyNotices.txt",
    "MIT-rubert-tiny2": "rubert-tiny2-MIT.txt",
}
LIBS = [
    {"name": "ONNX Runtime", "version": "1.30.0", "author": "© Microsoft Corporation", "license": "MIT", "text": "MIT-onnxruntime",
     "extra": "onnxruntime-notices", "use": "движок нейросети модуля «Мудрость»", "url": "https://github.com/microsoft/onnxruntime"},
    {"name": "rubert-tiny2 (модель)", "version": "int8", "author": "© David Dale", "license": "MIT", "text": "MIT-rubert-tiny2",
     "use": "смысловой поиск в «Мудрости»", "url": "https://huggingface.co/cointegrated/rubert-tiny2"},
    {"name": "Jetpack Compose (UI, Foundation, Material 3, Material Icons)", "version": "BOM 2024.10.01", "author": "© The Android Open Source Project", "license": "Apache-2.0", "text": "Apache-2.0", "url": "https://developer.android.com/jetpack/androidx"},
    {"name": "AndroidX Core, Activity, Lifecycle, Navigation", "version": "1.13–1.15, 1.9.3, 2.8.7, 2.8.4", "author": "© The Android Open Source Project", "license": "Apache-2.0", "text": "Apache-2.0", "url": "https://developer.android.com/jetpack/androidx"},
    {"name": "AndroidX Room, WorkManager, DataStore", "version": "2.6.1, 2.10.0, 1.1.1", "author": "© The Android Open Source Project", "license": "Apache-2.0", "text": "Apache-2.0", "url": "https://developer.android.com/jetpack/androidx"},
    {"name": "AndroidX Profile Installer, Metrics (JankStats), Graphics Path", "version": "1.4.1, 1.0.0-beta01", "author": "© The Android Open Source Project", "license": "Apache-2.0", "text": "Apache-2.0", "url": "https://developer.android.com/jetpack/androidx"},
    {"name": "Guava ListenableFuture", "version": "1.0", "author": "© The Guava Authors", "license": "Apache-2.0", "text": "Apache-2.0", "url": "https://github.com/google/guava"},
    {"name": "Kotlin Standard Library", "version": "2.0", "author": "© JetBrains s.r.o. and Kotlin contributors", "license": "Apache-2.0", "text": "Apache-2.0", "url": "https://kotlinlang.org"},
    {"name": "kotlinx.coroutines", "version": "1.9.0", "author": "© JetBrains s.r.o.", "license": "Apache-2.0", "text": "Apache-2.0", "url": "https://github.com/Kotlin/kotlinx.coroutines"},
    {"name": "kotlinx.serialization", "version": "1.7.3", "author": "© JetBrains s.r.o.", "license": "Apache-2.0", "text": "Apache-2.0", "url": "https://github.com/Kotlin/kotlinx.serialization"},
]
STYLE_WORDS = r"\s+(Thin|ExtraLight|Extra Light|Light|Regular|Medium|SemiBold|Semi Bold|Bold|ExtraBold|Black)$"

def license_id(text):
    t = (text or "").lower()
    if "open font license" in t: return "OFL-1.1"
    if "apache" in t: return "Apache-2.0"
    if "mit license" in t: return "MIT"
    raise SystemExit("Unknown font license: " + text[:120])

def main():
    os.makedirs(OUT, exist_ok=True)
    for f in glob.glob(os.path.join(OUT, "*")): os.remove(f)
    # Generic OFL text (without one font's copyright header).
    ofl = open(os.path.join(SRC, "OFL-1.1.txt"), encoding="utf-8").read()
    i = ofl.find("SIL OPEN FONT LICENSE")
    open(os.path.join(OUT, "OFL-1.1.txt"), "w", encoding="utf-8").write(ofl[i:] if i >= 0 else ofl)
    for k, f in TEXTS.items():
        if k != "OFL-1.1": shutil.copy(os.path.join(SRC, f), os.path.join(OUT, f))
    fams = {}
    files = sorted(glob.glob(os.path.join(ROOT, "app", "src", "main", "res", "font", "*.ttf")))
    for p in files:
        n = TTFont(p, lazy=True)["name"]
        g = lambda i: (n.getDebugName(i) or "").strip()
        fam = re.sub(STYLE_WORDS, "", g(16) or g(1))
        lic = license_id(g(13))
        e = fams.setdefault(fam, {"family": fam, "files": [], "copyright": g(0), "designer": g(9), "license": lic,
                                  "licenseUrl": g(14), "text": lic if lic != "MIT" else "MIT-onnxruntime"})
        e["files"].append(os.path.basename(p))
    data = {"fontFiles": len(files), "fonts": sorted(fams.values(), key=lambda e: e["family"].lower()),
            "libraries": LIBS, "texts": TEXTS}
    json.dump(data, open(os.path.join(OUT, "index.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(f"{len(files)} font files, {len(fams)} families, {len(LIBS)} libraries ->", OUT)

main()
