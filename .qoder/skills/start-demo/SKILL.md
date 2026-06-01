---
name: start-demo
description: Start the demo web project (main/demo-web/) on port 8006.
---

# Start Demo Service

Start the demo web project at `main/demo-web/` (port 8006).

## Steps

1. Check for running demo processes:
   ```bash
   ps aux | grep -E 'main/demo-web.*vite' | grep -v grep
   ```
   Also check port 8006: `lsof -ti:8006`
   - Kill any running instance and port occupants
2. Start in background:
   ```bash
   cd /Users/minwang/codes/github/xiaozhi-esp32-server/main/demo-web && mkdir -p logs && nohup npx vite --port 8006 > logs/demo.log 2>&1 &
   ```
3. Wait 2-3 seconds, output `logs/demo.log` last 10 lines
4. Confirm success if log contains `Local:` and `http://localhost:8006`
