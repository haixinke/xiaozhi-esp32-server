# 软著材料目录索引

本目录存放上海爱予慧科技有限责任公司申请中国计算机软件著作权（CPCC R11）的全部材料。

## 目录结构

```
main/official-docs/
├── 00-公共参考/                      # 跨件共用材料
│   └── 软著申请官方要求调研.md        # 官方要求调研报告
├── 01-智控台-爱予慧AI智能体管理平台V1.0/   # 第 1 件（已提交口径准备完毕）
│   ├── 爱予慧AI智能体管理平台V1.0-源代码.pdf
│   ├── 爱予慧AI智能体管理平台V1.0-操作手册.pdf
│   ├── 上游MIT许可证明-LICENSE.pdf   # 二次开发，附原著作权人许可证明
│   ├── 申请表填写草稿.md
│   └── 提交清单.md
├── 02-蛋宝宝小程序-爱予慧蛋宝宝AI宠物小程序软件V1.0/  # 第 2 件（批 1）
│   ├── 爱予慧蛋宝宝AI宠物小程序软件V1.0-源代码.pdf
│   ├── 爱予慧蛋宝宝AI宠物小程序软件V1.0-操作手册.pdf
│   ├── 申请表填写草稿.md              # 100% 自研，无 MIT 附带
│   └── 提交清单.md
├── 03-语音服务-爱予慧AI语音交互服务系统V1.0/   # 第 3 件（批 2）
│   ├── 爱予慧AI语音交互服务系统V1.0-源代码.pdf
│   ├── 爱予慧AI语音交互服务系统V1.0-设计说明书.pdf
│   ├── 上游MIT许可证明-LICENSE.pdf   # 二次开发，附原著作权人许可证明
│   ├── 申请表填写草稿.md
│   └── 提交清单.md
├── 04-后端服务-爱予慧AI智能体管理服务后端V1.0/   # 第 5 件（批 3）
│   ├── 爱予慧AI智能体管理服务后端V1.0-源代码.pdf   # 取 manager-api Java 后端
│   ├── 爱予慧AI智能体管理服务后端V1.0-设计说明书.pdf
│   ├── 上游MIT许可证明-LICENSE.pdf   # 二次开发，附原著作权人许可证明
│   ├── 申请表填写草稿.md
│   └── 提交清单.md
├── screenshots/                      # 操作手册截图素材
│   ├── 01-智控台/                    # 27 张（Playwright 自动捕获）
│   └── 02-蛋宝宝/                    # 23 张（微信开发者工具自动捕获）
└── tools/                            # 生成脚本（可重跑）
    ├── 01-智控台/                    # gen_source_pdf.py / gen_manual.py / capture_screenshots.mjs
    ├── 02-蛋宝宝/                    # gen_source_egg.py / gen_manual_egg.py / capture_egg_screenshots.cjs
    ├── 03-语音服务/                 # gen_source_voice.py / gen_design_voice.py / gen_diagrams.py
    └── 05-后端服务/                 # gen_source_manager_api.py / gen_design_manager_api.py / gen_diagrams_manager_api.py
    ├── package.json                  # 共用 npm 依赖（playwright、miniprogram-automator）
    └── node_modules/
```

## 4 件软著总表

| 批次 | # | 软件全称 | 完成日期 | 文档类型 | MIT | 目录 | 状态 |
|---|---|---|---|---|---|---|---|
| 已交 | 1 | 爱予慧AI智能体管理平台 V1.0 | 2026-08-31 | 操作手册 | 附 | 01- | 材料齐（源码改取 manager-web Vue 前端，后端 Java 拆至 #5） |
| 批1 | 2 | 爱予慧蛋宝宝AI宠物小程序软件 V1.0 | 2026-08-15 | 操作手册 | 不涉及 | 02- | 材料齐 |
| 批2 | 3 | 爱予慧AI语音交互服务系统 V1.0 | 待定 | 设计说明书 | 附 | 03- | 材料齐 |
| 批3 | 4 | 爱予慧数字人交互演示软件 V1.0 | 待定 | 设计说明书 | 不涉及 | 待建 | 待做 |
| 批3 | 5 | 爱予慧AI智能体管理服务后端 V1.0 | 2026-07-31 | 设计说明书 | 附 | 04- | 材料齐 |

著作权人统一：上海爱予慧科技有限责任公司，单独开发，原始取得，全部权利，未发表，简称留空，版本 V1.0。

## 脚本重跑

各脚本路径已改为绝对路径，可在任意位置执行：

- 源码 PDF：`python3 tools/0X-XXX/gen_source[_egg|_voice].py`
- 操作手册 PDF：`python3 tools/0X-XXX/gen_manual[_egg].py`（依赖对应 screenshots/ 子目录）
- 设计说明书 PDF：`python3 tools/03-语音服务/gen_design_voice.py`（无界面服务，纯文本+表格）
- 后端设计说明书 PDF：`python3 tools/05-后端服务/gen_design_manager_api.py`（Java Spring Boot 后端，含 3 配图）
- 后端源码 PDF：`python3 tools/05-后端服务/gen_source_manager_api.py`（取 manager-api Java）
- 后端配图：`python3 tools/05-后端服务/gen_diagrams_manager_api.py`（graphviz 分层架构/鉴权/配置下发）
- 截图捕获：`node tools/01-智控台/capture_screenshots.mjs`（智控台，需本地服务 8001 端口 + Playwright）
- 截图捕获：`node tools/02-蛋宝宝/capture_egg_screenshots.cjs`（需微信开发者工具 cli auto --auto-port 9420）

## 用户侧待办

- [ ] 营业执照副本扫描件（PDF，放入对应 0X- 目录）
- [ ] 各件 CPCC 系统在线填报（照申请表填写草稿）
- [ ] 打印签章页盖公章扫描上传
- [ ] 等受理通知书
