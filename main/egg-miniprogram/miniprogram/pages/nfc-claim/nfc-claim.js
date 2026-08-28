const auth = require('../../utils/auth');
const wechatApi = require('../../utils/wechat-api');
const nfcClaimApi = require('../../utils/nfc-claim-api');
const { getPendingNfcClaimIntent, clearPendingNfcClaimIntent } = require('../../utils/nfc-claim-intent');
const petStore = require('../../utils/pet-store');

// States: BOOTSTRAPPING, NEED_PHONE, LOADING_PREVIEW, READY, SUBMITTING,
//         SUCCESS, CLAIMED_BY_OTHER, UNAVAILABLE, NETWORK_ERROR
// 注：CLAIMED_BY_SELF 不再是页面态——蛋已归本用户时跳过中间页，直接回首页
const STATES = {
  BOOTSTRAPPING: 'BOOTSTRAPPING',
  NEED_PHONE: 'NEED_PHONE',
  LOADING_PREVIEW: 'LOADING_PREVIEW',
  READY: 'READY',
  SUBMITTING: 'SUBMITTING',
  SUCCESS: 'SUCCESS',
  CLAIMED_BY_OTHER: 'CLAIMED_BY_OTHER',
  UNAVAILABLE: 'UNAVAILABLE',
  NETWORK_ERROR: 'NETWORK_ERROR'
};

const STATUS_LABELS = {
  CLAIMABLE: '可以领取',
  CLAIMED_BY_OTHER: '这只蛋宝宝已被其他小伙伴领取',
  UNAVAILABLE: '暂时无法领取'
};

// WXML 禁止绑定 prototype 字段名（JS 原型链保留属性，会渲染空白），pet 对象统一补 petType 别名
function withPetType(pet) {
  if (!pet) return null;
  return { ...pet, petType: pet.prototype || '' };
}

// Math.random 回退路径的 UUID v4 生成（原实现）
function fallbackUuid() {
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

Page({
  data: {
    state: STATES.BOOTSTRAPPING,
    claimRef: null,
    productName: '',
    petType: '',
    claimStatus: '',
    statusLabel: '',
    pet: null,
    errorMessage: '',
    authorizingPhone: false,
    hasPhoneBound: false
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
          this.setData({ hasPhoneBound: s.hasPhone === true });
          this.loadPreview();
        } else {
          this.setData({ state: STATES.UNAVAILABLE, errorMessage: '登录失败，请稍后重试。' });
        }
      }).catch(() => {
        this.setData({ state: STATES.UNAVAILABLE, errorMessage: '登录失败，请稍后重试。' });
      });
      return;
    }
    this.setData({ hasPhoneBound: session.hasPhone === true });
    this.loadPreview();
  },

  async loadPreview() {
    this.setData({ state: STATES.LOADING_PREVIEW, errorMessage: '' });
    try {
      // preview 不受手机号门禁限制（ADR 0003）：触碰即触发后端触碰自验证/锁后复验，
      // 未授权用户也能看到预览；授权只在真正领取时才需要
      const result = await nfcClaimApi.preview(this.data.claimRef);
      this.applyPreview(result);
    } catch (error) {
      // preview 失败不阻塞授权入口：WXML 在 NETWORK_ERROR 态仍按 hasPhoneBound 渲染授权按钮
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
      petType: result.prototype || '',  // WXML 禁止绑定 prototype 字段名（原型链保留属性），统一映射为 petType
      claimStatus: status,
      statusLabel: STATUS_LABELS[status] || ''
    };
    if (status === 'CLAIMABLE') {
      // 可领取但未授权手机号：先展示预览 + 授权按钮（NEED_PHONE），授权后重取 preview 转 READY
      this.setData({ ...data, state: this.data.hasPhoneBound ? STATES.READY : STATES.NEED_PHONE });
    } else if (status === 'CLAIMED_BY_SELF') {
      // 蛋已归本用户：不停留中间页，直接回首页
      this.goHomeWithPet(result.pet);
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
      // 授权成功后重取 preview（ADR 0003 Q5）：资产状态可能已变，且此刻转 READY
      this.setData({ hasPhoneBound: true });
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
      this._requestId = await this.generateRequestId();
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
      this.setData({ state: STATES.SUCCESS, pet: withPetType(result.pet), claimStatus: status });
    } else if (status === 'CLAIMED_BY_SELF') {
      // 领取竞态：确认瞬间蛋已被本用户（另一设备/会话）领取，同样直接回首页
      this.goHomeWithPet(result.pet);
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
    this.goHomeWithPet(null);
  },

  // 回首页的统一出口：有 pet 先落本地缓存（首页秒开），再清 intent、标记欢迎完成并跳转
  goHomeWithPet(pet) {
    if (pet) {
      petStore.savePetFromVO(pet);
    }
    clearPendingNfcClaimIntent();
    getApp().globalData.welcomeCompleted = true;
    wx.switchTab({ url: '/pages/home/home' });
  },

  generateRequestId() {
    // 优先使用加密安全随机源（基础库 2.15.0+）；失败或不支持时回退 Math.random。
    // requestId 仅作幂等键（非安全令牌），回退路径可接受。
    if (typeof wx.getRandomValues === 'function') {
      return new Promise((resolve) => {
        wx.getRandomValues({
          length: 16,
          success: (res) => {
            const bytes = new Uint8Array(res.randomValues);
            // UUID v4: 设置 version (4) 与 variant (10xx) 位
            bytes[6] = (bytes[6] & 0x0f) | 0x40;
            bytes[8] = (bytes[8] & 0x3f) | 0x80;
            const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
            resolve(`${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`);
          },
          fail: () => resolve(fallbackUuid())
        });
      });
    }
    return Promise.resolve(fallbackUuid());
  }
});
