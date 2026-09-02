#!/usr/bin/env python3
"""生成软著登记用源代码鉴别材料 PDF（爱予慧AI语音交互服务系统 V1.0）。

规则（依据《计算机软件著作权登记办法》第十条）：
- 取整个源程序的前 30 页 + 后 30 页，共 60 页
- 每页 60 行（≥50 行下限，确保合规）
- 页眉：软件全称 + 版本号；右上角连续页码
- A4 纵向 PDF

取材：xiaozhi-server 自研业务代码（app.py / config / models / core / plugins_func），
按业务叙事顺序拼成连续"源程序"，再取前 1800 行与后 1800 行。
本系统基于上游 xiaozhi-esp32-server（MIT, Copyright xinnan-tech 2025）二次开发，
新增语音流水线编排、Provider 工厂、WebSocket 设备连接、HTTP 接口等独创代码。
"""

import html
import os
import subprocess
import sys

ROOT = "/Users/minwang/codes/github/xiaozhi-esp32-server"
SRC_DIR = os.path.join(ROOT, "main/xiaozhi-server")
OUT_DIR = os.path.join(ROOT, "main/official-docs/03-语音服务-爱予慧AI语音交互服务系统V1.0")
OUT_PDF = os.path.join(OUT_DIR, "爱予慧AI语音交互服务系统V1.0-源代码.pdf")

SOFTWARE_NAME = "爱予慧AI语音交互服务系统 V1.0"

# 取材顺序：入口 → 配置 → 数据模型 → 核心连接/认证 → 业务处理 →
# Provider 流水线（ASR/VAD/LLM/TTS/意图/记忆/工具/内容安全）→
# HTTP/WebSocket 服务 → API → 工具 → 插件函数
# 按业务叙事排列，体现语音交互完整数据流
DIR_ORDER = [
    "app.py",                    # 服务入口
    "config",                    # 配置加载
    "models",                    # 数据模型
    os.path.join("core", "connection.py"),   # WebSocket 连接管理
    os.path.join("core", "auth.py"),          # 设备认证
    os.path.join("core", "handle"),          # 语音交互处理（收音/发音频/意图）
    os.path.join("core", "providers", "vad"),    # 语音活动检测
    os.path.join("core", "providers", "asr"),    # 语音识别
    os.path.join("core", "providers", "intent"), # 意图识别
    os.path.join("core", "providers", "llm"),    # 大语言模型
    os.path.join("core", "providers", "tts"),    # 语音合成
    os.path.join("core", "providers", "memory"), # 记忆管理
    os.path.join("core", "providers", "tools"),  # 工具调用
    os.path.join("core", "providers", "content_safety"),  # 内容安全
    os.path.join("core", "providers", "vllm"),    # 本地推理
    os.path.join("core", "http_server.py"),      # HTTP 服务
    os.path.join("core", "websocket_server.py"), # WebSocket 服务
    os.path.join("core", "api"),                 # REST API
    os.path.join("core", "utils"),               # 工具函数
    "plugins_func",                              # 函数插件
]

LINES_PER_PAGE = 60
HEAD_PAGES = 30
TAIL_PAGES = 30
HEAD_LINES = HEAD_PAGES * LINES_PER_PAGE  # 1800
TAIL_LINES = TAIL_PAGES * LINES_PER_PAGE  # 1800

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


def is_source(path: str) -> bool:
    """只取 .py 源码，排除 __pycache__、测试、备份。"""
    if not path.endswith(".py"):
        return False
    if "__pycache__" in path or path.endswith(".test.py"):
        return False
    bn = os.path.basename(path)
    if bn.startswith("test_"):
        return False
    return True


def collect_lines():
    """按业务叙事顺序收集全部源程序行，每个文件前插入文件标记注释行。"""
    lines = []
    for entry in DIR_ORDER:
        full = os.path.join(SRC_DIR, entry)
        if os.path.isfile(full) and full.endswith(".py"):
            rel = os.path.relpath(full, SRC_DIR)
            lines.append(f"# ===== 文件: xiaozhi-server/{rel} =====")
            with open(full, encoding="utf-8") as f:
                for raw in f:
                    lines.extend(wrap_line(raw.rstrip("\n")))
        elif os.path.isdir(full):
            for dirpath, dirnames, filenames in os.walk(full):
                # 跳过缓存目录
                dirnames[:] = [d for d in dirnames if d != "__pycache__"]
                for fn in sorted(filenames):
                    fpath = os.path.join(dirpath, fn)
                    if not is_source(fpath):
                        continue
                    rel = os.path.relpath(fpath, SRC_DIR)
                    lines.append(f"# ===== 文件: xiaozhi-server/{rel} =====")
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
        selected = lines
    else:
        selected = lines[:HEAD_LINES] + lines[-TAIL_LINES:]
    selected = selected[: len(selected) // LINES_PER_PAGE * LINES_PER_PAGE]
    pages = [
        selected[i : i + LINES_PER_PAGE]
        for i in range(0, len(selected), LINES_PER_PAGE)
    ]
    print(f"生成页数: {len(pages)} (每页 {LINES_PER_PAGE} 行)")

    os.makedirs(OUT_DIR, exist_ok=True)
    html_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".source_code_voice.html")
    with open(html_path, "w", encoding="utf-8") as f:
        f.write(build_html(pages))

    env = dict(os.environ)
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
