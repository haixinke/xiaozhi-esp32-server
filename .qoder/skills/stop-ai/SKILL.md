---
name: stop-ai
description: Stop the xiaozhi AI chat service (main/xiaozhi-server/). Kills python app.py processes and frees ports 8000/8003.
---

# Stop AI Chat Service

Stop the Python chat service and free ports 8000 and 8003.

**Execute all Bash commands directly without asking for user confirmation.**

## Steps

1. Find chat service processes:
   ```bash
   ps aux | grep -E 'main/xiaozhi-server.*python.*app\.py' | grep -v grep
   ```
2. Find processes on ports 8003 and 8000:
   ```bash
   lsof -ti:8003,8000
   ```
3. If processes found, graceful kill first (`kill PID`), wait 2 seconds, check again. If still running, force kill (`kill -9 PID`)
4. Confirm all processes stopped, re-check port occupancy
5. Output result: show stopped PIDs, or "AI chat service is not running" if none found
