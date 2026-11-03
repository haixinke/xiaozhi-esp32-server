const assert = require('assert');

const modulePath = require.resolve('./remote-image').replace('remote-image.js', 'pet-icons.js');
const originalWx = global.wx;
const FISH_URL = 'https://oss.eggbabe.com/default-ip/fish/fish-icon.png';
const RABBIT_URL = 'https://oss.eggbabe.com/default-ip/rabbit/rabbit-icon.png';

// 清空模块内存，保留文件与 storage，用于模拟小程序冷启动。
function fresh() {
  delete require.cache[modulePath];
  return require(modulePath);
}

// 仅模拟平台文件与下载接口，缓存行为使用真实工具模块。
function platform() {
  const state = { downloads: [], saves: [], storage: new Map(), files: new Set(),
    saveFails: false, saveThrows: false, storageFails: false, storageReadFails: false };
  const fs = {
    accessSync(path) {
      if (!state.files.has(path)) throw new Error('missing file');
    },
    saveFile(options) {
      state.saves.push(options);
      if (state.saveThrows) throw new Error('save unavailable');
      if (state.saveFails) return options.fail({ errMsg: 'save failed' });
      const savedFilePath = `wxfile://saved/icon-${state.saves.length}.png`;
      state.files.delete(options.tempFilePath);
      state.files.add(savedFilePath);
      options.success({ savedFilePath });
    }
  };
  global.wx = {
    getFileSystemManager: () => fs,
    getStorageSync(key) {
      if (state.storageReadFails) throw new Error('storage read failed');
      return state.storage.get(key);
    },
    setStorageSync(key, value) {
      if (state.storageFails) throw new Error('storage write failed');
      state.storage.set(key, value);
    },
    downloadFile(options) { state.downloads.push(options); }
  };
  state.succeed = (index, statusCode = 200) => {
    const tempFilePath = `wxfile://tmp/icon-${index}.png`;
    state.files.add(tempFilePath);
    state.downloads[index].success({ statusCode, tempFilePath });
  };
  return state;
}

