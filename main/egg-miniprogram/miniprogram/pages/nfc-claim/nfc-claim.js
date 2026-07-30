const auth = require('../../utils/auth');
const wechatApi = require('../../utils/wechat-api');
const nfcClaimApi = require('../../utils/nfc-claim-api');
const { getPendingNfcClaimIntent, clearPendingNfcClaimIntent } = require('../../utils/nfc-claim-intent');
const petStore = require('../../utils/pet-store');

// States: BOOTSTRAPPING, NEED_PHONE, LOADING_PREVIEW, READY, SUBMITTING,
//         SUCCESS, CLAIMED_BY_SELF, CLAIMED_BY_OTHER, UNAVAILABLE, NETWORK_ERROR
const STATES = {
  BOOTSTRAPPING: 'BOOTSTRAPPING',
  NEED_PHONE: 'NEED_PHONE',
  LOADING_PREVIEW: 'LOADING_PREVIEW',
  READY: 'READY',
  SUBMITTING: 'SUBMITTING',
  SUCCESS: 'SUCCESS',
  CLAIMED_BY_SELF: 'CLAIMED_BY_SELF',
  CLAIMED_BY_OTHER: 'CLAIMED_BY_OTHER',
  UNAVAILABLE: 'UNAVAILABLE',
  NETWORK_ERROR: 'NETWORK_ERROR'
};

const STATUS_LABELS = {
  CLAIMABLE: '可以领取',
  CLAIMED_BY_SELF: '你已经领取了这只蛋宝宝',
  CLAIMED_BY_OTHER: '这只蛋宝宝已被其他小伙伴领取',
  UNAVAILABLE: '暂时无法领取'
};

Page({
  data: {
    state: STATES.BOOTSTRAPPING,
    claimRef: null,
    productName: '',
    prototype: '',
    claimStatus: '',
    statusLabel: '',
    pet: null,
    errorMessage: '',
    authorizingPhone: false
  },

  _requestId: null,

  onLoad() {
    const intent = getPendingNfcClaimIntent();
    if (!intent || !intent.claimRef) {
      this.setData({ state: STATES.UNAVAILABLE, errorMessage: '领取信息已过期，请重新触碰蛋宝宝实物。' });
      return;
    }
    this.setData({ claimRef: intent.claimRef });
    this.bootstrap();
  },

  bootstrap() {
    const session = auth.getSession();
    if (!session || auth.isExpired()) {
      getApp().ensureLogin().then((s) => {
        if (s && s.userId) {
          this.checkPhone(s);
        } else {
          this.setData({ state: STATES.UNAVAILABLE, errorMessage: '登录失败，请稍后重试。' });
        }
      }).catch(() => {
        this.setData({ state: STATES.UNAVAILABLE, errorMessage: '登录失败，请稍后重试。' });
      });
      return;
    }
    this.checkPhone(session);
  },

  checkPhone(session) {
    if (session.hasPhone !== true) {
      this.setData({ state: STATES.NEED_PHONE });
      return;
    }
    this.loadPreview();
  },

  async loadPreview() {
    this.setData({ state: STATES.LOADING_PREVIEW, errorMessage: '' });
    try {
      const result = await nfcClaimApi.preview(this.data.claimRef);
      this.applyPreview(result);
    } catch (error) {
      this.setData({
        state: STATES.NETWORK_ERROR,
        errorMessage: (error && error.userMessage) || '暂时无法连接服务，请稍后重试'
      });
    }
  },

  applyPreview(result) {
    const status = result.claimStatus || '';
    const data = {
      productName: result.productName || '',
      prototype: result.prototype || '',
      claimStatus: status,
      statusLabel: STATUS_LABELS[status] || ''
    };
    if (status === 'CLAIMABLE') {
      this.setData({ ...data, state: STATES.READY });
    } else if (status === 'CLAIMED_BY_SELF') {
      this.setData({ ...data, state: STATES.CLAIMED_BY_SELF, pet: result.pet || null });
    } else if (status === 'CLAIMED_BY_OTHER') {
      this.setData({ ...data, state: STATES.CLAIMED_BY_OTHER });
    } else {
      this.setData({ ...data, state: STATES.UNAVAILABLE, statusLabel: STATUS_LABELS.UNAVAILABLE });
    }
  },

  async onAuthorizePhone(event) {
    const phoneCode = event && event.detail && event.detail.code;
    if (!phoneCode) {
      wx.showToast({ title: '需要授权手机号后才能领取蛋宝宝', icon: 'none' });
      return;
    }
    if (this.data.authorizingPhone) return;
    this.setData({ authorizingPhone: true });
    try {
      const session = await getApp().ensureLogin();
      if (!session || !session.userId) throw new Error('invalid login session');
      await wechatApi.bindPhone(phoneCode);
      const boundSession = auth.markPhoneBound();
      if (!boundSession) throw new Error('invalid login session');
      getApp().applySession(boundSession);
      this.loadPreview();
    } catch (error) {
      wx.showToast({ title: error.userMessage || '暂时无法连接服务，请稍后重试', icon: 'none' });
      this.setData({ state: STATES.NEED_PHONE });
    } finally {
      this.setData({ authorizingPhone: false });
    }
  },

  async onConfirmClaim() {
    if (this.data.state !== STATES.READY) return;
    // Generate requestId on first attempt; reuse on retry
    if (!this._requestId) {
      this._requestId = this.generateRequestId();
    }
    this.doConfirm();
  },

  async doConfirm() {
    this.setData({ state: STATES.SUBMITTING, errorMessage: '' });
    try {
      const result = await nfcClaimApi.confirm(this.data.claimRef, this._requestId);
      this.handleClaimResult(result);
    } catch (error) {
      this.setData({
        state: STATES.NETWORK_ERROR,
        errorMessage: (error && error.userMessage) || '暂时无法连接服务，请稍后重试'
      });
    }
  },

  handleClaimResult(result) {
    const status = result.claimStatus || '';
    if (status === 'CLAIMED' && result.pet) {
      petStore.savePetFromVO(result.pet);
      clearPendingNfcClaimIntent();
      getApp().globalData.welcomeCompleted = true;
      this.setData({ state: STATES.SUCCESS, pet: result.pet, claimStatus: status });
    } else if (status === 'CLAIMED_BY_SELF' && result.pet) {
      petStore.savePetFromVO(result.pet);
      clearPendingNfcClaimIntent();
      this.setData({ state: STATES.CLAIMED_BY_SELF, pet: result.pet });
    } else {
      this.setData({ state: STATES.UNAVAILABLE, errorMessage: '领取失败，请稍后重试。' });
    }
  },

  onRetry() {
    if (this.data.state !== STATES.NETWORK_ERROR) return;
    if (this._requestId) {
      this.doConfirm();
    } else {
      this.loadPreview();
    }
  },

  onGoHome() {
    getApp().globalData.welcomeCompleted = true;
    clearPendingNfcClaimIntent();
    wx.switchTab({ url: '/pages/home/home' });
  },

  generateRequestId() {
    // UUID v4
    const hex = '0123456789abcdef';
    let uuid = '';
    for (let i = 0; i < 36; i++) {
      if (i === 8 || i === 13 || i === 18 || i === 23) { uuid += '-'; continue; }
      if (i === 14) { uuid += '4'; continue; }
      if (i === 19) { uuid += hex[(Math.random() * 4 | 8)]; continue; }
      uuid += hex[(Math.random() * 16) | 0];
    }
    return uuid;
  }
});
