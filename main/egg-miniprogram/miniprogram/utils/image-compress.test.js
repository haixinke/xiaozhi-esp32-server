// image-compress 单元测试：node 直接运行（node image-compress.test.js），桩 wx 全局。
const assert = require('assert');

const originalWx = global.wx;

// 可控桩：各 wx API 的行为由用例注入
let fileSizes = {};          // path -> size，getFileInfo 的数据源
let compressImageImpl = null;
let canvasImageSize = null;  // { width, height } canvas 加载图片的原始尺寸
let canvasExportPath = '';
let canvasUnavailable = false;
let compressImageCalls = 0;
let canvasExportOpts = null;
let lastCanvasCtx = null;

function fakeCanvas() {
  const ctx = {
    fillStyle: '',
    fillRectCalls: [],
    drawImageCalls: [],
    fillRect(x, y, w, h) { this.fillRectCalls.push([x, y, w, h]); },
    drawImage(img, x, y, w, h) { this.drawImageCalls.push([img, x, y, w, h]); }
  };
  lastCanvasCtx = ctx;
  return {
    width: 0,
    height: 0,
    getContext: () => ctx,
    createImage() {
      const img = { width: canvasImageSize.width, height: canvasImageSize.height };
      Object.defineProperty(img, 'src', {
        set() { setTimeout(() => img.onload && img.onload(), 0); }
      });
      return img;
    }
  };
}

global.wx = {
  getFileInfo(opts) {
    const size = fileSizes[opts.filePath];
    if (size === undefined) {
      opts.fail && opts.fail({});
    } else {
      opts.success({ size });
    }
  },
  compressImage(opts) {
    compressImageCalls += 1;
    if (compressImageImpl) compressImageImpl(opts);
  },
  createSelectorQuery() {
    if (canvasUnavailable) throw new Error('no canvas');
    return {
      in() { return this; },
      select() { return this; },
      fields() { return this; },
      exec(cb) { cb([{ node: fakeCanvas() }]); }
    };
  },
  canvasToTempFilePath(opts) {
    canvasExportOpts = opts;
    opts.success({ tempFilePath: canvasExportPath });
  }
};

const { compressIfNeeded } = require('./image-compress');

const MB = 1024 * 1024;
const tests = [];

function reset() {
  fileSizes = {};
  compressImageImpl = (opts) => opts.fail({});
  canvasImageSize = { width: 4000, height: 3000 };
  canvasExportPath = '/tmp/scaled.jpg';
  canvasUnavailable = false;
  compressImageCalls = 0;
  canvasExportOpts = null;
  lastCanvasCtx = null;
}

tests.push(['小图直通，不触发压缩', async () => {
  reset();
  const result = await compressIfNeeded({ tempFilePath: '/tmp/ok.jpg', size: 1 * MB, page: {} });
  assert.strictEqual(result.ok, true);
  assert.strictEqual(result.tempFilePath, '/tmp/ok.jpg');
  assert.strictEqual(compressImageCalls, 0);
}]);

tests.push(['size 缺失时 getFileInfo 补查，小图直通', async () => {
  reset();
  fileSizes['/tmp/ok.jpg'] = 2 * MB;
  const result = await compressIfNeeded({ tempFilePath: '/tmp/ok.jpg', page: {} });
  assert.strictEqual(result.ok, true);
  assert.strictEqual(result.tempFilePath, '/tmp/ok.jpg');
  assert.strictEqual(compressImageCalls, 0);
}]);

tests.push(['size 未知且查不到时放行（后端兜底）', async () => {
  reset();
  const result = await compressIfNeeded({ tempFilePath: '/tmp/unknown.jpg', page: {} });
  assert.strictEqual(result.ok, true);
  assert.strictEqual(result.tempFilePath, '/tmp/unknown.jpg');
}]);

