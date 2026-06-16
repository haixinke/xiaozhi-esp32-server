/**
 * change-role：换角色。复用 destiny 的角色肖像 swiper（图片 + 文字，左右滑动选择）。
 * 流程：滑动选新角色 -> 二次确认 -> POST /companion/update -> 成功态 -> 置 needReconnectAfterReshape。
 */
const { getTheme, applyTheme } = require('../../../utils/theme');
const { get, post } = require('../../../utils/request');
const codes = require('../../../config/companion-codes');

const ROLES = codes.ROLES;
const CHARACTER_AVATARS = codes.CHARACTER_AVATARS;
const CHARACTER_IMAGES = codes.CHARACTER_IMAGES;

// 角色卡：图片 + 名称，用于 swiper 左右滑动选择（图片沿用 destiny 的 bg-img）
const CHARACTERS = ROLES.map(function (r) {
  return { id: r.id, name: r.label, image: CHARACTER_IMAGES[r.id] };
});

function idxOf(arr, id) {
  for (var i = 0; i < arr.length; i++) {
    if (arr[i].id === id) return i;
  }
  return 0;
}

Page({
  data: {
    darkMode: getTheme(),
    deviceId: '',
    characters: CHARACTERS,
    currentIdx: 0,
    currentRole: '',
    currentLabel: '',
    selectedName: '',
    isSameAsCurrent: true,
    remain: 0,
    showConfirm: false,
    submitting: false,
    done: false,
    doneLabel: ''
  },

  onLoad() {
    applyTheme(this);
    const app = getApp();
    this.setData({ deviceId: (app.globalData && app.globalData.virtualMAC) || '' });
    this._load();
  },
  onShow() { applyTheme(this); },

  async _load() {
    try {
      const res = await get('/companion/detail/' + this.data.deviceId);
      const c = (res && res.code === 0 && res.data) ? res.data : null;
      const role = c ? c.character : '';
      const idx = idxOf(CHARACTERS, role);
      this.setData({
        currentRole: role,
        currentLabel: CHARACTERS[idx].name,
        currentIdx: idx,
        selectedName: CHARACTERS[idx].name,
        isSameAsCurrent: true
      });
      const inv = await get('/item/inventory');
      const list = (inv && inv.code === 0 && inv.data) ? inv.data : [];
      const row = list.filter(function (i) { return i.skuCode === 'role_change'; })[0];
      this.setData({ remain: row ? (row.remainCount || 0) : 0 });
    } catch (e) {
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  // 左右滑动切换角色卡
  onCharChange(e) {
    const idx = e.detail.current;
    this.setData({
      currentIdx: idx,
      selectedName: CHARACTERS[idx].name,
      isSameAsCurrent: CHARACTERS[idx].id === this.data.currentRole
    });
  },

  onConfirmTap() {
    if (this.data.submitting) return;
    const sel = CHARACTERS[this.data.currentIdx];
    if (!sel || sel.id === this.data.currentRole) {
      wx.showToast({ title: '请选择不同的角色', icon: 'none' });
      return;
    }
    if (this.data.remain <= 0) {
      this._noVoucher();
      return;
    }
    this.setData({ showConfirm: true });
  },

  _noVoucher() {
    wx.showModal({
      title: '还没有换角色券',
      content: '换角色需要消耗一张换角色券（¥99）',
      confirmText: '去背包获取',
      cancelText: '再想想',
      success: (r) => {
        if (r.confirm) wx.navigateTo({ url: '/pages/backpack/backpack?focus=role_change' });
      }
    });
  },

  async onReshape() {
    if (this.data.submitting) return;
    const sel = CHARACTERS[this.data.currentIdx];
    this.setData({ submitting: true, showConfirm: false });
    wx.showLoading({ title: '重塑中', mask: true });
    try {
      const res = await post('/companion/update', {
        deviceId: this.data.deviceId,
        character: sel.id,
        avatar: CHARACTER_AVATARS[sel.id],
        defaultImage: CHARACTER_IMAGES[sel.id]
      });
      wx.hideLoading();
      if (!res || res.code !== 0) {
        const code = res && res.code;
        if (code === 10321) { this._noVoucher(); }
        else { wx.showToast({ title: (res && res.msg) || '更换失败', icon: 'none' }); }
        this.setData({ submitting: false });
        return;
      }
      const app = getApp();
      app.globalData.needReconnectAfterReshape = true;
      // 立即刷新缓存的女友头像/背景图，使聊天主页与设置页返回时即时反映新角色
      // （globalData 仅在启动时由 fetchCompanionData 拉取一次，不刷新会显示旧角色）
      app.globalData.companionAvatar = CHARACTER_AVATARS[sel.id] || app.globalData.companionAvatar;
      app.globalData.companionBgImage = CHARACTER_IMAGES[sel.id] || app.globalData.companionBgImage;
      this.setData({ done: true, doneLabel: sel.name });
    } catch (e) {
      wx.hideLoading();
      wx.showToast({ title: '网络异常，请重试', icon: 'none' });
    } finally {
      this.setData({ submitting: false });
    }
  },

  onDone() { wx.navigateBack(); },
  onCloseConfirm() { this.setData({ showConfirm: false }); }
});
