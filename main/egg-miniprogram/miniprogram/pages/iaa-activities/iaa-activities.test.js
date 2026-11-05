const assert = require('assert');
const Module = require('module');

const originalLoad = Module._load;
let definition;
const calls = [];
let currentPet = null;
let gamesResponse = async () => [
  { id: 1, gameType: 'LISTEN_TO_YOU', gameTypeName: '听你说', icon: 'https://example.com/listen.png', description: '说说今天', earnStars: 8, consumeStars: 0 },
  { id: 2, gameType: 'TRAVEL', gameTypeName: '旅行', earnStars: 0, consumeStars: 20 }
];
// Java Long 经后端全局序列化后为字符串，页面仍应显示星星余额。
let balanceResponse = async () => ({ balance: '38' });
global.Page = (value) => { definition = value; };
global.wx = {
  navigateTo(options) { calls.push(['navigate', options.url]); },
  showToast(options) { calls.push(['toast', options.title]); }
};
Module._load = function (request, parent) {
  if (request === '../../utils/pet-store') {
    return { getPetSnapshot: () => ({ pet: currentPet }) };
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