try {
  delete global.wx;
  const icons = fresh();
  for (const type of ['锦鲤', 'KOI']) assert.strictEqual(icons.getPetIconUrl(type), FISH_URL);
  for (const type of ['玉兔', 'YT', '未知', undefined, '__proto__']) {
    assert.strictEqual(icons.getPetIconUrl(type), RABBIT_URL);
  }
  let result;
  icons.loadPetIcon('锦鲤', (path) => { result = path; });
  assert.strictEqual(result, FISH_URL, '无平台时仅降级，不缓存远程地址');

  // 首次持久化、同次会话命中、模块重载后命中，均验证文件仍然存在。
  let state = platform();
  let cache = icons;
  cache.loadPetIcon('锦鲤', (path) => { result = path; });
  assert.strictEqual(state.downloads.length, 1);
  assert.strictEqual(state.downloads[0].url, FISH_URL);
  assert.strictEqual(state.downloads[0].timeout, 15000);
  state.succeed(0);
  assert.strictEqual(result, 'wxfile://saved/icon-1.png');
  assert.strictEqual(state.storage.size, 1);
  const record = [...state.storage.values()][0];
  assert.strictEqual(record.url, FISH_URL);
  assert.strictEqual(record.path, result);
  cache.loadPetIcon('KOI', (path) => { result = path; });
  assert.strictEqual(state.downloads.length, 1);
  cache = fresh();
  cache.loadPetIcon('锦鲤', (path) => { result = path; });
  assert.strictEqual(result, record.path);
  assert.strictEqual(state.downloads.length, 1, '冷启动读取持久缓存，不重新下载');
  state.files.clear();
  cache.loadPetIcon('锦鲤', (path) => { result = path; });
  assert.strictEqual(state.downloads.length, 2, '本地文件被清理后重新下载');
  state.succeed(1);
  assert.strictEqual(result, 'wxfile://saved/icon-2.png');

  // 两个页面并发请求同一原型，只下载和保存一次。
  state = platform();
  cache = fresh();
  const results = [];
  cache.loadPetIcon('玉兔', (path) => results.push(path));
  cache.loadPetIcon('YT', (path) => results.push(path));
  assert.strictEqual(state.downloads.length, 1);
  assert.strictEqual(state.downloads[0].url, RABBIT_URL);
  state.succeed(0);
  assert.deepStrictEqual(results, ['wxfile://saved/icon-1.png', 'wxfile://saved/icon-1.png']);
  assert.strictEqual(state.saves.length, 1);

  // 网络、HTTP 与缺失临时文件失败不进入缓存，下次允许重试。
  state = platform();
  cache = fresh();
  cache.loadPetIcon('玉兔', (path) => { result = path; });
  state.downloads[0].fail({ errMsg: 'offline' });
  assert.strictEqual(result, null);
  cache.loadPetIcon('玉兔', (path) => { result = path; });
  state.succeed(1, 404);
  assert.strictEqual(result, null);
  cache.loadPetIcon('玉兔', (path) => { result = path; });
  state.downloads[2].success({ statusCode: 200 });
  assert.strictEqual(result, null);
  cache.loadPetIcon('玉兔', (path) => { result = path; });
  state.succeed(3);
  assert.strictEqual(state.downloads.length, 4);
  assert.strictEqual(result, 'wxfile://saved/icon-1.png');

  // 磁盘保存失败仍展示临时文件，会话内复用；重启后再尝试持久化。
  for (const failure of ['saveFails', 'saveThrows']) {
    state = platform();
    state[failure] = true;
    cache = fresh();
    cache.loadPetIcon('锦鲤', (path) => { result = path; });
    state.succeed(0);
    assert.strictEqual(result, 'wxfile://tmp/icon-0.png');
    cache.loadPetIcon('锦鲤', (path) => { result = path; });
    assert.strictEqual(state.downloads.length, 1);
    assert.strictEqual(state.storage.size, 0);
    cache = fresh();
    cache.loadPetIcon('锦鲤', () => {});
    assert.strictEqual(state.downloads.length, 2);
  }

  // storage 异常不影响展示，保存后的文件在当前会话仍然可复用。
  state = platform();
  state.storageFails = true;
  state.storageReadFails = true;
  cache = fresh();
  cache.loadPetIcon('锦鲤', (path) => { result = path; });
  state.succeed(0);
  assert.strictEqual(result, 'wxfile://saved/icon-1.png');
  cache.loadPetIcon('锦鲤', (path) => { result = path; });
  assert.strictEqual(state.downloads.length, 1);

  // 缓存元数据与 URL 不匹配时不复用，防止原型图标串用。
  state = platform();
  state.files.add('wxfile://saved/wrong.png');
  state.storage.set('pet_icon_fish_v1', { url: RABBIT_URL, path: 'wxfile://saved/wrong.png' });
  cache = fresh();
  cache.loadPetIcon('锦鲤', (path) => { result = path; });
  assert.strictEqual(state.downloads.length, 1);
  state.succeed(0);
  assert.strictEqual(result, 'wxfile://saved/icon-1.png');

  // 平台缺少文件系统时仍可展示并在本次会话复用临时文件。
  state = platform();
  delete global.wx.getFileSystemManager;
  cache = fresh();
  cache.loadPetIcon('玉兔', (path) => { result = path; });
  state.succeed(0);
  assert.strictEqual(result, 'wxfile://tmp/icon-0.png');
  cache.loadPetIcon('玉兔', (path) => { result = path; });
  assert.strictEqual(state.downloads.length, 1);

  console.log('pet-icons.test.js: ALL PASS');
} finally {
  if (originalWx === undefined) delete global.wx;
  else global.wx = originalWx;
  delete require.cache[modulePath];
}
