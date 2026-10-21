#!/usr/bin/env python3
"""Movie Lover: রিপোর রুটে এলোমেলো (flat) আপলোড করা ফাইলগুলো বিল্ডের আগে ঠিক ফোল্ডারে সাজায়
এবং logo.png থেকে সব সাইজের অ্যাপ আইকন বানায়।
আপডেট: রুটে ফাইল থাকলে সেটাই সবসময় জিতবে (আগে থেকে ফোল্ডারে থাকা পুরোনো ফাইল ওভাররাইট হবে)।"""
import os, shutil, sys

RES = "app/src/main/res"
MAP = {
    "AndroidManifest.xml": "app/src/main/AndroidManifest.xml",
    "app-build.gradle":    "app/build.gradle",
    "MainActivity.kt":     "app/src/main/java/com/movilover/app/MainActivity.kt",
    "offline.html":        "app/src/main/assets/offline.html",
    "colors.xml":          RES + "/values/colors.xml",
    "strings.xml":         RES + "/values/strings.xml",
    "themes.xml":          RES + "/values/themes.xml",
    "splash_bg.xml":       RES + "/drawable/splash_bg.xml",
    "ic_launcher.xml":     RES + "/mipmap-anydpi-v26/ic_launcher.xml",
    "ic_launcher_round.xml": RES + "/mipmap-anydpi-v26/ic_launcher_round.xml",
}
for src, dst in MAP.items():
    if os.path.isfile(src):
        existed = os.path.isfile(dst)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copyfile(src, dst)
        print(("OVERWROTE" if existed else "placed"), src, "->", dst)

# ---- আইকন: logo.png থেকে ----
if os.path.isfile("logo.png"):
    from PIL import Image, ImageDraw
    im = Image.open("logo.png").convert("RGB")
    bbox = im.convert("L").point(lambda v: 255 if v > 38 else 0).getbbox()
    logo = im.crop(bbox)
    def canvas(size, frac, circle=False):
        c = Image.new("RGB", (size, size), (0, 0, 0))
        w = int(size * frac); h = int(logo.height * w / logo.width)
        c.paste(logo.resize((w, h), Image.LANCZOS), ((size - w) // 2, (size - h) // 2))
        c = c.convert("RGBA")
        if circle:
            m = Image.new("L", (size * 4, size * 4), 0)
            ImageDraw.Draw(m).ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
            c.putalpha(m.resize((size, size), Image.LANCZOS))
        return c
    os.makedirs(RES + "/drawable-nodpi", exist_ok=True)
    canvas(432, 0.62).save(RES + "/drawable-nodpi/ic_launcher_foreground.png")
    for d, s in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        os.makedirs(f"{RES}/mipmap-{d}", exist_ok=True)
        canvas(s, 0.86).save(f"{RES}/mipmap-{d}/ic_launcher.png")
        canvas(s, 0.80, True).save(f"{RES}/mipmap-{d}/ic_launcher_round.png")
    print("icons generated from logo.png")

REQUIRED = list(MAP.values()) + [RES + "/drawable-nodpi/ic_launcher_foreground.png",
                                 "build.gradle", "settings.gradle", "site-url.txt", "release.keystore"]
missing = [p for p in REQUIRED if not os.path.isfile(p)]
if missing:
    print("\n❌ এই ফাইলগুলো রিপোতে পাওয়া যায়নি:")
    for m in missing: print("   -", m)
    print("→ ডিপ্লয় গাইড দেখে ফাইলগুলো যোগ করুন (logo.png ছাড়া আইকন বানানো যাবে না)।")
    sys.exit(1)
print("✅ সব ফাইল ঠিক আছে")
