#!/usr/bin/env python3
"""Generates the talent-point icon: a gold gem in a cushion cut (an octagon
with a flat, paler table, stepped facets and a dark edge), the symbol shown
after the point counter in the header and on the price badges.

Run from the project root: ``python3 tools/generate_point_icon.py``
(needs Pillow). Like tools/GenerateTextures.java, every texture is written
as ``Name.png`` (1 texel per UI unit) and ``Name@2x.png`` (2 texels per
unit) so the client shows it 1:1 at both UI scales.

Output, in core/src/main/resources/Common/UI/Custom/Pages/TalentGraph/Node/:
  Point.png       8 units, the gem of the price badges (Node.ui)
  PointLarge.png  12 units, the gem of the header (TalentGraphPage.ui)

The drawing is vector-like: shapes are rasterised at SUPERSAMPLE times the
final size and box-filtered down, which anti-aliases every edge. Transparent
texels carry the edge colour under their zero alpha, so the client's bilinear
filtering never blends the outline with black.
"""

from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent.parent / "core/src/main/resources/Common/UI/Custom/Pages/TalentGraph/Node"
SIZES = {"Point": 8, "PointLarge": 12}
SCALES = {1: "", 2: "@2x"}
SUPERSAMPLE = 16

PALE = (0xFF, 0xEF, 0xB0)
GOLD = (0xF6, 0xD2, 0x5A)
DARK = (0xB8, 0x70, 0x12)
EDGE = (0x5E, 0x34, 0x06)
WHITE = (0xFF, 0xFF, 0xFF)


def octagon(c, r, cut):
    """Square of half-size r centred on c, corners cut by `cut`, clockwise from the top-left edge."""
    return [
        (c - r + cut, c - r), (c + r - cut, c - r), (c + r, c - r + cut), (c + r, c + r - cut),
        (c + r - cut, c + r), (c - r + cut, c + r), (c - r, c + r - cut), (c - r, c - r + cut),
    ]


def gradient_fill(img, polygon, start, end):
    """Fills `polygon` with a diagonal gradient from `start` (top-left) to `end` (bottom-right)."""
    w, h = img.size
    grad = Image.new("RGBA", (w, h))
    px = grad.load()
    for y in range(h):
        for x in range(w):
            t = (x + y) / (w + h - 2)
            px[x, y] = tuple(round(a + (b - a) * t) for a, b in zip(start, end)) + (255,)
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).polygon(polygon, fill=255)
    img.paste(grad, (0, 0), mask)


def gem(size, scale):
    """The cushion-cut gem, `size` units at `scale` texels per unit."""
    ss = scale * SUPERSAMPLE
    px = size * ss
    img = Image.new("RGBA", (px, px), EDGE + (0,))
    draw = ImageDraw.Draw(img)

    def u(v):  # units -> supersampled pixels
        return v * ss

    c = size / 2
    r = size / 2 - 0.5
    outer = [(u(x), u(y)) for x, y in octagon(c, r, r * 0.42)]
    ri = r * 0.55
    table = [(u(x), u(y)) for x, y in octagon(c, ri, ri * 0.42)]
    edge_w = max(0.6, size * 0.07)
    facet_w = max(0.5, size * 0.045)

    # Bevel: light top-left, dark bottom-right.
    gradient_fill(img, outer, GOLD, DARK)
    # Step facets: one line per corner, from the girdle to the table.
    for (ox, oy), (ix, iy) in zip(outer, table):
        draw.line([(ox, oy), (ix, iy)], fill=EDGE, width=round(u(facet_w)))
    # Flat table, then both outlines.
    draw.polygon(table, fill=PALE)
    draw.line(table + table[:1], fill=EDGE, width=round(u(edge_w)), joint="curve")
    draw.line(outer + outer[:1], fill=EDGE, width=round(u(edge_w)), joint="curve")
    # Glint on the table.
    k = size * 0.07
    gx, gy = c - ri * 0.45, c - ri * 0.45
    draw.ellipse([u(gx - k), u(gy - k), u(gx + k), u(gy + k)], fill=WHITE)

    out = img.reduce(SUPERSAMPLE)
    # reduce() zeroes the colour of fully transparent texels: put the edge
    # colour back under them.
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            if px[x, y][3] == 0:
                px[x, y] = EDGE + (0,)
    return out


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, size in SIZES.items():
        for scale, suffix in SCALES.items():
            path = OUT / f"{name}{suffix}.png"
            gem(size, scale).save(path, optimize=True)
            print(f"{path.relative_to(Path.cwd())}  {size * scale}x{size * scale}")


if __name__ == "__main__":
    main()
