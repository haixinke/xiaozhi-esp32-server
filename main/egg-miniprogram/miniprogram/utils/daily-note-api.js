const { get, post } = require('./request');

// 小纸条（每日内容，见 GLOSSARY「小纸条」「小纸条已打开」）
// 当日查询返回 DailyNoteTodayVO: { date, title, content, solarTerm, game }；
// 已打开/无当日内容/无合格游戏时后端返回 data=null，原样透传
function getToday() {
  return get('/app/daily-note/today');
}

// 标记当日小纸条已打开（幂等）。返回 DailyRewardResult:
// 首次有效打开 awarded=true 且 amount 为实发星星数；
// 当天已打开/无内容/并发冲突返回 awarded=false、amount=0
function markOpened() {
  return post('/app/daily-note/opened');
}

module.exports = {
  getToday,
  markOpened
};
