#!/usr/bin/env python3
"""生成设计说明书配图（架构图 / 时序图 / 流程图），graphviz dot → PNG。"""

import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "diagrams")
os.makedirs(OUT, exist_ok=True)

FONT = "Songti SC"
MONO = "Menlo"

ARCH_DOT = f"""
digraph G {{
  rankdir=TB;
  graph [fontname="{FONT}", fontsize=12, bgcolor="white", pad=0.2, nodesep=0.35, ranksep=0.55];
  node  [fontname="{FONT}", fontsize=12, style="filled,rounded", shape=box, color="#555", fillcolor="#f5f5f5", margin="0.12,0.06"];
  edge  [fontname="{FONT}", fontsize=10, color="#555"];

  subgraph cluster_ext {{ label="外部"; style=dashed; color="#999";
    esp [label="ESP32 终端设备", fillcolor="#e8f0fe"];
    ai  [label="AI 服务商\\n(ASR/TTS/LLM)", fillcolor="#e8f0fe"];
    mt  [label="智控台 manager-api", fillcolor="#e8f0fe"];
  }}

  subgraph cluster_in {{ label="爱予慧AI语音交互服务系统"; style="rounded"; color="#333"; penwidth=1.2;
    subgraph cluster_l1 {{ label="接入层"; style="rounded"; color="#888";
      ws  [label="WebSocketServer\\n端口 8000", fillcolor="#fff3e0"];
      http[label="SimpleHttpServer\\n端口 8003", fillcolor="#fff3e0"];
      auth[label="JWT 鉴权", fillcolor="#fff3e0"];
    }}
    subgraph cluster_l2 {{ label="编排层"; style="rounded"; color="#888";
      conn[label="ConnectionHandler\\n连接状态机", fillcolor="#e8f5e9"];
      hdl [label="消息处理器\\n(hello/listen/abort/iot)", fillcolor="#e8f5e9"];
    }}
    subgraph cluster_l3 {{ label="能力层"; style="rounded"; color="#888";
      prov[label="Provider 工厂\\nASR/TTS/LLM/VAD/Intent/Memory", fillcolor="#f3e5f5"];
      tool[label="UnifiedToolHandler\\n+ 函数插件", fillcolor="#f3e5f5"];
    }}
    subgraph cluster_l4 {{ label="存储层"; style="rounded"; color="#888";
      ob  [label="OceanBase\\n向量记忆 + 知识图谱", fillcolor="#fce4ec"];
      rd  [label="Redis\\n缓存", fillcolor="#fce4ec"];
    }}
  }}

  esp -> ws  [label="Opus 音频流"];
  ws  -> auth [label="握手鉴权"];
  auth -> conn;
  conn -> hdl;
  hdl  -> prov [label="流水线编排"];
  hdl  -> tool [label="工具调用"];
  prov -> ai   [label="ASR/TTS/LLM API"];
  conn -> ob   [label="记忆存取"];
  conn -> rd   [label="热缓存"];
  http -> mt   [label="远程配置/OTA"];
  mt  -> conn  [label="配置下发/设备校验", style=dashed];
  http -> esp  [label="OTA 固件", style=dashed];
}}
"""

