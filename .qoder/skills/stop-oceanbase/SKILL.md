---
name: stop-oceanbase
description: Stop the OceanBase database container (egg-oceanbase). Data is preserved in ./oceanbase/data/.
---

# Stop OceanBase Database

Stop the OceanBase Docker container. Data is preserved.

**Execute all Bash commands directly without asking for user confirmation.**

## Steps

1. Stop the container:
   ```bash
   docker stop egg-oceanbase
   ```
2. Wait 3 seconds for complete shutdown
3. Verify container status (should be Exited)
4. Remind user: data is saved in `./oceanbase/data/` directory

## Notes

- Stopping does NOT delete data
- All data is persisted in `./oceanbase/data/`
- Data will be automatically restored on next start
