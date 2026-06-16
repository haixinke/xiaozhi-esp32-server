/**
 * change-soul：换性格。一张 soul_quirk_change 同时改灵魂特质(必选2)+小任性(必选1)。
 * 提交时把灵魂特质逗号分隔成 soulTraits、小任性成 soulQuirk 一并 POST。
 */
const { getTheme, applyTheme } = require('../../../utils/theme');
const { get, post } = require('../../../utils/request');
const codes = require('../../../config/companion-codes');

Page({
  data: {
    darkMode: getTheme(),
    deviceId: '',
    curTraitsStr: '', curTraitsLabel: '', curQuirkLabel: '', curQuirkId: '',
    traits: codes.SOUL_TRAITS.map(function (t) { return { id: t.id, label: t.label, sel: false }; }),
    quirks: codes.QUIRKS.map(function (q) { return { id: q.id, label: q.label }; }),
    traitSel: [], quirkSel: '',
    quirkSelLabel: '',
    fromLabel: '',
    toLabel: '',
    remain: 0,
    showConfirm: false,
    submitting: false,
    done: false
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
      if (!c) return;
      const traitsStr = c.soulTraits || '';
      const traitsArr = traitsStr ? traitsStr.split(',') : [];
      const curTraitsLabel = codes.SOUL_TRAITS.filter(function (t) { return traitsArr.indexOf(t.id) > -1; })
        .map(function (t) { return t.label; }).join(' · ');
      const curQuirkLabel = codes.getLabel(codes.QUIRKS, c.soulQuirk);
      // 预选当前值
      const traits = this.data.traits.map(function (t) { t.sel = traitsArr.indexOf(t.id) > -1; return t; });
      this.setData({
        curTraitsStr: traitsStr, curTraitsLabel, curQuirkLabel, curQuirkId: c.soulQuirk || '',
        traits, traitSel: traitsArr, quirkSel: c.soulQuirk || '', quirkSelLabel: curQuirkLabel,
        fromLabel: (curTraitsLabel || '未设置') + ' ／ ' + (curQuirkLabel || '未设置')
      });
      const inv = await get('/item/inventory');
      const list = (inv && inv.code === 0 && inv.data) ? inv.data : [];
      const row = list.filter(function (i) { return i.skuCode === 'soul_quirk_change'; })[0];
      this.setData({ remain: row ? (row.remainCount || 0) : 0 });
    } catch (e) {
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  onTraitTap(e) {
    const idx = e.currentTarget.dataset.index;
    const traits = this.data.traits;
    if (traits[idx].sel) {
      traits[idx].sel = false;
    } else {
      const cnt = traits.filter(function (t) { return t.sel; }).length;
      if (cnt >= 2) { wx.showToast({ title: '最多选择两条', icon: 'none' }); return; }
      traits[idx].sel = true;
    }
    const sel = traits.filter(function (t) { return t.sel; });
    const ids = sel.map(function (t) { return t.id; });
    const label = sel.map(function (t) { return t.label; }).join(' · ');
    this.setData({ traits, traitSel: ids, toLabel: (label || '未设置') + ' ／ ' + (this.data.quirkSelLabel || '未设置') });
  },

  onQuirkTap(e) {
    const idx = e.currentTarget.dataset.index;
    const q = this.data.quirks[idx];
    const same = this.data.quirkSel === q.id;
    this.setData({
      quirkSel: same ? '' : q.id, quirkSelLabel: same ? '' : q.label,
      toLabel: this._traitsLabel() + ' ／ ' + (same ? '未设置' : q.label)
    });
  },

  _traitsLabel() {
    return this.data.traits.filter(function (t) { return t.sel; }).map(function (t) { return t.label; }).join(' · ') || '未设置';
  },

  // 变化检测：灵魂特质集合（排序后）或小任性不同，才算需要重塑
  _isChanged() {
    const newStr = this.data.traitSel.slice().sort().join(',');
    const oldStr = (this.data.curTraitsStr || '').split(',').filter(Boolean).sort().join(',');
    return newStr !== oldStr || this.data.quirkSel !== this.data.curQuirkId;
  },

  onConfirmTap() {
    if (this.data.submitting) return;
    if (this.data.traitSel.length < 2) {
      wx.showToast({ title: '请选择 2 条灵魂特质', icon: 'none' }); return;
    }
    if (!this.data.quirkSel) {
      wx.showToast({ title: '请选择 1 条小任性', icon: 'none' }); return;
    }
    if (!this._isChanged()) { wx.showToast({ title: '内容未发生变化', icon: 'none' }); return; }
    if (this.data.remain <= 0) { this._noVoucher(); return; }
    this.setData({ showConfirm: true });
  },

  _noVoucher() {
    wx.showModal({
      title: '还没有换性格券', content: '重塑性格需要一张换性格券（¥99）',
      confirmText: '去背包获取', cancelText: '再想想',
      success: (r) => { if (r.confirm) wx.navigateTo({ url: '/pages/backpack/backpack?focus=soul_quirk_change' }); }
    });
  },

  async onReshape() {
    if (this.data.submitting) return;
    this.setData({ submitting: true, showConfirm: false });
    wx.showLoading({ title: '重塑中', mask: true });
    try {
      const res = await post('/companion/update', {
        deviceId: this.data.deviceId,
        soulTraits: this.data.traitSel.join(','),
        soulQuirk: this.data.quirkSel
      });
      wx.hideLoading();
      if (!res || res.code !== 0) {
        if (res && res.code === 10321) { this._noVoucher(); }
        else { wx.showToast({ title: (res && res.msg) || '更换失败', icon: 'none' }); }
        this.setData({ submitting: false }); return;
      }
      getApp().globalData.needReconnectAfterReshape = true;
      this.setData({ done: true });
    } catch (e) {
      wx.hideLoading(); wx.showToast({ title: '网络异常，请重试', icon: 'none' });
    } finally { this.setData({ submitting: false }); }
  },

  onDone() { wx.navigateBack(); },
  onCloseConfirm() { this.setData({ showConfirm: false }); }
});
