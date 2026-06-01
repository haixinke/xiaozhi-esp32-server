---
name: stop-api
description: Stop the Java backend service (main/manager-api/). Kills mvn/java processes and frees port 8002.
---

# Stop Backend API Service

Stop the Java Spring Boot backend and free port 8002.

## Steps

1. Find backend service processes:
   ```bash
   ps aux | grep -E 'main/manager-api.*(mvn|java)' | grep -v grep
   ```
2. Find processes on port 8002:
   ```bash
   lsof -ti:8002
   ```
3. If processes found, graceful kill first (`kill PID`), wait 3 seconds (Spring Boot needs time for graceful shutdown), check again. If still running, force kill (`kill -9 PID`)
4. Confirm all processes stopped, re-check port occupancy
5. Output result: show stopped PIDs, or "Backend service is not running" if none found
