#!/usr/bin/env python3
"""生成软著登记用源代码鉴别材料 PDF（爱予慧AI智能体管理平台 V1.0）。

规则（依据《计算机软件著作权登记办法》第十条）：
- 取整个源程序的前 30 页 + 后 30 页，共 60 页
- 每页 60 行（≥50 行下限）
- 页眉：软件全称 + 版本号；右上角连续页码
- A4 纵向 PDF

取材：manager-web 自研前端代码（Vue.js 2 管理控制台）。
按 main.js→App.vue→router→store→apis→views→components→utils→i18n 顺序
拼成连续"源程序"，取前 30 页 + 后 30 页。
全部为上海爱予慧二次开发的前端代码。
"""

import html
import os
import subprocess
import sys

ROOT = "/Users/minwang/codes/github/xiaozhi-esp32-server"
SRC_DIR = os.path.join(ROOT, "main/manager-web/src")
OUT_DIR = os.path.join(ROOT, "main/official-docs/01-智控台-爱予慧AI智能体管理平台V1.0")
OUT_PDF = os.path.join(OUT_DIR, "爱予慧AI智能体管理平台V1.0-源代码.pdf")

SOFTWARE_NAME = "爱予慧AI智能体管理平台 V1.0"

# 顶层文件（入口），按加载顺序
TOP_FILES = ["main.js", "App.vue", "registerServiceWorker.js", "service-worker.js"]

# 目录顺序：路由/状态/接口/视图/组件/工具/国际化
DIR_ORDER = ["router", "store", "apis", "views", "components", "utils", "i18n"]

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


def collect_lines():
    """按顺序收集源程序行，每个文件前插入文件标记注释行。"""
    lines = []
    # 顶层入口文件
    for fn in TOP_FILES:
        fpath = os.path.join(SRC_DIR, fn)
        if not os.path.isfile(fpath):
            continue
        lines.append(f"// ===== 文件: src/{fn} =====")
        with open(fpath, encoding="utf-8") as f:
            for raw in f:
                lines.extend(wrap_line(raw.rstrip("\n")))

    # 各目录
    for d in DIR_ORDER:
        ddir = os.path.join(SRC_DIR, d)
        if not os.path.isdir(ddir):
            continue
        for dirpath, _, filenames in os.walk(ddir):
            for fn in sorted(filenames):
                if not (fn.endswith(".vue") or fn.endswith(".js")):
                    continue
                # 跳过测试文件与繁体中文 locale（避免源码 PDF 出现繁体字）
                bn = os.path.basename(fn)
                if bn.startswith("test_") or ".test." in bn:
                    continue
                if bn == "zh_TW.js" or bn == "zh_HK.js":
                    continue
                # i18n 翻译字典（locale 数据文件）非业务逻辑，排除以腾出空间放业务码
                if d == "i18n" and bn != "index.js":
                    continue
                fpath = os.path.join(dirpath, fn)
                rel = os.path.relpath(fpath, SRC_DIR)
                lines.append(f"// ===== 文件: src/{rel} =====")
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
  font-family: "Menlo", "Courier New", "Songti SC", "SimSun", monospace;
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
    html_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".source_code.html")
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
