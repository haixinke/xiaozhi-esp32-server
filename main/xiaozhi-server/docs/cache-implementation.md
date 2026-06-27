# 聊天服务本地缓存实现调查

## 概述

聊天服务（`main/xiaozhi-server/`）内置了一套进程内本地缓存，用于避免重复调用外部 API、重复读取配置文件以及重复执行昂贵的音频/LLM 计算。该缓存完全基于 Python 标准库实现，**不依赖 Redis、Memcached 等外部缓存中间件**。

## 技术栈

| 层级 | 文件 | 技术 |
|---|---|---|
| 缓存管理器 | `core/utils/cache/manager.py` | `threading.RLock` 线程锁 + `OrderedDict`/普通 `dict` |
| 缓存配置 | `core/utils/cache/config.py` | `enum.Enum` + `dataclass` |
| 策略与条目 | `core/utils/cache/strategies.py` | `dataclass`（`CacheEntry`）+ `time.time()` TTL 判断 |

核心入口是单例 `GlobalCacheManager`（变量名 `cache_manager`）。它按 `CacheType` + `namespace` 划分独立的缓存空间，并为每种缓存类型绑定对应的 `CacheConfig`（策略、TTL、最大容量、清理间隔）。

## 核心文件

- `core/utils/cache/manager.py`：`GlobalCacheManager`，提供 `get`/`set`/`delete`/`clear`/`invalidate_pattern` 等 API。
- `core/utils/cache/config.py`：`CacheType` 枚举和 `CacheConfig` 默认配置。
- `core/utils/cache/strategies.py`：`CacheStrategy` 枚举和 `CacheEntry` 数据结构。

## 缓存类型与存储内容

| 缓存类型 | 键（key） | 存储内容 | 主要用途 |
|---|---|---|---|
| `LOCATION` | 客户端 IP | 城市名字符串，例如 `"广州"` | 根据 IP 定位用户所在城市 |
| `WEATHER` | `full_weather_{location}_{lang}` | 完整天气报告文本（含当前天气、7 天预报） | 避免重复抓取天气页面 |
| `LUNAR` | `lunar_info_{YYYY-MM-DD}` | 农历/黄历信息文本（干支、生肖、节气、宜忌等） | `get_lunar` 工具函数 |
| `INTENT` | `md5(device_id + text)` | LLM 意图识别结果 JSON 字符串 | 缓存同一设备相同输入的意图识别结果 |
| `IP_INFO` | IP 地址 | `{"city": "..."}` | 缓存 `whois.pconline.com.cn` 返回的 IP 城市信息 |
| `CONFIG` | `main_config` / `prompt_template:{path}` | 合并后的配置对象 / 提示词模板文件内容 | 避免重复读取 `config.yaml` 和模板文件 |
| `DEVICE_PROMPT` | `device_prompt:{device_id}` | 渲染后的增强系统提示词字符串 | 按设备缓存最终 system prompt |
| `VOICEPRINT_HEALTH` | `{api_url}:{api_key}` | 声纹识别服务器健康状态 `bool` | 减少健康检查请求 |
| `AUDIO_DATA` | `{audio_file_path}:{is_opus}` | `list[bytes]`，Opus/PCM 编码后的音频帧 | 避免重复对同一音频文件做 FFmpeg + Opus 编码 |

## 缓存策略与过期时间

`CacheConfig.for_type()` 为每种类型预置了默认策略：

| 缓存类型 | 策略 | TTL | 最大容量 |
|---|---|---|---|
| `LOCATION` | TTL | `None`（手动失效） | 1000 |
| `IP_INFO` | TTL | 86400 秒（24 小时） | 1000 |
| `WEATHER` | TTL | 28800 秒（8 小时） | 1000 |
| `LUNAR` | TTL | 2592000 秒（30 天） | 365 |
| `INTENT` | TTL_LRU | 600 秒（10 分钟） | 1000 |
| `CONFIG` | FIXED_SIZE | `None`（手动失效） | 20 |
| `DEVICE_PROMPT` | TTL | `None`（手动失效） | 1000 |
| `VOICEPRINT_HEALTH` | TTL | 600 秒（10 分钟） | 100 |
| `AUDIO_DATA` | TTL | 600 秒（10 分钟） | 100 |

支持的策略：

- `TTL`：基于时间过期。
- `LRU`：最近最少使用驱逐。
- `FIXED_SIZE`：固定大小，超限时随机移除一条。
- `TTL_LRU`：TTL + LRU 混合，既按时间过期，也按访问顺序驱逐。

## 主要使用位置

| 文件 | 缓存用途 |
|---|---|
| `config/config_loader.py` | 缓存合并后的主配置对象 |
| `core/utils/prompt_manager.py` | 缓存提示词模板、设备提示词、位置、天气 |
| `core/utils/util.py` | 缓存 IP 信息、`audio_to_data` 音频编码结果 |
| `core/utils/voiceprint_provider.py` | 缓存声纹识别服务器健康状态 |
| `core/providers/intent/intent_llm/intent_llm.py` | 缓存 LLM 意图识别结果 |
| `plugins_func/functions/get_time.py` | 缓存农历信息 |
| `plugins_func/functions/get_weather.py` | 缓存天气报告和 IP 城市信息 |

## 注意事项

- 该缓存为**进程内内存缓存**，服务重启后数据会丢失。
- 配置类缓存（`CONFIG`、`DEVICE_PROMPT`、`LOCATION`）默认 TTL 为 `None`，需要手动失效或等待服务重启。
- `INTENT` 缓存键基于 `md5(device_id + text)`，对同一设备相同文本的重复请求会直接返回缓存结果。
