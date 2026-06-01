---
name: start-ai
description: Start the xiaozhi AI chat service (main/xiaozhi-server/). Checks venv, kills port 8003 conflicts, and launches app.py in background.
---

# Start AI Chat Service

Start the Python chat service at `main/xiaozhi-server/` (ports 8000 WebSocket + 8003 HTTP).

**Execute all Bash commands directly without asking for user confirmation.**

## Prerequisites

A virtual environment must exist at `main/xiaozhi-server/.venv/`. If missing, stop and output:
`Error: Virtual environment not found. Run: python -m venv .venv`

## Steps

1. Check if `.venv/` exists under `main/xiaozhi-server/`
   - If not, output error and stop
2. Check port 8003 occupancy: `lsof -ti:8003` — kill any process found
3. Check for running chat service: `ps aux | grep -E 'main/xiaozhi-server.*python.*app\.py' | grep -v grep`
   - Kill any running instance
4. Ensure logs directory exists:
   ```bash
   cd /Users/minwang/codes/github/xiaozhi-esp32-server/main/xiaozhi-server && mkdir -p logs
   ```
5. Start in background using venv Python:
   ```bash
   nohup .venv/bin/python app.py > logs/app.log 2>&1 &
   ```
6. Wait 2-3 seconds, then output `logs/app.log` last 15 lines to confirm startup
