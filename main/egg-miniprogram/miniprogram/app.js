const auth = require('./utils/auth');
const { post } = require('./utils/request');

const AUTH_FIELDS = ['token', 'userId', 'openid', 'isNewUser', 'hasPhone', 'agentId'];

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

  onShow() {
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
    if (route === 'pages/welcome/welcome' || route === '/pages/welcome/welcome') return;
    if (this.globalData.welcomeCompleted === true) return;
    if (!auth.getSession()) return;
    if (this.globalData.hasPhone !== true) {
      wx.reLaunch({ url: '/pages/welcome/welcome' });
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
