#!/usr/bin/env python3
"""生成软著登记用操作手册 PDF。

- 页眉：软件全称 + 版本号；右上角连续页码（封面除外）
- 内容：系统简介、运行环境、各功能模块操作说明 + Playwright 截图
- 截图位于 ../screenshots/，由 capture_screenshots.mjs 生成
"""

import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = "/Users/minwang/codes/github/xiaozhi-esp32-server"
SHOTS = os.path.join(ROOT, "main/official-docs/screenshots/01-智控台")
OUT_PDF = os.path.join(ROOT, "main/official-docs/01-智控台-爱予慧AI智能体管理平台V1.0/爱予慧AI智能体管理平台V1.0-操作手册.pdf")

SOFTWARE_NAME = "爱予慧AI智能体管理平台 V1.0"
COMPANY = "上海爱予慧科技有限责任公司"


def img(name):
    """截图存在则返回 img 标签，否则返回占位提示。"""
    path = os.path.join(SHOTS, name + ".png")
    if os.path.exists(path):
        return f'<img class="shot" src="file://{path}" alt="{name}">'
    return f'<p class="missing">[待补截图: {name}]</p>'


SECTIONS = [
    (
        "一、系统概述",
        f"""
<p>{SOFTWARE_NAME[:-5]}是上海爱予慧科技有限责任公司独立开发的 AI 智能体一体化运营管理软件，面向 AI 语音陪伴硬件（ESP32 智能终端）与配套微信小程序，提供智能体配置、设备管理、宠物养成运营、NFC 资产管理、用户与反馈管理等全流程运营支撑能力。</p>
<p>系统采用前后端分离架构：前端为基于 Vue.js 2 与 Element UI 构建的 Web 管理控制台；后端基于 Java 21 与 Spring Boot 3 提供 RESTful API；数据层采用 MySQL 兼容关系数据库与 Redis 缓存。平台通过 Provider 适配层对接多家大模型、语音合成与语音识别服务商，支持运行时动态调整 AI 参数而无需重启服务。</p>
<p>本手册面向平台运营与管理人员，说明系统的运行环境与各功能模块的操作方法。</p>
<h3>1.1 运行环境</h3>
<p>服务端：Java 21 运行环境、Spring Boot 3.4、MySQL 兼容数据库（OceanBase）、Redis 6 及以上。前端：现代浏览器（Chrome、Edge、Safari），推荐分辨率 1440×900 及以上。</p>
<h3>1.2 登录系统</h3>
<p>在浏览器地址栏输入平台部署地址，进入登录页。输入管理员分配的账号与密码，完成图形验证后点击登录，进入系统首页。首页展示平台核心数据概览与功能导航。</p>
{img('01-首页')}
""",
    ),
    (
        "二、智能体管理",
        f"""
<p>智能体管理用于维护与终端用户对话的 AI 角色。运营人员可基于模板快速创建智能体，并对其角色设定、声纹与音色进行配置。</p>
<h3>2.1 智能体模板</h3>
<p>智能体模板页展示平台内置的角色模板。点击模板可查看角色设定详情，基于模板可快速完成新智能体的参数初始化，降低配置成本。</p>
{img('02-智能体模板')}
<h3>2.2 角色配置</h3>
<p>角色配置页用于编辑智能体的角色提示词、对话参数与关联模型。配置保存后实时生效，无需重启服务。</p>
{img('03-角色配置')}
<h3>2.3 声纹管理</h3>
<p>声纹管理页用于维护用户声纹特征，支持声纹的登记、查看与删除，为语音交互中的说话人识别提供数据支撑。</p>
{img('04-声纹管理')}
<h3>2.4 音色克隆与音色资源</h3>
<p>音色克隆管理页用于提交与审核音色克隆任务；音色资源页展示已开通的音色资源列表及其开通状态，供智能体配置时选用。</p>
{img('05-音色克隆')}
{img('06-音色资源')}
""",
    ),
    (
        "三、设备与用户管理",
        f"""
<h3>3.1 设备管理</h3>
<p>设备管理页展示已注册 ESP32 终端设备的列表，包括设备标识、绑定用户、激活状态与最近连接时间。支持按条件检索设备、查看设备详情与解绑操作。</p>
{img('07-设备管理')}
<h3>3.2 用户管理</h3>
<p>用户管理页展示平台账号与小程序用户列表，支持查询、禁用与启用等管理操作，并可查看用户关联的设备与宠物信息。</p>
{img('08-用户管理')}
""",
    ),
    (
        "四、模型与系统配置",
        f"""
<h3>4.1 模型配置</h3>
<p>模型配置页用于维护大语言模型、语音合成、语音识别等 AI 能力的接入参数，包括服务商选择、接口地址与密钥配置，支持按用途分别配置。</p>
{img('09-模型配置')}
<h3>4.2 供应商管理</h3>
<p>供应商管理页集中维护 AI 服务商账号与配额信息，为模型配置提供可选的供应商来源。</p>
{img('10-供应商管理')}
<h3>4.3 参数管理</h3>
<p>参数管理页以键值形式维护系统运行参数，修改后实时下发至各服务节点，支撑运行期调优。</p>
{img('11-参数管理')}
<h3>4.4 字典管理</h3>
<p>字典管理页维护业务枚举字典（如反馈类型、年龄区间等）。小程序端拉取字典失败时回退本地兜底列表，保障弱网可用性。</p>
{img('12-字典管理')}
<h3>4.5 知识库管理</h3>
<p>知识库管理页用于维护智能体可检索的知识条目，支持新增、编辑、启停与检索测试。</p>
{img('13-知识库管理')}
<h3>4.6 功能配置与替换词管理</h3>
<p>功能配置页控制各端功能的开关与入口展示；替换词管理页维护对话文本的敏感词与替换规则，命中规则的文本在输出前完成替换。</p>
{img('14-功能配置')}
{img('15-替换词管理')}
<h3>4.7 通讯录管理</h3>
<p>通讯录管理页维护设备可呼叫的联系人信息，供终端语音呼叫功能使用。</p>
{img('16-通讯录管理')}
""",
    ),
    (
        "五、NFC 资产管理",
        f"""
<p>NFC 资产管理覆盖蛋宝宝配套 NFC 标签的全生命周期：商品类型定义、生产批次、Scheme 生成、写卡（工厂 CSV 模式与手动写卡模式）、锁卡复验、触碰激活与审计追溯。</p>
<h3>5.1 商品类型</h3>
<p>商品类型页定义 NFC 标签对应的商品形态与规格，是批次与资产的基础数据。</p>
{img('17-NFC商品类型')}
<h3>5.2 批次管理</h3>
<p>批次管理页维护 NFC 标签生产批次，批次状态机驱动从创建、写入到入库的流转，页面展示各批次状态与数量统计。</p>
{img('18-NFC批次管理')}
<h3>5.3 Scheme 任务</h3>
<p>Scheme 任务页用于生成微信 NFC Scheme 链接，批量产出待写入标签的 URI。</p>
{img('19-NFC-Scheme任务')}
<h3>5.4 写卡任务</h3>
<p>写卡任务页支持两种模式：工厂 CSV 模式面向量产，导出 CSV 交工厂写入并回读校验；手动写卡模式面向小批量验证，操作员用手机逐张写入并以真实触碰完成自验证。</p>
{img('20-NFC写卡任务')}
<h3>5.5 资产管理</h3>
<p>资产管理页以标签为粒度展示每张 NFC 资产的状态（已写入、已验证、已锁卡、已入库），支持按批次筛选与状态追溯。</p>
{img('21-NFC资产管理')}
<h3>5.6 扫码激活</h3>
<p>扫码激活页展示用户触碰 NFC 标签后的激活记录，包括激活时间、用户与关联宠物领养结果。</p>
{img('22-NFC扫码激活')}
<h3>5.7 审计日志</h3>
<p>审计日志页记录 NFC 生命周期中的关键操作，支持按操作类型与时间范围检索，满足合规追溯要求。</p>
{img('23-NFC审计日志')}
""",
    ),
    (
        "六、故事引擎与反馈管理",
        f"""
<h3>6.1 故事引擎</h3>
<p>故事引擎页用于运营宠物的共享叙事状态：配置大场景、小场景与动作文案，以及各时段候选背景图。故事引擎按整点评估并切换故事状态，同时段内自动轮换背景图并同步更新配文。</p>
{img('24-故事引擎')}
<h3>6.2 反馈管理</h3>
<p>反馈管理页展示小程序用户提交的诉求，每条反馈具有格式为 FB日期-序列号的受理编号，便于追溯。运营可在此查看诉求内容并流转处理状态；按产品规则，反馈不回复用户、不可删除。</p>
{img('25-反馈管理')}
""",
    ),
    (
        "七、运维管理",
        f"""
<h3>7.1 服务端管理</h3>
<p>服务端管理页展示各语音服务节点的在线状态与负载信息，支持节点上下线操作，保障语音链路可用性。</p>
{img('26-服务端管理')}
<h3>7.2 OTA 管理</h3>
<p>OTA 管理页用于上传与发布 ESP32 终端固件，按版本灰度推送升级，并可查看各设备的升级进度。</p>
{img('27-OTA管理')}
""",
    ),
]


