# AI 宠物孵化演示 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 demo-web 从单一测试页面改造为 AI 宠物孵化演示，包含蛋→孵化→破壳→出生→聊天的完整流程。

**Architecture:** 单页面状态机（5 个阶段），CSS/SVG 动画实现视觉效果。新文件 `index.html` 作为演示入口，复用现有 `core/` 模块（WebSocket、音频、MCP）。孵化逻辑封装在 `hatch/` 目录，兔子渲染封装在 `rabbit/` 目录。

**Tech Stack:** 原生 HTML/CSS/JS（ES Modules），SVG 动画，CSS keyframes，Fetch API，零外部依赖。

---

## 文件清单

| 文件 | 操作 | 职责 |
|------|------|------|
| `index.html` | 新建 | 演示入口页面，包含孵化场景和聊天场景的 DOM 结构 |
| `css/hatch.css` | 新建 | 孵化流程所有样式（蛋、裂纹、粒子、进度条、出生卡片） |
| `css/rabbit.css` | 新建 | 兔子 SVG 样式和动画（呼吸、眨眼、说话、倾听） |
| `js/hatch-app.js` | 新建 | 演示页面入口，初始化孵化流程，管理场景切换 |
| `js/hatch/hatch-manager.js` | 新建 | 孵化状态机，管理 5 个阶段的切换和生命周期 |
| `js/hatch/egg.js` | 新建 | 蛋的 SVG 渲染、呼吸动画、摇晃动画、裂纹绘制 |
| `js/hatch/hatching.js` | 新建 | 孵化互动逻辑：点击事件、进度管理、爱心粒子 |
| `js/hatch/cracking.js` | 新建 | 破壳动画：蛋壳裂开、粒子飞散效果 |
| `js/hatch/birth.js` | 新建 | 出生展示：API 调用、宠物信息卡、过渡到聊天 |
| `js/rabbit/rabbit.js` | 新建 | 兔子 SVG 渲染和状态动画（待机/说话/倾听/跳动） |

---

### Task 1: 创建 index.html 页面骨架

**Files:**
- Create: `main/demo-web/index.html`

- [ ] **Step 1: 创建 index.html**

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
    <title>AI 宠物孵化</title>
    <link rel="stylesheet" href="css/test_page.css?v=0205">
    <link rel="stylesheet" href="css/hatch.css">
    <link rel="stylesheet" href="css/rabbit.css">
    <style>
        .scene { display: none; }
        .scene.active { display: flex; }
    </style>
