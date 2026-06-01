---
name: stop-web
description: Stop the web management console (main/manager-web/). Kills npm/node processes and frees port 8001.
---

# Stop Web Service

Stop the Vue.js web management console and free port 8001.

## Steps

1. Find web service processes:
   ```bash
   ps aux | grep -E 'main/manager-web.*(npm|node).*serve' | grep -v grep
   ```
2. Find processes on port 8001:
   ```bash
   lsof -ti:8001
   ```
3. If processes found, graceful kill first (`kill PID`), wait 2 seconds, check again. If still running, force kill (`kill -9 PID`)
4. Confirm all processes stopped, re-check port occupancy
5. Output result: show stopped PIDs, or "Web service is not running" if none found
