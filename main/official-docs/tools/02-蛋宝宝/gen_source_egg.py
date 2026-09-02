#!/usr/bin/env python3
"""生成 egg-miniprogram 软著登记用源代码鉴别材料 PDF。

规则（依据《计算机软件著作权登记办法》第十条）：
- 取整个源程序的前 30 页 + 后 30 页，共 60 页
- 每页 50 行
- 页眉：软件全称 + 版本号；右上角连续页码
- A4 纵向 PDF

取材：egg-miniprogram 微信小程序全部自研源码（.js 逻辑 + .wxml 模板），
排除 .test.js（测试代码）、.wxss（样式）、.json（配置数据）。
按业务重要性排序：入口 → 配置 → 服务 → 工具(API层) → 底栏 → 组件 → 页面，
使核心业务逻辑出现在前 30 页。全部为上海爱予慧 100% 自研代码，无上游 MIT 依赖。
"""

import html
import os
import subprocess
import sys

ROOT = "/Users/minwang/codes/github/xiaozhi-esp32-server"
SRC_DIR = os.path.join(ROOT, "main/egg-miniprogram/miniprogram")
OUT_DIR = os.path.join(ROOT, "main/official-docs/02-蛋宝宝小程序-爱予慧蛋宝宝AI宠物小程序软件V1.0")
OUT_PDF = os.path.join(OUT_DIR, "爱予慧蛋宝宝AI宠物小程序软件V1.0-源代码.pdf")

SOFTWARE_NAME = "爱予慧蛋宝宝AI宠物小程序软件 V1.0"

# 取材目录顺序（业务重要性优先，核心逻辑置前）
DIR_ORDER = [
    "config",
    "services",
    "utils",
    "custom-tab-bar",
    "components",
    "pages",
]

LINES_PER_PAGE = 60
HEAD_PAGES = 30
TAIL_PAGES = 30
HEAD_LINES = HEAD_PAGES * LINES_PER_PAGE  # 1800
TAIL_LINES = TAIL_PAGES * LINES_PER_PAGE  # 1800

# A4 内容宽约 180mm；8pt 等宽字体每行约 106 个半角列
MAX_COLS = 104


def display_width(s: str) -> int:
    """显示宽度：CJK 字符按 2 列计。"""
    return sum(2 if ord(c) > 0x2E7F else 1 for c in s)


def wrap_line(line: str):
    """超长行按显示宽度折行，续行缩进 4 空格。制表符先展开。"""
    line = line.expandtabs(4).rstrip()
    if display_width(line) <= MAX_COLS:
        return [line]
    parts = []
    cur, cur_w = "", 0
    for ch in line:
        w = 2 if ord(ch) > 0x2E7F else 1
        if cur_w + w > MAX_COLS:
            parts.append(cur)
            cur, cur_w = "    " + ch, 4 + w
        else:
            cur += ch
            cur_w += w
    parts.append(cur)
    return parts


def is_source(fn: str) -> bool:
    """只取 .js 和 .wxml，排除测试文件。"""
    if fn.endswith(".test.js"):
        return False
    return fn.endswith(".js") or fn.endswith(".wxml")


def collect_lines():
    """按目录顺序收集源程序行，每个文件前插入文件标记行。"""
    lines = []

    # 入口文件 app.js 置最前
    app_js = os.path.join(SRC_DIR, "app.js")
    if os.path.isfile(app_js):
        lines.append("===== 文件: app.js =====")
        with open(app_js, encoding="utf-8") as f:
            for raw in f:
                lines.extend(wrap_line(raw.rstrip("\n")))


    # 按业务顺序遍历各目录
    for subdir in DIR_ORDER:
        dpath = os.path.join(SRC_DIR, subdir)
        if not os.path.isdir(dpath):
            continue
        for dirpath, _, filenames in os.walk(dpath):
            # 每个文件单独处理；同目录内按文件名排序
            for fn in sorted(filenames):
                if not is_source(fn):
                    continue
                fpath = os.path.join(dirpath, fn)
                rel = os.path.relpath(fpath, SRC_DIR)
                lines.append(f"===== 文件: {rel} =====")
                with open(fpath, encoding="utf-8") as f:
                    for raw in f:
                        lines.extend(wrap_line(raw.rstrip("\n")))

    return lines


def build_html(pages):
    page_divs = []
    for page_lines in pages:
        body = html.escape("\n".join(page_lines))
        page_divs.append(f'<div class="src-page"><pre>{body}</pre></div>')
    pages_html = "\n".join(page_divs)
    return f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<style>
@page {{
  size: A4;
  margin: 22mm 15mm 18mm 15mm;
  @top-center {{ content: "{SOFTWARE_NAME}"; font-family: "Songti SC", "SimSun", serif; font-size: 10.5pt; }}
  @top-right {{ content: "第 " counter(page) " 页"; font-family: "Songti SC", "SimSun", serif; font-size: 9pt; }}
}}
body {{ margin: 0; }}
.src-page {{ page-break-after: always; }}
.src-page pre {{
  margin: 0;
  font-family: "Songti SC", "Menlo", "Courier New", monospace;
  font-size: 8pt;
  line-height: 3.95mm;
  white-space: pre;
}}
</style>
</head>
<body>
{pages_html}
</body>
</html>
"""


def main():
    lines = collect_lines()
    total = len(lines)
    print(f"源程序总行数: {total}")
    if total < HEAD_LINES + TAIL_LINES:
        selected = lines  # 不足 60 页则全部提交
    else:
        selected = lines[:HEAD_LINES] + lines[-TAIL_LINES:]
    # 末尾截断到整页
    selected = selected[: len(selected) // LINES_PER_PAGE * LINES_PER_PAGE]
    pages = [
        selected[i : i + LINES_PER_PAGE]
        for i in range(0, len(selected), LINES_PER_PAGE)
    ]
    print(f"生成页数: {len(pages)} (每页 {LINES_PER_PAGE} 行)")

    os.makedirs(OUT_DIR, exist_ok=True)
    html_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".source_egg.html")
    with open(html_path, "w", encoding="utf-8") as f:
        f.write(build_html(pages))

    env = dict(os.environ)
    # weasyprint 依赖 Homebrew 的 pango/glib，框架版 Python 需显式给出库路径
    env["DYLD_FALLBACK_LIBRARY_PATH"] = "/opt/homebrew/lib"
    subprocess.run(
        ["weasyprint", html_path, OUT_PDF],
        check=True,
        capture_output=True,
        env=env,
    )
    print(f"输出: {OUT_PDF}")


if __name__ == "__main__":
    sys.exit(main())
