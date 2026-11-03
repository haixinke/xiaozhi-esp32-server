const { get } = require('./request');

// 活动入口的启用游戏由后端筛选，前端不再维护静态游戏清单。
function listActivityGames() {
  return get('/app/game/list', { entrance: 'ACTIVITY' });
}

module.exports = { listActivityGames };
