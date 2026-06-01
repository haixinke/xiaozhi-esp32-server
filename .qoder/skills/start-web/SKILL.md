---
name: start-web
description: Start the web management console (main/manager-web/) on port 8001.
---

# Start Web Service

Start the Vue.js web management console at `main/manager-web/` (port 8001).

## Steps

1. Check for running web processes:
   ```bash
   ps aux | grep -E 'main/manager-web.*(npm|node).*serve' | grep -v grep
   ```
   Also check port 8001: `lsof -ti:8001`
   - Kill any running instance and port occupants
2. Start in background:
   ```bash
   cd /Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-web && mkdir -p logs && nohup npm run serve > logs/web.log 2>&1 &
   ```
3. Wait 5-8 seconds (first build is slow), output `logs/web.log` last 20 lines
4. Confirm success if log contains `App running at` or `Compiled successfully`