</head>
<body>
    <!-- 背景容器 -->
    <div class="background-container" id="backgroundContainer">
        <div class="background-overlay"></div>
    </div>

    <!-- 场景1：孵化 -->
    <div class="scene active" id="hatchScene">
        <div class="hatch-container">
            <!-- 连接状态 -->
            <div class="connection-status-top">
                <div class="status-indicator">
                    <span class="status-dot status-disconnected"></span>
                    <span id="connectionStatus">离线</span>
                </div>
            </div>

            <!-- 蛋容器 -->
            <div class="egg-wrapper" id="eggWrapper">
                <svg id="eggSvg" class="egg-svg" viewBox="0 0 140 180" width="140" height="180">
                    <!-- 蛋身 -->
                    <ellipse cx="70" cy="100" rx="55" ry="70" fill="#FFF5E6" stroke="#F0D9B5" stroke-width="2"/>
                    <!-- 高光 -->
                    <ellipse cx="50" cy="75" rx="15" ry="25" fill="rgba(255,255,255,0.6)" transform="rotate(-15 50 75)"/>
                    <circle cx="45" cy="60" r="5" fill="rgba(255,255,255,0.8)"/>
                    <!-- 腮红 -->
                    <ellipse cx="35" cy="110" rx="10" ry="6" fill="rgba(255,180,180,0.4)"/>
                    <ellipse cx="105" cy="110" rx="10" ry="6" fill="rgba(255,180,180,0.4)"/>
                    <!-- 裂纹组（孵化时动态添加） -->
                    <g id="crackGroup"></g>
                </svg>
                <!-- 爱心粒子容器 -->
                <div class="particle-container" id="particleContainer"></div>
            </div>

            <!-- 进度条 -->
            <div class="hatch-progress" id="hatchProgress" style="display:none;">
                <div class="progress-bar">
                    <div class="progress-fill" id="progressFill"></div>
                </div>
                <span class="progress-text" id="progressText">0%</span>
            </div>

            <!-- 开始按钮 -->
            <button class="hatch-start-btn" id="hatchStartBtn">开始孵化</button>

            <!-- 提示文字 -->
            <p class="hatch-hint" id="hatchHint"></p>
        </div>
    </div>

    <!-- 场景2：出生展示 -->
    <div class="scene" id="birthScene">
        <div class="birth-container">
            <!-- 兔子 -->
            <div class="birth-rabbit" id="birthRabbit"></div>
            <!-- 宠物信息卡 -->
            <div class="pet-info-card" id="petInfoCard">
                <h2 class="pet-name" id="petName"></h2>
                <div class="pet-details">
                    <span class="pet-mbti" id="petMbti"></span>
                    <span class="pet-zodiac" id="petZodiac"></span>
                    <span class="pet-birth-date" id="petBirthDate"></span>
                </div>
            </div>
        </div>
    </div>

    <!-- 场景3：聊天（复用 test_page 的结构） -->
    <div class="scene" id="chatScene">
        <div class="container">
            <!-- 兔子伴侣（左下角） -->
            <div class="rabbit-companion" id="rabbitCompanion"></div>

            <!-- 连接状态 -->
            <div class="connection-status-top">
                <div class="status-indicator">
                    <span class="status-dot status-disconnected"></span>
                    <span id="connectionStatus2">离线</span>
                </div>
            </div>

            <!-- 聊天消息流 -->
            <div class="chat-container">
                <div class="chat-stream" id="chatStream"></div>
                <div class="chat-ipt" id="chatIpt">
                    <input type="text" id="messageInput" autocomplete="off" placeholder="输入消息，按Enter发送">
                </div>
            </div>

            <!-- 底部控制栏 -->
            <div class="control-bar">
                <button class="control-btn" id="settingsBtn" title="设置">
                    <svg class="btn-icon" viewBox="0 0 24 24" fill="currentColor">
                        <path d="M12 15.5A3.5 3.5 0 0 1 8.5 12A3.5 3.5 0 0 1 12 8.5A3.5 3.5 0 0 1 15.5 12A3.5 3.5 0 0 1 12 15.5M19.43 12.97C19.47 12.65 19.5 12.33 19.5 12C19.5 11.67 19.47 11.34 19.43 11L21.54 9.37C21.73 9.22 21.78 8.95 21.66 8.73L19.66 5.27C19.54 5.05 19.27 4.96 19.05 5.05L16.56 6.05C16.04 5.66 15.5 5.32 14.87 5.07L14.5 2.42C14.46 2.18 14.25 2 14 2H10C9.75 2 9.54 2.18 9.5 2.42L9.13 5.07C8.5 5.32 7.96 5.66 7.44 6.05L4.95 5.05C4.73 4.96 4.46 5.05 4.34 5.27L2.34 8.73C2.22 8.95 2.27 9.22 2.46 9.37L4.57 11C4.53 11.34 4.5 11.67 4.5 12C4.5 12.33 4.53 12.65 4.57 12.97L2.46 14.63C2.27 14.78 2.22 15.05 2.34 15.27L4.34 18.73C4.46 18.95 4.73 19.03 4.95 18.95L7.44 17.94C7.96 18.34 8.5 18.68 9.13 18.93L9.5 21.58C9.54 21.82 9.75 22 10 22H14C14.25 22 14.46 21.82 14.5 21.58L14.87 18.93C15.5 18.68 16.04 18.34 16.56 17.94L19.05 18.95C19.27 19.03 19.54 18.95 19.66 18.73L21.66 15.27C21.78 15.05 21.73 14.78 21.54 14.63L19.43 12.97Z"/>
                    </svg>
                    <span class="btn-text">设置</span>
                </button>

                <button class="control-btn dial-btn" id="dialBtn" title="拨号">
                    <svg class="btn-icon" viewBox="0 0 24 24" fill="currentColor">
                        <path d="M6.62,10.79C8.06,13.62 10.38,15.94 13.21,17.38L15.41,15.18C15.69,14.9 16.08,14.82 16.43,14.93C17.55,15.3 18.75,15.5 20,15.5A1,1 0 0,1 21,16.5V20A1,1 0 0,1 20,21A17,17 0 0,1 3,4A1,1 0 0,1 4,3H7.5A1,1 0 0,1 8.5,4C8.5,5.25 8.7,6.45 9.07,7.57C9.18,7.92 9.1,8.31 8.82,8.59L6.62,10.79Z"/>
                    </svg>
                    <span class="btn-text">拨号</span>
                </button>

                <button class="control-btn" id="recordBtn" title="开始录音" disabled>
                    <svg class="btn-icon" viewBox="0 0 24 24" fill="currentColor">
                        <path d="M12,2A3,3 0 0,1 15,5V11A3,3 0 0,1 12,14A3,3 0 0,1 9,11V5A3,3 0 0,1 12,2M19,11C19,14.53 16.39,17.44 13,17.93V21H11V17.93C7.61,17.44 5,14.53 5,11H7A5,5 0 0,0 12,16A5,5 0 0,0 17,11H19Z"/>
                    </svg>
                    <span class="btn-text">录音</span>
                </button>

                <button class="control-btn" id="backgroundBtn" title="切换背景">
                    <svg class="btn-icon" viewBox="0 0 24 24" fill="currentColor">
                        <path d="M12,18A6,6 0 0,1 6,12C6,11 6.25,10.03 6.7,9.2L5.24,7.74C4.46,8.97 4,10.43 4,12A8,8 0 0,0 12,20V23L16,19L12,15V18M12,4V1L8,5L12,9V6A6,6 0 0,1 18,12C18,13 17.75,13.97 17.3,14.8L18.76,16.26C19.54,15.03 20,13.57 20,12A8,8 0 0,0 12,4Z"/>
                    </svg>
                    <span class="btn-text">背景</span>
                </button>

                <button class="control-btn" id="rehatchBtn" title="重新孵化">
                    <svg class="btn-icon" viewBox="0 0 24 24" fill="currentColor">
                        <path d="M12 4V1L8 5L12 9V6A6 6 0 0 1 18 12H20A8 8 0 0 0 12 4Z"/>
                    </svg>
                    <span class="btn-text">重新孵化</span>
                </button>
            </div>
        </div>
    </div>

    <!-- 设置弹窗（从 test_page.html 复制，ID 保持一致） -->
    <div class="modal" id="settingsModal">
        <div class="modal-content settings-modal">
            <div class="modal-header">
                <h2>设置</h2>
                <button class="close-btn" id="closeSettingsBtn">&times;</button>
            </div>
            <div class="modal-body">
                <div class="settings-tabs">
                    <button class="tab-btn active" data-tab="device">设备配置</button>
                    <button class="tab-btn" data-tab="mcp">MCP工具</button>
                </div>
                <div class="tab-content active" id="deviceTab">
                    <div class="config-panel">
                        <div class="control-panel">
                            <div class="config-row">
                                <div class="config-item">
                                    <label for="deviceMac">设备MAC:</label>
                                    <input type="text" id="deviceMac" placeholder="device-id">
                                </div>
                            </div>
                            <div class="config-row">
                                <div class="config-item">
                                    <label for="clientId">客户端ID:</label>
                                    <input type="text" id="clientId" value="web_test_client" placeholder="client-id">
                                </div>
                                <div class="config-item">
                                    <label for="deviceName">设备名称:</label>
                                    <input type="text" id="deviceName" value="Web测试设备" maxlength="50" placeholder="deviceName">
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="connection-controls">
                        <div class="input-group">
                            <label for="otaUrl">OTA服务器地址:</label>
                            <input type="text" id="otaUrl" value="http://127.0.0.1:8002/xiaozhi/ota/" placeholder="OTA服务器地址"/>
                        </div>
                        <div class="input-group">
                            <label for="serverUrl">WebSocket服务器地址:</label>
                            <input type="text" id="serverUrl" value="" readonly disabled placeholder="填写OTA地址后，点击拨号按钮自动连接"/>
                        </div>
                        <div class="input-group">
                            <label for="visionUrl">视觉分析地址:</label>
                            <input type="text" id="visionUrl" value="" readonly disabled placeholder="成功建立ws连接后自动获取"/>
                        </div>
                    </div>
                </div>
                <div class="tab-content" id="mcpTab">
                    <div class="mcp-tools-container">
                        <div class="mcp-tools-header"><h3>MCP 工具管理</h3></div>
                        <div class="mcp-tools-panel" id="mcpToolsPanel">
                            <div class="mcp-tools-list" id="mcpToolsContainer"></div>
                            <div class="mcp-actions">
                                <button class="btn-primary" id="addMcpToolBtn">➕ 添加新工具</button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <!-- MCP 工具编辑模态框 -->
    <div id="mcpToolModal" class="modal">
        <div class="modal-content">
            <div class="modal-header">
                <h2 id="mcpModalTitle">添加工具</h2>
                <button class="close-btn" id="closeMcpModalBtn">&times;</button>
            </div>
            <div class="modal-body">
                <div id="mcpErrorContainer"></div>
                <form id="mcpToolForm">
                    <div class="input-group">
                        <label for="mcpToolName">工具名称 *</label>
                        <input type="text" id="mcpToolName" placeholder="例如: self.get_device_status" required>
                    </div>
                    <div class="input-group">
                        <label for="mcpToolDescription">工具描述 *</label>
                        <textarea id="mcpToolDescription" placeholder="详细描述工具的功能和使用场景..." required></textarea>
                    </div>
                    <div class="input-group">
                        <div class="input-group-header">
                            <label>输入参数</label>
                            <button type="button" class="properties-btn-primary" id="addMcpPropertyBtn">➕ 添加参数</button>
                        </div>
                        <div class="properties-container">
                            <div class="mcp-empty-state" id="mcpEmptyState">暂无参数，点击上方按钮添加参数</div>
                            <div class="mcp-properties-list" id="mcpPropertiesContainer"></div>
                        </div>
                    </div>
                    <div class="input-group">
                        <label for="mcpMockResponse">模拟返回结果 (JSON 格式，可选)</label>
                        <textarea id="mcpMockResponse" placeholder='{"success": true, "data": "执行成功"}'></textarea>
                    </div>
                    <div class="modal-actions">
                        <button type="button" class="btn-secondary" id="cancelMcpBtn">取消</button>
                        <button type="submit" class="btn-primary">保存</button>
                    </div>
                </form>
            </div>
        </div>
    </div>

    <!-- 参数编辑模态框 -->
    <div id="mcpPropertyModal" class="modal">
        <div class="modal-content property-modal">
            <div class="modal-header">
                <h2 id="mcpPropertyModalTitle">编辑参数</h2>
                <button class="close-btn" id="closeMcpPropertyModalBtn">&times;</button>
            </div>
            <div class="modal-body">
                <form id="mcpPropertyForm">
                    <input type="hidden" id="mcpPropertyIndex" value="-1">
                    <div class="input-group">
                        <label for="mcpPropertyName">参数名称 *</label>
                        <input type="text" id="mcpPropertyName" placeholder="例如: param_1" required>
                    </div>
                    <div class="input-group">
                        <label for="mcpPropertyType">数据类型 *</label>
                        <select id="mcpPropertyType" class="model-select" required>
                            <option value="string">字符串</option>
                            <option value="integer">整数</option>
                            <option value="number">数字</option>
                            <option value="boolean">布尔值</option>
                            <option value="array">数组</option>
                            <option value="object">对象</option>
                        </select>
                    </div>
                    <div class="input-group" id="mcpPropertyRangeGroup" style="display: none;">
                        <div class="config-row">
                            <div class="config-item">
                                <label for="mcpPropertyMinimum">最小值</label>
                                <input type="number" id="mcpPropertyMinimum" placeholder="可选">
                            </div>
                            <div class="config-item">
                                <label for="mcpPropertyMaximum">最大值</label>
                                <input type="number" id="mcpPropertyMaximum" placeholder="可选">
                            </div>
                        </div>
                    </div>
                    <div class="input-group">
                        <label for="mcpPropertyDescription">参数描述</label>
                        <input type="text" id="mcpPropertyDescription" placeholder="可选">
                    </div>
                    <div class="input-group">
                        <label class="mcp-checkbox-label">
                            <input type="checkbox" id="mcpPropertyRequired">
                            必填参数
                        </label>
                    </div>
                    <div class="modal-actions">
                        <button type="button" class="btn-secondary" id="cancelMcpPropertyBtn">取消</button>
                        <button type="submit" class="btn-primary">保存</button>
                    </div>
                </form>
            </div>
        </div>
    </div>

    <!-- Toast 容器 -->
    <div class="toast-container" id="toastContainer"></div>

    <!-- 脚本 -->
    <script src="js/ui/background-load.js?v=0205"></script>
    <script src="js/utils/libopus.js?v=0205"></script>
    <script type="module" src="js/hatch-app.js"></script>
