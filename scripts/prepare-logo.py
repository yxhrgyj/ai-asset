from pathlib import Path
from PIL import Image
import sys

source = Image.open(sys.argv[1]).convert('RGB')
output = Path(__file__).resolve().parents[1] / 'frontend/public/brand'
output.mkdir(parents=True, exist_ok=True)
source.save(output / 'logo-original.png')
mark = source.crop((235, 248, 1020, 718)).convert('RGBA')
# Remove the neutral white background while preserving saturated logo colors.
pixels = []
for r, g, b, _ in mark.getdata():
    alpha = 255 - min(r, g, b)
    if max(r, g, b) - min(r, g, b) < 18:
        pixels.append((0, 0, 0, 0))
    else:
        pixels.append(tuple(round((c - 255 + alpha) * 255 / alpha) for c in (r, g, b)) + (alpha,))
mark.putdata(pixels)
mark.thumbnail((640, 384), Image.Resampling.LANCZOS)
mark.save(output / 'logo-mark.png')
icon = Image.new('RGBA', (64, 64))
mark.thumbnail((60, 60), Image.Resampling.LANCZOS)
icon.alpha_composite(mark, ((64 - mark.width) // 2, (64 - mark.height) // 2))
icon.save(output / 'favicon.png')
