// 重截 nfc-claim 页：强制 READY 可领取态（绕过后端 preview，注入示例数据）
const automator = require('miniprogram-automator');
const { join } = require('node:path');
const OUT = join(__dirname, '../../screenshots/02-蛋宝宝/07-NFC领取.png');
const WS = 'ws://127.0.0.1:9420';

(async () => {
  const mp = await automator.connect({ wsEndpoint: WS });
  console.log('已连接');
  await mp.reLaunch('/pages/nfc-claim/nfc-claim');
  await new Promise((r) => setTimeout(r, 2500));
  const page = await mp.currentPage();
  const before = await page.data();
  console.log('加载后态:', before.state, before.errorMessage);
  // 强制覆盖为 READY 可领取态
  await page.setData({
    state: 'READY',
    productName: '蛋宝宝·星空款',
    petType: '原型·星空蛋',
    statusLabel: '可以领取',
    claimRef: 'DEMO-CLAIM-001',
    hasPhoneBound: true,
    errorMessage: ''
  });
  await new Promise((r) => setTimeout(r, 1000));
  const after = await page.data();
  console.log('覆盖后态:', after.state, after.productName, after.statusLabel);
  await mp.screenshot({ path: OUT });
  console.log('OK ->', OUT);
  await mp.disconnect();
})().catch((e) => { console.error(e); process.exit(1); });
