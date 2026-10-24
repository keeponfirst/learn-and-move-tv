#!/usr/bin/env python3
"""重產 Noto Sans TC 子集（Bold 700、Black 900），收 App 會畫出來的每一個字。

用法：
  python3 scripts/font-subset.py --source 可變字型.ttf   # 收字 → 產 res/font/notosanstc_{bold,black}.ttf → 驗缺字
  python3 scripts/font-subset.py --check                 # 只驗：現有子集缺哪些字（缺字 > 0 就回 1）

收字範圍（新增字串或素材後重跑一次，就不會再漏字）：
  - app/src/main/java/**/*.kt 的字串與字元常值（不收註解）
  - app/src/main/assets/**/*.json 的所有字串值（卡片、例詞、瑜伽、刷牙分區）
  - app/src/main/assets/**/*.tsv 全文（刷牙、巨人小螞蟻的台詞）
  - app/src/main/res/values/*.xml 的文字
  - ASCII 可見字元全收
原字型：Noto Sans TC 可變字型 2.004（NotoSansTC-wght.ttf，SIL OFL 1.1），
  從 https://github.com/notofonts/noto-cjk 或 Google Fonts 下載，用 --source 指定路徑。
  也可以放在 repo 外層的 fonts/NotoSansTC-wght.ttf（預設路徑）。字型檔不進 repo。
原字型本身沒有的字（例如表情符號）列出來，App 會退回系統字型；這些不算子集缺字。
需要 fontTools（pip install fonttools）。
"""
import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "app/src/main"
FONT_DIR = MAIN / "res/font"
OUTPUTS = {"notosanstc_bold.ttf": 700, "notosanstc_black.ttf": 900}
DEFAULT_SOURCE = ROOT.parent / "fonts/NotoSansTC-wght.ttf"


def kotlin_literals(src: str) -> list[str]:
    """Kotlin 原始碼裡的字串、字元常值（含字串樣板 ${...} 裡面的字串）。略過 // 與 /* */ 註解。"""
    out: list[str] = []
    i, n = 0, len(src)

    def read_template(j: int) -> int:
        # j 指在 "${" 的 "{" 後面；讀到對應的 "}"，裡面當程式碼再掃一次。
        depth, start = 1, j
        while j < n and depth > 0:
            if src[j] == '"':
                j = read_string(j)
                continue
            if src[j] == "{":
                depth += 1
            elif src[j] == "}":
                depth -= 1
            j += 1
        out.extend(kotlin_literals(src[start:j - 1]))
        return j

    def read_string(j: int) -> int:
        # j 指在開頭的引號；回傳結尾引號後面的位置。
        if src.startswith('"""', j):
            end = src.find('"""', j + 3)
            end = n if end < 0 else end
            out.append(src[j + 3:end])
            return end + 3
        j += 1
        buf = []
        while j < n and src[j] != '"' and src[j] != "\n":
            if src[j] == "\\" and j + 1 < n:
                buf.append(src[j + 1])
                j += 2
            elif src.startswith("${", j):
                j = read_template(j + 2)
            else:
                buf.append(src[j])
                j += 1
        out.append("".join(buf))
        return j + 1

    while i < n:
        if src.startswith("//", i):
            i = src.find("\n", i)
            i = n if i < 0 else i
        elif src.startswith("/*", i):
            depth, i = 1, i + 2
            while i < n and depth > 0:
                if src.startswith("/*", i):
                    depth, i = depth + 1, i + 2
                elif src.startswith("*/", i):
                    depth, i = depth - 1, i + 2
                else:
                    i += 1
        elif src[i] == '"':
            i = read_string(i)
        elif src[i] == "'":
            end = src.find("'", i + 2 if src[i + 1:i + 2] == "\\" else i + 1)
            if end > 0:
                out.append(src[i + 1:end])
                i = end + 1
            else:
                i += 1
        else:
            i += 1
    return out


def json_strings(value) -> list[str]:
    if isinstance(value, str):
        return [value]
    if isinstance(value, dict):
        return [s for k, v in value.items() for s in [k] + json_strings(v)]
    if isinstance(value, list):
        return [s for v in value for s in json_strings(v)]
    return []


def collect() -> tuple[set[str], dict[str, int]]:
    """回傳 (要收的字, 各來源收到幾個字)。"""
    chars: set[str] = {chr(c) for c in range(0x20, 0x7F)}
    counts: dict[str, int] = {}

    def add(name: str, texts: list[str]):
        got = {ch for t in texts for ch in t if ch.isprintable() or ch == "　"}
        counts[name] = counts.get(name, 0) + len(got)
        chars.update(got)

    for f in sorted((MAIN / "java").rglob("*.kt")):
        add("Kotlin 字串", kotlin_literals(f.read_text(encoding="utf-8")))
    for f in sorted((MAIN / "assets").rglob("*.json")):
        add("assets JSON", json_strings(json.loads(f.read_text(encoding="utf-8"))))
    for f in sorted((MAIN / "assets").rglob("*.tsv")):
        add("assets TSV", [f.read_text(encoding="utf-8")])
    for f in sorted((MAIN / "res").glob("values*/*.xml")):
        add("res/values", ["".join(ET.parse(f).getroot().itertext())])
    return chars, counts


def cmap_of(path: Path) -> set[str]:
    return {chr(c) for c in TTFont(path, lazy=True).getBestCmap()}


def show(chars) -> str:
    return " ".join(f"{c}(U+{ord(c):04X})" for c in sorted(chars))


def build(source: Path, chars: set[str]):
    have = cmap_of(source)
    absent = {c for c in chars if c not in have and not c.isspace()}
    if absent:
        print(f"原字型也沒有（退回系統字型，不算缺字）：{len(absent)} 個：{show(absent)}")
    wanted = sorted(c for c in chars if c in have)
    for name, weight in OUTPUTS.items():
        font = TTFont(source)
        options = subset.Options()
        options.name_IDs = ["*"]  # 留 STAT 的軸名，instancer 才改得出「Noto Sans TC Bold」這種名字
        options.name_languages = ["*"]
        options.layout_features = ["*"]
        options.notdef_outline = True
        sub = subset.Subsetter(options)
        sub.populate(unicodes=[ord(c) for c in wanted])
        sub.subset(font)
        static = instancer.instantiateVariableFont(font, {"wght": weight}, updateFontNames=True)
        out = FONT_DIR / name
        static.save(out)
        print(f"{out.relative_to(ROOT)}：wght {weight}、{len(static.getBestCmap())} 字、{out.stat().st_size:,} bytes")


def check(chars: set[str], source: Path | None) -> int:
    have_src = cmap_of(source) if source and source.exists() else None
    worst = 0
    for name in OUTPUTS:
        have = cmap_of(FONT_DIR / name)
        missing = {c for c in chars if c not in have and not c.isspace()}
        if have_src is not None:
            missing &= have_src  # 原字型也沒有的不算
        print(f"{name}：要收 {len(chars)} 字，缺 {len(missing)} 字" + (f"：{show(missing)}" if missing else ""))
        worst = max(worst, len(missing))
    return 1 if worst else 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    ap.add_argument("--check", action="store_true", help="只驗現有子集，不重產")
    args = ap.parse_args()
    chars, counts = collect()
    print("收字：" + "、".join(f"{k} {v}" for k, v in counts.items()) + f"；合計（含 ASCII）{len(chars)} 字")
    if not args.check:
        if not args.source.exists():
            print(f"找不到原字型：{args.source}", file=sys.stderr)
            return 1
        build(args.source, chars)
    return check(chars, args.source)


if __name__ == "__main__":
    sys.exit(main())
