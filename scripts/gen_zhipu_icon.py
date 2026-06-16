#!/usr/bin/env python3
"""
Generate Zhipu AI branded launcher icons at all standard Android densities.

Visual design:
  - AMOLED black background (rounded square for adaptive icon safe zone)
  - Deep-blue gradient glow disc centered
  - Large stylized "Z" lettermark in emerald green
  - Four accent dots (deep_blue, crimson, neon_yellow, emerald) along the bottom
    representing the 4-color palette the app uses inside the UI
  - "ZAI" wordmark below the Z for brand clarity

Output: ic_launcher.png and ic_launcher_round.png at
  mdpi(48) hdpi(72) xhdpi(96) xxhdpi(144) xxxhdpi(192)
plus a 512x512 Play Store master.
"""
import os, math, hashlib
from PIL import Image, ImageDraw, ImageFont, ImageFilter

# ---------- Palette (must match colors.xml) ----------
AMOLED       = (0, 0, 0, 255)
DEEP_BLUE    = (10, 37, 64, 255)
DEEP_BLUE_HI = (24, 70, 120, 255)
CRIMSON      = (220, 20, 60, 255)
NEON_YELLOW  = (255, 230, 0, 255)
EMERALD      = (0, 200, 83, 255)
WHITE        = (255, 255, 255, 255)

OUT_BASE = "/home/z/my-project/download/zai-wrapper/app/src/main/res"
SIZES = {
    "mipmap-mdpi":    48,
    "mipmap-hdpi":    72,
    "mipmap-xhdpi":   96,
    "mipmap-xxhdpi":  144,
    "mipmap-xxxhdpi": 192,
}
MASTER_SIZE = 512  # Play Store

# Try to find a good font; fall back to default
FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
    "/usr/share/fonts/truetype/freefont/FreeSansBold.ttf",
]
FONT_PATH = next((p for p in FONT_CANDIDATES if os.path.exists(p)), None)


def load_font(size):
    if FONT_PATH:
        return ImageFont.truetype(FONT_PATH, size)
    return ImageFont.load_default()


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))


