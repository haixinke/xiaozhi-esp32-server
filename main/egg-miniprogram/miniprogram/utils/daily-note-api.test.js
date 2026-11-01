const assert = require('assert');
const Module = require('module');

// 小纸条 API 封装测试：只断言请求路径/方法与响应透传，不关心页面状态
let calls = [];
let responses = [];
global.wx = { request(options) { calls.push(options); options.success(responses.shift()); } };
global.getApp = () => ({ silentLogin: async () => {} });

const originalLoad = Module._load;
Module._load = function (request) {
  if (request === '../config/api') return { API_BASE_URL: 'https://api.example/xiaozhi' };
  if (request === './auth') return { getSession: () => ({ token: 't' }), clearSession: () => {} };
  return originalLoad.apply(this, arguments);
};

const dailyNoteApi = require('./daily-note-api');

function enqueue(data) {
  responses.push({ statusCode: 200, data: { code: 0, data } });
}

(async () => {
  // 当日查询：GET /app/daily-note/today，透传 DailyNoteTodayVO（date/title/content/solarTerm/game）
  const todayVO = {
    date: '2026-11-01',
    title: '今日小纸条',
    content: '第一行\n第二行',
    solarTerm: '立冬',
    game: { gameType: 'LISTEN_TO_YOU', icon: 'https://oss.example/icon.png', description: '听你说', earnStars: 5 }
  };
  enqueue(todayVO);
  const today = await dailyNoteApi.getToday();
  assert.ok(calls.at(-1).url.endsWith('/app/daily-note/today'), `expected url ending with /app/daily-note/today, got ${calls.at(-1).url}`);
  assert.strictEqual(calls.at(-1).method, 'GET', 'getToday uses GET');
  assert.deepStrictEqual(today, todayVO, 'getToday returns DailyNoteTodayVO');

  // 当日无内容/已打开/无合格游戏：后端返回 data=null，原样透传 null
  enqueue(null);
  const empty = await dailyNoteApi.getToday();
  assert.strictEqual(empty, null, 'getToday passes through null data');

  // 标记已打开：POST /app/daily-note/opened，无请求体，透传奖励结算结果
  const reward = { awarded: true, skipped: false, amount: 11, transactionId: 1001, rewardId: 2002, rewardDate: '2026-11-01' };
  enqueue(reward);
  const opened = await dailyNoteApi.markOpened();
  const lastCall = calls.at(-1);
  assert.ok(lastCall.url.endsWith('/app/daily-note/opened'), `expected url ending with /app/daily-note/opened, got ${lastCall.url}`);
  assert.strictEqual(lastCall.method, 'POST', 'markOpened uses POST');
  assert.deepStrictEqual(opened, reward, 'markOpened returns DailyRewardResult');

  // 重复打开/无内容：awarded=false 且 amount=0，原样透传
  const notAwarded = { awarded: false, skipped: false, amount: 0, transactionId: null, rewardId: null, rewardDate: '2026-11-01' };
  enqueue(notAwarded);
  const repeat = await dailyNoteApi.markOpened();
  assert.deepStrictEqual(repeat, notAwarded, 'markOpened passes through not-awarded result');

  console.log('daily-note-api.test.js: ALL PASS');
})().finally(() => { Module._load = originalLoad; });
