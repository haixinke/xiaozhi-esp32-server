# Plan: 情景二视频动态选择

## 需求

情景二的视频 URL 当前硬编码为 `VIDEO_URLS[1]`（永远是 `baiyueguang_tianmei2.mp4`）。
需要改为和情景一相同的逻辑：根据用户选择的角色（charId）和音色（voiceId）动态构建视频 URL。

## URL 规律确认

用户提供的 URL 与情景一完全对称，只是 `/one/` 变为 `/two/`：

- 情景一: `{BASE}/{charId}/one/{charId}_{style}.mp4`
- 情景二: `{BASE}/{charId}/two/{charId}_{style}.mp4`

## 修改方案（1 个文件，2 处改动）

### 文件: `main/miniprogram/pages/memory-anchor/memory-anchor.js`

#### 改动 1: 通用化 URL 构建函数（第 16-21 行）

将 `buildScenarioOneUrl` 改为 `buildScenarioUrl(charId, voiceId, scenario)`，接受情景编号参数：

```javascript
function buildScenarioUrl(charId, voiceId, scenario) {
  var styles = Object.values(codes.VOICE_STYLES);
  var style = codes.VOICE_STYLES[voiceId] || (styles.indexOf(voiceId) >= 0 ? voiceId : '');
  if (!charId || !style) return VIDEO_URLS[scenario === 2 ? 1 : 0];
  var folder = scenario === 2 ? 'two' : 'one';
  return codes.VIDEO_BASE_URL + '/' + charId + '/' + folder + '/' + charId + '_' + style + '.mp4';
}
```

#### 改动 2: 情景二切换时动态构建 URL（第 77 行）

`onRelationSelect` 中把 `VIDEO_URLS[1]` 替换为动态构建：

```javascript
// 旧: videoUrl: VIDEO_URLS[1],
// 新:
var app = getApp();
var flow = app.globalData.destinyFlow || {};
videoUrl: buildScenarioUrl(flow.charId, flow.voiceId, 2),
```

#### 改动 3: 更新 onLoad 中的调用（第 43 行）

```javascript
// 旧: var videoUrl = buildScenarioOneUrl(flow.charId, flow.voiceId);
// 新:
var videoUrl = buildScenarioUrl(flow.charId, flow.voiceId, 1);
```

## 风险

- **低**: URL 构建逻辑与情景一完全一致，只是 `/one/` → `/two/`，无新逻辑引入
- **低**: fallback 仍使用 `VIDEO_URLS` 数组中的硬编码 URL

## 不改的内容

- `companion-codes.js` — 无需修改，已有的 `VOICE_STYLES`、`VIDEO_BASE_URL` 完全够用
- WXML / WXSS — 无 UI 变更
