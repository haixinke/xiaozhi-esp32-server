const assert = require('assert');
const fs = require('fs');
const path = require('path');
const Module = require('module');

const pageDir = __dirname;
const pagePath = require.resolve('./collection-card');
const template = fs.readFileSync(path.join(pageDir, 'collection-card.wxml'), 'utf8');
const styles = fs.readFileSync(path.join(pageDir, 'collection-card.wxss'), 'utf8');
const script = fs.readFileSync(path.join(pageDir, 'collection-card.js'), 'utf8');
const originalLoad = Module._load;
const originalPage = global.Page;

let pageConfig;
let inviteMineResult = null;
let inviteMineError = null;

Module._load = function (request, parent, isMain) {
  if (parent && parent.filename === pagePath) {
    if (request === '../../utils/pet-store') {
      return {
        getPet: () => null,
        getPetById: (petId) => (petId === 'pet-b'
          ? { id: 'pet-b', prototype: '玉兔', name: '小玉', hatchedAt: Date.now(), collectionCards: [{ id: 'c3', brief: '兔首卡', style: '锦' }] }
          : null),
        todayKey: () => '2026-10-04'
      };
    }
    if (request === '../../utils/invite-api') {
      return {
        getMine: async () => {
          if (inviteMineError) throw inviteMineError;
          return inviteMineResult;
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
    data: {
      ...pageConfig.data,
      card: { name: '蛋宝宝', serial: '001' }
    },
    setData(changes) { this.data = { ...this.data, ...changes }; }
  };
}

// 性别必须用 SVG 图标渲染：iOS 真机对 ♀/♂ 强制回退到符号/emoji 字体且基线偏低，
// 字体方案（去 FE0F、调行高）均无法垂直居中；未知性别回退文本 genderLabel
assert.match(
  template,
  /<view wx:if="{{genderClass}}" class="gender-icon {{genderClass}}" aria-label="{{genderLabel}}"><\/view>/,
  '性别必须通过 gender-icon SVG 图标展示'
);
assert.match(
  template,
  /<text wx:else class="stat-value">{{genderLabel}}<\/text>/,
  '未知性别必须回退到文本 genderLabel'
);
assert.match(styles, /\.gender-icon--female \{ background-image: url\("data:image\/svg\+xml/, '女性图标必须是内联 SVG');
assert.match(styles, /\.gender-icon--male \{ background-image: url\("data:image\/svg\+xml/, '男性图标必须是内联 SVG');
// 星座符号（♈ 等）仍是字符渲染，必须保留放开裁切的修饰类，避免 iOS 裁掉下半截
assert.match(
  styles,
  /\.stat-value--symbol \{ overflow: visible; line-height: normal; \}/,
  '符号修饰类必须放开 overflow 裁切并使用自然行高'
);
// genderLabel 仅供 Canvas 分享卡与文本回退，必须是文本形态 ♀/♂（不带 U+FE0F）
assert.match(script, /if \(value === 'FEMALE'\) return '♀';/, '女性文本回退必须是文本形态♀');
assert.match(script, /if \(value === 'MALE'\) return '♂';/, '男性文本回退必须是文本形态♂');
assert.doesNotMatch(script, /\ufe0f/, '性别符号不得带 U+FE0F 变体选择符');
assert.match(script, /function genderClass\(value\)/, '必须提供 genderClass 映射 SVG 图标修饰类');
assert.match(
  template,
  /<button wx:if="{{shareReady}}" class="secondary" open-type="share">分享给好友<\/button>/,
  'share button must remain unavailable until the invitation lookup has completed'
);

(async () => {
  require('./collection-card');
  assert.ok(pageConfig, 'collection-card page should be registered');

// --- onLoad 按 petId 定位宠物（"我的卡册"跨宠物跳转）---
const wxStubs = {
  toastTitles: [],
  navigateBackCount: 0,
  shareMenus: []
};
global.wx = {
  getStorageSync() { return ''; },
  showToast(options) { wxStubs.toastTitles.push(options.title); },
  navigateBack() { wxStubs.navigateBackCount += 1; },
  hideShareMenu(options) { wxStubs.shareMenus.push(options); },
  showShareMenu() {}
};
const onLoadPage = {
  ...Object.fromEntries(Object.entries(pageConfig).filter(([, value]) => typeof value === 'function')),
  data: { ...pageConfig.data },
  setData(changes) { this.data = { ...this.data, ...changes }; }
};
onLoadPage.onLoad({ petId: 'pet-b', index: '0' });
assert.strictEqual(onLoadPage.data.card.id, 'c3', 'onLoad 按 petId 取到对应宠物的卡');
assert.strictEqual(onLoadPage.data.card.name, '小玉', '卡片展示所属宠物名');
assert.strictEqual(onLoadPage.data.pet.name, '小玉', 'pet 为 petId 对应宠物');
wxStubs.toastTitles.length = 0;
onLoadPage.onLoad({ petId: 'pet-x', index: '0' });
assert.strictEqual(wxStubs.toastTitles[0], '收藏卡信息已更新，请刷新后重试', 'petId 未命中缓存时提示并返回，不回退激活宠物');
// navigateBack 有 600ms 延迟，等它触发后再断言
await new Promise((resolve) => setTimeout(resolve, 700));
assert.strictEqual(wxStubs.navigateBackCount, 1, '无卡时返回上一页');
delete global.wx;

  const loadingSharePage = makePage();
  assert.strictEqual(
    loadingSharePage.onShareAppMessage(),
    false,
    'friend sharing must be unavailable while the invitation lookup is pending'
  );

  inviteMineResult = { code: 'EGG-ABCD', remaining: 3, status: 1 };
  inviteMineError = null;
  const activeInvitePage = makePage();
  await activeInvitePage.loadShareInviteCode();
  assert.deepStrictEqual(
    activeInvitePage.onShareAppMessage(),
    {
      title: '我孵化了蛋宝宝，编号 001',
      path: '/pages/home/home?v=1&source=home_share&inviteCode=EGG-ABCD'
    },
    'friend sharing should include an active personal invitation code'
  );
  assert.deepStrictEqual(
    activeInvitePage.onShareTimeline(),
    { title: '我孵化了蛋宝宝，编号 001' },
    'timeline sharing must not carry or prefill an invitation code'
  );

  inviteMineResult = null;
  inviteMineError = new Error('network unavailable');
  const fallbackSharePage = makePage();
  await fallbackSharePage.loadShareInviteCode();
  assert.deepStrictEqual(
    fallbackSharePage.onShareAppMessage(),
    {
      title: '我孵化了蛋宝宝，编号 001',
      path: '/pages/home/home?v=1&source=home_share'
    },
    'friend sharing should fall back to a normal home share when no usable code is available'
  );

  inviteMineResult = { code: 'EGG-USED', remaining: 0, status: 1 };
  inviteMineError = null;
  const exhaustedInvitePage = makePage();
  await exhaustedInvitePage.loadShareInviteCode();
  assert.deepStrictEqual(
    exhaustedInvitePage.onShareAppMessage(),
    {
      title: '我孵化了蛋宝宝，编号 001',
      path: '/pages/home/home?v=1&source=home_share'
    },
    'friend sharing should not include an exhausted invitation code'
  );

  console.log('collection-card.test.js: ALL PASS');
})().finally(() => {
  Module._load = originalLoad;
  global.Page = originalPage;
  delete require.cache[pagePath];
}).catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