</body>
</html>
```

- [ ] **Step 2: 在浏览器中验证页面加载**

Run: `cd main/demo-web && python -m http.server 8006`
Open: `http://localhost:8006/index.html`
Expected: 页面显示蛋的 SVG 图形和"开始孵化"按钮，无 JS 错误

- [ ] **Step 3: Commit**

```bash
git add main/demo-web/index.html
git commit -m "feat: add hatch demo page skeleton (index.html)"
```

---

### Task 2: 创建孵化样式文件 hatch.css

**Files:**
- Create: `main/demo-web/css/hatch.css`

- [ ] **Step 1: 创建 hatch.css**

```css
/* ===== 场景切换 ===== */
.scene {
    display: none;
    width: 100%;
    min-height: 100vh;
    position: fixed;
    top: 0;
    left: 0;
    z-index: 1;
}
.scene.active {
    display: flex;
    align-items: center;
    justify-content: center;
}

/* ===== 孵化场景 ===== */
.hatch-container {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 100vh;
    position: relative;
}

/* ===== 蛋 ===== */
.egg-wrapper {
    position: relative;
    cursor: pointer;
    -webkit-tap-highlight-color: transparent;
    user-select: none;
    transition: filter 0.3s ease;
}

.egg-svg {
    filter: drop-shadow(0 8px 20px rgba(0, 0, 0, 0.15));
    transition: transform 0.15s ease;
}

/* 呼吸动画 */
.egg-svg.breathing {
    animation: eggBreathe 3s ease-in-out infinite;
}

@keyframes eggBreathe {
    0%, 100% { transform: scale(1); }
    50% { transform: scale(1.03); }
}

/* 光晕呼吸 */
.egg-wrapper.glowing::after {
    content: '';
    position: absolute;
    top: 50%;
    left: 50%;
    width: 180px;
    height: 220px;
    transform: translate(-50%, -50%);
    border-radius: 50%;
    background: radial-gradient(ellipse, rgba(255, 182, 193, 0.3) 0%, transparent 70%);
    animation: glowPulse 3s ease-in-out infinite;
    pointer-events: none;
}

@keyframes glowPulse {
    0%, 100% { opacity: 0.5; transform: translate(-50%, -50%) scale(1); }
    50% { opacity: 1; transform: translate(-50%, -50%) scale(1.1); }
}

/* 摇晃动画 - 根据进度分级 */
.egg-svg.wobble-light {
    animation: wobbleLight 2s ease-in-out infinite;
}

@keyframes wobbleLight {
    0%, 100% { transform: rotate(0deg); }
    25% { transform: rotate(2deg); }
    75% { transform: rotate(-2deg); }
}

.egg-svg.wobble-medium {
    animation: wobbleMedium 1.2s ease-in-out infinite;
}

@keyframes wobbleMedium {
    0%, 100% { transform: rotate(0deg) translateY(0); }
    25% { transform: rotate(5deg) translateY(-3px); }
    75% { transform: rotate(-5deg) translateY(-3px); }
}

.egg-svg.wobble-heavy {
    animation: wobbleHeavy 0.8s ease-in-out infinite;
}

@keyframes wobbleHeavy {
    0%, 100% { transform: rotate(0deg) translateY(0); }
    25% { transform: rotate(8deg) translateY(-5px); }
    50% { transform: rotate(0deg) translateY(0); }
    75% { transform: rotate(-8deg) translateY(-5px); }
}

.egg-svg.wobble-extreme {
    animation: wobbleExtreme 0.5s ease-in-out infinite;
}

@keyframes wobbleExtreme {
    0%, 100% { transform: rotate(0deg) translateY(0); }
    20% { transform: rotate(10deg) translateY(-8px); }
    40% { transform: rotate(-10deg) translateY(-5px); }
    60% { transform: rotate(8deg) translateY(-8px); }
    80% { transform: rotate(-8deg) translateY(-3px); }
}

/* 蛋即将破壳发光 */
.egg-svg.glow-crack {
    filter: drop-shadow(0 0 20px rgba(255, 182, 193, 0.8)) drop-shadow(0 0 40px rgba(255, 107, 157, 0.4));
}

/* 点击弹性反馈 */
.egg-svg.bounce {
    animation: eggBounce 0.3s ease;
}

@keyframes eggBounce {
    0% { transform: scale(1); }
    40% { transform: scale(1.08); }
    100% { transform: scale(1); }
}

/* 裂纹 SVG 动画 */
.crack-path {
    stroke: #D4A574;
    stroke-width: 2;
    fill: none;
    stroke-linecap: round;
    stroke-dasharray: 100;
    stroke-dashoffset: 100;
    animation: crackDraw 0.8s ease forwards;
}

@keyframes crackDraw {
    to { stroke-dashoffset: 0; }
}

/* ===== 粒子效果 ===== */
.particle-container {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    pointer-events: none;
    overflow: visible;
}

.heart-particle {
    position: absolute;
    font-size: 16px;
    pointer-events: none;
    animation: heartFloat 1s ease-out forwards;
    opacity: 0;
}

@keyframes heartFloat {
    0% { opacity: 1; transform: translateY(0) scale(0.5); }
    50% { opacity: 0.8; transform: translateY(-30px) scale(1); }
    100% { opacity: 0; transform: translateY(-60px) scale(0.8); }
}

/* 飘字 */
.float-text {
    position: absolute;
    font-size: 14px;
    font-weight: bold;
    color: #FF6B9D;
    pointer-events: none;
    animation: floatUp 0.8s ease-out forwards;
}

@keyframes floatUp {
    0% { opacity: 1; transform: translateY(0); }
    100% { opacity: 0; transform: translateY(-40px); }
}

/* ===== 进度条 ===== */
.hatch-progress {
    margin-top: 30px;
    display: flex;
    align-items: center;
    gap: 12px;
    width: 200px;
}

.progress-bar {
    flex: 1;
    height: 12px;
    background: rgba(255, 255, 255, 0.3);
    border-radius: 6px;
    overflow: hidden;
    backdrop-filter: blur(4px);
}

.progress-fill {
    height: 100%;
    width: 0%;
    background: linear-gradient(90deg, #FFB6C1, #FF6B9D);
    border-radius: 6px;
    transition: width 0.3s ease;
}

.progress-text {
    font-size: 14px;
    font-weight: bold;
    color: #FF6B9D;
    min-width: 36px;
    text-align: right;
}

/* ===== 按钮 ===== */
.hatch-start-btn {
    margin-top: 30px;
    padding: 14px 40px;
    font-size: 16px;
    font-weight: bold;
    color: white;
    background: linear-gradient(135deg, #FFB6C1, #FF6B9D);
    border: none;
    border-radius: 30px;
    cursor: pointer;
    box-shadow: 0 4px 15px rgba(255, 107, 157, 0.4);
    transition: all 0.3s ease;
}

.hatch-start-btn:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 20px rgba(255, 107, 157, 0.6);
}

.hatch-start-btn:active {
    transform: translateY(0);
}

.hatch-start-btn.hidden {
    display: none;
}

/* ===== 提示文字 ===== */
.hatch-hint {
    margin-top: 16px;
    font-size: 14px;
    color: rgba(255, 255, 255, 0.7);
    text-align: center;
}

/* ===== 破壳动画 ===== */
.egg-svg.cracking {
    animation: crackShake 0.5s ease-in-out;
}

@keyframes crackShake {
    0%, 100% { transform: rotate(0deg) scale(1); }
    20% { transform: rotate(12deg) scale(1.05); }
    40% { transform: rotate(-10deg) scale(1.08); }
    60% { transform: rotate(8deg) scale(1.1); }
    80% { transform: rotate(-6deg) scale(1.12); }
}

.egg-svg.crack-burst {
    animation: crackBurst 0.6s ease-out forwards;
}

@keyframes crackBurst {
    0% { transform: scale(1); opacity: 1; }
    50% { transform: scale(1.2); opacity: 0.8; }
    100% { transform: scale(1.5); opacity: 0; }
}

.shell-fragment {
    position: absolute;
    width: 20px;
    height: 16px;
    background: #FFF5E6;
    border: 1px solid #F0D9B5;
    border-radius: 0 50% 50% 50%;
    pointer-events: none;
}

/* ===== 出生场景 ===== */
.birth-container {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 100vh;
}

.birth-rabbit {
    animation: birthPop 0.8s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
    transform: scale(0);
}

@keyframes birthPop {
    0% { transform: scale(0) rotate(-10deg); }
    60% { transform: scale(1.1) rotate(5deg); }
    100% { transform: scale(1) rotate(0deg); }
}

/* ===== 宠物信息卡 ===== */
.pet-info-card {
    margin-top: 30px;
    background: rgba(255, 255, 255, 0.9);
    backdrop-filter: blur(10px);
    border-radius: 20px;
    padding: 24px 36px;
    text-align: center;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
    animation: cardFadeIn 0.6s ease 0.4s both;
    opacity: 0;
}

@keyframes cardFadeIn {
    0% { opacity: 0; transform: translateY(20px); }
    100% { opacity: 1; transform: translateY(0); }
}

.pet-name {
    font-size: 28px;
    color: #333;
    margin: 0 0 12px 0;
}

.pet-details {
    display: flex;
    gap: 16px;
    justify-content: center;
    flex-wrap: wrap;
}

.pet-mbti,
.pet-zodiac,
.pet-birth-date {
    font-size: 14px;
    color: #666;
    background: #FFF0F5;
    padding: 4px 12px;
    border-radius: 12px;
}

/* ===== Toast ===== */
.toast-container {
    position: fixed;
    top: 60px;
    left: 50%;
    transform: translateX(-50%);
    z-index: 9999;
    display: flex;
    flex-direction: column;
    gap: 8px;
    align-items: center;
}

.toast {
    background: rgba(0, 0, 0, 0.75);
    color: white;
    padding: 10px 24px;
    border-radius: 8px;
    font-size: 14px;
    animation: toastIn 0.3s ease, toastOut 0.3s ease 2.7s forwards;
}

@keyframes toastIn {
    from { opacity: 0; transform: translateY(-10px); }
    to { opacity: 1; transform: translateY(0); }
}

@keyframes toastOut {
    from { opacity: 1; }
    to { opacity: 0; }
}

/* ===== 聊天场景中兔子的定位 ===== */
.rabbit-companion {
    position: fixed;
    bottom: 80px;
    left: 20px;
    z-index: 10;
    transition: transform 0.3s ease;
}

/* ===== 重新孵化按钮样式覆盖 ===== */
#rehatchBtn {
    color: #FF6B9D;
}
#rehatchBtn:hover {
    background: rgba(255, 107, 157, 0.1);
}
```

