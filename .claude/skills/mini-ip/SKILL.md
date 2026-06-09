---
name: mini-ip
description: Update miniprogram BASE_URL to local machine IP. Use when switching to local development, running `/mini-ip`, or user mentions changing miniprogram API address to local IP.
---

## Target File

`main/miniprogram/utils/request.js` — line containing `BASE_URL`.

## Steps

1. Detect local IP:
   ```
   ipconfig getifaddr en0 || ipconfig getifaddr en1
   ```
   If both fail, ask user for IP.

2. Read `main/miniprogram/utils/request.js` and find the `BASE_URL` line.

3. Replace the IP (keep port and path unchanged):
   ```
   const BASE_URL = 'http://<NEW_IP>:8002/xiaozhi';
   ```

4. Report the change: old IP → new IP.
