# ALB HTTPS 监听 JA3 指纹拦截排查笔记

> 一次"浏览器能访问、curl 和微信小程序都被 RST"的诡异故障排查记录。核心结论：阿里云 ALB 前置的 Bot 防护按 **TLS 客户端指纹（JA3/JA4）** 白名单，只放真浏览器，拦截一切 SDK/curl/小程序。

---

## 一、环境信息

| 组件 | 信息 |
|---|---|
| 域名 | `chat-api.benniu.tech` |
| DNS 解析 | CNAME 到 ALB `alb-fzim53hqxfv5xcio61.cn-shanghai.alb.aliyuncsslb.com` |
| ALB 公网 IP | `47.103.211.57` / `47.116.210.196`（多可用区） |
| ALB 监听 | 443 / HTTPS |
| TLS 安全策略 | 原 `tls_cipher_policy_1_0`，后改 `tls_cipher_policy_1_2_strict_with_1_3` |
| 后端 | 阿里云 SAE（Spring Boot，`/xiaozhi/actuator/health` 暴露健康检查） |
| 服务器组 | 已绑定，后端实例全部健康 |
| 客户端 | macOS 14.4 + 微信开发者工具 + Chrome 149 + Clash 代理（7897 端口） |

---

## 二、现象

微信开发者工具控制台报：

```
POST https://chat-api.benniu.tech/xiaozhi/ota/                net::ERR_CONNECTION_RESET
GET  https://chat-api.benniu.tech/xiaozhi/companion/detail/.. net::ERR_CONNECTION_RESET
GET  https://chat-api.benniu.tech/xiaozhi/subscription/...   net::ERR_CONNECTION_RESET
```

但**浏览器直接访问** `https://chat-api.benniu.tech/xiaozhi/actuator/health` 返回 `200 OK`、`{"status":"UP"}`。

---

## 三、排查决策树

```
现象：浏览器 ✅ / curl ❌ / 小程序 ❌ 的 TLS 握手 RST
   │
   ├─ 怀疑①：证书链不全 ──────── 排除（浏览器握手成功，证书有效）
   ├─ 怀疑②：TLS 策略太严 ────── 排除（已改为 _1_2_strict_with_1_3 仍失败）
   ├─ 怀疑③：DNS 指向错误 ────── 排除（DNS 正确 CNAME 到 ALB）
   ├─ 怀疑④：ALB 监听/证书异常 ─ 排除（监听运行中，证书有效未过期）
   ├─ 怀疑⑤：后端服务挂了 ────── 排除（服务器组健康，actuator 返回 UP）
   ├─ 怀疑⑥：本机出口 IP 被加黑 ─ 排除（浏览器直连同 IP 也 OK）
   ├─ 怀疑⑦：HTTP 层 UA 拦截 ─── 排除（curl 伪装 Chrome UA 也 RST）
   ├─ 怀疑⑧：IP 段整体被拦 ───── 排除（走 Clash 代理换出口 IP 仍 RST）
   │
   └─ ✅ 锁定：JA3 / JA4 TLS 指纹拦截（WAF Bot 管理 / DDoS 反爬）
```

---

## 四、关键诊断命令与证据

### 1. openssl 强制 TLS 1.2 → 立即 RST

```bash
openssl s_client -connect chat-api.benniu.tech:443 -servername chat-api.benniu.tech -tls1_2 < /dev/null 2>&1 | head -10
```

```
Connecting to 47.103.211.57
write:errno=54
CONNECTED(00000006)
---
no peer certificate available
---
SSL handshake has read 0 bytes and written 218 bytes
...
Cipher    : 0000
```

**解读**：TCP 三次握手成功，发出 218 字节 Client Hello 后立即被 RST，**连 ServerHello 都没收到**，说明拦截发生在 TLS 握手阶段，不是 HTTP 层。

### 2. openssl 强制 TLS 1.3 → 收到 alert 70

```bash
openssl s_client -connect chat-api.benniu.tech:443 -servername chat-api.benniu.tech -tls1_3 < /dev/null 2>&1 | head -5
```

