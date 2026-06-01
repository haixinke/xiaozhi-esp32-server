---
name: start-api
description: Start the Java backend service (main/manager-api/). Builds with Maven and launches Spring Boot on port 8002.
---

# Start Backend API Service

Start the Java Spring Boot backend at `main/manager-api/` (port 8002).

## Steps

1. Check for running backend processes:
   ```bash
   ps aux | grep -E 'main/manager-api.*(mvn|java)' | grep -v grep
   ```
   Also check port 8002: `lsof -ti:8002`
   - Kill any running instance and port occupants
2. Compile and package:
   ```bash
   cd /Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-api && mvn clean package -DskipTests
   ```
3. Check build result — if failed, stop and output error
4. Start in background:
   ```bash
   mkdir -p /Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-api/logs && nohup java -jar /Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-api/target/xiaozhi-esp32-api-*.jar > /Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-api/logs/api.log 2>&1 &
   ```
5. Wait 5-8 seconds (Spring Boot starts slowly), output `logs/api.log` last 20 lines
6. Confirm success if log contains `Started AdminApplication`