- [ ] **Step 2: 验证样式加载**

刷新 `http://localhost:8006/index.html`
Expected: 蛋居中显示，有投影效果，按钮为粉色圆角

- [ ] **Step 3: Commit**

```bash
git add main/demo-web/css/hatch.css
git commit -m "feat: add hatch animation styles (hatch.css)"
```

---

### Task 3: 创建兔子 SVG 渲染模块

**Files:**
- Create: `main/demo-web/css/rabbit.css`
- Create: `main/demo-web/js/rabbit/rabbit.js`

- [ ] **Step 1: 创建 rabbit.css**

```css
/* ===== 兔子 SVG ===== */
.rabbit-svg {
    filter: drop-shadow(0 4px 12px rgba(0, 0, 0, 0.1));
}

/* 呼吸动画 */
.rabbit-svg.idle {
    animation: rabbitBreathe 3s ease-in-out infinite;
}

@keyframes rabbitBreathe {
    0%, 100% { transform: translateY(0); }
    50% { transform: translateY(-4px); }
}

/* 眨眼 */
.rabbit-eye-lid {
    animation: blink 4s ease-in-out infinite;
}

@keyframes blink {
    0%, 45%, 55%, 100% { transform: scaleY(1); }
    50% { transform: scaleY(0.1); }
}

/* 说话 - 嘴巴张合 */
.rabbit-svg.speaking .rabbit-mouth {
    animation: mouthMove 0.3s ease-in-out infinite alternate;
}

@keyframes mouthMove {
    0% { transform: scaleY(1); }
    100% { transform: scaleY(1.5); }
}

/* 倾听 - 耳朵竖起 */
.rabbit-svg.listening .rabbit-ear-left {
    animation: earPerkLeft 0.4s ease forwards;
}
.rabbit-svg.listening .rabbit-ear-right {
    animation: earPerkRight 0.4s ease forwards;
}

@keyframes earPerkLeft {
    0% { transform: rotate(0deg); }
    100% { transform: rotate(-8deg); }
}

@keyframes earPerkRight {
    0% { transform: rotate(0deg); }
    100% { transform: rotate(8deg); }
}

/* 收到消息 - 开心跳动 */
.rabbit-svg.happy {
    animation: rabbitJump 0.5s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes rabbitJump {
    0%, 100% { transform: translateY(0); }
    40% { transform: translateY(-15px) scale(1.05); }
    60% { transform: translateY(-10px); }
}

/* 出生场景大兔子 */
.birth-rabbit .rabbit-svg {
    width: 160px;
    height: 200px;
}

/* 伴侣小兔子 */
.rabbit-companion .rabbit-svg {
    width: 80px;
    height: 100px;
}
```

- [ ] **Step 2: 创建 rabbit.js**

```js
/**
 * 兔子 SVG 渲染和状态管理
 */

const RABBIT_SVG = `
<svg class="rabbit-svg idle" viewBox="0 0 160 200" xmlns="http://www.w3.org/2000/svg">
    <!-- 左耳 -->
    <g class="rabbit-ear-left" style="transform-origin: 50px 80px;">
        <ellipse cx="50" cy="40" rx="16" ry="40" fill="#FFFFFF" stroke="#F0D9B5" stroke-width="1.5"/>
        <ellipse cx="50" cy="40" rx="8" ry="28" fill="#FFB6C1"/>
    </g>
    <!-- 右耳 -->
    <g class="rabbit-ear-right" style="transform-origin: 110px 80px;">
        <ellipse cx="110" cy="40" rx="16" ry="40" fill="#FFFFFF" stroke="#F0D9B5" stroke-width="1.5"/>
        <ellipse cx="110" cy="40" rx="8" ry="28" fill="#FFB6C1"/>
    </g>
    <!-- 头 -->
    <circle cx="80" cy="95" r="42" fill="#FFFFFF" stroke="#F0D9B5" stroke-width="1.5"/>
    <!-- 左眼 -->
    <g class="rabbit-eye-lid" style="transform-origin: 65px 88px;">
        <circle cx="65" cy="88" r="5" fill="#333"/>
        <circle cx="63" cy="86" r="2" fill="#FFF"/>
    </g>
    <!-- 右眼 -->
    <g class="rabbit-eye-lid" style="transform-origin: 95px 88px;">
        <circle cx="95" cy="88" r="5" fill="#333"/>
        <circle cx="93" cy="86" r="2" fill="#FFF"/>
    </g>
    <!-- 鼻子 -->
    <ellipse cx="80" cy="98" rx="4" ry="3" fill="#FFB6C1"/>
    <!-- 嘴 -->
    <path class="rabbit-mouth" d="M76 102 Q80 107 84 102" fill="none" stroke="#F0D9B5" stroke-width="1.5" stroke-linecap="round" style="transform-origin: 80px 102px;"/>
    <!-- 腮红 -->
    <ellipse cx="52" cy="100" rx="10" ry="6" fill="rgba(255,180,180,0.5)"/>
    <ellipse cx="108" cy="100" rx="10" ry="6" fill="rgba(255,180,180,0.5)"/>
    <!-- 身体 -->
    <ellipse cx="80" cy="155" rx="35" ry="30" fill="#FFFFFF" stroke="#F0D9B5" stroke-width="1.5"/>
    <!-- 尾巴 -->
    <circle cx="80" cy="185" r="8" fill="#FFFFFF" stroke="#F0D9B5" stroke-width="1.5"/>
</svg>
`;

/**
 * 创建兔子元素并挂载到容器
 * @param {HTMLElement} container
 * @returns {{ el: SVGSVGElement, setState: (state: string) => void }}
 */
export function createRabbit(container) {
    container.innerHTML = RABBIT_SVG;
    const svg = container.querySelector('.rabbit-svg');

    return {
        el: svg,

        /**
         * 设置兔子状态
         * @param {'idle'|'speaking'|'listening'|'happy'} state
         */
        setState(state) {
            if (!svg) return;
            svg.classList.remove('idle', 'speaking', 'listening', 'happy');
            switch (state) {
                case 'speaking':
                    svg.classList.add('speaking');
                    break;
                case 'listening':
                    svg.classList.add('listening');
                    break;
                case 'happy':
                    svg.classList.add('happy');
                    // 跳动结束后恢复待机
                    svg.addEventListener('animationend', () => {
                        svg.classList.remove('happy');
                        svg.classList.add('idle');
                    }, { once: true });
                    break;
                default:
                    svg.classList.add('idle');
            }
        }
    };
}
```

- [ ] **Step 3: 在 index.html 中临时测试兔子渲染**

在浏览器控制台执行：
```js
import('./js/rabbit/rabbit.js').then(m => {
    const c = document.getElementById('birthRabbit');
    const rabbit = m.createRabbit(c);
    setTimeout(() => rabbit.setState('happy'), 1000);
});
```
Expected: 出生场景显示兔子 SVG，1秒后跳动

