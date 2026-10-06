const auth = require('./utils/auth');
const { post } = require('./utils/request');
const { captureNfcClaimIntent } = require('./utils/nfc-claim-intent');
const shareInvite = require('./utils/share-invite');

const AUTH_FIELDS = ['token', 'userId', 'openid', 'isNewUser', 'hasPhone', 'agentId'];

// 免登录公开页白名单：不受"未绑手机号踢回 welcome"约束（app 级守卫见 redirectUnboundToWelcome）
const PUBLIC_PAGES = ['pages/welcome/welcome', 'pages/postcard/postcard'];

function loginWithWechat() {
  return new Promise((resolve, reject) => {
    wx.login({
      success: (result) => {
        if (!result || !result.code) {
          reject(new Error('微信登录失败，请重试'));
          return;
        }
        resolve(result.code);
      },
      fail: () => reject(new Error('微信登录失败，请重试'))
    });
  }).then((code) => post('/wechat/login', { code }, { anonymous: true }));
}

function saveShareInviteContext(options) {
  const context = shareInvite.parseEntryOptions(options);
  if (context) shareInvite.savePending(context);
}

App({
  globalData: {
    version: '1.0.0-mvp',
    authReady: null,
    token: null,
    userId: null,
    openid: null,
    isNewUser: null,
    hasPhone: null,
    agentId: null,
    welcomeCompleted: false,
    launchPath: 'pages/home/home'
  },

  onLaunch(options) {
    captureNfcClaimIntent(options);
    saveShareInviteContext(options);
    this.globalData.launchPath = options && options.path
      ? options.path
      : 'pages/home/home';
    this.globalData.authReady = this.ensureLogin()
      .then((session) => {
        this.redirectUnboundToWelcome(this.globalData.launchPath);
        return session;
      })
      .catch(() => {
        this.applySession(null);
        return null;
      });
  },

  onShow(options) {
    captureNfcClaimIntent(options);
    saveShareInviteContext(options);
    const session = auth.getSession();
    if (session && auth.isExpired()) {
      this.clearLoginState();
      return;
    }
    if (session && auth.isExpiringSoon()) {
      this.silentLogin().catch(() => null);
    }
    const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : [];
    const currentRoute = pages.length ? pages[pages.length - 1].route : this.globalData.launchPath;
    this.redirectUnboundToWelcome(currentRoute);
  },

  redirectUnboundToWelcome(route) {
    // 免登录公开页白名单：这些路径不受"未绑手机号踢回 welcome"约束。
    // postcard 为好友免授权分享入口（点卡片直达，未登录/未绑定都直接可看）
    const normalized = String(route || '').replace(/^\//, '');
    if (PUBLIC_PAGES.indexOf(normalized) !== -1) {
      this._welcomeRedirecting = false;
      return;
    }
    if (this.globalData.welcomeCompleted === true) return;
    if (this._welcomeRedirecting === true) return;
    if (!auth.getSession()) return;
    if (this.globalData.hasPhone !== true) {
      this._welcomeRedirecting = true;
      const redirect = () => wx.reLaunch({ url: '/pages/welcome/welcome' });
      if (typeof wx.nextTick === 'function') wx.nextTick(redirect);
      else setTimeout(redirect, 0);
    }
  },

  applySession(session) {
    AUTH_FIELDS.forEach((field) => {
      this.globalData[field] = session ? session[field] : null;
    });
  },

  silentLogin() {
    if (this._loginPromise) return this._loginPromise;
    this._loginPromise = loginWithWechat()
      .then((loginData) => {
        const session = auth.saveSession(loginData);
        this.applySession(session);
        return session;
      });
    this._loginPromise.then(
      () => { this._loginPromise = null; },
      () => { this._loginPromise = null; }
    );
    return this._loginPromise;
  },

  ensureLogin() {
    const session = auth.getSession();
    if (session && !auth.isExpired() && !auth.isExpiringSoon()) {
      this.applySession(session);
      return Promise.resolve(session);
    }
    return this.silentLogin();
  },

  clearLoginState() {
    auth.clearSession();
    this.applySession(null);
  }
});
