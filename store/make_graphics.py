"""Generates the Google Play graphics that are drawn rather than screenshotted:

  store/icon-512.png                 app icon, 512x512, full square (Play rounds it)
  store/feature-graphic-en.png       feature graphic, 1024x500
  store/feature-graphic-ja.png       the same with a Japanese tagline

The icon uses the same geometry and colours as the launcher icon
(app/src/main/res/drawable/ic_launcher_*.xml): three handwritten lines on a yellow note.
Re-run after changing the launcher icon or the palette:

    python store/make_graphics.py
"""
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
SS = 4  # supersampling factor for smooth edges

YELLOW = (0xFF, 0xF5, 0x9D)
SLATE_800 = (0x26, 0x32, 0x38)
SLATE_600 = (0x37, 0x47, 0x4F)
NOTE_COLORS = [(0xFF, 0xF5, 0x9D), (0xF8, 0xBB, 0xD0), (0xB3, 0xE5, 0xFC),
               (0xC5, 0xE1, 0xA5), (0xFF, 0xCC, 0x80)]


def font(path, size):
    return ImageFont.truetype(path, size)


def line(draw, x0, x1, y, width, color):
    r = width / 2
    draw.rounded_rectangle([x0 - r, y - r, x1 + r, y + r], radius=r, fill=color)


def make_icon():
    size = 512 * SS
    img = Image.new("RGB", (size, size), YELLOW)
    d = ImageDraw.Draw(img)
    # Launcher icon proportions (38..70 of 108), enlarged to suit a standalone icon.
    x0, x1_full, x1_short = 128 * SS, 384 * SS, 296 * SS
    for y, x1 in ((190, x1_full), (256, x1_full), (322, x1_short)):
        line(d, x0, x1, y * SS, 30 * SS, SLATE_600)
    img = img.resize((512, 512), Image.LANCZOS)
    img.convert("RGBA").save(os.path.join(HERE, "icon-512.png"), optimize=True)


def note_card(w, h, color):
    """A sticky note with a few text-like lines, as an RGBA image."""
    img = Image.new("RGBA", (w * SS, h * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, w * SS - 1, h * SS - 1], radius=10 * SS, fill=color + (255,))
    ink = tuple(int(c * 0.45) for c in color) + (255,)
    for i, frac in enumerate((0.78, 0.62, 0.70, 0.40)):
        y = (46 + i * 34) * SS
        line(d, 24 * SS, int(24 * SS + (w - 48) * SS * frac), y, 9 * SS, ink)
    return img


def make_feature(lang):
    w, h = 1024, 500
    img = Image.new("RGB", (w, h), SLATE_800)
    # Soft top-to-bottom gradient from slate 800 to slate 600.
    grad = Image.linear_gradient("L").resize((w, h))
    img = Image.composite(Image.new("RGB", (w, h), SLATE_600), img, grad)

    # Five notes, fanned out on the right.
    notes = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    shadow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    angles = [-16, -8, 0, 8, 16]
    for i, (color, angle) in enumerate(zip(NOTE_COLORS, angles)):
        card = note_card(210, 250, color).rotate(angle, expand=True, resample=Image.BICUBIC)
        card = card.resize((card.width // SS, card.height // SS), Image.LANCZOS)
        cx = 560 + i * 78
        cy = 250 + abs(i - 2) * 6
        pos = (cx - card.width // 2 + 40, cy - card.height // 2)
        sh = Image.new("RGBA", card.size, (0, 0, 0, 0))
        sh.putalpha(card.getchannel("A").point(lambda v: int(v * 0.35)))
        shadow.alpha_composite(sh, (pos[0] + 4, pos[1] + 8))
        notes.alpha_composite(card, pos)
    shadow = shadow.filter(ImageFilter.GaussianBlur(8))
    img = img.convert("RGBA")
    img.alpha_composite(shadow)
    img.alpha_composite(notes)

    d = ImageDraw.Draw(img)
    title_font = font(r"C:\Windows\Fonts\segoeuib.ttf", 92)
    d.text((64, 150), "kotonote", font=title_font, fill=(255, 255, 255))
    if lang == "ja":
        tag_font = font(r"C:\Windows\Fonts\YuGothB.ttc", 32)
        lines = ["付箋は5枚だけ。", "一覧も保存もなし。"]
    else:
        tag_font = font(r"C:\Windows\Fonts\segoeui.ttf", 34)
        lines = ["Five sticky notes.", "Nothing more."]
    y = 276
    for text in lines:
        d.text((68, y), text, font=tag_font, fill=(0xCF, 0xD8, 0xDC))
        y += 50
    img.convert("RGB").save(os.path.join(HERE, "feature-graphic-%s.png" % lang), optimize=True)


if __name__ == "__main__":
    make_icon()
    make_feature("en")
    make_feature("ja")
    print("ok")
