// 重截 welcome 页：mock 阻止跳转 + 清存储，确保停留在欢迎页渲染按钮
const automator = require('miniprogram-automator');
const { join } = require('node:path');
const OUT = join(__dirname, '../../screenshots/02-蛋宝宝/01-欢迎授权.png');
const WS = 'ws://127.0.0.1:9420';

(async () => {
  const mp = await automator.connect({ wsEndpoint: WS });
  console.log('已连接');
  // 1. 清存储 + mock 阻止所有跳转（switchTab/navigateTo/reLaunch 在页面内调用时变 no-op）
  await mp.evaluate(() => { try { wx.clearStorageSync(); } catch (e) {} });
  await mp.mockWxMethod('switchTab', '');
  await mp.mockWxMethod('navigateTo', '');
  await mp.mockWxMethod('redirectTo', '');
  console.log('存储清空 + 跳转已 mock');
  // 2. reLaunch welcome（不被 mock，因为用 automator 的 reLaunch 而非页面内 wx.reLaunch）
  await mp.reLaunch('/pages/welcome/welcome');
  await new Promise((r) => setTimeout(r, 3000));
  // 3. 校验
  const page = await mp.currentPage();
  const data = await page.data();
  console.log('当前页:', page.path, 'ready=', data.ready);
  if (!data.ready) {
    await page.setData({ ready: true, hasPendingInvite: false });
    await new Promise((r) => setTimeout(r, 1000));
  }
  // 4. 恢复 mock（避免影响后续）
  await mp.restoreWxMethod('switchTab');
  await mp.restoreWxMethod('navigateTo');
  await mp.restoreWxMethod('redirectTo');
  await mp.screenshot({ path: OUT });
  console.log('OK ->', OUT);
  await mp.disconnect();
})().catch((e) => { console.error(e); process.exit(1); });
