"""產生 Android TV banner（xhdpi 320x180 px，圖內要有文字）。

字型用 Noto Sans TC（SIL OFL 1.1）。字型檔不在 repo 裡，請自己下載：
https://fonts.google.com/noto/specimen/Noto+Sans+TC

用法：
  python3 tools/make_banner.py app/src/main/res/drawable-xhdpi/banner.png --font <NotoSansTC 字型檔>
  或設環境變數 BANNER_FONT=<NotoSansTC 字型檔>，再省略 --font。
"""
import argparse
import os

from PIL import Image, ImageDraw, ImageFont


def load_font(path, size, weight):
    font = ImageFont.truetype(path, size)
    # 可變字重字型（例如 NotoSansTC-wght.ttf）要指定字重；靜態字型會丟 OSError，照原樣用。
    try:
        font.set_variation_by_axes([weight])
    except OSError:
        pass
    return font


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("out_path")
    parser.add_argument("--font", default=os.environ.get("BANNER_FONT"))
    args = parser.parse_args()
    if not args.font:
        raise SystemExit("請用 --font 或環境變數 BANNER_FONT 指定 Noto Sans TC 字型檔")

    img = Image.new("RGB", (320, 180), (38, 70, 83))
    draw = ImageDraw.Draw(img)
    # 4 字 × 60 px ＝ 240 px，左右各留 40 px。
    big = load_font(args.font, 60, 400)
    small = load_font(args.font, 30, 400)
    draw.text((160, 76), "動動學學", font=big, fill=(255, 214, 102), anchor="mm")
    draw.text((160, 140), "Learn & Move", font=small, fill=(255, 255, 255), anchor="mm")
    img.save(args.out_path)


if __name__ == "__main__":
    main()
