// 重截收藏卡 + 相册页（后端已有内容）
const automator = require('miniprogram-automator');
const { join } = require('node:path');
const SHOTS = join(__dirname, '../../screenshots/02-蛋宝宝');
const WS = 'ws://127.0.0.1:9420';

const PAGES = [
  ['pages/collection-card/collection-card', '09-收藏卡'],
  ['pages/album/album', '10-相册'],
];

(async () => {
  const mp = await automator.connect({ wsEndpoint: WS });
  console.log('已连接');
  for (const [route, name] of PAGES) {
    try {
      await mp.reLaunch('/' + route);
      await new Promise((r) => setTimeout(r, 3000));
      await mp.screenshot({ path: join(SHOTS, name + '.png') });
      console.log('OK', name);
    } catch (e) {
      console.log('FAIL', name, (e.message || '').split('\n')[0]);
    }
  }
  await mp.disconnect();
  console.log('完成');
})().catch((e) => { console.error(e); process.exit(1); });
