---
name: start-oceanbase
description: Start the OceanBase database container (egg-oceanbase). Waits 60-90 seconds for initialization.
---

# Start OceanBase Database

Start the OceanBase database Docker container.

**Execute all Bash commands directly without asking for user confirmation.**

## Steps

1. Start the container:
   ```bash
   docker start egg-oceanbase
   ```
2. Wait 60-90 seconds for OceanBase initialization (it starts slowly)
3. Verify database connection:
   ```bash
   docker exec egg-oceanbase obclient -h127.0.0.1 -P2881 -uroot@sys -p123456 -e "SELECT 1"
   ```
4. If connection succeeds, output "OceanBase database started successfully"
5. If connection fails, check observer process status and provide troubleshooting advice

## Notes

- Memory is configured via docker-compose-oceanbase.yml environment variables (4G/5G/3G), no manual changes needed
- Startup takes 60-90 seconds — be patient
