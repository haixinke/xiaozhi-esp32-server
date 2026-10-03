// photo-gallery-helper 单元测试：node 直接运行（node photo-gallery-helper.test.js）。
const assert = require('assert');
const { formatDayLabel, buildTimelineItems, cycleDeck } = require('./photo-gallery-helper');

const TODAY = '2026-10-03';
const photo = (id) => ({ taskId: String(id), resultUrl: `https://oss.eggbabe.com/x/${id}.jpg`, createTime: '' });

// formatDayLabel：今天
assert.strictEqual(formatDayLabel('2026-10-03', TODAY), '今天');

// formatDayLabel：今年 → M月D日 周X（2026-10-01 是周四）
assert.strictEqual(formatDayLabel('2026-10-01', TODAY), '10月1日 周四');

// formatDayLabel：往年 → 带完整年份
assert.strictEqual(formatDayLabel('2025-12-08', TODAY), '2025年12月8日 周一');

// buildTimelineItems：同年不插里程碑
const sameYear = buildTimelineItems([
  { date: '2026-10-03', photos: [photo(1)] },
  { date: '2026-10-01', photos: [photo(2)] }
], TODAY);
assert.deepStrictEqual(sameYear.map((i) => i.type), ['day', 'day']);
assert.strictEqual(sameYear[0].label, '今天');
assert.strictEqual(sameYear[1].label, '10月1日 周四');

// buildTimelineItems：跨年插里程碑，里程碑年份为进入的旧年
const crossYear = buildTimelineItems([
  { date: '2026-01-02', photos: [photo(1)] },
  { date: '2025-12-31', photos: [photo(2)] },
  { date: '2025-12-30', photos: [photo(3)] }
], TODAY);
assert.deepStrictEqual(crossYear.map((i) => i.type), ['day', 'milestone', 'day', 'day']);
assert.strictEqual(crossYear[1].year, 2025);
assert.strictEqual(crossYear[2].label, '2025年12月31日 周三');

// buildTimelineItems：多次跨年插多个里程碑（模拟翻页后累积）
const multiYear = buildTimelineItems([
  { date: '2026-06-01', photos: [photo(1)] },
  { date: '2025-06-01', photos: [photo(2)] },
  { date: '2024-06-01', photos: [photo(3)] }
], TODAY);
assert.deepStrictEqual(multiYear.map((i) => i.type), ['day', 'milestone', 'day', 'milestone', 'day']);
assert.strictEqual(multiYear[1].year, 2025);
assert.strictEqual(multiYear[3].year, 2024);

// cycleDeck：顶卡循环到册底，原数组不被改动
const deck = [photo(1), photo(2), photo(3)];
const cycled = cycleDeck(deck);
assert.deepStrictEqual(cycled.map((p) => p.taskId), ['2', '3', '1']);
assert.deepStrictEqual(deck.map((p) => p.taskId), ['1', '2', '3']);

// cycleDeck：单张不变
assert.deepStrictEqual(cycleDeck([photo(1)]).map((p) => p.taskId), ['1']);

console.log('photo-gallery-helper tests passed');
