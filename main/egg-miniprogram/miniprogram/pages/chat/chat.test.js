const assert = require('assert');
const Module = require('module');

// chat.js 依赖的模块全部 mock 掉，本测试只关注时间分隔条相关纯逻辑
const originalLoad = Module._load;
Module._load = function (request) {
  if (request === '../../utils/pet-store') return { getPet: () => null, saveMessage: () => {}, getDailyStatus: () => null };
  if (request === '../../utils/pet-api') return {};
  if (request === '../../utils/ota') return {};
  if (request === '../../utils/websocket') return function () {};
  if (request === '../../utils/audio') return function () {};
  return originalLoad.apply(this, arguments);
};

let pageConfig = null;
global.Page = (config) => { pageConfig = config; };

require('./chat');

Module._load = originalLoad;

// 页面方法不依赖 onLoad 初始化，直接以 pageConfig 为 this 调用
const page = pageConfig;

const MIN = 60 * 1000;
// 固定基准时间，避免跨天/跨年边界导致用例不稳定：2026-07-20 10:00 本地时间
const BASE = new Date(2026, 6, 20, 10, 0, 0).getTime();

function msg(time, extra) {
  return Object.assign({ id: 'm', role: 'user', content: 'hi', time }, extra || {});
}

// --- _parseTime ------------------------------------------------------------

// 数字时间戳原样返回
assert.strictEqual(page._parseTime(BASE), BASE);
// ISO 字符串解析
assert.strictEqual(
  page._parseTime('2026-07-20T10:00:00.000'),
  new Date(2026, 6, 20, 10, 0, 0).getTime()
);
// iOS 不兼容的 "yyyy-MM-dd HH:mm:ss" 格式被归一化为 ISO
assert.strictEqual(
  page._parseTime('2026-07-20 10:00:00'),
  new Date(2026, 6, 20, 10, 0, 0).getTime()
);
// 非法值回退为当前时间
assert.ok(Math.abs(page._parseTime('not-a-date') - Date.now()) < 1000);
assert.ok(Math.abs(page._parseTime('') - Date.now()) < 1000);

// --- _isSameDay ------------------------------------------------------------

assert.strictEqual(page._isSameDay(BASE, BASE + 5 * MIN), true);
// 同一天 23:59 vs 次日 00:00
assert.strictEqual(
  page._isSameDay(new Date(2026, 6, 20, 23, 59).getTime(), new Date(2026, 6, 21, 0, 0).getTime()),
  false
);
assert.strictEqual(page._isSameDay(0, BASE), false);
assert.strictEqual(page._isSameDay(BASE, 0), false);

// --- _formatTimeLabel ------------------------------------------------------
// 文案依赖「今天/昨天/同年」，用例基于运行时 now 动态构造

const now = Date.now();
const nowDate = new Date(now);

// 今天：仅 HH:mm
const todayLabel = page._formatTimeLabel(now);
assert.ok(/^\d{2}:\d{2}$/.test(todayLabel), `today label should be HH:mm, got ${todayLabel}`);

// 昨天：「昨天 HH:mm」
const yesterday = new Date(nowDate);
yesterday.setDate(yesterday.getDate() - 1);
yesterday.setHours(9, 5, 0, 0);
assert.strictEqual(page._formatTimeLabel(yesterday.getTime()), '昨天 09:05');

// 同年更早：「M月D日 HH:mm」（取年初，必不落在今天/昨天）
const sameYear = new Date(nowDate.getFullYear(), 0, 5, 8, 3, 0, 0);
assert.strictEqual(page._formatTimeLabel(sameYear.getTime()), '1月5日 08:03');

// 跨年：「YYYY年M月D日 HH:mm」
const lastYear = new Date(nowDate.getFullYear() - 1, 11, 25, 18, 30, 0, 0);
assert.strictEqual(
  page._formatTimeLabel(lastYear.getTime()),
  `${nowDate.getFullYear() - 1}年12月25日 18:30`
);

// --- _stampSeparators -------------------------------------------------------

// 空数组原样返回
assert.deepStrictEqual(page._stampSeparators([]), []);

// 首条必显，后续 5 分钟内不重复显示
{
  const out = page._stampSeparators([
    msg(BASE),
    msg(BASE + 1 * MIN),
    msg(BASE + 4 * MIN),
  ]);
  assert.strictEqual(out[0].showTime, true);
  assert.strictEqual(out[1].showTime, false);
  assert.strictEqual(out[2].showTime, false);
}

// 间隔 ≥5 分钟显示分隔条
{
  const out = page._stampSeparators([
    msg(BASE),
    msg(BASE + 5 * MIN),
  ]);
  assert.strictEqual(out[1].showTime, true);
  assert.strictEqual(out[1].timeLabel, page._formatTimeLabel(BASE + 5 * MIN));
}

// 间隔不足 5 分钟但跨天也显示
{
  const day1 = new Date(2026, 6, 20, 23, 58).getTime();
  const day2 = new Date(2026, 6, 21, 0, 1).getTime();
  const out = page._stampSeparators([msg(day1), msg(day2)]);
  assert.strictEqual(out[1].showTime, true);
}

// 打标不修改原消息对象，其他字段保留
{
  const input = [msg(BASE, { audioId: 'a1' })];
  const out = page._stampSeparators(input);
  assert.strictEqual(input[0].showTime, undefined, 'should not mutate input');
  assert.strictEqual(out[0].audioId, 'a1');
}

// --- _appendWithSeparator ---------------------------------------------------

// 空列表：首条必显
{
  const out = page._appendWithSeparator([], msg(BASE));
  assert.strictEqual(out.length, 1);
  assert.strictEqual(out[0].showTime, true);
}

// 与末条间隔 <5 分钟：不显示
{
  const out = page._appendWithSeparator([msg(BASE)], msg(BASE + 2 * MIN));
  assert.strictEqual(out[1].showTime, false);
  assert.strictEqual(out[1].timeLabel, '');
}

// 与末条间隔 ≥5 分钟：显示
{
  const out = page._appendWithSeparator([msg(BASE)], msg(BASE + 6 * MIN));
  assert.strictEqual(out[1].showTime, true);
  assert.strictEqual(out[1].timeLabel, page._formatTimeLabel(BASE + 6 * MIN));
}

// 与末条跨天：显示（即使间隔 <5 分钟）
{
  const day1 = new Date(2026, 6, 20, 23, 59).getTime();
  const day2 = new Date(2026, 6, 21, 0, 0).getTime();
  const out = page._appendWithSeparator([msg(day1)], msg(day2));
  assert.strictEqual(out[1].showTime, true);
}

// 追加不修改原数组
{
  const list = [msg(BASE)];
  const out = page._appendWithSeparator(list, msg(BASE + 1 * MIN));
  assert.strictEqual(list.length, 1, 'should not mutate input array');
  assert.strictEqual(out.length, 2);
}

console.log('chat.test.js: ALL PASS');