```
error:0A00042E:ssl3_read_bytes:tlsv1 alert protocol version:ssl/record/rec_layer_s3.c:918:SSL alert number 70
```

**解读**：`alert 70 = protocol_version`，但 OpenSSL 也算非浏览器客户端指纹，仍被拦。

### 3. curl 默认 → RST

```bash
curl -v https://chat-api.benniu.tech/xiaozhi/actuator/health 2>&1 | head -15
```

```
* Connected to chat-api.benniu.tech (47.103.211.57) port 443
* ALPN: curl offers h2,http/1.1
* (304) (OUT), TLS handshake, Client hello (1):
} [325 bytes data]
* Recv failure: Connection reset by peer
* LibreSSL/3.3.6: error:02FFF036:system library:func(4095):Connection reset by peer
curl: (35) Recv failure: Connection reset by peer
```

### 4. curl 伪装 Chrome UA → 仍 RST（排除 HTTP 层 UA 拦截）

```bash
curl -v -A "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36" \
  https://chat-api.benniu.tech/xiaozhi/actuator/health
```

**结果**：依然在 TLS Client Hello 阶段被 RST。

→ 拦截发生在 HTTP 之前，跟 UA 无关。

### 5. curl 走 Clash 代理 → 仍 RST（排除出口 IP 拦截）

```bash
curl -x http://127.0.0.1:7897 -v https://chat-api.benniu.tech/xiaozhi/actuator/health 2>&1 | head -20
```

```
* Connected to 127.0.0.1 (127.0.0.1) port 7897
< HTTP/1.1 200 Connection established     ← 代理隧道建立成功
* ALPN: curl offers h2,http/1.1
* (304) (OUT), TLS handshake, Client hello (1):
* LibreSSL SSL_connect: SSL_ERROR_SYSCALL in connection to chat-api.benniu.tech:443
curl: (35) LibreSSL SSL_connect: SSL_ERROR_SYSCALL
```

**解读**：代理隧道建立后，curl 仍然用自己的 LibreSSL JA3 指纹发起 TLS 握手 → 依然 RST。**出口 IP 已变（变代理节点 IP），指纹没变**，所以排除 IP 段拦截。

### 6. 浏览器（直连 + 不走代理）→ ✅ 200 OK

F12 → Network → `actuator/health` 请求 Headers：

```
Remote Address: 47.103.211.57:443
Status Code: 200 OK
content-type: application/vnd.spring-boot.actuator.v3+json
user-agent: Mozilla/5.0 ... Chrome/149.0.0.0 Safari/537.36
```

**解读**：浏览器直连同 IP 同端口，握手成功，证明 TCP 443 监听正常、证书有效、TLS 策略兼容、后端服务健康。

---

## 五、测试矩阵（决定性证据）

| # | 客户端 | 出口 IP | TLS 指纹 | 结果 |
|---|---|---|---|---|
| 1 | Chrome 直连 | 本机真实 IP | Chrome 标准 JA3 | ✅ 200 |
| 2 | Chrome 走 Clash | 代理节点 IP | Chrome 标准 JA3 | ✅ 200 |
| 3 | curl + LibreSSL 直连 | 本机真实 IP | LibreSSL JA3 | ❌ RST |
| 4 | curl + LibreSSL 走 Clash | 代理节点 IP | LibreSSL JA3 | ❌ RST |
| 5 | openssl 直连 | 本机真实 IP | OpenSSL JA3 | ❌ RST |
| 6 | 微信开发者工具 | 本机真实 IP | 微信特殊 JA3 | ❌ RST |

**唯一变量**：TLS 客户端指纹（JA3/JA4）。浏览器指纹白名单内 → 通；其他指纹 → RST。

---

## 六、根因结论

阿里云 ALB 前置挂了一层 **Bot 防护 / 反爬虫** 产品（WAF 3.0 的 Bot 管理、DDoS 高防的智能防护、或 ALB 自带的防爬功能），启用了 **TLS 客户端指纹白名单**：只放行真浏览器 JA3 指纹，对 curl/openssl/SDK/微信小程序的 JA3 一律在 TCP 443 握手阶段直接 RST。

