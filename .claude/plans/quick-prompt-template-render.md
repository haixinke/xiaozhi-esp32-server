# 方案 A 改造计划：快速提示词模板化

## 目标

让 `get_quick_prompt` 也基于 `agent-base-prompt.txt` 模板渲染，使首次回复（快速提示词窗口期）获得 `anti_ai_smell` / `conversation_rhythm` / `tts_format_constraints` 等静态行为约束，缩小与增强提示词阶段的质量差距。

## 已确认事实

- `get_quick_prompt` (`prompt_manager.py:105-126`) 当前原样返回 `user_prompt`，不渲染模板
- `build_enhanced_prompt` (`prompt_manager.py:226-292`) 做完整 Jinja2 渲染
- `<memory>` 不是模板变量，模板里是空标签，由 `dialogue.py:138-140` 运行时 regex 注入 —— 方案 A 无空记忆困惑
- `{{current_time}}` 由 `dialogue.py:132-134` 运行时替换为实际时间 —— 方案 A 保持字面量
- `connection.py:562` 调用 `get_quick_prompt(user_prompt)`，此时 `self.device_id` (266 行已赋值)、`self.client_ip` (259 行已赋值) 可用
- `prompt_manager.py` 已 `from jinja2 import Template`，`EMOJI_List` 定义在 26 行
- `connection.py:603` 已有 `emoji_enabled=(self.features or {}).get("emoji", True)` 模式可复用

## 改动点

### 1. `prompt_manager.py` — 改造 `get_quick_prompt` (105-126 行)

扩展签名 + 复用模板渲染（动态变量填空/占位）：

```python
def get_quick_prompt(self, user_prompt: str, device_id: str = None, emoji_enabled: bool = True) -> str:
    """快速获取系统提示词（基于模板渲染静态约束，动态变量填空）"""
    device_cache_key = f"device_prompt:{device_id}"
    cached_device_prompt = self.cache_manager.get(
        self.CacheType.DEVICE_PROMPT, device_cache_key
    )
    if cached_device_prompt is not None:
        self.logger.bind(tag=TAG).debug(f"使用设备 {device_id} 的缓存提示词")
        return cached_device_prompt

    # 模板未加载，兜底返回原 user_prompt
    if not self.base_prompt_template:
        if device_id:
            self.cache_manager.set(self.CacheType.DEVICE_PROMPT, device_cache_key, user_prompt)
        self.logger.bind(tag=TAG).debug(f"模板未加载，使用原始提示词: {user_prompt}")
        return user_prompt

    try:
        today_date, today_weekday, lunar_date = self._get_current_time_info()
        language = (
            self.config.get("TTS", {})
            .get(self.config.get("selected_module", {}).get("TTS", ""), {})
            .get("language") or "中文"
        )
        template = Template(self.base_prompt_template)
        quick_prompt = template.render(
            base_prompt=user_prompt,
            current_time="{{current_time}}",   # 保持字面量，运行时由 dialogue.py 替换
            today_date=today_date,             # 时间不需 I/O，填真值
            today_weekday=today_weekday,
            lunar_date=lunar_date,
            local_address="未知",               # 不查 IP，填占位（保持零延迟）
            weather_info="未知",               # 不查天气 API
            emojiList=EMOJI_List,
            device_id=device_id,
            client_ip=None,
            dynamic_context="",                # 不查数据库
            language=language,
            emoji_enabled=emoji_enabled,
        )
        if device_id:
            self.cache_manager.set(self.CacheType.DEVICE_PROMPT, device_cache_key, quick_prompt)
        self.logger.bind(tag=TAG).info(
            f"快速提示词模板渲染成功，长度: {len(quick_prompt)}"
        )
        return quick_prompt
    except Exception as e:
        self.logger.bind(tag=TAG).error(f"快速提示词渲染失败，回退原始提示词: {e}")
        return user_prompt
```

### 2. `connection.py:562` — 调用点传参

```python
prompt = self.prompt_manager.get_quick_prompt(
    user_prompt,
    device_id=self.device_id,
    emoji_enabled=(self.features or {}).get("emoji", True),
)
```

## 不改动

- `agent-base-prompt.txt` 不动
- `build_enhanced_prompt` 不动
- `dialogue.py` 的运行时注入（`<memory>`、`{{current_time}}`）不动

## 验证

### 单元测试（新建 `main/xiaozhi-server/test/test_prompt_manager.py`）

构造 `PromptManager`，调 `get_quick_prompt`，断言：
- 返回含 `<anti_ai_smell>`、`<tts_format_constraints>`、`<conversation_rhythm>` 段
- `{{base_prompt}}` 已替换为传入的 `user_prompt`（角色人设）
- `<memory></memory>` 为空（运行时注入，非模板变量）
- `{{current_time}}` 保持字面量（未被替换）
- `local_address` / `weather_info` 显示「未知」
- `emoji_enabled=False` 时返回不含 Emoji 白名单规则的 `tts_format_constraints` 变体
- `base_prompt_template` 为空时回退返回原 `user_prompt`

### 手动验证

用 `main/demo-web/` 模拟设备连接后立刻说话，验证首次回复：
- 无 Markdown（`**加粗**`、列表 `-`、代码块）
- 无括号动作描写（`[叹气]`、`(笑着说)`）
- 长度收敛到 1-2 句
- 与增强提示词阶段的回复风格一致

## 风险与处理

| 风险 | 等级 | 处理 |
|---|---|---|
| 缓存覆盖（快速与增强 prompt 共用 key） | LOW | 现有行为，增强 prompt 覆盖快速 prompt，非新问题 |
| `self.features` 在 562 行时未完全赋值 | LOW | `(self.features or {}).get("emoji", True)` 兜底 |
| Jinja2 渲染开销 | LOW | 有设备缓存，仅首次渲染 |
| 项目无 pytest 框架 | MEDIUM | 需自建测试目录与 `conftest.py`，或先靠手动验证 |

## 复杂度：LOW

- 改动 2 个文件、约 30 行
- 复用现有 `build_enhanced_prompt` 的渲染模式
- 无外部 I/O 新增，保持零延迟启动
