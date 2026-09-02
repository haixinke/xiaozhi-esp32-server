#!/usr/bin/env python3
"""生成 #5 设计说明书配图（分层架构 / 远程配置下发时序 / 鉴权流程），graphviz dot -> PNG。"""

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
    web [label="manager-web\\n智控台前端", fillcolor="#e8f0fe"];
    mob [label="manager-mobile\\n移动管理端", fillcolor="#e8f0fe"];
    py  [label="xiaozhi-server\\nPython AI 核心", fillcolor="#e8f0fe"];
    esp [label="ESP32 设备", fillcolor="#e8f0fe"];
  }}

  subgraph cluster_in {{ label="爱予慧AI智能体管理服务后端"; style="rounded"; color="#333"; penwidth=1.2;
    subgraph cluster_l1 {{ label="接入层"; style="rounded"; color="#888";
      ctl [label="Controller 层\\n(@RestController, /xiaozhi)", fillcolor="#fff3e0"];
      knf [label="Knife4j/SpringDoc\\n接口文档", fillcolor="#fff3e0"];
    }}
    subgraph cluster_l2 {{ label="控制层"; style="rounded"; color="#888";
      shiro [label="Shiro 过滤器链\\nauthc/jwt/anon", fillcolor="#e8f5e9"];
      gh    [label="全局 Handler\\n异常/XSS/参数解析", fillcolor="#e8f5e9"];
      asp   [label="Aspect 切面\\n操作日志/幂等", fillcolor="#e8f5e9"];
    }}
    subgraph cluster_l3 {{ label="服务层"; style="rounded"; color="#888";
      svc [label="Service 业务\\ndevice/agent/config/pdc/pet\\nvoiceclone/sys/...", fillcolor="#f3e5f5"];
    }}
    subgraph cluster_l4 {{ label="持久化层"; style="rounded"; color="#888";
      mp  [label="MyBatis-Plus DAO\\n+ Liquibase 迁移", fillcolor="#fce4ec"];
      ob  [label="OceanBase\\n(MySQL 兼容)", fillcolor="#fce4ec"];
      rd  [label="Redis\\n缓存/会话", fillcolor="#fce4ec"];
    }}
  }}

  web -> ctl [label="REST /xiaozhi"];
  mob -> ctl;
  ctl -> shiro [label="请求拦截"];
  shiro -> gh;
  gh -> asp;
  asp -> svc;
  svc -> mp [label="CRUD"];
  mp -> ob;
  svc -> rd [label="缓存读写"];
  svc -> py  [label="配置下发", style=dashed];
  py  -> esp [label="WebSocket", style=dashed];
}}
"""

SEQ_DOT = f"""
digraph G {{
  rankdir=TB;
  graph [fontname="{FONT}", fontsize=12, bgcolor="white", pad=0.2, nodesep=0.25, ranksep=0.4];
  node  [fontname="{FONT}", fontsize=11, style="filled,rounded", shape=box, color="#555", fillcolor="#f5f5f5", margin="0.1,0.05"];
  edge  [fontname="{FONT}", fontsize=10, color="#333"];

  d0 [label="① 智控台前端提交配置变更", fillcolor="#e8f0fe"];
  d1 [label="② ConfigController 接收\\n@RequiresPermissions 校验", fillcolor="#fff3e0"];
  d2 [label="③ ConfigService 更新\\n写入 OceanBase t_config", fillcolor="#e8f5e9"];
  d4 [label="④ xiaozhi-server 拉取\\nGET /xiaozhi/config", fillcolor="#f3e5f5"];
  d5 [label="⑤ 三层配置递归合并\\n覆盖本地 .config.yaml", fillcolor="#fffde7"];
  d6 [label="⑥ Provider 热重载\\n无需重启即生效", fillcolor="#e8f5e9"];
  d7 [label="⑦ 设备下发新参数", fillcolor="#e8f0fe"];

  d0 -> d1 -> d2;
  d2 -> d4 [label="轮询/触发", style=dashed];
  d4 -> d5 -> d6 -> d7;
}}
"""

FLOW_DOT = f"""
digraph G {{
  rankdir=TB;
  graph [fontname="{FONT}", fontsize=12, bgcolor="white", pad=0.2, nodesep=0.3, ranksep=0.5];
  node  [fontname="{FONT}", fontsize=11, style="filled,rounded", shape=box, color="#555", fillcolor="#f5f5f5", margin="0.12,0.06"];
  edge  [fontname="{FONT}", fontsize=10, color="#333"];

  s1 [label="HTTP 请求到达", fillcolor="#e8f0fe"];
  s2 [label="Shiro 过滤器链", fillcolor="#fff3e0"];
  s3 [label="路径匹配?", shape=diamond, fillcolor="#fffde7"];
  s4 [label="anon 白名单\\n(登录/文档/OTA)", fillcolor="#e8f5e9"];
  s5 [label="authc/jwt 校验", shape=diamond, fillcolor="#fffde7"];
  s6 [label="Token 解析 +\\n会话查询 Redis", fillcolor="#fce4ec"];
  s7 [label="@RequiresPermissions\\n权限注解校验", shape=diamond, fillcolor="#fffde7"];
  s8 [label="进入 Controller", fillcolor="#e8f5e9"];
  s9 [label="401 未认证", fillcolor="#fce4ec"];
  s10[label="403 无权限", fillcolor="#fce4ec"];

  s1 -> s2 -> s3;
  s3 -> s4 [label="白名单"];
  s3 -> s5 [label="需鉴权"];
  s5 -> s6 [label="解析"];
  s6 -> s7 [label="会话有效"];
  s5 -> s9 [label="无效/缺失", style=dashed];
  s7 -> s8 [label="通过"];
  s7 -> s10 [label="不足", style=dashed];
  s4 -> s8;
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
