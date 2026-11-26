const assert = require('assert');
const Module = require('module');

const originalLoad = Module._load;
let definition;
const calls = [];
let currentPet = { id: 'hatched', hatchStatus: 'HATCHED' };
let otherPets = [currentPet];
let gamesResponse = async () => [
  { id: 1, gameType: 'LISTEN_TO_YOU', gameTypeName: '听你说', icon: 'https://example.com/listen.png', description: '说说今天', earnStars: 8, consumeStars: 0 },
  { id: 2, gameType: 'TRAVEL', gameTypeName: '旅行', earnStars: 0, consumeStars: 20 }
];
// Java Long 经后端全局序列化后为字符串，页面仍应显示星星余额。
let balanceResponse = async () => ({ balance: '38' });
global.Page = (value) => { definition = value; };
global.wx = {
  navigateTo(options) { calls.push(['navigate', options.url]); },
  navigateBack(options) { calls.push(['back', options]); if (options && options.fail) options.fail(); },
  switchTab(options) { calls.push(['tab', options.url]); if (options && options.fail) options.fail(); },
  showToast(options) { calls.push(['toast', options.title]); }
};
Module._load = function (request, parent) {
  if (request === '../../utils/phone-gate') return { withPhoneGate: (definition) => definition };
  if (request === '../../utils/pet-store') {
    return { getPetSnapshot: () => ({ pet: currentPet, pets: currentPet ? [currentPet, ...otherPets] : otherPets }) };
  }
  if (request === './request' && parent.filename.endsWith('-api.js')) {
    return { get(url, data) {
      calls.push([url, data]);
      if (url === '/app/game/list') return gamesResponse();
      if (url === '/star/balance') return balanceResponse();
      throw new Error(`Unexpected endpoint ${url}`);
    } };
  }
  return originalLoad.apply(this, arguments);
};
function page() {
  return {
    ...definition,
    data: JSON.parse(JSON.stringify(definition.data)),
    setData(change) { this.data = { ...this.data, ...change }; }
  };
}