- [ ] **Step 4: Commit**

```bash
git add main/demo-web/css/rabbit.css main/demo-web/js/rabbit/rabbit.js
git commit -m "feat: add rabbit SVG renderer with state animations"
```

---

### Task 4: 创建孵化状态机

**Files:**
- Create: `main/demo-web/js/hatch/hatch-manager.js`

- [ ] **Step 1: 创建 hatch-manager.js**

```js
/**
 * 孵化状态机
 * 管理5个阶段的切换：egg -> hatching -> cracking -> birth -> chat
 */

/** @typedef {'egg'|'hatching'|'cracking'|'birth'|'chat'} HatchStage */

export class HatchManager {
    constructor() {
        /** @type {HatchStage} */
        this.currentStage = 'egg';
        /** @type {Map<HatchStage, Function>} */
        this.stageHandlers = new Map();
        this.onStageChange = null;
    }

    /**
     * 注册阶段处理函数
     * @param {HatchStage} stage
     * @param {{ enter: () => void | Promise<void>, exit?: () => void }} handler
     */
    registerStage(stage, handler) {
        this.stageHandlers.set(stage, handler);
    }

    /**
     * 切换到指定阶段
     * @param {HatchStage} stage
     */
    async goTo(stage) {
        const prev = this.currentStage;
        if (prev === stage) return;

        // 退出当前阶段
        const prevHandler = this.stageHandlers.get(prev);
        if (prevHandler && prevHandler.exit) {
            prevHandler.exit();
        }

        this.currentStage = stage;

        // 进入新阶段
        const nextHandler = this.stageHandlers.get(stage);
        if (nextHandler && nextHandler.enter) {
            await nextHandler.enter();
        }

        if (this.onStageChange) {
            this.onStageChange(stage, prev);
        }
    }

    /**
     * 重置到初始状态
     */
    async reset() {
        await this.goTo('egg');
    }

    /**
     * 获取当前阶段
     * @returns {HatchStage}
     */
    getStage() {
        return this.currentStage;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add main/demo-web/js/hatch/hatch-manager.js
git commit -m "feat: add hatch state machine (HatchManager)"
```

---

### Task 5: 创建蛋的渲染和动画模块

**Files:**
- Create: `main/demo-web/js/hatch/egg.js`

- [ ] **Step 1: 创建 egg.js**

```js
/**
 * 蛋的渲染和动画控制
 */

/**
 * 裂纹 SVG path 数据（按进度阈值分组）
 * 每组裂纹在对应进度时显示
 */
const CRACK_PATHS = [
    // 30%: 细小裂纹
    { threshold: 30, paths: [
        'M 70 30 Q 65 50 68 70',
        'M 70 30 Q 75 45 72 65',
    ]},
    // 50%: 更多裂纹
    { threshold: 50, paths: [
        'M 55 35 Q 60 55 58 75',
        'M 85 35 Q 80 55 82 75',
    ]},
    // 70%: 裂纹变粗
    { threshold: 70, paths: [
        'M 40 50 Q 50 60 45 80',
        'M 100 50 Q 90 60 95 80',
        'M 70 25 L 70 50',
    ]},
    // 90%: 大量裂纹
    { threshold: 90, paths: [
        'M 35 40 Q 45 55 40 75',
        'M 105 40 Q 95 55 100 75',
        'M 60 25 Q 65 40 58 55',
        'M 80 25 Q 75 40 82 55',
    ]},
];

/**
 * 初始化蛋的呼吸动画
 * @param {SVGSVGElement} eggSvg
 */
export function startBreathing(eggSvg) {
    eggSvg.classList.add('breathing');
    eggSvg.closest('.egg-wrapper').classList.add('glowing');
}

/**
 * 停止呼吸动画
 * @param {SVGSVGElement} eggSvg
 */
export function stopBreathing(eggSvg) {
    eggSvg.classList.remove('breathing');
    eggSvg.closest('.egg-wrapper').classList.remove('glowing');
}

/**
 * 根据进度更新蛋的摇晃等级
 * @param {SVGSVGElement} eggSvg
 * @param {number} progress 0-100
 */
export function updateWobble(eggSvg, progress) {
    eggSvg.classList.remove('wobble-light', 'wobble-medium', 'wobble-heavy', 'wobble-extreme');

    if (progress >= 90) {
        eggSvg.classList.add('wobble-extreme');
    } else if (progress >= 60) {
        eggSvg.classList.add('wobble-heavy');
    } else if (progress >= 30) {
        eggSvg.classList.add('wobble-medium');
    } else if (progress > 0) {
        eggSvg.classList.add('wobble-light');
    }

    // 90%+ 添加发光效果
    if (progress >= 90) {
        eggSvg.classList.add('glow-crack');
    } else {
        eggSvg.classList.remove('glow-crack');
    }
}

/**
 * 根据进度更新裂纹显示
 * @param {SVGGElement} crackGroup
 * @param {number} progress 0-100
 * @param {number} prevProgress 上一次的进度（避免重复添加）
 */
export function updateCracks(crackGroup, progress, prevProgress) {
    CRACK_PATHS.forEach(({ threshold, paths }) => {
        if (progress >= threshold && prevProgress < threshold) {
            paths.forEach(d => {
                const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
                path.setAttribute('d', d);
                path.classList.add('crack-path');
                crackGroup.appendChild(path);
            });
        }
    });
}

/**
 * 播放点击弹性反馈
 * @param {SVGSVGElement} eggSvg
 */
export function playBounce(eggSvg) {
    // 先移除摇晃动画，播放弹性，再恢复摇晃
    const currentWobble = [...eggSvg.classList].find(c => c.startsWith('wobble-'));
    eggSvg.classList.remove('breathing', ...[...eggSvg.classList].filter(c => c.startsWith('wobble-')));
    eggSvg.classList.add('bounce');

    eggSvg.addEventListener('animationend', () => {
        eggSvg.classList.remove('bounce');
        if (currentWobble) {
            eggSvg.classList.add(currentWobble);
        }
    }, { once: true });
}

/**
 * 清除所有裂纹
 * @param {SVGGElement} crackGroup
 */
export function clearCracks(crackGroup) {
    crackGroup.innerHTML = '';
}

/**
 * 清除蛋的所有动画类
 * @param {SVGSVGElement} eggSvg
 */
export function resetEgg(eggSvg) {
    eggSvg.classList.remove(
        'breathing', 'wobble-light', 'wobble-medium',
        'wobble-heavy', 'wobble-extreme', 'glow-crack', 'bounce'
    );
    const wrapper = eggSvg.closest('.egg-wrapper');
    if (wrapper) wrapper.classList.remove('glowing');
}
```

- [ ] **Step 2: Commit**

```bash
git add main/demo-web/js/hatch/egg.js
git commit -m "feat: add egg rendering and animation module"
```

---

### Task 6: 创建孵化互动模块

**Files:**
- Create: `main/demo-web/js/hatch/hatching.js`

- [ ] **Step 1: 创建 hatching.js**

