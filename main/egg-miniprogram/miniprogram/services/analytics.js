/**
 * analytics stub — 轻量空实现
 * life-scene 等页面调用 analytics.track() 时静默跳过，不报错。
 */
function track() {
  return { ok: true };
}

function flush() {
  return Promise.resolve({ ok: true, count: 0 });
}

module.exports = { track, flush };
