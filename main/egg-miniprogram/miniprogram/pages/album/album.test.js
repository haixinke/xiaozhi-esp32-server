const assert = require('assert');
const fs = require('fs');
const path = require('path');
const Module = require('module');

const pageDir = __dirname;
const pagePath = require.resolve('./album');
const template = fs.readFileSync(path.join(pageDir, 'album.wxml'), 'utf8');
// WXML 必须把 petId 放进 dataset，否则点击跳转缺参数
assert.match(template, /data-pet-id="\{\{item\.petId\}\}"/, '卡片点击必须携带 petId');

let pageConfig = null;
let petsProvider = null;
let listPetsResult = null;
let listPetsError = null;
let navigateToUrl = null;

const storage = new Map();
global.wx = {
  getStorageSync(key) { return storage.has(key) ? storage.get(key) : ''; },
  setStorageSync(key, value) { storage.set(key, value); },
  removeStorageSync(key) { storage.delete(key); },
  navigateTo(options) { navigateToUrl = options.url; }
};

const originalLoad = Module._load;
Module._load = function (request, parent, isMain) {
  if (parent && parent.filename === pagePath) {
    if (request === '../../utils/pet-store') {
      const store = {
        getPetById: (petId) => (petsProvider ? petsProvider.find((p) => String(p.id) === String(petId)) || null : null),
        getAllPets: () => (petsProvider ? petsProvider.slice() : []),
        cachePets: (pets) => { if (pets && pets.length) petsProvider = pets.map((vo) => vo); },
        mapPetFromVO: (vo) => ({ id: vo.id, prototype: vo.prototype, name: vo.nickname || '', collectionCards: vo.collectionCards || [] })
      };
      return store;
    }
    if (request === '../../utils/pet-api') {
      return {
        listPets: async () => {
          if (listPetsError) throw listPetsError;
          return listPetsResult;
        }
      };
    }
  }
  return originalLoad.call(this, request, parent, isMain);
};

global.Page = (config) => { pageConfig = config; };

function makePage() {
  return {
    ...Object.fromEntries(Object.entries(pageConfig).filter(([, value]) => typeof value === 'function')),
    data: JSON.parse(JSON.stringify(pageConfig.data)),
    setData(changes) { this.data = { ...this.data, ...changes }; }
  };
}

(async () => {
  require('./album');
  assert.ok(pageConfig, 'album page should be registered');

  // --- 多宠物聚合：拍平所有宠物的收藏卡，按 createDate 倒序，tie 按 sortOrder 升序 ---
  petsProvider = [
    {
      id: 'pet-a', prototype: '锦鲤', name: '小金',
      collectionCards: [
        { id: 'c1', brief: '首卡', createDate: '2026-10-01 10:00:00', sortOrder: 0 },
        { id: 'c2', brief: '第二张', createDate: '2026-10-03 09:00:00', sortOrder: 1 }
      ]
    },
    {
      id: 'pet-b', prototype: '玉兔', name: '小玉',
      collectionCards: [
        { id: 'c3', brief: '兔首卡', createDate: '2026-10-03 09:00:00', sortOrder: 0 }
      ]
    }
  ];
  listPetsResult = null;
  listPetsError = new Error('network unavailable');
  const page = makePage();
  await page.onShow();

  // 网络失败时回退缓存渲染（Q3）
  assert.strictEqual(page.data.cards.length, 3, '全部宠物的卡都被聚合');
  assert.strictEqual(page.data.cards[0].id, 'c3', 'createDate 最新的排最前');
  assert.strictEqual(page.data.cards[1].id, 'c2', 'createDate 倒序');
  assert.strictEqual(page.data.cards[2].id, 'c1', 'createDate 最早排最后');
  // createDate 相同的 c2 与 c3：tie 按 sortOrder 升序 → c3(0) 在 c2(1) 前
  assert.strictEqual(page.data.cards[0].id, 'c3', 'tie-break by sortOrder asc');
  assert.strictEqual(page.data.cards[1].id, 'c2', 'tie-break by sortOrder asc (2nd)');
  assert.strictEqual(page.data.cards[0].name, '小玉', '每张卡带所属宠物名');
  assert.strictEqual(page.data.cards[2].name, '小金', '每张卡带所属宠物名 (2)');
  assert.strictEqual(page.data.cards[0].petId, 'pet-b', '每张卡带 petId 供跳转');
  assert.strictEqual(page.data.cards[0].index, 0, 'index 为该宠物 collectionCards 内下标');

  // --- 跳转：单卡页带 petId（Q1）---
  // WXML 用 data-pet-id，dataset 归一化为 camelCase petId
  page.onOpen({ currentTarget: { dataset: { index: 1, petId: 'pet-a' } } });
  assert.strictEqual(
    navigateToUrl,
    '/pages/collection-card/collection-card?petId=pet-a&index=1',
    '跳转单卡页必须带 petId 与该宠物内的 index'
  );

  // --- 空宠物/空卡 ---
  petsProvider = [{ id: 'pet-c', prototype: '玉兔', name: '蛋蛋', collectionCards: [] }];
  const emptyPage = makePage();
  await emptyPage.onShow();
  assert.strictEqual(emptyPage.data.cards.length, 0, '无卡宠物不产生条目');
  assert.match(template, /卡册还是空的/, '空态文案保留');

  // --- 网络刷新成功：更新缓存 ---
  petsProvider = [];
  listPetsError = null;
  listPetsResult = [
    { id: 'pet-a', prototype: '锦鲤', nickname: '小金', collectionCards: [{ id: 'c1', createDate: '2026-09-01 08:00:00', sortOrder: 0 }] }
  ];
  const refreshedPage = makePage();
  await refreshedPage.onShow();
  assert.strictEqual(refreshedPage.data.cards.length, 1, '刷新成功后按接口数据渲染');
  assert.strictEqual(refreshedPage.data.cards[0].name, '小金', '刷新后 pet 名取自后端 nickname');

  console.log('album.test.js: ALL PASS');
})().finally(() => {
  Module._load = originalLoad;
  delete require.cache[pagePath];
}).catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