```js
/**
 * 孵化互动逻辑：点击事件、进度管理、爱心粒子
 */

import { playBounce, updateWobble, updateCracks } from './egg.js?v=0508';

/**
 * 创建孵化互动控制器
 * @param {object} deps
 * @param {SVGSVGElement} deps.eggSvg
 * @param {SVGGElement} deps.crackGroup
 * @param {HTMLElement} deps.progressContainer
 * @param {HTMLElement} deps.progressFill
 * @param {HTMLElement} deps.progressText
 * @param {HTMLElement} deps.particleContainer
 * @param {HTMLElement} deps.hintEl
 * @param {() => void} deps.onComplete - 进度100%时的回调
 * @returns {{ start: () => void, stop: () => void, getProgress: () => number }}
 */
export function createHatchingController(deps) {
    const {
        eggSvg, crackGroup, progressContainer, progressFill,
        progressText, particleContainer, hintEl, onComplete
    } = deps;

    let progress = 0;
    let prevProgress = 0;
    let autoTimer = null;
    let isActive = false;

    function updateUI() {
        progressFill.style.width = `${progress}%`;
        progressText.textContent = `${Math.round(progress)}%`;
        updateWobble(eggSvg, progress);
        updateCracks(crackGroup, progress, prevProgress);
        prevProgress = progress;
    }

    function addProgress(amount) {
        if (!isActive) return;
        progress = Math.min(100, progress + amount);
        updateUI();

        if (progress >= 100) {
            stop();
            if (onComplete) onComplete();
        }
    }

    function onEggClick(e) {
        if (!isActive) return;
        const amount = 3 + Math.random() * 2; // 3~5%
        addProgress(amount);
        playBounce(eggSvg);
        spawnHeartParticle(particleContainer, e);
        spawnFloatText(particleContainer, `+${Math.round(amount)}%`);
    }

    function spawnHeartParticle(container, e) {
        const rect = container.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;
        const hearts = ['❤', '💕', '💗', '✨'];

        for (let i = 0; i < 3; i++) {
            const particle = document.createElement('div');
            particle.className = 'heart-particle';
            particle.textContent = hearts[Math.floor(Math.random() * hearts.length)];
            particle.style.left = `${x + (Math.random() - 0.5) * 30}px`;
            particle.style.top = `${y}px`;
            container.appendChild(particle);
            particle.addEventListener('animationend', () => particle.remove());
        }
    }

    function spawnFloatText(container, text) {
        const rect = container.getBoundingClientRect();
        const el = document.createElement('div');
        el.className = 'float-text';
        el.textContent = text;
        el.style.left = `${rect.width / 2 - 15}px`;
        el.style.top = `${rect.height / 2 - 40}px`;
        container.appendChild(el);
        el.addEventListener('animationend', () => el.remove());
    }

    function start() {
        isActive = true;
        progress = 0;
        prevProgress = 0;
        progressContainer.style.display = 'flex';
        if (hintEl) hintEl.textContent = '点击蛋来加速孵化！';

        // 自动增长 0.5%/秒
        autoTimer = setInterval(() => {
            addProgress(0.5);
        }, 1000);

        // 绑定点击事件
        eggSvg.closest('.egg-wrapper').addEventListener('click', onEggClick);
        eggSvg.closest('.egg-wrapper').addEventListener('touchend', (e) => {
            e.preventDefault();
            onEggClick({ clientX: e.changedTouches[0].clientX, clientY: e.changedTouches[0].clientY });
        });

        updateUI();
    }

    function stop() {
        isActive = false;
        if (autoTimer) {
            clearInterval(autoTimer);
            autoTimer = null;
        }
        const wrapper = eggSvg.closest('.egg-wrapper');
        if (wrapper) {
            wrapper.removeEventListener('click', onEggClick);
        }
    }

    function getProgress() {
        return progress;
    }

    return { start, stop, getProgress };
}
```

- [ ] **Step 2: Commit**

```bash
git add main/demo-web/js/hatch/hatching.js
git commit -m "feat: add hatching interaction module (click, progress, particles)"
```

---

### Task 7: 创建破壳动画模块

**Files:**
- Create: `main/demo-web/js/hatch/cracking.js`

- [ ] **Step 1: 创建 cracking.js**

```js
/**
 * 破壳动画：蛋壳裂开 + 粒子飞散
 */

/**
 * 播放破壳动画
 * @param {object} deps
 * @param {SVGSVGElement} deps.eggSvg
 * @param {HTMLElement} deps.particleContainer
 * @param {number} duration - 动画总时长(ms)，默认2000
 * @returns {Promise<void>}
 */
export function playCrackingAnimation(deps, duration = 2000) {
    const { eggSvg, particleContainer } = deps;

    return new Promise(resolve => {
        // 阶段1：剧烈摇晃
        eggSvg.classList.remove(...[...eggSvg.classList].filter(c => c.startsWith('wobble-')));
        eggSvg.classList.add('cracking');

        // 阶段2（duration/2时）：蛋壳爆裂
        setTimeout(() => {
            eggSvg.classList.remove('cracking');
            eggSvg.classList.add('crack-burst');
            spawnShellFragments(particleContainer);
            spawnBurstParticles(particleContainer);
        }, duration / 2);

        // 动画结束
        setTimeout(() => {
            eggSvg.classList.remove('crack-burst');
            resolve();
        }, duration);
    });
}

/**
 * 生成蛋壳碎片
 * @param {HTMLElement} container
 */
function spawnShellFragments(container) {
    const rect = container.getBoundingClientRect();
    const centerX = rect.width / 2;
    const centerY = rect.height / 2;

    for (let i = 0; i < 8; i++) {
        const fragment = document.createElement('div');
        fragment.className = 'shell-fragment';

        const angle = (Math.PI * 2 * i) / 8 + (Math.random() - 0.5) * 0.5;
        const distance = 60 + Math.random() * 40;
        const endX = centerX + Math.cos(angle) * distance;
        const endY = centerY + Math.sin(angle) * distance;
        const rotation = Math.random() * 360;

        fragment.style.left = `${centerX}px`;
        fragment.style.top = `${centerY}px`;
        fragment.style.transition = 'all 0.8s cubic-bezier(0.25, 0.46, 0.45, 0.94)';
        fragment.style.opacity = '1';
        container.appendChild(fragment);

        requestAnimationFrame(() => {
            fragment.style.left = `${endX}px`;
            fragment.style.top = `${endY}px`;
            fragment.style.transform = `rotate(${rotation}deg)`;
        });

        setTimeout(() => {
            fragment.style.opacity = '0';
            setTimeout(() => fragment.remove(), 300);
        }, 600);
    }
}

/**
 * 生成爆裂粒子（星星 + 爱心）
 * @param {HTMLElement} container
 */
function spawnBurstParticles(container) {
    const rect = container.getBoundingClientRect();
    const centerX = rect.width / 2;
    const centerY = rect.height / 2;
    const particles = ['✨', '⭐', '💖', '🌟', '💫'];

    for (let i = 0; i < 12; i++) {
        const particle = document.createElement('div');
        particle.textContent = particles[Math.floor(Math.random() * particles.length)];
        particle.style.position = 'absolute';
        particle.style.fontSize = `${12 + Math.random() * 10}px`;
        particle.style.left = `${centerX}px`;
        particle.style.top = `${centerY}px`;
        particle.style.pointerEvents = 'none';
        particle.style.transition = 'all 0.8s cubic-bezier(0.25, 0.46, 0.45, 0.94)';
        particle.style.opacity = '1';
        container.appendChild(particle);

        const angle = (Math.PI * 2 * i) / 12;
        const distance = 50 + Math.random() * 60;
        const endX = centerX + Math.cos(angle) * distance;
        const endY = centerY + Math.sin(angle) * distance;

        requestAnimationFrame(() => {
            particle.style.left = `${endX}px`;
            particle.style.top = `${endY}px`;
        });

        setTimeout(() => {
            particle.style.opacity = '0';
            setTimeout(() => particle.remove(), 300);
        }, 700);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add main/demo-web/js/hatch/cracking.js
git commit -m "feat: add cracking animation module (shell burst, particles)"
```

---

### Task 8: 创建出生展示模块

**Files:**
- Create: `main/demo-web/js/hatch/birth.js`

- [ ] **Step 1: 创建 birth.js**

```js
/**
 * 出生展示：调用 API、展示宠物信息卡、过渡到聊天
 */

import { createRabbit } from '../rabbit/rabbit.js?v=0508';

/** 星座名称映射 */
const ZODIAC_NAMES = {
    aries: '白羊座', taurus: '金牛座', gemini: '双子座', cancer: '巨蟹座',
    leo: '狮子座', virgo: '处女座', libra: '天秤座', scorpio: '天蝎座',
    sagittarius: '射手座', capricorn: '摩羯座', aquarius: '水瓶座', pisces: '双鱼座',
};

/** 默认宠物数据（API 失败时使用） */
const DEFAULT_PET = {
    nickname: '小团子',
    mbti: 'INFP',
    zodiac: 'taurus',
    birthDate: new Date().toISOString(),
};

/**
 * 展示出生场景
 * @param {object} deps
 * @param {HTMLElement} deps.birthScene
 * @param {HTMLElement} deps.birthRabbitContainer
 * @param {HTMLElement} deps.petNameEl
 * @param {HTMLElement} deps.petMbtiEl
 * @param {HTMLElement} deps.petZodiacEl
 * @param {HTMLElement} deps.petBirthDateEl
 * @param {Function} deps.showToast
 * @param {string} otaUrl - OTA 服务器地址，用于推导 API 地址
 * @param {string} deviceId - 设备 ID
 * @returns {Promise<{ petData: object }>}
 */
export async function showBirthScene(deps, otaUrl, deviceId) {
    const {
        birthScene, birthRabbitContainer, petNameEl,
        petMbtiEl, petZodiacEl, petBirthDateEl, showToast
    } = deps;

    // 切换到出生场景
    birthScene.classList.add('active');

    // 创建兔子
    const rabbit = createRabbit(birthRabbitContainer);

    // 调用出生 API
    let petData;
    try {
        petData = await callBirthApi(otaUrl, deviceId);
    } catch (err) {
        showToast(`出生信息加载失败: ${err.message}，使用默认数据`);
        petData = DEFAULT_PET;
    }

    // 填充宠物信息卡
    petNameEl.textContent = petData.nickname;
    petMbtiEl.textContent = petData.mbti;
    petZodiacEl.textContent = ZODIAC_NAMES[petData.zodiac] || petData.zodiac;
    petBirthDateEl.textContent = formatDate(petData.birthDate);

    return { petData, rabbit };
}

/**
 * 调用出生 API
 * @param {string} otaUrl - 如 "http://127.0.0.1:8002/xiaozhi/ota/"
 * @param {string} deviceId
 * @returns {Promise<object>}
 */
async function callBirthApi(otaUrl, deviceId) {
    if (!deviceId) {
        throw new Error('设备ID为空，请先在设置中配置设备MAC');
    }

    // 从 OTA URL 推导 API 地址
    const apiBase = otaUrl.replace(/\/xiaozhi\/ota\/?$/, '');
    const url = `${apiBase}/xiaozhi/pet/birth`;

    const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ deviceId }),
    });

    if (!response.ok) {
        const errorBody = await response.text().catch(() => '');
        if (response.status === 404 || errorBody.includes('10205')) {
            throw new Error('设备未找到，请先注册设备');
        }
        if (response.status === 409 || errorBody.includes('10206')) {
            throw new Error('该设备已有宠物');
        }
        throw new Error(`API 返回 ${response.status}`);
    }

    const result = await response.json();

    // 适配后端响应格式（可能是 { data: {...} } 或直接返回数据）
    if (result.code !== undefined && result.data) {
        return result.data;
    }
    return result;
}

/**
 * 格式化日期
 * @param {string} dateStr
 * @returns {string}
 */
function formatDate(dateStr) {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    const h = String(date.getHours()).padStart(2, '0');
    const min = String(date.getMinutes()).padStart(2, '0');
    return `${y}-${m}-${d} ${h}:${min}`;
}
```

