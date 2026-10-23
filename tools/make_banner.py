"""產生 Android TV banner（xhdpi 320x180 px，圖內要有文字）。

用法：python3 tools/make_banner.py app/src/main/res/drawable-xhdpi/banner.png
"""
import sys

from PIL import Image, ImageDraw, ImageFont

FONT_CANDIDATES = [
    "/System/Library/Fonts/PingFang.ttc",
    "/System/Library/Fonts/STHeiti Medium.ttc",
    "/System/Library/Fonts/Hiragino Sans GB.ttc",
]


def load_font(size):
    for path in FONT_CANDIDATES:
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    raise SystemExit("找不到中文字型")


def main(out_path):
    img = Image.new("RGB", (320, 180), (38, 70, 83))
    draw = ImageDraw.Draw(img)
    # 4 字 × 60 px ＝ 240 px，左右各留 40 px。
    big = load_font(60)
    small = load_font(30)
    draw.text((160, 76), "動動學學", font=big, fill=(255, 214, 102), anchor="mm")
    draw.text((160, 140), "Learn & Move", font=small, fill=(255, 255, 255), anchor="mm")
    img.save(out_path)


if __name__ == "__main__":
    main(sys.argv[1])