def radial_glow(size, center_color, edge_color, radius):
    """Draw a radial-gradient disc on a transparent RGBA image."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    cx = cy = size / 2.0
    r2 = radius * radius
    for y in range(size):
        for x in range(size):
            dx = x - cx
            dy = y - cy
            d2 = dx * dx + dy * dy
            if d2 <= r2:
                t = math.sqrt(d2) / radius
                col = lerp(center_color, edge_color, t)
                # Smooth edge falloff
                edge = 1.0 if t < 0.85 else max(0.0, 1.0 - (t - 0.85) / 0.15)
                px[x, y] = (col[0], col[1], col[2], int(255 * edge))
    return img


def render_master(size):
    """Render the master icon at `size` x `size`."""
    img = Image.new("RGBA", (size, size), AMOLED)
    draw = ImageDraw.Draw(img)

    # Soft outer deep-blue halo
    halo = radial_glow(size, DEEP_BLUE_HI, (0, 0, 0, 0), size * 0.48)
    img = Image.alpha_composite(img, halo)

    # Inner deep-blue disc (cinematic vignette feel)
    disc_r = size * 0.40
    cx = cy = size / 2
    draw = ImageDraw.Draw(img)
    draw.ellipse(
        [cx - disc_r, cy - disc_r, cx + disc_r, cy + disc_r],
        fill=DEEP_BLUE,
    )

    # Subtle emerald ring around the disc for the "AI" signal
    ring_w = max(2, size // 96)
    draw.ellipse(
        [cx - disc_r - ring_w, cy - disc_r - ring_w,
         cx + disc_r + ring_w, cy + disc_r + ring_w],
        outline=EMERALD, width=ring_w,
    )

    # Large "Z" lettermark
    z_size = int(size * 0.45)
    z_font = load_font(z_size)
    z_text = "Z"
    bbox = draw.textbbox((0, 0), z_text, font=z_font)
    tw = bbox[2] - bbox[0]
    th = bbox[3] - bbox[1]
    tx = (size - tw) / 2 - bbox[0]
    ty = (size - th) / 2 - bbox[1] - int(size * 0.02)
    # Shadow for cinematic depth
    shadow_layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow_layer)
    sd.text((tx + size * 0.012, ty + size * 0.012), z_text,
            font=z_font, fill=(0, 0, 0, 180))
    shadow_layer = shadow_layer.filter(ImageFilter.GaussianBlur(size * 0.008))
    img = Image.alpha_composite(img, shadow_layer)
    draw = ImageDraw.Draw(img)
    draw.text((tx, ty), z_text, font=z_font, fill=NEON_YELLOW)

    # "ZAI" wordmark beneath the Z, in white, small
    wm_size = int(size * 0.10)
    wm_font = load_font(wm_size)
    wm_text = "ZAI"
    bbox = draw.textbbox((0, 0), wm_text, font=wm_font)
    wmw = bbox[2] - bbox[0]
    wmh = bbox[3] - bbox[1]
    wmx = (size - wmw) / 2 - bbox[0]
    wmy = cy + disc_r * 0.55 - wmh / 2 - bbox[1]
    draw.text((wmx, wmy), wm_text, font=wm_font, fill=WHITE)

    # Four accent dots along the bottom inside the disc
    dot_r = max(2, size // 70)
    dot_y = int(cy + disc_r * 0.85)
    palette = [DEEP_BLUE_HI, CRIMSON, NEON_YELLOW, EMERALD]
    spacing = size * 0.10
    start_x = cx - spacing * 1.5
    for i, c in enumerate(palette):
        dx = start_x + i * spacing
        draw.ellipse([dx - dot_r, dot_y - dot_r,
                      dx + dot_r, dot_y + dot_r], fill=c)

    return img


def make_round(master, size):
    """Mask the master to a circle for ic_launcher_round."""
    mask = Image.new("L", (size, size), 0)
    md = ImageDraw.Draw(mask)
    md.ellipse([0, 0, size, size], fill=255)
    round_img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    round_img.paste(master, (0, 0), mask)
    return round_img


def main():
    # Generate the master once
    master = render_master(MASTER_SIZE).convert("RGBA")

    # Save Play Store master
    play_master_path = os.path.join(OUT_BASE, "ic_launcher-playstore.png")
    master.save(play_master_path, "PNG")
    print(f"Saved Play Store master: {play_master_path} ({MASTER_SIZE}x{MASTER_SIZE})")

    # Save all density buckets
    for folder, sz in SIZES.items():
        out_dir = os.path.join(OUT_BASE, folder)
        os.makedirs(out_dir, exist_ok=True)
        scaled = master.resize((sz, sz), Image.LANCZOS)
        scaled.save(os.path.join(out_dir, "ic_launcher.png"), "PNG")
        make_round(master, MASTER_SIZE).resize((sz, sz), Image.LANCZOS) \
            .save(os.path.join(out_dir, "ic_launcher_round.png"), "PNG")
        print(f"Saved {folder}/ic_launcher{{,_round}}.png ({sz}x{sz})")

    # Also save the foreground-only layer for adaptive icons (mipmap-anydpi-v26)
    anydpi_dir = os.path.join(OUT_BASE, "mipmap-anydpi-v26")
    os.makedirs(anydpi_dir, exist_ok=True)
    # Adaptive icon XML
    adaptive_xml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/amoled_black"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
"""
    with open(os.path.join(anydpi_dir, "ic_launcher.xml"), "w") as f:
        f.write(adaptive_xml)
    with open(os.path.join(anydpi_dir, "ic_launcher_round.xml"), "w") as f:
        f.write(adaptive_xml)
    print("Saved mipmap-anydpi-v26 adaptive icon XMLs")

    # Foreground drawable (the Z mark on transparent background, sized 108x108
    # safe-zone for adaptive icons). Reuse master but make background transparent.
    fg = render_master(MASTER_SIZE)
    # Replace AMOLED background with transparency
    data = fg.getdata()
    new_data = []
    for px in data:
        if px[:3] == (0, 0, 0):
            new_data.append((0, 0, 0, 0))
        else:
            new_data.append(px)
    fg.putdata(new_data)
    fg_dir = os.path.join(OUT_BASE, "drawable")
    os.makedirs(fg_dir, exist_ok=True)
    fg.save(os.path.join(fg_dir, "ic_launcher_foreground.png"), "PNG")
    print("Saved drawable/ic_launcher_foreground.png")

    print("\nAll icons generated successfully.")


if __name__ == "__main__":
    main()