- [ ] **Step 2: Commit**

```bash
git add main/demo-web/js/hatch/birth.js
git commit -m "feat: add birth scene module (API call, pet info card)"
```

---

### Task 9: 创建演示页面入口 hatch-app.js

**Files:**
- Create: `main/demo-web/js/hatch-app.js`

- [ ] **Step 1: 创建 hatch-app.js**

```js
/**
 * 孵化演示页面入口
 * 整合状态机、蛋动画、孵化互动、破壳、出生展示、聊天
 */

import { checkOpusLoaded, initOpusEncoder } from './core/audio/opus-codec.js?v=0205';
import { getAudioPlayer } from './core/audio/player.js?v=0205';
import { checkMicrophoneAvailability, isHttpNonLocalhost } from './core/audio/recorder.js?v=0205';
import { initMcpTools } from './core/mcp/tools.js?v=0205';
import { loadConfig, saveConfig, getConfig } from './config/manager.js?v=0205';
import { log } from './utils/logger.js?v=0205';
import { HatchManager } from './hatch/hatch-manager.js?v=0508';
import { startBreathing, stopBreathing, resetEgg, clearCracks } from './hatch/egg.js?v=0508';
import { createHatchingController } from './hatch/hatching.js?v=0508';
import { playCrackingAnimation } from './hatch/cracking.js?v=0508';
import { showBirthScene } from './hatch/birth.js?v=0508';
import { createRabbit } from './rabbit/rabbit.js?v=0508';
import { uiController } from './ui/controller.js?v=0205';

class HatchApp {
    constructor() {
        this.hatchManager = new HatchManager();
        this.hatchingController = null;
        this.rabbitCompanion = null;
    }

    async init() {
        log('正在初始化孵化演示...', 'info');

        // 缓存 DOM 元素
        this.els = {
            hatchScene: document.getElementById('hatchScene'),
            birthScene: document.getElementById('birthScene'),
            chatScene: document.getElementById('chatScene'),
            eggSvg: document.getElementById('eggSvg'),
            crackGroup: document.getElementById('crackGroup'),
            eggWrapper: document.getElementById('eggWrapper'),
            hatchStartBtn: document.getElementById('hatchStartBtn'),
            hatchProgress: document.getElementById('hatchProgress'),
            progressFill: document.getElementById('progressFill'),
            progressText: document.getElementById('progressText'),
            particleContainer: document.getElementById('particleContainer'),
            hatchHint: document.getElementById('hatchHint'),
            birthRabbitContainer: document.getElementById('birthRabbit'),
            petName: document.getElementById('petName'),
            petMbti: document.getElementById('petMbti'),
            petZodiac: document.getElementById('petZodiac'),
            petBirthDate: document.getElementById('petBirthDate'),
            rabbitCompanionEl: document.getElementById('rabbitCompanion'),
            rehatchBtn: document.getElementById('rehatchBtn'),
            toastContainer: document.getElementById('toastContainer'),
            connectionStatus: document.getElementById('connectionStatus'),
            connectionStatus2: document.getElementById('connectionStatus2'),
        };

        // 初始化核心模块
        checkOpusLoaded();
        initOpusEncoder();
        const audioPlayer = getAudioPlayer();
        await audioPlayer.start();
        initMcpTools();

        // 检查麦克风
        await this.checkMicrophone();

        // 初始化 UI 控制器
        uiController.init();
        this.initRehatchButton();

        // 注册状态机阶段
        this.registerStages();

        // 进入蛋阶段
        await this.hatchManager.goTo('egg');

        log('孵化演示初始化完成', 'success');
    }

    async checkMicrophone() {
        try {
            const available = await checkMicrophoneAvailability();
            const isHttp = isHttpNonLocalhost();
            window.microphoneAvailable = available;
            window.isHttpNonLocalhost = isHttp;
        } catch {
            window.microphoneAvailable = false;
            window.isHttpNonLocalhost = isHttpNonLocalhost();
        }
    }

    registerStages() {
        const { hatchManager, els } = this;

        // 阶段1: 蛋（静态展示）
        hatchManager.registerStage('egg', {
            enter: () => {
                this.switchScene('hatchScene');
                resetEgg(els.eggSvg);
                clearCracks(els.crackGroup);
                startBreathing(els.eggSvg);
                els.hatchStartBtn.classList.remove('hidden');
                els.hatchProgress.style.display = 'none';
                if (els.hatchHint) els.hatchHint.textContent = '';
            },
            exit: () => {
                stopBreathing(els.eggSvg);
                els.hatchStartBtn.classList.add('hidden');
            }
        });

        // 阶段2: 孵化（用户互动）
        hatchManager.registerStage('hatching', {
            enter: () => {
                this.hatchingController = createHatchingController({
                    eggSvg: els.eggSvg,
                    crackGroup: els.crackGroup,
                    progressContainer: els.hatchProgress,
                    progressFill: els.progressFill,
                    progressText: els.progressText,
                    particleContainer: els.particleContainer,
                    hintEl: els.hatchHint,
                    onComplete: () => hatchManager.goTo('cracking'),
                });
                this.hatchingController.start();
            },
            exit: () => {
                if (this.hatchingController) {
                    this.hatchingController.stop();
                    this.hatchingController = null;
                }
            }
        });

        // 阶段3: 破壳
        hatchManager.registerStage('cracking', {
            enter: async () => {
                if (els.hatchHint) els.hatchHint.textContent = '即将破壳...';

                // 播放破壳动画，在50%时调用API
                const crackingPromise = playCrackingAnimation({
                    eggSvg: els.eggSvg,
                    particleContainer: els.particleContainer,
                }, 2000);

                // 动画播放到一半时发起API请求
                setTimeout(async () => {
                    try {
                        const config = getConfig();
                        const otaUrl = document.getElementById('otaUrl')?.value?.trim() || '';
                        this.birthResult = await showBirthScene({
                            birthScene: els.birthScene,
                            birthRabbitContainer: els.birthRabbitContainer,
                            petNameEl: els.petName,
                            petMbtiEl: els.petMbti,
                            petZodiacEl: els.petZodiac,
                            petBirthDateEl: els.petBirthDate,
                            showToast: (msg) => this.showToast(msg),
                        }, otaUrl, config.deviceId);
                    } catch (err) {
                        this.showToast(err.message);
                    }
                }, 1000);

                await crackingPromise;
                // 破壳动画完成后等待一下再切换
                await new Promise(r => setTimeout(r, 1500));
                await hatchManager.goTo('birth');
            }
        });

        // 阶段4: 出生展示
        hatchManager.registerStage('birth', {
            enter: () => {
                // 出生场景已在 cracking 阶段显示
                // 展示3.5秒后过渡到聊天
                setTimeout(async () => {
                    await hatchManager.goTo('chat');
                }, 3500);
            },
            exit: () => {
                els.birthScene.classList.remove('active');
            }
        });

        // 阶段5: 聊天
        hatchManager.registerStage('chat', {
            enter: () => {
                this.switchScene('chatScene');

                // 创建兔子伴侣
                if (els.rabbitCompanionEl && !this.rabbitCompanion) {
                    this.rabbitCompanion = createRabbit(els.rabbitCompanionEl);
                }

                // 初始化 UI 控制器的事件（拨号、录音等）
                this.initChatControls();
            }
        });

        // 绑定开始按钮
        els.hatchStartBtn.addEventListener('click', () => {
            hatchManager.goTo('hatching');
        });
    }

    initChatControls() {
        // 拨号按钮
        const dialBtn = document.getElementById('dialBtn');
        if (dialBtn && !dialBtn._hatchBound) {
            dialBtn._hatchBound = true;
            dialBtn.addEventListener('click', () => {
                dialBtn.disabled = true;
                setTimeout(() => { dialBtn.disabled = false; }, 3000);

                const { getWebSocketHandler } = require('./core/network/websocket.js?v=0205');
                const wsHandler = getWebSocketHandler();
                if (wsHandler.isConnected()) {
                    wsHandler.disconnect();
                    uiController.updateDialButton(false);
                    uiController.addChatMessage('已断开连接~', false);
                } else {
                    uiController.handleConnect();
                }
            });
        }

        // 录音按钮
        const recordBtn = document.getElementById('recordBtn');
        if (recordBtn && !recordBtn._hatchBound) {
            recordBtn._hatchBound = true;
            recordBtn.addEventListener('click', () => {
                const { getAudioRecorder } = require('./core/audio/recorder.js?v=0205');
                const audioRecorder = getAudioRecorder();
                if (audioRecorder.isRecording) {
                    audioRecorder.stop();
                    recordBtn.classList.remove('recording');
                    recordBtn.querySelector('.btn-text').textContent = '录音';
                } else {
                    recordBtn.classList.add('recording');
                    recordBtn.querySelector('.btn-text').textContent = '录音中';
                    setTimeout(() => audioRecorder.start(), 100);
                }
            });
        }

        // 聊天输入
        const chatIpt = document.getElementById('chatIpt');
        if (chatIpt && !chatIpt._hatchBound) {
            chatIpt._hatchBound = true;
            chatIpt.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' && e.target.value) {
                    const { getWebSocketHandler } = require('./core/network/websocket.js?v=0205');
                    getWebSocketHandler().sendTextMessage(e.target.value);
                    e.target.value = '';
                }
            });
        }
    }

    initRehatchButton() {
        const { els, hatchManager } = this;
        if (els.rehatchBtn) {
            els.rehatchBtn.addEventListener('click', () => {
                // 断开 WebSocket
                const { getWebSocketHandler } = require('./core/network/websocket.js?v=0205');
                const wsHandler = getWebSocketHandler();
                if (wsHandler.isConnected()) {
                    wsHandler.disconnect();
                }

                // 清理兔子伴侣
                this.rabbitCompanion = null;
                if (els.rabbitCompanionEl) els.rabbitCompanionEl.innerHTML = '';

                // 重置聊天流
                const chatStream = document.getElementById('chatStream');
                if (chatStream) chatStream.innerHTML = '';

                // 重置到蛋阶段
                hatchManager.reset();
            });
        }
    }

    switchScene(sceneId) {
        document.querySelectorAll('.scene').forEach(s => s.classList.remove('active'));
        const scene = document.getElementById(sceneId);
        if (scene) scene.classList.add('active');
    }

    showToast(message) {
        const toast = document.createElement('div');
        toast.className = 'toast';
        toast.textContent = message;
        this.els.toastContainer.appendChild(toast);
        setTimeout(() => toast.remove(), 3000);
    }
}

// 启动
const hatchApp = new HatchApp();
window.chatApp = hatchApp;
window.hatchApp = hatchApp;
document.addEventListener('DOMContentLoaded', () => hatchApp.init());
```

