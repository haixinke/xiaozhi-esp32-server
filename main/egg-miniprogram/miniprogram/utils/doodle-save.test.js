const assert = require('assert');
const Module = require('module');

const originalLoad = Module._load;
const calls = [];
let uploadResult = 'https://oss.example/doodles/pet-1.png';
let uploadError = null;
let saveResult = { ok: true };
let saveError = null;
let shellError = null;
let shellResult = true;

const doodleApi = {
  async uploadDoodleImage(tempFilePath) {
    calls.push(['upload', tempFilePath]);
    if (uploadError) throw uploadError;
    return uploadResult;
  }
};

const petStore = {
  async saveDoodle(artUrl, petId) {
    calls.push(['saveDoodle', artUrl, petId]);
    if (saveError) throw saveError;
    return saveResult;
  },
  saveDoodleShell(petId, operations) {
    if (shellError) throw shellError;
    calls.push(['saveDoodleShell', petId, operations]);
    return shellResult;
  }
};

function reset() {
  calls.length = 0;
  uploadResult = 'https://oss.example/doodles/pet-1.png';
  uploadError = null;
  saveResult = { ok: true };
  saveError = null;
  shellError = null;
  shellResult = true;
}

function loadDoodleSave() {
  Module._load = function (request) {
    if (request === './doodle-api') return doodleApi;
    if (request === './pet-store') return petStore;
    return originalLoad.apply(this, arguments);
  };

  try {
    return require('./doodle-save');
  } catch (error) {
    if (error && error.code === 'MODULE_NOT_FOUND' && error.message.includes("'./doodle-save'")) return {};
    throw error;
  } finally {
    Module._load = originalLoad;
  }
}

