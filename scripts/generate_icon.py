#!/usr/bin/env python3
"""
Generate the LightPass (Movie Tickets) launcher icon.

Same design language as gi-os/LightFog and gi-os/LightFastread: heavy white line
art, round caps, full-bleed pure black, one asymmetric focal ring. LightFog draws
a folded map with a dashed route running to a ringed pin; LightFastread draws an
open book with dashed lines of text and the current word ringed. This is the
ticket equivalent - a stub with a dashed perforation and the seat ringed - so all
three read as siblings in the LightOS app drawer.

Measured from LightFog's icon.png (1024x1024):
  outline stroke   30px  (2.93% of canvas)
  detail stroke    24px  (2.34%)
  ink bounding box 0.616 wide x 0.458 tall, optically centred

Geometry is emitted from one function at whatever scale the target needs, so the
SVG (rasterised to the mipmap WebPs) and the Android VectorDrawable (the adaptive
icon foreground) are built from the same numbers. Nothing is rescaled by string
surgery, which is what would otherwise mangle the arc flags in the notch paths.

Usage:  python3 scripts/generate_icon.py
Needs:  pip install cairosvg pillow
"""

import io
import os

S = 1024                      # design space
OUTLINE = 30                  # ticket outline stroke
DETAIL = 24                   # perforation, text rows, focal ring

# --- ticket ---------------------------------------------------------------
# Landscape stub, ink box ~630x390 centred, matching LightFog's proportions.
LEFT, RIGHT = 197, 827
TOP, BOT = 317, 707
CORNER = 40                   # body corner radius
PERF_X = 636                  # perforation line, cutting off the right-hand stub
# The bite has to be deeper than the 30px stroke or the stroke fills it in and the
# edge just looks wobbly instead of torn.
NOTCH = 58                    # semicircular bite taken out of top and bottom edge


def ticket_outline(k, off):
    """Ticket body as a single closed path. k scales from design units, off shifts.

    The notches are real arcs cut into the top and bottom edges rather than
    separate overlaid shapes, so the stroke stays one continuous contour and no
    seam shows at the join.
    """
    def x(v):
        return v * k + off
    y = x

    l, r, t, b = x(LEFT), x(RIGHT), y(TOP), y(BOT)
    c, p, n = CORNER * k, x(PERF_X), NOTCH * k
    return (
        f"M {l + c:.3f},{t:.3f} "
        f"L {p - n:.3f},{t:.3f} "
        f"A {n:.3f},{n:.3f} 0 0 0 {p + n:.3f},{t:.3f} "   # top notch, bites down
        f"L {r - c:.3f},{t:.3f} "
        f"A {c:.3f},{c:.3f} 0 0 1 {r:.3f},{t + c:.3f} "
        f"L {r:.3f},{b - c:.3f} "
        f"A {c:.3f},{c:.3f} 0 0 1 {r - c:.3f},{b:.3f} "
        f"L {p + n:.3f},{b:.3f} "
        f"A {n:.3f},{n:.3f} 0 0 0 {p - n:.3f},{b:.3f} "   # bottom notch, bites up
        f"L {l + c:.3f},{b:.3f} "
        f"A {c:.3f},{c:.3f} 0 0 1 {l:.3f},{b - c:.3f} "
        f"L {l:.3f},{t + c:.3f} "
        f"A {c:.3f},{c:.3f} 0 0 1 {l + c:.3f},{t:.3f} "
        f"Z"
    )


def dashes(x0, x1, yv, dash, gap, k, off):
    """Horizontal dashed row, emitted as explicit segments.

    VectorDrawable has no stroke-dasharray, so the dashes are placed by hand and
    the raster and the vector stay identical in shape.
    """
    segs, xx = [], x0
    while xx < x1:
        x2 = min(xx + dash, x1)
        if x2 - xx > dash * 0.45:          # drop a runt dash at the end
            segs.append(f"M {xx * k + off:.3f},{yv * k + off:.3f} "
                        f"L {x2 * k + off:.3f},{yv * k + off:.3f}")
        xx += dash + gap
    return segs


def perforation(k, off):
    """Vertical dashed tear line between the notches."""
    segs, yv = [], TOP + NOTCH + 26
    end = BOT - NOTCH - 26
    dash, gap = 46, 32
    while yv < end:
        y2 = min(yv + dash, end)
        if y2 - yv > dash * 0.45:
            segs.append(f"M {PERF_X * k + off:.3f},{yv * k + off:.3f} "
                        f"L {PERF_X * k + off:.3f},{y2 * k + off:.3f}")
        yv += dash + gap
    return segs


def text_rows(k, off):
    """Two dashed rows on the ticket face - the printed film and showtime."""
    out = []
    out += dashes(272, 552, 428, 60, 34, k, off)
    out += dashes(272, 470, 528, 60, 34, k, off)
    return out


# The stub carries a single ringed dot: the seat. Same focal-reticle device as
# LightFastread's ringed word and LightFog's ringed pin, and the same reason -
# a face full of dashes on one side collapsing to one point of attention on the
# other is what makes the icon readable at 48px.
#
# The dot is a *filled* circle, not a zero-length round-capped stroke. SVG renders
# the latter as a dot but Skia does not have to, and Android would have shipped an
# empty ring to the device while the preview PNGs looked correct.
SEAT_C = (732, 512)
SEAT_R = 56
SEAT_DOT = 21


