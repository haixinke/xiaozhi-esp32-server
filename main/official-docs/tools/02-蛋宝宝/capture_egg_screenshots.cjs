// egg-miniprogram 小程序页面批量截图（软著操作手册素材）
// 用法: node capture_egg_screenshots.cjs
// 依赖: miniprogram-automator + 微信开发者工具 cli auto 已启动
// 输出: main/official-docs/screenshots-egg/*.png
const automator = require('miniprogram-automator');
const { mkdirSync } = require('node:fs');
const { join } = require('node:path');

const OUT_DIR = join(__dirname, '../../screenshots/02-蛋宝宝');
const WS = 'ws://127.0.0.1:9420';

// 页面按用户流程排序（操作手册叙事顺序）
const PAGES = [
  ['pages/welcome/welcome', '01-欢迎授权'],
  ['pages/home/home', '02-首页'],
  ['pages/hatch-guide/hatch-guide', '03-孵化引导'],
  ['pages/nickname/nickname', '04-取名'],
  ['pages/wish/wish', '05-心愿'],
  ['pages/lesson/lesson', '06-课程'],
  ['pages/nfc-claim/nfc-claim', '07-NFC领取'],
  ['pages/add-device/add-device', '08-添加设备'],
  ['pages/collection-card/collection-card', '09-收藏卡'],
  ['pages/album/album', '10-相册'],
  ['pages/chat/chat', '11-聊天'],
  ['pages/chat-settings/chat-settings', '12-聊天设置'],
  ['pages/my/my', '13-我的'],
  ['pages/profile/profile', '14-资料'],
  ['pages/account/account', '15-账号'],
  ['pages/settings/settings', '16-设置'],
  ['pages/age-range/age-range', '17-年龄段'],
  ['pages/invite-codes/invite-codes', '18-邀请码'],
  ['pages/feedback/feedback', '19-反馈'],
  ['pages/help/help', '20-帮助'],
  ['pages/deregister/deregister', '21-注销'],
  ['pages/privacy/privacy', '22-隐私'],
  ['pages/terms/terms', '23-条款'],
];

mkdirSync(OUT_DIR, { recursive: true });

(async () => {
  let miniProgram;
  try {
    console.log('连接自动化端口', WS);
    miniProgram = await automator.connect({ wsEndpoint: WS });
    console.log('已连接');
  } catch (e) {
    console.error('连接失败:', e.message);
    process.exit(1);
  }

  for (const [route, name] of PAGES) {
    try {
      await miniProgram.reLaunch('/' + route);
      await new Promise((r) => setTimeout(r, 2000));
      await miniProgram.screenshot({ path: join(OUT_DIR, name + '.png') });
      console.log('OK', name);
    } catch (e) {
      console.log('FAIL', name, (e.message || '').split('\n')[0]);
    }
  }

  await miniProgram.disconnect();
  console.log('完成，输出目录:', OUT_DIR);
})().catch((e) => {
  console.error('致命错误:', e);
  process.exit(1);
});
