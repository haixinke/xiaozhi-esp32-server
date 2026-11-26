/* webview 页面测试：Page/wx 桩件，验证外部游戏链接的路由还原与加载失败提示 */
const assert = require('assert');

let definition;
const calls = [];
global.Page = (value) => { definition = value; };
global.wx = {
  navigateBack(options) { calls.push(['back', options]); },
  showToast(options) { calls.push(['toast', options.title]); }
};

function page() {
  return {
    ...definition,
    data: JSON.parse(JSON.stringify(definition.data)),
    setData(change) { this.data = { ...this.data, ...change }; }
  };
}

require('./webview');

// 完整还原：查询参数与锚点必须原样进入 web-view src
{
  const view = page();
  const target = 'https://games.example.com/play?level=3&lang=zh-CN&a=b%20c#stage-2';
  view.onLoad({ url: encodeURIComponent(target) });
  assert.strictEqual(view.data.url, target, 'encoded url must be fully reconstructed');
  assert.strictEqual(view.data.error, '', 'valid url must not surface an error');
}

// 运营链接本身含已编码字符（如 %20）时不能被二次解码破坏
{
  const view = page();
  const target = 'https://games.example.com/search?q=%E8%9B%8B%E5%AE%9D%E5%AE%9D&page=2';
  view.onLoad({ url: encodeURIComponent(target) });
  assert.strictEqual(view.data.url, target, 'url-encoded content inside query must survive the route round-trip');
}

// 缺失或非 HTTPS 地址：不渲染 web-view，提示并允许返回
{
  const view = page();
  view.onLoad({});
  assert.strictEqual(view.data.url, '', 'missing url must not render web-view');
  assert.ok(view.data.error.length > 0, 'missing url must show an understandable error');

  const insecure = page();
  insecure.onLoad({ url: encodeURIComponent('http://games.example.com/play') });
  assert.strictEqual(insecure.data.url, '', 'non-https url must be rejected');
  assert.ok(insecure.data.error.length > 0, 'non-https url must show an understandable error');
}

// web-view 加载失败：给出可理解的提示，且不再停留在空白 web-view
{
  const view = page();
  view.onLoad({ url: encodeURIComponent('https://games.example.com/play') });
  view.onError({});
  assert.ok(view.data.error.length > 0, 'load failure must surface an error');
  assert.strictEqual(view.data.url, '', 'failed page must tear down the web-view');
  assert.ok(calls.some(([kind, title]) => kind === 'toast' && title.length > 0), 'load failure must toast');
}

// 加载成功清除错误态（重试或重新进入时恢复）
{
  const view = page();
  view.onLoad({ url: encodeURIComponent('https://games.example.com/play') });
  view.onError({});
  view.onLoad({ url: encodeURIComponent('https://games.example.com/play') });
  assert.strictEqual(view.data.error, '', 'successful reload clears the error');
}

// 返回：页面栈可退时 navigateBack
{
  const view = page();
  view.onLoad({ url: encodeURIComponent('https://games.example.com/play') });
  view.onBack();
  assert.ok(calls.some(([kind]) => kind === 'back'), 'back must navigate back');
}

console.log('webview.test.js: ALL PASS');
