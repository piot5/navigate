#!/usr/bin/env python3
"""Erzeugt die Navigate-Grafiken.

Warum generiert und nicht handgeschrieben: die Pfaddaten der abgerundeten
Quadrate (9 Stück pro Glyphe) und das TV-Banner sind reine Rechnerei —
von Hand tippt man sich Rundungsfehler in die SVG-Pfade.

Aufruf:  python3 tools/make_assets.py
Ergebnis (idempotent, überschreibt):
  app/src/main/res/drawable/ic_launcher_foreground.xml
  app/src/main/res/drawable/ic_launcher_legacy.xml
  app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
  app/src/main/res/mipmap-anydpi/ic_launcher.xml
  app/src/main/res/drawable-nodpi/banner.png
"""
from __future__ import annotations

import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

BG = "#10151C"
GLYPH = "#6EA8FE"
TEXT = "#E8EAED"

VECTOR_HEAD = (
    '<?xml version="1.0" encoding="utf-8"?>\n'
    '<!-- GENERIERT von tools/make_assets.py — nicht von Hand ändern. -->\n'
    '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
    '    android:width="{w}dp"\n'
    '    android:height="{h}dp"\n'
    '    android:viewportWidth="{vw}"\n'
    '    android:viewportHeight="{vh}">\n'
)


def rrect(x: float, y: float, size: float, r: float) -> str:
    """SVG-Pfad für ein abgerundetes Quadrat (Uhrzeigersinn ab oberer Kante)."""
    side = size - 2 * r
    return (
        f"M{x + r},{y} h{side} a{r},{r} 0 0 1 {r},{r} v{side} "
        f"a{r},{r} 0 0 1 {-r},{r} h{-side} a{r},{r} 0 0 1 {-r},{-r} v{-side} "
        f"a{r},{r} 0 0 1 {r},{-r} z"
    )


def grid_path(margin: float, size: float, gap: float, r: float) -> str:
    """9 Quadrate (3x3) als ein Pfad — ein <path> statt neun spart Dex/Parse-Zeit."""
    parts = []
    for row in range(3):
        for col in range(3):
            parts.append(rrect(margin + col * (size + gap), margin + row * (size + gap), size, r))
    return " ".join(parts)


def write(path: str, content: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(content)
    print(f"  geschrieben: {os.path.relpath(path, ROOT)}")


def make_vectors() -> None:
    # Adaptive Icon: 108x108-Viewport, sichtbare Fläche ist die zentrale 72er
    # Safe-Zone (18..90). Genau dort sitzt das 3x3-Raster.
    fg = VECTOR_HEAD.format(w=108, h=108, vw=108, vh=108)
    fg += f'    <path\n        android:fillColor="{GLYPH}"\n'
    fg += f'        android:pathData="{grid_path(18, 20, 6, 5)}" />\n</vector>\n'
    write(os.path.join(RES, "drawable", "ic_launcher_foreground.xml"), fg)

    # Legacy-Icon (API < 26): Hintergrund + Raster randlos in 24x24.
    legacy = VECTOR_HEAD.format(w=24, h=24, vw=24, vh=24)
    legacy += f'    <path\n        android:fillColor="{BG}"\n        android:pathData="M0,0h24v24h-24z" />\n'
    legacy += f'    <path\n        android:fillColor="{GLYPH}"\n'
    legacy += f'        android:pathData="{grid_path(3, 5, 1.5, 1.4)}" />\n</vector>\n'
    write(os.path.join(RES, "drawable", "ic_launcher_legacy.xml"), legacy)

    adaptive = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- GENERIERT von tools/make_assets.py — nicht von Hand ändern. -->\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/ic_launcher_background" />\n'
        '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
        '    <!-- Monochrom-Ebene (Android 13+): das System tönt sie selbst.\n'
        '         Die Glyphe ist einfarbig und taugt deshalb direkt als Maske.\n'
        '         Ohne diesen Eintrag meldet lint MonochromeLauncherIcon. -->\n'
        '    <monochrome android:drawable="@drawable/ic_launcher_foreground" />\n'
        '</adaptive-icon>\n'
    )
    write(os.path.join(RES, "mipmap-anydpi-v26", "ic_launcher.xml"), adaptive)

    # anydpi ohne Versionsqualifier: greift auf API 21–25, wo adaptive-icons
    # unbekannt sind. Als Layer-List, damit auch dort Hintergrund + Glyphe
    # zusammenkommen.
    fallback = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- GENERIERT von tools/make_assets.py — nicht von Hand ändern. -->\n'
        '<layer-list xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <item android:drawable="@drawable/ic_launcher_legacy" />\n'
        '</layer-list>\n'
    )
    write(os.path.join(RES, "mipmap-anydpi", "ic_launcher.xml"), fallback)


def make_banner() -> None:
    """TV-Banner 320x180 (Vorgabe der Android-TV-Leanback-Spezifikation).

    Der Banner ist die einzige Grafik, die das System nicht aus dem Icon
    ableiten kann — ohne ihn fehlt die App in der TV-Apps-Zeile.
    """
    w, h = 320, 180
    img = Image.new("RGB", (w, h), BG)
    draw = ImageDraw.Draw(img)

    # Diagonaler Verlauf als Hintergrund (billig: eine Linie pro Spalte).
    top = (16, 21, 28)
    bottom = (7, 9, 13)
    for x in range(w):
        t = x / (w - 1)
        col = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        draw.line([(x, 0), (x, h)], fill=col)

    # 3x3-Raster links, gleiche Geometrie wie das Icon.
    cell, gap, radius = 22, 8, 5
    gx, gy = 34, (h - (3 * cell + 2 * gap)) // 2
    for row in range(3):
        for col in range(3):
            x = gx + col * (cell + gap)
            y = gy + row * (cell + gap)
            draw.rounded_rectangle([x, y, x + cell, y + cell], radius=radius, fill=GLYPH)

    font_path = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
    font = ImageFont.truetype(font_path, 34)
    text = "Navigate"
    box = draw.textbbox((0, 0), text, font=font)
    tx = gx + 3 * cell + 2 * gap + 26
    ty = (h - (box[3] - box[1])) // 2 - box[1]
    draw.text((tx, ty), text, font=font, fill=TEXT)

    out = os.path.join(RES, "drawable-nodpi", "banner.png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out, "PNG", optimize=True)
    print(f"  geschrieben: {os.path.relpath(out, ROOT)} ({w}x{h})")


def main() -> None:
    print("Navigate-Grafiken:")
    make_vectors()
    make_banner()
    print("fertig.")


if __name__ == "__main__":
    main()