(async () => {
  require('./iaa-activities');
  const view = page();
  // 未破壳或无宠物时直达本页被拦截：提示并返回首页，不发起活动请求；switchTab 失败兜底 navigateBack
  const countToasts = (title) => calls.filter(([kind, text]) => kind === 'toast' && text === title).length;
  const countTabs = () => calls.filter(([kind]) => kind === 'tab').length;
  otherPets = [];
  currentPet = { id: 'egg', hatchStatus: 'EGG' };
  await view.onShow();
  assert.strictEqual(countToasts('破壳后解锁百宝箱'), 1, 'unhatched shows unlock toast');
  assert.strictEqual(countTabs(), 1, 'unhatched switches back home');
  assert.ok(calls.some(([kind]) => kind === 'back'), 'switchTab failure falls back to navigateBack');
  assert.ok(!calls.some(([url]) => url === '/app/game/list'), 'unhatched entry must not load games');
  // 账号另有已破壳宠物时，提示切换而非死等当前蛋破壳
  otherPets = [{ id: 'pet-hatched', hatchStatus: 'HATCHED' }];
  await view.onShow();
  assert.strictEqual(countToasts('请切换到已破壳的蛋宝宝'), 1, 'multi-pet account prompted to switch');
  assert.strictEqual(countTabs(), 2);
  otherPets = [];
  currentPet = null;
  await view.onShow();
  assert.strictEqual(countTabs(), 3, 'no pet still switches back home');
  currentPet = { id: 'hatched', hatchStatus: 'HATCHED' };
  otherPets = [currentPet];
  await view.onShow();
  assert.ok(calls.some(([url, data]) => url === '/app/game/list' && data.entrance === 'ACTIVITY'));
  assert.strictEqual(view.data.playable.length, 2, 'all configured games remain visible');
  assert.strictEqual(view.data.playable[0].title, '听你说');
  assert.strictEqual(view.data.playable[0].description, '说说今天');
  assert.strictEqual(view.data.playable[0].ctaText, '赚 8');
  assert.strictEqual(view.data.playable[1].ctaText, '花 20');
  assert.strictEqual(view.data.starBalance, 38);
  assert.strictEqual(view.data.balanceError, false, 'serialized Long balance must not show a loading failure');
  assert.strictEqual(view.data.loading, false);

  for (const balance of [0, '0', 38]) {
    balanceResponse = async () => ({ balance });
    await view.refresh();
    assert.strictEqual(view.data.starBalance, Number(balance));
    assert.strictEqual(view.data.balanceError, false);
  }
  for (const balance of [null, undefined, '', ' ', true, {}, -1, '-1', 'invalid', 1.5, Infinity]) {
    balanceResponse = async () => ({ balance });
    await view.refresh();
    assert.strictEqual(view.data.balanceError, true, 'invalid balance must retain the retry state');
    assert.strictEqual(view.data.starBalance, '—', 'invalid balance must not appear as zero');
  }
  balanceResponse = async () => ({ balance: '38' });
  await view.refresh();
  assert.strictEqual(view.data.starBalance, 38);
  assert.strictEqual(view.data.balanceError, false, 'successful retry clears the failure');

  gamesResponse = async () => { throw new Error('network'); };
  await view.onShow();
  assert.strictEqual(view.data.error, '活动加载失败，请重试');
  assert.strictEqual(view.data.loading, false);
  assert.deepStrictEqual(view.data.playable, [], 'failed refresh must not retain stale games');
  gamesResponse = async () => [];
  await view.refresh();
  assert.strictEqual(view.data.error, '');
  assert.deepStrictEqual(view.data.playable, [], 'empty is a successful result');

  balanceResponse = async () => { throw new Error('balance unavailable'); };
  gamesResponse = async () => [{ id: 3, gameType: 'LISTEN_TO_YOU' }];
  await view.onShow();
  assert.strictEqual(view.data.playable.length, 1, 'balance failure must not hide playable games');
  assert.strictEqual(view.data.starBalance, '—');
  assert.strictEqual(view.data.balanceError, true);

  view.onTapRow({ currentTarget: { dataset: { id: 3 } } });
  assert.deepStrictEqual(calls.at(-1), ['navigate', '/pages/listen/listen']);

  // 外部游戏：进入 webview 页并完整还原目标地址（查询参数与锚点经路由编码传递）
  const externalUrl = 'https://games.example.com/play?level=3&lang=zh-CN#stage-2';
  gamesResponse = async () => [
    { id: 10, gameType: 'SOME_H5', gameTypeName: '跳跳乐', gameCategory: 'EXTERNAL', jumpUrl: externalUrl, earnStars: 0, consumeStars: 0, dailyLimit: 0 }
  ];
  await view.refresh();
  assert.strictEqual(view.data.playable[0].ctaText, '去玩', 'external games are free: 去玩 label');
  const beforeExternal = calls.length;
  view.onTapRow({ currentTarget: { dataset: { id: 10 } } });
  assert.strictEqual(calls.length, beforeExternal + 1, 'external tap issues exactly one navigation');
  assert.deepStrictEqual(calls.at(-1), ['navigate', `/pages/webview/webview?url=${encodeURIComponent(externalUrl)}`],
    'external tap opens webview with encoded url');
  assert.ok(!calls.some(([url]) => url === '/star' || String(url).includes('settle') || String(url).includes('consume')),
    'opening external games must not call any settlement api');

  // 重复打开外部游戏：每次仅导航，无扣费/计次
  view.onTapRow({ currentTarget: { dataset: { id: 10 } } });
  assert.deepStrictEqual(calls.at(-1), ['navigate', `/pages/webview/webview?url=${encodeURIComponent(externalUrl)}`],
    'reopening an external game navigates again without extra side effects');

  // 旧响应缺少 gameCategory：按内部游戏分流（未识别类型保留未开放提示）
  gamesResponse = async () => [{ id: 11, gameType: 'SOME_H5', jumpUrl: externalUrl }];
  await view.refresh();
  view.onTapRow({ currentTarget: { dataset: { id: 11 } } });
  assert.deepStrictEqual(calls.at(-1), ['toast', '暂未开放'],
    'missing category keeps legacy gameType routing even when jumpUrl is present');

  // 外部游戏缺少 jumpUrl：提示而非导航到空白页
  gamesResponse = async () => [{ id: 12, gameType: 'SOME_H5', gameCategory: 'EXTERNAL', jumpUrl: '' }];
  await view.refresh();
  view.onTapRow({ currentTarget: { dataset: { id: 12 } } });
  assert.strictEqual(calls.at(-1)[0], 'toast', 'external game without url must toast instead of navigating');

  // 外部游戏 jumpUrl 为纯空白字符：与缺失同义，提示并停留（共享 helper 统一 trim 语义）
  gamesResponse = async () => [{ id: 13, gameType: 'SOME_H5', gameCategory: 'EXTERNAL', jumpUrl: '   ' }];
  await view.refresh();
  const beforeBlank = calls.length;
  view.onTapRow({ currentTarget: { dataset: { id: 13 } } });
  assert.deepStrictEqual(calls.slice(beforeBlank), [['toast', '暂时打不开，请稍后再试']],
    'whitespace-only url must toast and stay put without navigating');

  // 外部游戏链接带首尾空白：trim 后导航（服务端落库前已 trim，此处兜底防御）
  gamesResponse = async () => [{ id: 14, gameType: 'SOME_H5', gameCategory: 'EXTERNAL', jumpUrl: `  ${externalUrl}  ` }];
  await view.refresh();
  view.onTapRow({ currentTarget: { dataset: { id: 14 } } });
  assert.deepStrictEqual(calls.at(-1), ['navigate', `/pages/webview/webview?url=${encodeURIComponent(externalUrl)}`],
    'url with surrounding whitespace navigates with the trimmed address');

  gamesResponse = async () => [{ id: 4, gameType: 'NEW_GAME' }];
  await view.refresh();
  view.onTapRow({ currentTarget: { dataset: { id: '4' } } });
  assert.deepStrictEqual(calls.at(-1), ['toast', '暂未开放']);
  const beforeInvalid = calls.length;
  view.onTapRow({ currentTarget: { dataset: { id: 999 } } });
  assert.strictEqual(calls.length, beforeInvalid, 'unknown row must not navigate');

  // 活动页可浏览不代表可以开局：必须由当前选中的已破壳宠物进入一起画。
  gamesResponse = async () => [{ id: 8, gameType: 'DRAW_TOGETHER' }];
  await view.refresh();
  currentPet = null;
  view.onTapRow({ currentTarget: { dataset: { id: 8 } } });
  assert.deepStrictEqual(calls.at(-1), ['toast', '请先领取蛋宝宝并陪它破壳']);
  currentPet = { id: 'egg', hatchStatus: 'EGG' };
  view.onTapRow({ currentTarget: { dataset: { id: 8 } } });
  assert.deepStrictEqual(calls.at(-1), ['toast', '请切换到已破壳的蛋宝宝，或陪它完成孵化']);
  currentPet = { id: 'hatched', hatchStatus: 'HATCHED' };
  view.onTapRow({ currentTarget: { dataset: { id: 8 } } });
  assert.deepStrictEqual(calls.at(-1), ['navigate', '/drawing/pages/draw/draw?source=activity']);

  let resolveOld;
  gamesResponse = () => new Promise(resolve => { resolveOld = resolve; });
  const oldLoad = view.onShow();
  gamesResponse = async () => [{ id: 5, gameType: 'CURRENT' }];
  await view.onShow();
  resolveOld([{ id: 6, gameType: 'OLD' }]);
  await oldLoad;
  assert.strictEqual(view.data.playable[0].gameType, 'CURRENT', 'older responses cannot replace current data');

  gamesResponse = () => new Promise(resolve => { resolveOld = resolve; });
  const finalLoad = view.onShow();
  view.onUnload();
  const lastData = view.data;
  resolveOld([{ id: 7 }]);
  await finalLoad;
  assert.strictEqual(view.data, lastData, 'no updates after unload');
  console.log('iaa-activities.test.js: ALL PASS');
})().catch((error) => { console.error(error); process.exitCode = 1; })
  .finally(() => { Module._load = originalLoad; });