def circle_path(cx, cy, r):
    """Circle as two arcs - portable across SVG and VectorDrawable."""
    return (f"M {cx - r:.3f},{cy:.3f} "
            f"a {r:.3f},{r:.3f} 0 1,0 {2 * r:.3f},0 "
            f"a {r:.3f},{r:.3f} 0 1,0 {-2 * r:.3f},0 Z")


def strokes(k, off):
    """Every stroked path, as (pathData, width) at the given scale."""
    out = [(ticket_outline(k, off), OUTLINE * k)]
    for d in text_rows(k, off) + perforation(k, off):
        out.append((d, DETAIL * k))
    out.append((circle_path(SEAT_C[0] * k + off, SEAT_C[1] * k + off, SEAT_R * k),
                DETAIL * k))
    return out


def fills(k, off):
    return [circle_path(SEAT_C[0] * k + off, SEAT_C[1] * k + off, SEAT_DOT * k)]


def svg(size, pad=0.0):
    """pad shrinks the art toward the centre, for the adaptive-icon safe zone."""
    k = 1.0 - pad
    off = S * pad / 2
    body = [f'<rect width="{S}" height="{S}" fill="#000000"/>']
    for d, w in strokes(k, off):
        body.append(f'<path d="{d}" fill="none" stroke="#FFFFFF" stroke-width="{w:.3f}" '
                    f'stroke-linecap="round" stroke-linejoin="round"/>')
    for d in fills(k, off):
        body.append(f'<path d="{d}" fill="#FFFFFF"/>')
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" '
            f'viewBox="0 0 {S} {S}">' + "".join(body) + "</svg>")


def vector_drawable():
    """Adaptive-icon foreground: 108dp viewport, art inside the 72dp safe zone."""
    k = (72.0 / 108.0) * (108.0 / S)        # design units -> dp, shrunk to safe zone
    off = (108.0 - S * k) / 2.0
    paths = []
    for d, w in strokes(k, off):
        paths.append(f'''    <path
        android:pathData="{d}"
        android:strokeColor="#FFFFFF"
        android:strokeWidth="{w:.3f}"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />''')
    for d in fills(k, off):
        paths.append(f'''    <path
        android:pathData="{d}"
        android:fillColor="#FFFFFF" />''')
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp"\n'
            '    android:height="108dp"\n'
            '    android:viewportWidth="108"\n'
            '    android:viewportHeight="108">\n'
            + "\n".join(paths) + "\n</vector>\n")


BACKGROUND = '''<?xml version="1.0" encoding="utf-8"?>
<!-- Pure black. On the Light Phone III's OLED these pixels are simply off. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#000000"
        android:pathData="M0,0h108v108h-108z" />
</vector>
'''

ADAPTIVE = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
'''

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def main():
    import cairosvg
    from PIL import Image, ImageDraw

    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
    res = os.path.join(root, "app/src/main/res")

    def render(size, round_mask=False):
        # Supersample then LANCZOS down: 30px strokes at mdpi land on ~1.4 device
        # pixels, and rasterising straight to 48px loses them to gamma.
        png = cairosvg.svg2png(bytestring=svg(size).encode(),
                               output_width=size * 4, output_height=size * 4)
        im = Image.open(io.BytesIO(png)).convert("RGBA")
        if round_mask:
            mask = Image.new("L", im.size, 0)
            ImageDraw.Draw(mask).ellipse((0, 0, im.size[0] - 1, im.size[1] - 1), fill=255)
            im.putalpha(mask)
        return im.resize((size, size), Image.LANCZOS)

    for d, px in DENSITIES.items():
        out_dir = os.path.join(res, f"mipmap-{d}")
        os.makedirs(out_dir, exist_ok=True)
        for name, rnd in (("ic_launcher", False), ("ic_launcher_round", True)):
            out = os.path.join(out_dir, f"{name}.webp")
            render(px, rnd).save(out, "WEBP", lossless=True, quality=100)
            print("wrote", os.path.relpath(out, root))

    v26 = os.path.join(res, "mipmap-anydpi-v26")
    os.makedirs(v26, exist_ok=True)
    for name in ("ic_launcher", "ic_launcher_round"):
        open(os.path.join(v26, f"{name}.xml"), "w").write(ADAPTIVE)
        print("wrote", os.path.relpath(os.path.join(v26, f"{name}.xml"), root))

    dr = os.path.join(res, "drawable")
    os.makedirs(dr, exist_ok=True)
    open(os.path.join(dr, "ic_launcher_foreground.xml"), "w").write(vector_drawable())
    open(os.path.join(dr, "ic_launcher_background.xml"), "w").write(BACKGROUND)
    print("wrote app/src/main/res/drawable/ic_launcher_foreground.xml")
    print("wrote app/src/main/res/drawable/ic_launcher_background.xml")

    # Reference art, same role as LightFog's assets/images/icon.png. Obtainium
    # shows the APK's own launcher icon, but this is what the README and the
    # GitHub Pages QR page use.
    ref = os.path.join(root, "docs/icon.png")
    os.makedirs(os.path.dirname(ref), exist_ok=True)
    render(1024).convert("RGB").save(ref)
    print("wrote docs/icon.png")


if __name__ == "__main__":
    main()
