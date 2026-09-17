// photo-gen 页面单元测试：node 直接运行（node photo-gen.test.js），桩 wx 与 image-gen-api。
const assert = require('assert');
const Module = require('module');

const pagePath = require.resolve('./photo-gen');
const originalLoad = Module._load;
const originalPage = global.Page;
const originalWx = global.wx;

let pageConfig;
let chooseMediaImpl = null;
let uploadResult = null;
let uploadError = null;
let createResult = null;
let pollSequence = [];
let toastMessages = [];

Module._load = function (request, parent, isMain) {
  if (parent && parent.filename === pagePath) {
    if (request === '../../utils/image-gen-api') {
      return {
        uploadGenPhoto: async () => {
          if (uploadError) throw uploadError;
          return uploadResult;
        },
        createImageGenTask: async () => createResult,
        getImageGenTask: async () => pollSequence.shift()
      };
    }
  }
  return originalLoad.call(this, request, parent, isMain);
};

global.Page = (config) => { pageConfig = config; };
global.wx = {
  chooseMedia: (opts) => chooseMediaImpl && chooseMediaImpl(opts),
  showToast: (opts) => toastMessages.push(opts.title),
  downloadFile: () => { throw new Error('not stubbed'); },
  saveImageToPhotosAlbum: () => { throw new Error('not stubbed'); }
};

require('./photo-gen');

function makePage() {
  const page = {
    ...pageConfig,
    data: { ...pageConfig.data },
    setData(patch) { Object.assign(this.data, patch); }
  };
  return page;
}

function reset() {
  toastMessages = [];
  pollSequence = [];
  uploadResult = 'https://oss.eggbabe.com/ai-gen/1/a.jpg';
  uploadError = null;
  createResult = { taskId: 't-1', status: 'PENDING' };
  chooseMediaImpl = null;
}

const tests = [];

tests.push(['chooseMedia 超过5MB时提示且不写入预览', () => {
  reset();
  const page = makePage();
  const big = { tempFiles: [{ tempFilePath: '/tmp/big.jpg', size: 6 * 1024 * 1024 }] };
  chooseMediaImpl = (opts) => opts.success(big);
  page.onChoosePhoto();
  assert.strictEqual(page.data.photoPreview, '');
  assert.ok(toastMessages.some((t) => t.includes('5MB')));
}]);

tests.push(['chooseMedia 正常时写入预览', () => {
  reset();
  const page = makePage();
  chooseMediaImpl = (opts) => opts.success({ tempFiles: [{ tempFilePath: '/tmp/ok.jpg', size: 1024 }] });
  page.onChoosePhoto();
  assert.strictEqual(page.data.photoPreview, '/tmp/ok.jpg');
}]);

tests.push(['未选图时点击生成不动作', async () => {
  reset();
  const page = makePage();
  await page.onStartGenerate();
  assert.strictEqual(page.data.phase, 'pick');
}]);

tests.push(['生成流程进入working并开始轮询，SUCCEEDED后进入result', async () => {
  reset();
  const page = makePage();
  chooseMediaImpl = (opts) => opts.success({ tempFiles: [{ tempFilePath: '/tmp/ok.jpg', size: 1024 }] });
  page.onChoosePhoto();
  pollSequence = [{ status: 'RUNNING' }, { status: 'SUCCEEDED', resultUrl: 'https://oss.eggbabe.com/ai-gen/1/t-1.png', caption: '好运连连' }];
  page._pollStartedAt = Date.now();

  await page.onStartGenerate();
  assert.strictEqual(page.data.phase, 'working');
  assert.strictEqual(page._taskId, 't-1');
  page._stopPolling(); // 测试手动驱动，不走真实定时器

  await page._pollOnce();
  assert.strictEqual(page.data.phase, 'working');
  await page._pollOnce();
  assert.strictEqual(page.data.phase, 'result');
  assert.strictEqual(page.data.resultUrl, 'https://oss.eggbabe.com/ai-gen/1/t-1.png');
  assert.strictEqual(page.data.caption, '好运连连');
}]);

tests.push(['轮询到FAILED时进入failed并展示原因', async () => {
  reset();
  const page = makePage();
  page._taskId = 't-1';
  page._pollStartedAt = Date.now();
  page.setData({ phase: 'working' });
  pollSequence = [{ status: 'FAILED', failReason: '图片未通过审核，换一张试试' }];

  await page._pollOnce();
  assert.strictEqual(page.data.phase, 'failed');
  assert.strictEqual(page.data.failReason, '图片未通过审核，换一张试试');
}]);

tests.push(['分享标题使用抽中的文案', () => {
  reset();
  const page = makePage();
  page.setData({ caption: '玉兔呈祥' });
  const share = page.onShareAppMessage();
  assert.strictEqual(share.title, '玉兔呈祥');
  assert.strictEqual(share.path, '/pages/home/home');
}]);

tests.push(['onRetry 复位到选图态', () => {
  reset();
  const page = makePage();
  page.setData({ phase: 'failed', failReason: 'x', resultUrl: 'u', caption: 'c' });
  page.onRetry();
  assert.strictEqual(page.data.phase, 'pick');
  assert.strictEqual(page.data.failReason, '');
}]);

(async () => {
  let failed = 0;
  for (const [name, fn] of tests) {
    try {
      await fn();
      console.log(`PASS ${name}`);
    } catch (error) {
      failed += 1;
      console.error(`FAIL ${name}: ${error.message}`);
    }
  }
  Module._load = originalLoad;
  global.Page = originalPage;
  global.wx = originalWx;
  if (failed > 0) {
    process.exit(1);
  }
  console.log('ALL PASS');
})();
