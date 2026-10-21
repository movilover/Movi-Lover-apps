#!/usr/bin/env python3
"""Movie Lover: রিপোর রুটের ফাইলগুলো (MainActivity.kt, colors.xml, themes.xml ...) বিল্ড-প্রজেক্টে বসায়।

ব্যবহার (workflow এ):   python3 prepare.py proj
  - রিপোর রুটে যে ফাইল আছে সেটাই সবসময় জিতবে — project.zip এর ভেতরের পুরোনো ফাইল ওভাররাইট হয়।
  - app-build.gradle ডিফল্টে বসানো হয় না (বিল্ড সেটিং ভাঙার ঝুঁকি)। দরকার হলে APPLY_GRADLE=1 দিন।
"""
import os, sys, shutil, glob

DEST = sys.argv[1] if len(sys.argv) > 1 else "."

# project.zip এর ভেতরে যদি একটা উপরের ফোল্ডার থাকে, সেটা ধরে নেওয়া
if not os.path.isdir(os.path.join(DEST, "app", "src", "main")):
    subs = [d for d in glob.glob(os.path.join(DEST, "*")) if os.path.isdir(os.path.join(d, "app", "src", "main"))]
    if len(subs) == 1:
        DEST = subs[0]
if not os.path.isdir(os.path.join(DEST, "app", "src", "main")):
    print("❌ app/src/main পাওয়া যায়নি:", DEST); sys.exit(1)
print("প্রজেক্ট ফোল্ডার:", DEST)

RES = "app/src/main/res"
MAP = {
    "AndroidManifest.xml":   "app/src/main/AndroidManifest.xml",
    "MainActivity.kt":       "app/src/main/java/com/movilover/app/MainActivity.kt",
    "offline.html":          "app/src/main/assets/offline.html",
    "colors.xml":            RES + "/values/colors.xml",
    "strings.xml":           RES + "/values/strings.xml",
    "themes.xml":            RES + "/values/themes.xml",
    "splash_bg.xml":         RES + "/drawable/splash_bg.xml",
    "ic_launcher.xml":       RES + "/mipmap-anydpi-v26/ic_launcher.xml",
    "ic_launcher_round.xml": RES + "/mipmap-anydpi-v26/ic_launcher_round.xml",
}
if os.environ.get("APPLY_GRADLE") == "1":
    MAP["app-build.gradle"] = "app/build.gradle"

# ---- ডায়াগনস্টিক: পুরোনো MainActivity-তে লাল প্রোগ্রেস বার ছিল কি? ----
for old in glob.glob(os.path.join(DEST, "app/src/main/java/**/MainActivity.*"), recursive=True):
    try:
        t = open(old, encoding="utf-8", errors="ignore").read()
        print("পুরোনো", os.path.relpath(old, DEST), "-> ProgressBar আছে:", "ProgressBar" in t)
    except Exception: pass

for src, dst in MAP.items():
    if not os.path.isfile(src):
        continue
    full = os.path.join(DEST, dst)
    if src == "MainActivity.kt":   # ডুপ্লিকেট ক্লাস এড়াতে অন্য জায়গার পুরোনো MainActivity মুছে ফেলা
        for old in glob.glob(os.path.join(DEST, "app/src/main/java/**/MainActivity.*"), recursive=True):
            if os.path.abspath(old) != os.path.abspath(full):
                os.remove(old); print("মুছে ফেলা হলো (ডুপ্লিকেট):", os.path.relpath(old, DEST))
    existed = os.path.isfile(full)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    shutil.copyfile(src, full)
    print(("OVERWROTE" if existed else "placed"), src, "->", dst)

print("✅ রিপোর রুটের ফাইলগুলো প্রজেক্টে বসানো হয়েছে")
