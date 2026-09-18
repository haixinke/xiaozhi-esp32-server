// photo-gen 页面单元测试：node 直接运行（node photo-gen.test.js），桩 wx、image-gen-api 与 image-compress。
const assert = require('assert');
const Module = require('module');

const pagePath = require.resolve('./photo-gen');
const originalLoad = Module._load;
const originalPage = global.Page;
const originalWx = global.wx;

let pageConfig;
let chooseMediaImpl = null;
let chooseImageImpl = null;
let uploadResult = null;
let uploadError = null;
let createResult = null;
let pollSequence = [];
let toastMessages = [];
let loadingMessages = [];
let callOrder = [];
let compressResult = null;
let fileInfoSizes = {};

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
    if (request === '../../utils/image-compress') {
      return {
        compressIfNeeded: async () => compressResult,
        MAX_FILE_SIZE: 5 * 1024 * 1024
      };
    }
  }
  return originalLoad.call(this, request, parent, isMain);
};

global.Page = (config) => { pageConfig = config; };
global.wx = {
  chooseMedia: (opts) => chooseMediaImpl && chooseMediaImpl(opts),
  chooseImage: (opts) => chooseImageImpl && chooseImageImpl(opts),
  getFileInfo: (opts) => {
    const size = fileInfoSizes[opts.filePath];
    if (size === undefined) {
      opts.fail && opts.fail({});
    } else {
      opts.success({ size });
    }
  },
  showToast: (opts) => { toastMessages.push(opts.title); callOrder.push('toast'); },
  showLoading: (opts) => { loadingMessages.push(opts.title); callOrder.push('showLoading'); },
  hideLoading: () => { callOrder.push('hideLoading'); },
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
  loadingMessages = [];
  callOrder = [];
  pollSequence = [];
  uploadResult = 'https://oss.eggbabe.com/ai-gen/1/a.jpg';
  uploadError = null;
  createResult = { taskId: 't-1', status: 'PENDING' };
  chooseMediaImpl = null;
  chooseImageImpl = null;
  compressResult = { ok: false, tempFilePath: '', size: 0 };
  fileInfoSizes = {};
  global.wx.chooseMedia = (opts) => chooseMediaImpl && chooseMediaImpl(opts);
}

const tests = [];

tests.push(['chooseMedia 超过5MB且压缩失败时提示且不写入预览', async () => {
  reset();
  const page = makePage();
  const big = { tempFiles: [{ tempFilePath: '/tmp/big.jpg', size: 6 * 1024 * 1024 }] };
  chooseMediaImpl = (opts) => opts.success(big);
  page.onChoosePhoto();
  await page._choosing;
  assert.strictEqual(page.data.photoPreview, '');
  assert.ok(toastMessages.some((t) => t.includes('5MB')));
}]);

tests.push(['chooseMedia 超过5MB但压缩成功时写入压缩后预览', async () => {
  reset();
  const page = makePage();
  compressResult = { ok: true, tempFilePath: '/tmp/compressed.jpg', size: 2 * 1024 * 1024 };
  chooseMediaImpl = (opts) => opts.success({ tempFiles: [{ tempFilePath: '/tmp/big.jpg', size: 6 * 1024 * 1024 }] });
  page.onChoosePhoto();
  await page._choosing;
  assert.strictEqual(page.data.photoPreview, '/tmp/compressed.jpg');
  assert.ok(loadingMessages.some((t) => t.includes('处理中')));
}]);

tests.push(['chooseMedia 正常时写入预览', async () => {
  reset();
  const page = makePage();
  chooseMediaImpl = (opts) => opts.success({ tempFiles: [{ tempFilePath: '/tmp/ok.jpg', size: 1024 }] });
  page.onChoosePhoto();
  await page._choosing;
  assert.strictEqual(page.data.photoPreview, '/tmp/ok.jpg');
}]);

tests.push(['chooseImage 降级路径补 getFileInfo 后写入预览', async () => {
  reset();
  const page = makePage();
  delete global.wx.chooseMedia; // 模拟基础库 <2.10.0
  fileInfoSizes['/tmp/legacy.jpg'] = 1024;
  chooseImageImpl = (opts) => opts.success({ tempFilePaths: ['/tmp/legacy.jpg'] });
  page.onChoosePhoto();
  await page._choosing;
  assert.strictEqual(page.data.photoPreview, '/tmp/legacy.jpg');
}]);

tests.push(['chooseImage 降级路径大图同样走压缩', async () => {
  reset();
  const page = makePage();
  delete global.wx.chooseMedia;
  fileInfoSizes['/tmp/legacy-big.jpg'] = 7 * 1024 * 1024;
  compressResult = { ok: true, tempFilePath: '/tmp/legacy-c.jpg', size: 2 * 1024 * 1024 };
  chooseImageImpl = (opts) => opts.success({ tempFilePaths: ['/tmp/legacy-big.jpg'] });
  page.onChoosePhoto();
  await page._choosing;
  assert.strictEqual(page.data.photoPreview, '/tmp/legacy-c.jpg');
}]);

tests.push(['压缩失败时先收 loading 再弹 toast（hideLoading 会连带关掉 toast）', async () => {
  reset();
  const page = makePage();
  chooseMediaImpl = (opts) => opts.success({ tempFiles: [{ tempFilePath: '/tmp/big.jpg', size: 6 * 1024 * 1024 }] });
  page.onChoosePhoto();
  await page._choosing;
  const hideIdx = callOrder.indexOf('hideLoading');
  const toastIdx = callOrder.indexOf('toast');
  assert.ok(hideIdx !== -1 && toastIdx !== -1);
  assert.ok(hideIdx < toastIdx, `hideLoading(${hideIdx}) 必须先于 toast(${toastIdx})`);
}]);

tests.push(['压缩处理中禁止重复选图', async () => {
  reset();
  const page = makePage();
  let chooseCalls = 0;
  chooseMediaImpl = () => { chooseCalls += 1; };
  page._compressing = true;
  page.onChoosePhoto();
  assert.strictEqual(chooseCalls, 0);
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
  await page._choosing;
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
