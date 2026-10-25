---
name: clean-logs
description: 清理项目日志目录的旧日志。覆盖 main/manager-api/logs/、main/xiaozhi-server/logs/、.sandcastle/logs/。默认保留最近 7 天。rotated 文件（带 .YYYY-MM-DD.N.log 后缀）按文件名日期判断；.sandcastle/logs/ 按 mtime 判断。活跃日志（api.log/app.log/error.log 等无日期后缀文件）和 .sandcastle batch 状态文件（batch-*.json）永远跳过。用户说"清理日志"/"删除旧日志"/"clean logs"/"日志目录太大"/"释放日志空间"/"清理 sandcastle 日志"时使用。执行前先 dry-run 列出待删文件给用户确认，再实际删除。
---

# Clean Logs

清理以下三处旧日志：

- `main/manager-api/logs/`
- `main/xiaozhi-server/logs/`
- `.sandcastle/logs/`

## 保留策略

- **按天数保留**：默认最近 **7 天**。用户可通过参数覆盖（如 "保留 3 天"）。
- **活跃文件跳过**：`api.log`、`app.log`、`error.log`、`manager-api.log`、`xiaozhi-esp32-api.log` 等无日期后缀文件不动 —— 应用可能正在写入。
- **Rotated 文件识别**（manager-api / xiaozhi-server）：文件名匹配 `*.<YYYY-MM-DD>.<N>.log`，按**文件名内嵌日期**判断，不依赖 mtime（mtime 会被压缩/复制改变）。
- **Sandcastle 日志识别**（.sandcastle/logs/）：文件名没日期，按 **mtime** 判断。包括：
  - 顶层 `*-verify.log` / `sandcastle-*-implementer.log` / `sandcastle-*-reviewer.log` 等
  - 顶层 batch 子目录 `<timestamp>-<sha>/`（整棵删）
- **batch-*.json 永远跳过**：是 `loadBatch()` 的 resume 状态，删了会让未完成的 batch 重头跑。
- **非日志文件跳过**：`.DS_Store`、`app.pid`、未识别的子目录不动。

## 执行流程

1. **Dry-run 列出**：先扫所有目录，列出待删文件及大小、总释放空间。**不删任何东西**。
2. **报告并确认**：把清单给用户看，等明确确认（"删"/"确认"/"yes"）再执行。
3. **执行删除**：调 `scripts/clean_logs.py --days <N> --apply`。
4. **汇报结果**：删了几个、释放多少、剩余多少。

## 用法

```bash
# Dry-run（默认）
python3 .claude/skills/clean-logs/scripts/clean_logs.py --days 7

# 实际删除
python3 .claude/skills/clean-logs/scripts/clean_logs.py --days 7 --apply

# 额外加 rotated 目录
python3 .claude/skills/clean-logs/scripts/clean_logs.py --days 7 --dir main/manager-web/logs
```

## 边界

- 目录不存在不报错，记 0 个文件继续
- 删除失败的文件单独列出，不影响其他
- rotated 目录只扫顶层不递归；.sandcastle/logs 的 batch 子目录整棵删
- **不在 batch 跑期间执行**：running batch 的 verify/agent log 仍在写，mtime 是新不会被删，但并发删除仍可能踩到正在写入的文件。执行前 `ps aux | grep sandcastle` 确认没在跑，或问用户。