- [ ] **Step 2: 验证完整流程**

打开 `http://localhost:8006/index.html`:
1. 应看到蛋 + "开始孵化"按钮
2. 点击按钮 → 蛋开始摇晃，进度条出现
3. 点击蛋 → 弹性反馈 + 爱心粒子 + 进度增加
4. 进度满 → 破壳动画 → API 调用 → 兔子出现 + 信息卡
5. 3.5秒后 → 聊天界面，兔子在左下角
6. 点击"重新孵化" → 回到蛋

- [ ] **Step 3: Commit**

```bash
git add main/demo-web/js/hatch-app.js
git commit -m "feat: add hatch demo app entry (hatch-app.js)"
```

---

### Task 10: 集成验证和修复

**Files:**
- Modify: `main/demo-web/js/hatch-app.js` (如需修复)
- Modify: `main/demo-web/css/hatch.css` (如需修复)

- [ ] **Step 1: 启动 HTTP 服务器测试**

```bash
cd main/demo-web && python -m http.server 8006
```

- [ ] **Step 2: 测试完整流程（无后端）**

打开 `http://localhost:8006/index.html`:
- 蛋呼吸动画正常
- 点击"开始孵化"后进度条出现
- 点击蛋有弹性反馈和粒子
- 蛋摇晃随进度变化
- 裂纹随进度出现
- 进度满后破壳动画播放
- API 调用失败时显示 Toast 并使用默认数据
- 兔子出现并展示信息卡
- 自动过渡到聊天界面
- 兔子在左下角
- "重新孵化"按钮重置流程

- [ ] **Step 3: 测试与后端集成（如有后端运行）**

- 确保 OTA URL 配置正确
- 拨号按钮能正常连接
- 录音和聊天功能正常
- 重新孵化后拨号仍可正常工作

- [ ] **Step 4: 修复发现的问题**

根据测试结果修复任何 CSS/JS 问题。

- [ ] **Step 5: 最终 Commit**

```bash
git add -A main/demo-web/
git commit -m "feat: complete hatch demo integration and fixes"
```

---

### Task 11: 修改 controller.js 适配新页面

**Files:**
- Modify: `main/demo-web/js/ui/controller.js`

- [ ] **Step 1: 修改 updateConnectionUI 方法支持双连接状态元素**

在 `controller.js` 的 `updateConnectionUI` 方法中，增加对 `connectionStatus2` 元素的同步更新：

找到 `updateConnectionUI` 方法（约第293行），在方法末尾添加：

```js
// 同步更新第二个连接状态元素（孵化页面）
const connectionStatus2 = document.getElementById('connectionStatus2');
if (connectionStatus2) {
    connectionStatus2.textContent = isConnected ? '已连接' : '离线';
}
```

- [ ] **Step 2: 修改 addChatMessage 方法，确保两个页面都能用**

找到 `addChatMessage` 方法，确认 `chatStream` 元素查找逻辑不需要改动（当前实现已通过 `getElementById` 查找，两个场景使用不同 ID，需确认）。

由于 index.html 的聊天场景中 `chatStream` ID 与 test_page.html 相同，`addChatMessage` 不需要修改。

- [ ] **Step 3: 验证拨号流程在 index.html 中正常**

```bash
cd main/demo-web && python -m http.server 8006
```

打开 `http://localhost:8006/index.html`:
- 孵化完成后进入聊天界面
- 点击拨号按钮能正常打开设置
- 连接成功后状态显示正确

- [ ] **Step 4: Commit**

```bash
git add main/demo-web/js/ui/controller.js
git commit -m "fix: sync connection status UI for hatch demo page"
```

---

### Task 12: 最终验证和清理

- [ ] **Step 1: 确认 test_page.html 未被修改**

```bash
git diff HEAD -- main/demo-web/test_page.html
```
Expected: 无输出（test_page.html 未被修改）

- [ ] **Step 2: 确认所有新文件已创建**

```bash
ls -la main/demo-web/index.html main/demo-web/css/hatch.css main/demo-web/css/rabbit.css \
  main/demo-web/js/hatch-app.js main/demo-web/js/hatch/hatch-manager.js \
  main/demo-web/js/hatch/egg.js main/demo-web/js/hatch/hatching.js \
  main/demo-web/js/hatch/cracking.js main/demo-web/js/hatch/birth.js \
  main/demo-web/js/rabbit/rabbit.js
```
Expected: 所有文件存在

- [ ] **Step 3: 完整回归测试**

1. `test_page.html` 功能不变（拨号、录音、聊天、MCP）
2. `index.html` 完整孵化流程正常
3. 两个页面可独立访问，互不影响

- [ ] **Step 4: Final Commit**

```bash
git add -A main/demo-web/
git commit -m "feat: complete AI pet hatch demo with full hatching flow"
```
