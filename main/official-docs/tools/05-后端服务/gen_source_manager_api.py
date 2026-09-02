#!/usr/bin/env python3
"""生成软著登记用源代码鉴别材料 PDF（爱予慧AI智能体管理服务后端 V1.0）。

规则（依据《计算机软件著作权登记办法》第十条）：
- 取整个源程序的前 30 页 + 后 30 页，共 60 页
- 每页 60 行（≥50 行下限）
- 页眉：软件全称；右上角连续页码
- A4 纵向 PDF

取材：manager-api 自研 Java 源码（Spring Boot 管理后台）。
按业务叙事顺序拼成连续"源程序"，取前 30 页 + 后 30 页。
全部为上海爱予慧基于上游 MIT 二次开发的独创代码。
"""

import html
import os
import subprocess
import sys

ROOT = "/Users/minwang/codes/github/xiaozhi-esp32-server"
JAVA_ROOT = os.path.join(ROOT, "main/manager-api/src/main/java/xiaozhi")
MODULES_DIR = os.path.join(JAVA_ROOT, "modules")
COMMON_DIR = os.path.join(JAVA_ROOT, "common")
OUT_DIR = os.path.join(ROOT, "main/official-docs/04-后端服务-爱予慧AI智能体管理服务后端V1.0")
OUT_PDF = os.path.join(OUT_DIR, "爱予慧AI智能体管理服务后端V1.0-源代码.pdf")

SOFTWARE_NAME = "爱予慧AI智能体管理服务后端 V1.0"

# 取材顺序：common 基础设施 → 核心业务模块 → 扩展业务模块
# 前 30 页落 common/device/agent；后 30 页落 pdc/pet/voiceclone/sys/storyengine
MODULE_ORDER = [
    # 基础设施（common 各子包）
    ("common/config", "common/config"),
    ("common/exception", "common/exception"),
    ("common/handler", "common/handler"),
    ("common/interceptor", "common/interceptor"),
    ("common/aspect", "common/aspect"),
    ("common/constant", "common/constant"),
    ("common/utils", "common/utils"),
    ("common/annotation", "common/annotation"),
    ("common/convert", "common/convert"),
    ("common/page", "common/page"),
    ("common/validator", "common/validator"),
    ("common/xss", "common/xss"),
    ("common/oss", "common/oss"),
    ("common/upload", "common/upload"),
    ("common/redis", "common/redis"),
    ("common/service", "common/service"),
    ("common/dao", "common/dao"),
    ("common/entity", "common/entity"),
    ("common/user", "common/user"),
    # 核心业务
    ("modules/device", "modules/device"),
    ("modules/agent", "modules/agent"),
    ("modules/config", "modules/config"),
    ("modules/security", "modules/security"),
    # 扩展业务（后 30 页区）
    ("modules/pdc", "modules/pdc"),
    ("modules/pet", "modules/pet"),
    ("modules/voiceclone", "modules/voiceclone"),
    ("modules/sys", "modules/sys"),
    ("modules/storyengine", "modules/storyengine"),
    ("modules/knowledge", "modules/knowledge"),
    ("modules/payment", "modules/payment"),
    ("modules/subscription", "modules/subscription"),
    ("modules/model", "modules/model"),
    ("modules/timbre", "modules/timbre"),
    ("modules/wechat", "modules/wechat"),
    ("modules/invite", "modules/invite"),
    ("modules/companion", "modules/companion"),
    ("modules/feedback", "modules/feedback"),
    ("modules/item", "modules/item"),
    ("modules/correctword", "modules/correctword"),
    ("modules/email", "modules/email"),
    ("modules/sms", "modules/sms"),
    ("modules/llm", "modules/llm"),
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


def collect_lines():
    """按顺序收集源程序行，每个文件前插入文件标记注释行。

    先入 AdminApplication 入口类，再按 MODULE_ORDER 顺序遍历各子包。
    """
    lines = []
    # 入口类
    entry = os.path.join(JAVA_ROOT, "AdminApplication.java")
    if os.path.isfile(entry):
        lines.append("// ===== 文件: xiaozhi/AdminApplication.java =====")
        with open(entry, encoding="utf-8") as f:
            for raw in f:
                lines.extend(wrap_line(raw.rstrip("\n")))

    for rel_module, label in MODULE_ORDER:
        # rel_module 形如 "common/config" 或 "modules/device"
        base = COMMON_DIR if rel_module.startswith("common/") else MODULES_DIR
        sub = rel_module.split("/", 1)[1] if "/" in rel_module else rel_module
        mdir = os.path.join(base, sub)
        if not os.path.isdir(mdir):
            continue
        for dirpath, _, filenames in os.walk(mdir):
            for fn in sorted(filenames):
                if not fn.endswith(".java"):
                    continue
                fpath = os.path.join(dirpath, fn)
                rel = os.path.relpath(fpath, JAVA_ROOT)
                lines.append(f"// ===== 文件: xiaozhi/{rel} =====")
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
