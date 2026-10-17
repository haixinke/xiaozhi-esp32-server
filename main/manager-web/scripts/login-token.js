#!/usr/bin/env node
/**
 * 智控台免浏览器登录：SM2 加密 captcha+password，调 /user/login 取 token。
 * 用法: node scripts/login-token.js <captchaId> <captchaText>
 * 凭证从 .env.test 读 (MANAGER_WEB_USER / MANAGER_WEB_PASS)，SM2 公钥实时从
 * /user/pub-config 拉取。成功时把 token 追加/更新到 .env.test 的 MANAGER_WEB_TOKEN。
 */
const fs = require('fs');
const path = require('path');
const { sm2 } = require('sm-crypto');

const ROOT = path.resolve(__dirname, '..');
const ENV_FILE = path.join(ROOT, '.env.test');
const API = 'http://localhost:8002/xiaozhi';

function loadEnv() {
  const env = {};
  for (const line of fs.readFileSync(ENV_FILE, 'utf8').split('\n')) {
    const m = line.match(/^([A-Z_]+)=(.*)$/);
    if (m) env[m[1]] = m[2];
  }
  return env;
}

function saveToken(token) {
  let content = fs.readFileSync(ENV_FILE, 'utf8');
  const line = `MANAGER_WEB_TOKEN=${JSON.stringify(token)}`;
  if (/^MANAGER_WEB_TOKEN=/m.test(content)) {
    content = content.replace(/^MANAGER_WEB_TOKEN=.*/m, line);
  } else {
    content = content.trimEnd() + '\n' + line + '\n';
  }
  fs.writeFileSync(ENV_FILE, content, { mode: 0o600 });
}

async function main() {
  const [captchaId, captchaText] = process.argv.slice(2);
  if (!captchaId || !captchaText) {
    console.error('usage: login-token.js <captchaId> <captchaText>');
    process.exit(1);
  }
  const env = loadEnv();
  const username = env.MANAGER_WEB_USER;
  const password = env.MANAGER_WEB_PASS;
  if (!username || !password) {
    console.error('.env.test 缺 MANAGER_WEB_USER / MANAGER_WEB_PASS');
    process.exit(1);
  }

  const cfgRes = await fetch(`${API}/user/pub-config`);
  const cfg = await cfgRes.json();
  const publicKey = cfg.data && cfg.data.sm2PublicKey;
  if (!publicKey) {
    console.error('pub-config 未返回 sm2PublicKey');
    process.exit(1);
  }

  // 与 src/utils/index.js sm2Encrypt 一致：doEncrypt(明文, 公钥, 1)，结果加 "04" 前缀
  const encrypted = '04' + sm2.doEncrypt(captchaText + password, publicKey, 1);

  const loginRes = await fetch(`${API}/user/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: encrypted, captchaId }),
  });
  const body = await loginRes.json();
  if (body.code !== 0) {
    console.error(`login failed: code=${body.code} msg=${body.msg}`);
    process.exit(2);
  }
  // 前端 localStorage 存的是 JSON.stringify(data.data)
  const tokenStr = JSON.stringify(body.data);
  saveToken(tokenStr);
  console.log('OK token saved to .env.test MANAGER_WEB_TOKEN');
}

main().catch((e) => { console.error(e.message || e); process.exit(1); });