tests.push(['超限图一级 quality 压缩后达标', async () => {
  reset();
  compressImageImpl = (opts) => opts.success({ tempFilePath: '/tmp/c1.jpg' });
  fileSizes['/tmp/c1.jpg'] = 3 * MB;
  const result = await compressIfNeeded({ tempFilePath: '/tmp/big.jpg', size: 6 * MB, page: {} });
  assert.strictEqual(result.ok, true);
  assert.strictEqual(result.tempFilePath, '/tmp/c1.jpg');
  assert.strictEqual(result.size, 3 * MB);
}]);

tests.push(['一级仍超，二级 canvas 缩放转 JPEG 后达标', async () => {
  reset();
  compressImageImpl = (opts) => opts.success({ tempFilePath: '/tmp/c1.jpg' });
  fileSizes['/tmp/c1.jpg'] = 6 * MB;   // 一级后仍超
  fileSizes['/tmp/scaled.jpg'] = 2 * MB;
  const result = await compressIfNeeded({ tempFilePath: '/tmp/big.jpg', size: 8 * MB, page: {} });
  assert.strictEqual(result.ok, true);
  assert.strictEqual(result.tempFilePath, '/tmp/scaled.jpg');
  // 导出必须是 JPEG（PNG 透明通道转白底）
  assert.strictEqual(canvasExportOpts.fileType, 'jpg');
  assert.strictEqual(canvasExportOpts.destWidth, 2048);
  assert.strictEqual(canvasExportOpts.destHeight, Math.round(3000 * 2048 / 4000));
  // 白底填充在绘制前
  assert.strictEqual(lastCanvasCtx.fillStyle, '#ffffff');
  assert.strictEqual(lastCanvasCtx.fillRectCalls.length, 1);
  assert.strictEqual(lastCanvasCtx.drawImageCalls.length, 1);
}]);

tests.push(['一级压缩失败自动落到二级缩放', async () => {
  reset();
  compressImageImpl = (opts) => opts.fail({});
  fileSizes['/tmp/scaled.jpg'] = 1 * MB;
  const result = await compressIfNeeded({ tempFilePath: '/tmp/big.png', size: 9 * MB, page: {} });
  assert.strictEqual(result.ok, true);
  assert.strictEqual(result.tempFilePath, '/tmp/scaled.jpg');
}]);

tests.push(['canvas 压缩成功但查不到大小时放行（与入口的未知放行策略一致）', async () => {
  reset();
  compressImageImpl = (opts) => opts.success({ tempFilePath: '/tmp/c1.jpg' });
  fileSizes['/tmp/c1.jpg'] = 6 * MB; // 一级后仍超
  // /tmp/scaled.jpg 故意不入 fileSizes：getFileInfo 失败、大小未知
  const result = await compressIfNeeded({ tempFilePath: '/tmp/big.jpg', size: 8 * MB, page: {} });
  assert.strictEqual(result.ok, true);
  assert.strictEqual(result.tempFilePath, '/tmp/scaled.jpg');
}]);

tests.push(['两级后仍超限返回 ok=false', async () => {
  reset();
  compressImageImpl = (opts) => opts.success({ tempFilePath: '/tmp/c1.jpg' });
  fileSizes['/tmp/c1.jpg'] = 7 * MB;
  fileSizes['/tmp/scaled.jpg'] = 6 * MB;
  const result = await compressIfNeeded({ tempFilePath: '/tmp/huge.jpg', size: 20 * MB, page: {} });
  assert.strictEqual(result.ok, false);
}]);

tests.push(['无 compressImage 且无 canvas 时超限返回 ok=false', async () => {
  reset();
  const originalCompress = global.wx.compressImage;
  delete global.wx.compressImage;
  canvasUnavailable = true;
  try {
    const result = await compressIfNeeded({ tempFilePath: '/tmp/big.jpg', size: 6 * MB, page: {} });
    assert.strictEqual(result.ok, false);
  } finally {
    global.wx.compressImage = originalCompress;
  }
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
  global.wx = originalWx;
  if (failed > 0) {
    process.exit(1);
  }
  console.log('ALL PASS');
})();
