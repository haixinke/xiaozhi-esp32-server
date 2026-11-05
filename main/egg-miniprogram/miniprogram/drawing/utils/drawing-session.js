// 会话只驻留内存：稳定开局单号负责重试幂等，后台暂停不结束当前一局。
function createDrawingSession(options) {
  const api = options.api;
  const now = options.now || Date.now;
  const setTimer = options.setTimer || setTimeout;
  const clearTimer = options.clearTimer || clearTimeout;
  const requestId = options.requestId || `draw-${now()}-${Math.random().toString(36).slice(2)}`;
  let state = { config: null, session: null, result: null, busy: false, pendingStart: null,
    peekVisible: false, peekRemainingMs: 0, looksRemaining: 3, animateReward: false };
  let active = true;
  let disposed = false;
  let timer = null;
  let timerStartedAt = 0;
  let previewStarted = false;
  let starting = null;
  let completing = null;

  function snapshot() { return { ...state }; }
  function publish(patch) {
    if (disposed) return;
    state = { ...state, ...patch };
    if (options.onChange) options.onChange(snapshot());
  }
  function stopPeekTimer() {
    if (timer != null) clearTimer(timer);
    timer = null;
  }
  function dismissPeek() {
    stopPeekTimer();
    publish({ peekVisible: false, peekRemainingMs: 0 });
  }
  function runPeekTimer() {
    stopPeekTimer();
    if (!active || !state.peekVisible || disposed) return;
    timerStartedAt = now();
    timer = setTimer(dismissPeek, state.peekRemainingMs);
  }
  function showPeek() {
    publish({ peekVisible: true, peekRemainingMs: 5000 });
    runPeekTimer();
  }
  async function load() {
    publish({ busy: true });
    try {
      const config = await api.config(options.petId);
      publish({ config });
      return config;
    } finally { publish({ busy: false }); }
  }
  function start() {
    if (state.session) return Promise.resolve(state.session);
    if (starting) return starting;
    // 未知响应沿用原单号恢复局次，避免被新配置或已经用完的额度挡住。
    publish({ busy: true, pendingStart: true });
    starting = api.start({ petId: options.petId, requestId })
      .then(session => { publish({ session, pendingStart: null }); return session; })
      .catch(error => {
        // 明确的业务拒绝表示本次未开局；网络失败则仍可能已提交，需重放原请求。
        if (error && error.type === 'business') publish({ pendingStart: null });
        throw error;
      })
      .finally(() => { starting = null; publish({ busy: false }); });
    return starting;
  }
  function beginPreview() {
    if (!state.session || previewStarted || disposed) return;
    previewStarted = true;
    showPeek();
  }
  function peekAgain() {
    if (!active || state.busy || !previewStarted || state.peekVisible || state.result
      || state.looksRemaining <= 0 || disposed) return false;
    publish({ looksRemaining: state.looksRemaining - 1 });
    showPeek();
    return true;
  }
  function complete() {
    if (state.result) return Promise.resolve(state.result);
    if (completing) return completing;
    if (!state.session || !previewStarted || state.peekVisible || disposed) return Promise.resolve(null);
    publish({ busy: true });
    completing = api.complete(state.session.sessionId)
      .then(result => {
        publish({ result, animateReward: active && Number(result.awardedStars) > 0 });
        return result;
      }).finally(() => { completing = null; publish({ busy: false }); });
    return completing;
  }
  function pause() {
    if (!active || disposed) return;
    active = false;
    if (state.peekVisible && timer != null) {
      publish({ peekRemainingMs: Math.max(0, state.peekRemainingMs - (now() - timerStartedAt)) });
    }
    stopPeekTimer();
    publish({ animateReward: false });
  }
  return {
    snapshot, load, start, beginPreview, dismissPeek, peekAgain, complete, pause,
    resume() { if (!disposed) { active = true; runPeekTimer(); } },
    dispose() { disposed = true; active = false; stopPeekTimer(); }
  };
}

module.exports = { createDrawingSession };