> 拦截发生在 TLS 握手阶段（Client Hello 后即 RST），**HTTP 层根本没启动**，所以 UA 白名单、Referer 白名单等 HTTP 层规则**无效**。

---

## 七、修复方案

### 方案 1（推荐）：WAF / Bot 防护整体降级为"观察模式"

阿里云控制台 → **Web 应用防火墙 WAF 3.0**（或 DDoS 高防 / SCDN） → 防护配置 → 选 `chat-api.benniu.tech` 域名 → **Bot 管理 / 场景化 Bot 防护** → 防护模式改为"**观察**"（不拦只记日志）。

30 秒生效后验证：

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://chat-api.benniu.tech/xiaozhi/actuator/health
# 期望：200
```

### 方案 2（长期）：路径白名单

WAF 控制台 → **自定义防护规则** → 新增：

- 匹配条件：`URL 路径` **前缀匹配** `/xiaozhi/`
- 处置动作：**放行（跳过 Bot 检测）**
- 优先级：高于 Bot 管理默认策略

⚠️ 不要只加 `User-Agent` 白名单——拦截在 TLS 层，UA 还没到 HTTP 层就被 RST 了。

### 方案 3：直接关掉指纹白名单规则

如果 Bot 防护里有"客户端指纹识别 / JA3 指纹库 / 浏览器指纹白名单"这类规则，直接关闭。

### 方案 4：兜底提工单

如果控制台搜不到这个域名引用 WAF/DDoS/SCDN，直接提工单，附上测试矩阵和命令输出。阿里云后台日志能秒级定位是哪个产品在 RST。

工单模板：

```
ALB 实例：alb-fzim53hqxfv5xcio61.cn-shanghai.alb.aliyuncsslb.com
域名：chat-api.benniu.tech

现象：浏览器可正常 HTTPS 访问，但 curl/openssl/微信小程序在
TLS Client Hello 后立即被 RST（errno=54，read 0 bytes）。
已穷尽排查：证书、TLS 策略、DNS、监听状态、服务器组、出口 IP、UA、IP 段
全部排除，唯一变量为 TLS 客户端指纹（JA3/JA4）。

