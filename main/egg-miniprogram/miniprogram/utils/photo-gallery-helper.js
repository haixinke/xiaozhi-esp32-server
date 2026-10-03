// AI写真集纯逻辑：日期标签、时间轴项（含年份里程碑）、卡册循环重排。
// 与页面解耦，node 可直接单测（见 photo-gallery-helper.test.js）。
const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

/**
 * 日期节点标签：今天 → "今天"；今年 → "M月D日 周X"；往年 → "YYYY年M月D日 周X"。
 * @param {string} dateStr yyyy-MM-dd（后端按 Asia/Shanghai 分组）
 * @param {string} todayStr 今天的 yyyy-MM-dd（由页面注入，便于测试与跨零点一致性）
 * @returns {string}
 */
function formatDayLabel(dateStr, todayStr) {
  if (dateStr === todayStr) return '今天';
  const [year, month, day] = dateStr.split('-').map(Number);
  const weekday = WEEKDAYS[new Date(year, month - 1, day).getDay()];
  const thisYear = Number(todayStr.split('-')[0]);
  if (year === thisYear) return `${month}月${day}日 ${weekday}`;
  return `${year}年${month}月${day}日 ${weekday}`;
}

/**
 * 构建时间轴渲染项：日期节点倒序排列，跨年处在两个日期节点之间插入年份里程碑。
 * 里程碑不占分页单位，由相邻两天年份不同自动触发。
 * @param {Array<{date: string, photos: Array}>} days 按天倒序的日期节点
 * @param {string} todayStr 今天的 yyyy-MM-dd
 * @returns {Array<{type: 'milestone', key: string, year: number} | {type: 'day', key: string, date: string, label: string, photos: Array}>}
 */
function buildTimelineItems(days, todayStr) {
  const items = [];
  days.forEach((day, index) => {
    const year = Number(day.date.split('-')[0]);
    if (index > 0) {
      const prevYear = Number(days[index - 1].date.split('-')[0]);
      if (prevYear !== year) {
        items.push({ type: 'milestone', key: `milestone-${year}`, year });
      }
    }
    items.push({
      type: 'day',
      key: day.date,
      date: day.date,
      label: formatDayLabel(day.date, todayStr),
      photos: day.photos
    });
  });
  return items;
}

/**
 * 卡册循环：顶卡（首位）移到册底，返回新数组，不改动入参。
 * @param {Array} photos 当前卡册顺序（最新在顶）
 * @returns {Array}
 */
function cycleDeck(photos) {
  if (photos.length <= 1) return photos.slice();
  return photos.slice(1).concat(photos[0]);
}

module.exports = { formatDayLabel, buildTimelineItems, cycleDeck };
