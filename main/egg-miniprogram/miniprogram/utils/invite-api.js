const { get } = require('./request');

// GET /invite/mine 返回 InviteCodeVO：
// { code, quota, usedCount, remaining, status, expireTime, createDate }
function getMine() {
  return get('/invite/mine');
}

module.exports = { getMine };