请协助：
1. 确认是哪个产品在 RST（WAF/DDoS/SCDN/ALB 内置防爬?）
2. 如何关闭 JA3 指纹拦截或为 /xiaozhi/* 加路径白名单
3. 影响：所有 SDK/curl/微信小程序客户端均无法访问，仅浏览器可用
```

---

## 八、应急方案（阿里云修好前）

需要立刻让小程序联调能跑：

1. 找一个**没挂 Bot 防护**的 ALB / SLB / 直接绑 ECS 公网 IP
2. 临时接入点用新域名（如 `chat-api-dev.benniu.tech`）
3. 把新域名加到小程序合法域名列表
4. 临时改小程序 `BASE_URL` 指向新接入点

> 治标不治本。生产 ALB 上的 Bot 防护不调好，真实用户的微信请求一样被拦。

---

## 九、复盘要点

### 1. "浏览器能访问、curl 不能"的根因清单

按本次排查顺序，遇到这种不对称现象依次排除：

1. 证书链不完整（浏览器 AIA 补全，curl 不补全）→ ssllabs / `openssl s_client` 看 chain
2. TLS 策略太严（只开 TLS 1.3，小程序握手失败）→ `openssl -tls1_2` 测
3. DNS 指向错误（CNAME 链断了或指向废弃实例）→ `dig` 追链
4. ALB 监听 / 证书异常（监听卡死、证书过期）→ 控制台看监听状态
5. 出口 IP 被加黑（WAF/ACL 黑名单）→ 浏览器走代理看是否绕过
6. HTTP 层 UA 拦截（WAF 按 UA 拦爬虫）→ curl 伪装 Chrome UA
7. IP 段整体被拦（云盾 DDoS 封段）→ curl 走代理换出口 IP
8. **JA3/JA4 TLS 指纹拦截**（最隐蔽）→ 走代理换 IP 仍 RST + 浏览器仍 OK = 锁定

### 2. 关键诊断信号

- `errno=54` / `Connection reset by peer` + `read 0 bytes` → TLS 握手阶段被 RST，**不是后端问题**
- `alert 70 (protocol_version)` → 服务端明确拒绝该 TLS 版本（也可能是指纹拦截的伪装）
- 浏览器 Network 面板 `Remote Address: 127.0.0.1:7897` → 浏览器走了本地代理，不能作为"服务端正常"的证据

### 3. 排查避坑

- ❌ 别看到 `ERR_CONNECTION_RESET` 就改 nginx TLS 配置——先确认请求是否真的到了 nginx
- ❌ 别看到浏览器 OK 就以为服务端正常——浏览器可能有代理、QUIC、缓存
- ❌ 别在小程序里反复重试——频繁 TLS 握手失败会触发 WAF 自动封禁，越测越坏
- ✅ curl + openssl 是诊断 TLS 层问题的金标准，但要配合"走/不走代理"双测
- ✅ 阿里云控制台顶部搜索框输入域名，能列出所有引用该域名的安全产品

### 4. 微信小程序的 TLS 限制

微信小程序网络层（[官方文档](https://developers.weixin.qq.com/miniprogram/dev/framework/ability/network.html)）：

- 必须 TLS 1.2 及以上
- cipher 有白名单
- **不支持 HTTP/3 (QUIC)**，只能走 TCP 443
- 微信小程序的 JA3 指纹**不是浏览器 JA3**，会被部分 Bot 防护误杀

→ 任何对外的 API 域名接入了 Bot 防护后，**必须用真机扫码测一遍微信小程序**，不能只测浏览器。

---

## 十、附录：完整诊断脚本

```bash
#!/bin/bash
# alb-tls-diag.sh — ALB HTTPS 监听 TLS 故障诊断
# 用法：./alb-tls-diag.sh <域名>

DOMAIN="${1:-chat-api.benniu.tech}"

echo "===== 1. DNS 解析 ====="
dig +short "$DOMAIN"

echo ""
echo "===== 2. 本机出口 IP ====="
curl -s https://ifconfig.me; echo

echo ""
echo "===== 3. TLS 1.2 握手测试 ====="
openssl s_client -connect "$DOMAIN:443" -servername "$DOMAIN" -tls1_2 < /dev/null 2>&1 | \
  grep -E "Cipher|Protocol|Verify return|errno|alert" | head -10

echo ""
echo "===== 4. TLS 1.3 握手测试 ====="
openssl s_client -connect "$DOMAIN:443" -servername "$DOMAIN" -tls1_3 < /dev/null 2>&1 | \
  grep -E "Cipher|Protocol|Verify return|errno|alert" | head -10

echo ""
echo "===== 5. curl 直连 ====="
curl -s -o /dev/null -w "HTTP: %{http_code}, 耗时: %{time_total}s\n" \
  "https://$DOMAIN/xiaozhi/actuator/health"

echo ""
echo "===== 6. curl 伪装 Chrome UA ====="
curl -s -o /dev/null -w "HTTP: %{http_code}, 耗时: %{time_total}s\n" \
  -A "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36" \
  "https://$DOMAIN/xiaozhi/actuator/health"

echo ""
echo "===== 7. curl 走本地代理（如有） ====="
curl -x http://127.0.0.1:7897 -s -o /dev/null -w "HTTP: %{http_code}, 耗时: %{time_total}s\n" \
  "https://$DOMAIN/xiaozhi/actuator/health" 2>&1 || echo "代理不可用"

echo ""
echo "===== 诊断完成 ====="
echo "如果 3/4/5 全失败但浏览器能访问 → 大概率是 JA3 指纹拦截"
echo "如果 6 通了 → 是 HTTP 层 UA 拦截"
echo "如果 7 通了 → 是本机出口 IP 被加黑"
echo "如果 3/4/5/6/7 全失败 → ALB 监听本身异常，提工单"
```

---

**文档版本**：v1.0  
**排查日期**：2026-06-20  
**适用场景**：阿里云 ALB + WAF/DDoS 架构下，"浏览器可访问、SDK/curl/小程序不可访问"的 TLS 握手 RST 故障