def build_html():
    sections_html = "\n".join(
        f'<section><h2>{title}</h2>{body}</section>' for title, body in SECTIONS
    )
    return f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<style>
@page {{
  size: A4;
  margin: 22mm 20mm 20mm 20mm;
  @top-center {{ content: "{SOFTWARE_NAME}"; font-family: "Songti SC", serif; font-size: 10.5pt; }}
  @top-right {{ content: "第 " counter(page) " 页"; font-family: "Songti SC", serif; font-size: 9pt; }}
}}
@page :first {{
  @top-center {{ content: none; }}
  @top-right {{ content: none; }}
}}
body {{ font-family: "Songti SC", serif; font-size: 12pt; line-height: 1.8; color: #000; }}
.cover {{ text-align: center; page-break-after: always; }}
.cover h1 {{ margin-top: 200pt; font-size: 26pt; }}
.cover p {{ font-size: 14pt; margin-top: 40pt; }}
h2 {{ font-size: 16pt; page-break-before: always; border-bottom: 1px solid #000; padding-bottom: 6pt; }}
section:first-of-type h2 {{ page-break-before: avoid; }}
h3 {{ font-size: 13pt; }}
p {{ text-indent: 2em; margin: 6pt 0; }}
.shot {{ max-width: 100%; margin: 8pt 0; border: 1px solid #999; }}
.missing {{ color: #a00; text-indent: 0; }}
</style>
</head>
<body>
<div class="cover">
  <h1>{SOFTWARE_NAME}<br>操作手册</h1>
  <p>{COMPANY}</p>
  <p>2026 年 8 月</p>
</div>
{sections_html}
</body>
</html>
"""


def main():
    html_path = os.path.join(HERE, ".manual.html")
    with open(html_path, "w", encoding="utf-8") as f:
        f.write(build_html())

    env = dict(os.environ)
    env["DYLD_FALLBACK_LIBRARY_PATH"] = "/opt/homebrew/lib"
    subprocess.run(["weasyprint", html_path, OUT_PDF], check=True, env=env)
    print(f"输出: {OUT_PDF}")


if __name__ == "__main__":
    sys.exit(main())
