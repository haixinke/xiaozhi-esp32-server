const petStore = require('./pet-store');
const auth = require('./auth');

// 多个页面生命周期可同时进入；同一轮检查共享服务端恢复，避免重复请求改变当前宠物选择。
let accessCheck = null;

function returnHome() {
  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : [];
  const current = pages.length ? pages[pages.length - 1] : null;
  // 旧业务请求可能在用户打开隐私说明后才返回，不把阅读说明强行导航走。
  if (current && ['pages/privacy/privacy', 'pages/terms/terms'].includes(current.route)) return;
  if (current && current.route === 'pages/home/home') {
    if (typeof current.syncPhoneAuthorization === 'function') current.syncPhoneAuthorization();
  } else {
    wx.reLaunch({ url: '/pages/home/home' });
  }
}

// 服务端门禁优先于本地会话：收到 10228 后保留登录态，回首页完成真实绑定。
function requirePhoneBinding(context) {
  if (context && !auth.isPhoneBindingContextCurrent(context)) return;
  const app = getApp();
  app.globalData.phoneBindingRequired = true;
  returnHome();
}

function checkAccess() {
  if (accessCheck) return accessCheck;
  accessCheck = evaluateAccess();
  accessCheck.finally(() => { accessCheck = null; });
  return accessCheck;
}

// 登录或宠物恢复等待期间可能完成绑定或切换账号，过期结论必须按当前会话重新判断。
async function evaluateAccess() {
  const app = getApp();
  try {
    const context = auth.getPhoneBindingContext();
    const session = await app.ensureLogin();
    if (!auth.isPhoneBindingContextCurrent(context)) return evaluateAccess();
    if (!session || !session.userId) { returnHome(); return false; }
    if (session.hasPhone === true && !app.globalData.phoneBindingRequired) return true;
    const snapshot = await petStore.refreshPets();
    if (!auth.isPhoneBindingContextCurrent(context)) return evaluateAccess();
    const pets = (snapshot || petStore.getPetSnapshot()).pets;
    if (pets.some((pet) => pet.hatchStatus === 'HATCHED')) {
      requirePhoneBinding(context);
      return false;
    }
    // 渠道替换或删除可使账号重新只剩孵化中的蛋，旧门槛不能挡住新的孵化体验。
    app.globalData.phoneBindingRequired = false;
    return true;
  } catch (error) {
    // 状态未知时回首页的加载/重试流程，不能把恢复失败当成没有已破壳宠物。
    returnHome();
    return false;
  }
}

// 暂时隐藏时保留尚未运行的初始化；卸载后永久取消，避免恢复页面缺少画布或连接。
async function initializePage(page, definition) {
  const active = () => page._phoneGateAllowed && !page._phoneGateInactive && !page._phoneGateDisposed;
  if (!active()) return;
  if (page._phoneGateLoadArgs && !page._phoneGateLoadStarted) {
    page._phoneGateLoadStarted = true;
    page._phoneGateLoadPromise = Promise.resolve(definition.onLoad && definition.onLoad.apply(page, page._phoneGateLoadArgs));
  }
  await page._phoneGateLoadPromise;
  if (!active()) return;
  if (page._phoneGateReadyArgs && !page._phoneGateReadyStarted) {
    page._phoneGateReadyStarted = true;
    page._phoneGateReadyPromise = Promise.resolve(definition.onReady && definition.onReady.apply(page, page._phoneGateReadyArgs));
  }
  await page._phoneGateReadyPromise;
}

// 受限页面显式声明此包装；隐私、条款、领取和孵化页面保持原生命周期。
function withPhoneGate(definition) {
  const methods = Object.fromEntries(Object.entries(definition).map(([name, value]) => [
    name,
    typeof value === 'function' ? function (...args) {
      if (!this._phoneGateCleaningUp && (this._phoneGateAllowed !== true || this._phoneGateInactive || this._phoneGateDisposed)) return;
      return value.apply(this, args);
    } : value
  ]));
  return {
    ...methods,
    onLoad(...args) {
      this._phoneGateLoadArgs = args;
      this._phoneGateInactive = false;
      this._phoneGateAllowed = false;
      this._phoneGateFirstShow = true;
      const revision = this._phoneGateRevision = (this._phoneGateRevision || 0) + 1;
      this._phoneGateReady = checkAccess().then(async (allowed) => {
        if (this._phoneGateRevision !== revision || this._phoneGateDisposed) return false;
        this._phoneGateAllowed = allowed;
        await initializePage(this, definition);
        return allowed;
      });
      return this._phoneGateReady;
    },
    onShow(...args) {
      if (this._phoneGateDisposed) return Promise.resolve();
      this._phoneGateInactive = false;
      const firstShow = this._phoneGateFirstShow && this._phoneGateReady;
      const revision = firstShow ? this._phoneGateRevision
        : (this._phoneGateRevision = (this._phoneGateRevision || 0) + 1);
      const ready = firstShow || checkAccess();
      this._phoneGateFirstShow = false;
      this._phoneGateAllowed = false;
      return ready.then(async (allowed) => {
        if (this._phoneGateRevision !== revision || this._phoneGateDisposed) return;
        this._phoneGateAllowed = allowed;
        await initializePage(this, definition);
        if (allowed && !this._phoneGateInactive && !this._phoneGateDisposed && definition.onShow) return definition.onShow.apply(this, args);
      });
    },
    onReady(...args) {
      this._phoneGateReadyArgs = args;
      const revision = this._phoneGateRevision;
      return (this._phoneGateReady || checkAccess()).then(async (allowed) => {
        if (this._phoneGateRevision !== revision || this._phoneGateDisposed) return;
        this._phoneGateAllowed = allowed;
        await initializePage(this, definition);
      });
    },
    onHide(...args) {
      // 音频/计时器清理始终执行，未完成初始化留待下一次可见且验证通过时恢复。
      this._phoneGateCleaningUp = true;
      try {
        if (definition.onHide) return definition.onHide.apply(this, args);
      } finally {
        this._phoneGateCleaningUp = false;
        this._phoneGateInactive = true;
        this._phoneGateAllowed = false;
        this._phoneGateFirstShow = false;
        this._phoneGateRevision = (this._phoneGateRevision || 0) + 1;
      }
    },
    onUnload(...args) {
      this._phoneGateDisposed = true;
      this._phoneGateCleaningUp = true;
      try {
        if (definition.onUnload) return definition.onUnload.apply(this, args);
      } finally {
        this._phoneGateCleaningUp = false;
        this._phoneGateInactive = true;
        this._phoneGateAllowed = false;
        this._phoneGateRevision = (this._phoneGateRevision || 0) + 1;
      }
    }
  };
}

module.exports = { withPhoneGate, requirePhoneBinding };
