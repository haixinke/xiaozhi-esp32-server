/**
 * change-occupation：换职业。复用 destiny 的九宫格选择器。
 * 流程：选新职业 -> 二次确认 -> POST /companion/update -> 成功态 -> 置 needReconnectAfterReshape。
 */
const { getTheme, applyTheme } = require('../../../utils/theme');
const { get, post } = require('../../../utils/request');
const codes = require('../../../config/companion-codes');

Page({
  data: {
    darkMode: getTheme(),
    deviceId: '',
    currentOcc: '',
    currentLabel: '',
    occupations: codes.OCCUPATIONS,
    selected: '',
    selectedLabel: '',
    remain: 0,
    showConfirm: false,
    submitting: false,
    done: false,
    doneLabel: ''
  },

  onLoad() {
    applyTheme(this);
    const app = getApp();
    const deviceId = (app.globalData && app.globalData.virtualMAC) || '';
    this.setData({ deviceId });
    this._load();
  },
  onShow() { applyTheme(this); },

  async _load() {
    try {
      const res = await get('/companion/detail/' + this.data.deviceId);
      const c = (res && res.code === 0 && res.data) ? res.data : null;
      const occ = c ? c.occupation : '';
      this.setData({ currentOcc: occ, currentLabel: codes.getLabel(codes.OCCUPATIONS, occ), selected: occ, selectedLabel: codes.getLabel(codes.OCCUPATIONS, occ) });
      const inv = await get('/item/inventory');
      const list = (inv && inv.code === 0 && inv.data) ? inv.data : [];
      const row = list.filter(function (i) { return i.skuCode === 'occupation_change'; })[0];
      this.setData({ remain: row ? (row.remainCount || 0) : 0 });
    } catch (e) {
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  onOccTap(e) {
    const id = e.currentTarget.dataset.id;
    const occ = codes.OCCUPATIONS.filter(function (o) { return o.id === id; })[0];
    this.setData({ selected: id, selectedLabel: occ ? occ.label : '' });
  },

  onConfirmTap() {
    if (!this.data.selected || this.data.submitting) return;
    if (this.data.selected === this.data.currentOcc) {
      wx.showToast({ title: '请选择不同的职业', icon: 'none' });
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
      title: '还没有换职业券',
      content: '换职业需要消耗一张换职业券（¥299）',
      confirmText: '去背包获取',
      cancelText: '再想想',
      success: (r) => {
        if (r.confirm) wx.navigateTo({ url: '/pages/backpack/backpack?focus=occupation_change' });
      }
    });
  },

  async onReshape() {
    if (this.data.submitting) return;
    this.setData({ submitting: true, showConfirm: false });
    wx.showLoading({ title: '重塑中', mask: true });
    try {
      const res = await post('/companion/update', { deviceId: this.data.deviceId, occupation: this.data.selected });
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
      this.setData({ done: true, doneLabel: this.data.selectedLabel });
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
