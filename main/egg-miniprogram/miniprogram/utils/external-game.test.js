/* external-game 工具测试：wx 桩件，验证 trim、空白拦截、编码导航与失败提示 */
const assert = require('assert');

const calls = [];
global.wx = {
  navigateTo(options) {
    calls.push(['navigate', options.url]);
    if (options && options.fail && global.__navigateShouldFail) options.fail();
  },
  showToast(options) { calls.push(['toast', options.title]); }
};

const { openExternalGame } = require('./external-game');

// 合法链接：trim 后编码导航，查询参数与锚点完整保留，且无 toast
{
  const before = calls.length;
  const ok = openExternalGame({ jumpUrl: '  https://games.example.com/h5/maze?level=3&channel=note#stage-2  ' });
  assert.strictEqual(ok, true, 'valid url initiates navigation');
  assert.deepStrictEqual(calls.slice(before), [
    ['navigate', `/pages/webview/webview?url=${encodeURIComponent('https://games.example.com/h5/maze?level=3&channel=note#stage-2')}`]
  ], 'trimmed url is encoded and navigated without toast');
}

// 缺失/纯空白/非字符串链接：提示且不导航
{
  for (const game of [{ jumpUrl: '' }, { jumpUrl: '   ' }, { jumpUrl: null }, {}, null]) {
    const before = calls.length;
    const ok = openExternalGame(game);
    assert.strictEqual(ok, false, 'blank or missing url does not navigate');
    assert.deepStrictEqual(calls.slice(before), [['toast', '暂时打不开，请稍后再试']],
      `blank url toasts for ${JSON.stringify(game)}`);
  }
}

// 导航失败：给出与空白一致的提示
{
  global.__navigateShouldFail = true;
  const before = calls.length;
  openExternalGame({ jumpUrl: 'https://games.example.com/play' });
  assert.deepStrictEqual(calls.slice(before), [
    ['navigate', `/pages/webview/webview?url=${encodeURIComponent('https://games.example.com/play')}`],
    ['toast', '暂时打不开，请稍后再试']
  ], 'navigation failure surfaces the same toast');
  global.__navigateShouldFail = false;
}

console.log('external-game.test.js: ALL PASS');
