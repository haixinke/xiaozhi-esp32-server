const auth = require('../../utils/auth');
const nfcClaimApi = require('../../utils/nfc-claim-api');
const { getPendingNfcClaimIntent, clearPendingNfcClaimIntent } = require('../../utils/nfc-claim-intent');
const petStore = require('../../utils/pet-store');

// States: BOOTSTRAPPING, LOADING_PREVIEW, READY, SUBMITTING,
//         SUCCESS, CLAIMED_BY_OTHER, ALREADY_OWNED, NOT_ACTIVATED, UNAVAILABLE, NETWORK_ERROR
// 注：CLAIMED_BY_SELF 不再是页面态——蛋已归本用户时跳过中间页，直接回首页
const STATES = {
  BOOTSTRAPPING: 'BOOTSTRAPPING',
  LOADING_PREVIEW: 'LOADING_PREVIEW',
  READY: 'READY',
  SUBMITTING: 'SUBMITTING',
  SUCCESS: 'SUCCESS',
  CLAIMED_BY_OTHER: 'CLAIMED_BY_OTHER',
  ALREADY_OWNED: 'ALREADY_OWNED',
  NOT_ACTIVATED: 'NOT_ACTIVATED',
  UNAVAILABLE: 'UNAVAILABLE',
  NETWORK_ERROR: 'NETWORK_ERROR'
};

// 后端错误码：该账号已创建过宠物（一人一宠约束，与邀请码领养同码）
const ERR_PET_ALREADY_EXISTS = 10206;

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
    errorMessage: ''
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
          this.loadPreview();
        } else {
          this.setData({ state: STATES.UNAVAILABLE, errorMessage: '登录失败，请稍后重试。' });
        }
      }).catch(() => {
        this.setData({ state: STATES.UNAVAILABLE, errorMessage: '登录失败，请稍后重试。' });
      });
      return;
    }
    this.loadPreview();
  },

  async loadPreview() {
    this.setData({ state: STATES.LOADING_PREVIEW, errorMessage: '' });
    try {
      // 触碰即触发后端触碰自验证/锁后复验；手机号绑定统一延后到破壳成功之后。
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
      petType: result.prototype || '',  // WXML 禁止绑定 prototype 字段名（原型链保留属性），统一映射为 petType
      claimStatus: status,
      statusLabel: STATUS_LABELS[status] || ''
    };
    if (status === 'CLAIMABLE') {
      // 领取与孵化先体验，是否可领取仅由资产及领养资格决定。
      this.setData({ ...data, state: STATES.READY });
    } else if (status === 'CLAIMED_BY_SELF') {
      // 蛋已归本用户：不停留中间页，直接回首页
      this.goHomeWithPet(result.pet);
    } else if (status === 'CLAIMED_BY_OTHER') {
      this.setData({ ...data, state: STATES.CLAIMED_BY_OTHER });
    } else if (status === 'ALREADY_OWNED') {
      // 重复原型拦截：用户已持有同原型 NFC 宠物。面板带「去看看它」直达已有宠物——
      // preview 会回传该宠物；10206 竞态兜底路径没有宠物，按钮退化为回首页
      this.setData({ ...data, state: STATES.ALREADY_OWNED, ownedPet: result.pet || null });
    } else if (status === 'NOT_ACTIVATED') {
      // 卡已入库未激活：量产收货与放行之间被碰到，给出可解释提示
      this.setData({ ...data, state: STATES.NOT_ACTIVATED });
    } else {
      this.setData({ ...data, state: STATES.UNAVAILABLE, statusLabel: STATUS_LABELS.UNAVAILABLE });
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
      // 一人一宠约束：preview 后、confirm 前被并发领养等竞态场景，
      // 后端抛 10206 时同样落到"已领取过"面板，而非误导性的网络错误
      if (error && error.code === ERR_PET_ALREADY_EXISTS) {
        // 竞态兜底路径无已有宠物信息，ownedPet 置空使「去看看它」隐藏、只留回首页
        this.setData({ state: STATES.ALREADY_OWNED, ownedPet: null, errorMessage: '' });
        return;
      }
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
      // replacedInvitePet：渠道互斥（ADR 0007），本次领取静默替换了邀请码宠物，
      // 成功页带一句轻提示避免用户误以为原有宠物数据无故丢失
      this.setData({
        state: STATES.SUCCESS,
        pet: withPetType(result.pet),
        claimStatus: status,
        replacedInvitePet: !!result.replacedInvitePet
      });
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

  // 「去看看它」：重复原型拦截面板直达已有宠物（preview 回传的 ownedPet）
  onGoOwnedPet() {
    this.goHomeWithPet(this.data.ownedPet || null);
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