SEQ_DOT = f"""
digraph G {{
  rankdir=TB;
  graph [fontname="{FONT}", fontsize=12, bgcolor="white", pad=0.2, nodesep=0.25, ranksep=0.4];
  node  [fontname="{FONT}", fontsize=11, style="filled,rounded", shape=box, color="#555", fillcolor="#f5f5f5", margin="0.1,0.05"];
  edge  [fontname="{FONT}", fontsize=10, color="#333"];

  d0 [label="① 设备上传 Opus 帧", fillcolor="#e8f0fe"];
  d1 [label="② 解码 PCM + AEC 回声消除", fillcolor="#fff3e0"];
  d2 [label="③ VAD 语音活动检测", fillcolor="#fff3e0"];
  d3 [label="④ ASR 流式识别 → 文本", fillcolor="#e8f5e9"];
  d4 [label="⑤ 意图识别", fillcolor="#f3e5f5"];
  d5 [label="命中预设意图?", shape=diamond, fillcolor="#fffde7"];
  d6 [label="⑥ 执行工具/直接回答", fillcolor="#fce4ec"];
  d7 [label="⑦ LLM 多轮推理", fillcolor="#e8f5e9"];
  d8 [label="⑧ TTS 句子切分 + 流式合成", fillcolor="#fff3e0"];
  d9 [label="⑨ sendAudioHandle 回传", fillcolor="#e8f0fe"];
  d10[label="⑩ 设备播放", fillcolor="#e8f0fe"];

  d0 -> d1 -> d2 -> d3 -> d4 -> d5;
  d5 -> d6 [label="是"];
  d5 -> d7 [label="否"];
  d6 -> d8;
  d7 -> d8;
  d8 -> d9 -> d10;

  // 工具回灌
  d6 -> d7 [label="工具结果回灌", style=dashed, constraint=false];
}}
"""

FLOW_DOT = f"""
digraph G {{
  rankdir=TB;
  graph [fontname="{FONT}", fontsize=12, bgcolor="white", pad=0.2, nodesep=0.3, ranksep=0.5];
  node  [fontname="{FONT}", fontsize=11, style="filled,rounded", shape=box, color="#555", fillcolor="#f5f5f5", margin="0.12,0.06"];
  edge  [fontname="{FONT}", fontsize=10, color="#333"];

  s1 [label="设备 WebSocket 握手", fillcolor="#e8f0fe"];
  s2 [label="JWT/Oauth 鉴权", shape=diamond, fillcolor="#fffde7"];
  s3 [label="创建 ConnectionHandler", fillcolor="#e8f5e9"];
  s4 [label="懒加载 Provider\\n(ASR/TTS/LLM/VAD/..)", fillcolor="#e8f5e9"];
  s5 [label="记忆召回 + 提示词增强", fillcolor="#e8f5e9"];
  s6 [label="进入对话主循环", fillcolor="#e8f5e9"];
  s7 [label="收音 → 流水线 → 回传", fillcolor="#fff3e0"];
  s8 [label="用户打断(abort)?", shape=diamond, fillcolor="#fffde7"];
  s9 [label="清理 TTS 队列\\n重置音频状态", fillcolor="#fce4ec"];
  s10[label="无语音超时?", shape=diamond, fillcolor="#fffde7"];
  s11[label="保存对话摘要", fillcolor="#fce4ec"];
  s12[label="清理队列/释放资源", fillcolor="#fce4ec"];
  s13[label="关闭连接", fillcolor="#e8f0fe"];

  s1 -> s2;
  s2 -> s3 [label="通过"];
  s2 -> s13 [label="失败", style=dashed];
  s3 -> s4 -> s5 -> s6 -> s7;
  s7 -> s8;
  s8 -> s9 [label="是"];
  s8 -> s7 [label="否", style=dashed];
  s9 -> s6;
  s8 -> s10 [label="否"];
  s10 -> s11 [label="是(120s)"];
  s10 -> s7 [label="否", style=dashed];
  s11 -> s12 -> s13;
}}
"""

DOTS = {
    "arch": ARCH_DOT,
    "sequence": SEQ_DOT,
    "flow": FLOW_DOT,
}


def main():
    for name, src in DOTS.items():
        dot_path = os.path.join(OUT, f"{name}.dot")
        png_path = os.path.join(OUT, f"{name}.png")
        with open(dot_path, "w", encoding="utf-8") as f:
            f.write(src)
        r = subprocess.run(
            ["dot", "-Tpng", "-Gdpi=150", dot_path, "-o", png_path],
            capture_output=True, text=True,
        )
        if r.returncode != 0:
            print(f"FAIL {name}: {r.stderr}")
        else:
            print(f"OK {name} -> {png_path}")


if __name__ == "__main__":
    sys.exit(main())