(async () => {
  const { saveArtwork } = loadDoodleSave();
  assert.strictEqual(typeof saveArtwork, 'function', 'doodle-save exports saveArtwork');

  reset();
  let invalidOperationsResult;
  try {
    invalidOperationsResult = await saveArtwork({
      petId: 'pet-1',
      operations: null,
      exportArtwork: async () => '/tmp/doodle.png'
    });
  } catch (error) {
    invalidOperationsResult = { rejected: true, error };
  }
  assert.deepStrictEqual(invalidOperationsResult, {
    ok: false,
    message: '画作没有保存好，请再试一次'
  }, 'invalid operations return the normalized failure result instead of rejecting');
  assert.deepStrictEqual(calls, [], 'invalid operations have no persistence side effects');

  reset();
  const missingPetResult = await saveArtwork({
    petId: '',
    operations: [{ type: 'stroke' }],
    exportArtwork: async () => '/tmp/doodle.png'
  });
  assert.deepStrictEqual(missingPetResult, {
    ok: false,
    message: '画作没有保存好，请再试一次'
  }, 'a missing pet id fails before export or upload');
  assert.deepStrictEqual(calls, [], 'a missing pet id has no persistence side effects');

  reset();
  const operations = [{ type: 'stroke', points: [[1, 2], [3, 4]] }];
  const saved = await saveArtwork({
    petId: 'pet-1',
    operations,
    exportArtwork: async () => {
      calls.push(['export']);
      return '/tmp/doodle.png';
    }
  });
  assert.deepStrictEqual(calls, [
    ['export'],
    ['upload', '/tmp/doodle.png'],
    ['saveDoodle', 'https://oss.example/doodles/pet-1.png', 'pet-1'],
    ['saveDoodleShell', 'pet-1', operations]
  ], 'non-empty artwork is exported, uploaded, saved remotely, then cached locally');
  assert.deepStrictEqual(saved, {
    ok: true,
    petId: 'pet-1',
    artUrl: 'https://oss.example/doodles/pet-1.png',
    operations,
    empty: false
  }, 'non-empty save returns the committed artwork');

  reset();
  const emptyOperations = [];
  const emptySaved = await saveArtwork({
    petId: 'pet-1',
    operations: emptyOperations,
    exportArtwork: async () => {
      calls.push(['export']);
      return '/tmp/should-not-export.png';
    }
  });
  assert.deepStrictEqual(calls, [
    ['saveDoodle', '', 'pet-1'],
    ['saveDoodleShell', 'pet-1', emptyOperations]
  ], 'empty artwork skips export and upload before caching an empty shell');
  assert.deepStrictEqual(emptySaved, {
    ok: true,
    petId: 'pet-1',
    artUrl: '',
    operations: emptyOperations,
    empty: true
  }, 'empty save returns an explicit empty result');

  reset();
  saveResult = { ok: false, message: '空画作保存失败' };
  const emptyRemoteFailed = await saveArtwork({
    petId: 'pet-1',
    operations: emptyOperations,
    exportArtwork: async () => '/tmp/should-not-export.png'
  });
  assert.deepStrictEqual(emptyRemoteFailed, { ok: false, message: '空画作保存失败' });
  assert.deepStrictEqual(calls, [
    ['saveDoodle', '', 'pet-1']
  ], 'empty remote save failure skips export and upload without overwriting the local shell');

  reset();
  const exportFailed = await saveArtwork({
    petId: 'pet-1',
    operations,
    exportArtwork: async () => { throw { userMessage: '画布导出失败' }; }
  });
  assert.deepStrictEqual(exportFailed, { ok: false, message: '画布导出失败' });
  assert.deepStrictEqual(calls, [], 'export failure does not upload, save remotely, or overwrite the local shell');

  reset();
  uploadError = { userMessage: '画作上传失败' };
  const uploadFailed = await saveArtwork({
    petId: 'pet-1',
    operations,
    exportArtwork: async () => {
      calls.push(['export']);
      return '/tmp/doodle.png';
    }
  });
  assert.deepStrictEqual(uploadFailed, { ok: false, message: '画作上传失败' });
  assert.deepStrictEqual(calls, [
    ['export'],
    ['upload', '/tmp/doodle.png']
  ], 'upload failure does not save remotely or overwrite the local shell');

  reset();
  saveResult = { ok: false, message: '今日画作保存失败' };
  const remoteFailed = await saveArtwork({
    petId: 'pet-1',
    operations,
    exportArtwork: async () => {
      calls.push(['export']);
      return '/tmp/doodle.png';
    }
  });
  assert.deepStrictEqual(remoteFailed, { ok: false, message: '今日画作保存失败' });
  assert.deepStrictEqual(calls, [
    ['export'],
    ['upload', '/tmp/doodle.png'],
    ['saveDoodle', 'https://oss.example/doodles/pet-1.png', 'pet-1']
  ], 'remote save failure does not overwrite the local shell');

  reset();
  saveError = new Error('网络连接中断');
  const remoteRejected = await saveArtwork({
    petId: 'pet-1',
    operations,
    exportArtwork: async () => {
      calls.push(['export']);
      return '/tmp/doodle.png';
    }
  });
  assert.deepStrictEqual(remoteRejected, { ok: false, message: '网络连接中断' });
  assert.deepStrictEqual(calls, [
    ['export'],
    ['upload', '/tmp/doodle.png'],
    ['saveDoodle', 'https://oss.example/doodles/pet-1.png', 'pet-1']
  ], 'remote rejection does not overwrite the local shell');

  reset();
  shellError = new Error('本地画作写入失败');
  const shellFailed = await saveArtwork({
    petId: 'pet-1',
    operations,
    exportArtwork: async () => {
      calls.push(['export']);
      return '/tmp/doodle.png';
    }
  });
  assert.deepStrictEqual(shellFailed, { ok: false, message: '本地画作写入失败' });
  assert.deepStrictEqual(calls, [
    ['export'],
    ['upload', '/tmp/doodle.png'],
    ['saveDoodle', 'https://oss.example/doodles/pet-1.png', 'pet-1']
  ], 'unexpected local shell failure is normalized after remote persistence');

  reset();
  shellResult = false;
  const shellRejected = await saveArtwork({
    petId: 'pet-1',
    operations,
    exportArtwork: async () => {
      calls.push(['export']);
      return '/tmp/doodle.png';
    }
  });
  assert.deepStrictEqual(shellRejected, {
    ok: false,
    message: '画作已上传，但本地编辑记录保存失败，请重试'
  }, 'a reported local storage failure must not mark the editor revision as saved');

  console.log('doodle-save.test.js: ALL PASS');
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
