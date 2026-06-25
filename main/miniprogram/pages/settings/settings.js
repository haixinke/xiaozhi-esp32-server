// pages/settings/settings.js
const { getTheme, applyTheme, toggleTheme } = require('../../utils/theme');
const { get, post } = require('../../utils/request');

function featureLabel(code) {
  var labels = {
    'long_term_memory': '永久留存甜蜜回忆',
    'voice_input': '语音输入',
    'voice_call': '语音通话',
    'superpower': '赋予女友超能力（天气）',
    'social_moments': '看女友的私密空间'
  };
  return labels[code] || code;
}

Page({
  data: {
    darkMode: getTheme(),
    // 羁绊面板
    companionAvatar: '',
    userAvatar: '/images/user-default.png',
    planCode: null,
    identityName: '普通陪伴',
    // 契约浮窗（购买）
    showContractPopup: false,
    plans: [],
    selectedPlanId: null,
    contractLoading: false,
    // 契约详情浮窗
    showDetailPopup: false,
    detailLoading: false,
    detailData: null,
    // 契约确认浮窗（续费/升级）
    showConfirmPopup: false,
    confirmAction: null,
    confirmData: null,
    // 全量套餐缓存（用于续费/升级查表补全价格与权益）
    allPlans: [],
    // 信件浮窗（一封写给你的信）
    showLetterPopup: false
  },

  onLoad() {
    applyTheme(this);
  },

  onShow() {
    applyTheme(this);
    this.setData({
      userAvatar: wx.getStorageSync('userAvatar') || '/images/user-default.png'
    });
    this.loadCompanionAvatar();
    this.loadSubscription();

    // 从语音通话等入口跳转来时，自动打开契约购买浮窗
    var app = getApp();
    if (app.globalData.openContractPopupAfterSwitch) {
      app.globalData.openContractPopupAfterSwitch = false;
      this.loadPlans();
    }
  },

  loadCompanionAvatar() {
    var app = getApp();
    this.setData({
      companionAvatar: app.globalData.companionAvatar || '/images/avatar-default.png'
    });
  },

  async loadSubscription() {
    var app = getApp();
    var gd = app.globalData || {};
    if (gd.planCode) {
      this.setData({
        planCode: gd.planCode,
        identityName: this.getIdentityName(gd.planCode)
      });
    }
    try {
      var res = await get('/subscription/entitlements');
      if (res && res.code === 0 && res.data) {
        var planCode = res.data.active ? res.data.planCode : null;
        this.setData({
          planCode: planCode,
          identityName: this.getIdentityName(planCode)
        });
      }
    } catch (err) {
      console.warn('[settings] subscription check failed:', err);
    }
  },

  getIdentityName(planCode) {
    if (planCode === 'silver') return '专属守护';
    if (planCode === 'gold') return '特权家属';
    return '普通陪伴';
  },

  // 套餐规范化（价格分→元 + 权益码→文案），供 loadPlans 与 loadMySubscription 共用
  _normalizePlan(raw) {
    return {
      id: raw.id,
      planCode: raw.planCode,
      planName: raw.planName,
      priceYuan: (raw.priceFen / 100).toFixed(2),
      promoYuan: (raw.promoPriceFen / 100).toFixed(2),
      durationText: raw.durationDays ? (raw.durationDays + '天') : '',
      sort: raw.sort,
      features: (raw.features || []).map(function(code) {
        return { code: code, label: featureLabel(code) };
      })
    };
  },

  onChooseAvatar(e) {
    var avatarUrl = e.detail.avatarUrl;
    if (avatarUrl) {
      this.setData({ userAvatar: avatarUrl });
      wx.setStorageSync('userAvatar', avatarUrl);
    }
  },

  onContractTap() {
    if (this.data.planCode) {
      this.loadMySubscription();
      return;
    }
    this.loadPlans();
  },

  async loadMySubscription() {
    if (this.data.detailLoading) return;
    this.setData({ detailLoading: true });
    try {
      var results = await Promise.all([
        get('/subscription/me'),
        get('/subscription/plans')
      ]);
      var subRes = results[0];
      var plansRes = results[1];
      if (!subRes || subRes.code !== 0 || !subRes.data) {
        wx.showToast({ title: '加载失败', icon: 'none', duration: 1500 });
        return;
      }
      var sub = subRes.data;
      var plans = (plansRes && plansRes.code === 0 && plansRes.data) ? plansRes.data : [];
      this.setData({ allPlans: plans.map(this._normalizePlan) });
      var plan = plans.filter(function(p) { return p.planCode === sub.planCode; })[0];
      var planName = plan ? plan.planName : sub.planCode;
      var currentSort = plan ? plan.sort : 0;
      var identityName = this.getIdentityName(sub.planCode);
      var features = (sub.features || []).map(function(code) {
        return { code: code, label: featureLabel(code) };
      });
      var remainingText = '';
      if (sub.remainingSeconds > 0) {
        var days = Math.floor(sub.remainingSeconds / 86400);
        var hours = Math.floor((sub.remainingSeconds % 86400) / 3600);
        remainingText = days > 0 ? days + '天' + hours + '小时' : hours + '小时';
      } else {
        remainingText = '已过期';
      }
      var startStr = this.formatDate(sub.startAt);
      var endStr = this.formatDate(sub.endAt);
      var isActive = sub.status === 1;
      var higherPlan = plans.filter(function(p) { return p.sort > currentSort; })[0] || null;
      this.setData({
        showDetailPopup: true,
        detailData: {
          planName: planName,
          identityName: identityName,
          features: features,
          startAt: startStr,
          endAt: endStr,
          remainingText: remainingText,
          isActive: isActive,
          planId: sub.planId,
          canUpgrade: isActive && !!higherPlan,
          upgradePlanId: higherPlan ? higherPlan.id : null,
          upgradePlanName: higherPlan ? higherPlan.planName : ''
        }
      });
    } catch (err) {
      console.warn('[settings] load subscription detail failed:', err);
      wx.showToast({ title: '加载失败', icon: 'none', duration: 1500 });
    } finally {
      this.setData({ detailLoading: false });
    }
  },

  formatDate(dateStr) {
    if (!dateStr) return '';
    var d = new Date(dateStr);
    var y = d.getFullYear();
    var m = d.getMonth() + 1;
    var day = d.getDate();
    return y + '-' + (m < 10 ? '0' + m : m) + '-' + (day < 10 ? '0' + day : day);
  },

  onDetailOverlayTap() {
    this.setData({ showDetailPopup: false });
  },

  onDetailPanelTap() {
    // prevent bubbling
  },

  onRenew() {
    var detail = this.data.detailData;
    if (!detail || !detail.planId) return;
    var plan = this.data.allPlans.filter(function(p) { return p.id === detail.planId; })[0];
    if (!plan) {
      wx.showToast({ title: '套餐信息加载失败', icon: 'none', duration: 1500 });
      return;
    }
    this.setData({
      showDetailPopup: false,
      confirmAction: 'renew',
      confirmData: {
        id: plan.id,
        planName: plan.planName,
        promoYuan: plan.promoYuan,
        priceYuan: plan.priceYuan,
        durationText: plan.durationText,
        features: plan.features,
        title: '确认续约我们的小约定？',
        btnText: '确认续费'
      },
      showConfirmPopup: true
    });
  },

  onUpgrade() {
    var detail = this.data.detailData;
    if (!detail || !detail.upgradePlanId) return;
    var plan = this.data.allPlans.filter(function(p) { return p.id === detail.upgradePlanId; })[0];
    if (!plan) {
      wx.showToast({ title: '套餐信息加载失败', icon: 'none', duration: 1500 });
      return;
    }
    this.setData({
      showDetailPopup: false,
      confirmAction: 'upgrade',
      confirmData: {
        id: plan.id,
        planName: plan.planName,
        promoYuan: plan.promoYuan,
        priceYuan: plan.priceYuan,
        durationText: plan.durationText,
        features: plan.features,
        title: '确认升级到 ' + plan.planName + '？',
        btnText: '确认升级'
      },
      showConfirmPopup: true
    });
  },

  onConfirmSubmit() {
    var data = this.data.confirmData;
    if (!data || !data.id) return;
    this.setData({ showConfirmPopup: false });
    this._doSignContract(data.id);
  },

  onConfirmOverlayTap() {
    this.setData({ showConfirmPopup: false });
  },

  onConfirmPanelTap() {
    // prevent bubbling
  },

  onResubscribe() {
    this.setData({ showDetailPopup: false });
    this.loadPlans();
  },

  async loadPlans() {
    if (this.data.contractLoading) return;
    this.setData({ contractLoading: true });
    try {
      var res = await get('/subscription/plans');
      if (res && res.code === 0 && res.data) {
        var plans = res.data.map(this._normalizePlan);
        this.setData({
          plans: plans,
          showContractPopup: true,
          selectedPlanId: null
        });
      }
    } catch (err) {
      console.warn('[settings] load plans failed:', err);
      wx.showToast({ title: '加载失败', icon: 'none', duration: 1500 });
    } finally {
      this.setData({ contractLoading: false });
    }
  },

  onContractOverlayTap() {
    this.setData({ showContractPopup: false });
  },

  onContractPanelTap() {
    // prevent bubbling
  },

  onPlanSelect(e) {
    var id = e.currentTarget.dataset.id;
    this.setData({ selectedPlanId: id });
  },

  onSignContract() {
    if (!this.data.selectedPlanId) return;
    this._doSignContract(this.data.selectedPlanId);
  },

  async _doSignContract(planId) {
    wx.showLoading({ title: '正在下单...', mask: true });
    // 记录购买前的档位，用于判断本次是否为续费（续费无需断开重连）
    var previousPlanCode = getApp().globalData.planCode;
    try {
      // 1. 创建订单
      var orderRes = await post('/payment/order', {
        productType: 'SUBSCRIPTION',
        productRefId: planId,
        quantity: 1
      });
      if (!orderRes || orderRes.code !== 0 || !orderRes.data) {
        wx.hideLoading();
        wx.showToast({ title: (orderRes && orderRes.msg) || '下单失败', icon: 'none', duration: 2000 });
        return;
      }
      var order = orderRes.data;
      var prepayParams = order.prepayParams || {};

      // 2. 检测 Mock 模式（MockPaymentNotifyController 返回 {code:"SUCCESS", message:"SUCCESS"}）
      if (prepayParams.mockNotifyUrl) {
        var notifyRes = await post('/payment/notify/mock', {
          outTradeNo: order.outTradeNo,
          transactionId: 'MOCK_TX_' + Date.now(),
          amountFen: order.amountFen
        });
        if (!notifyRes || notifyRes.code !== 'SUCCESS') {
          wx.hideLoading();
          wx.showToast({ title: '支付失败，请重试', icon: 'none', duration: 2000 });
          return;
        }
      } else {
        // 真实微信支付
        var required = ['timeStamp', 'nonceStr', 'package', 'paySign'];
        var missing = required.filter(function(k) { return !prepayParams[k]; });
        if (missing.length) {
          wx.hideLoading();
          wx.showToast({ title: '支付参数异常，请重试', icon: 'none', duration: 2000 });
          return;
        }
        await new Promise(function(resolve, reject) {
          wx.requestPayment({
            timeStamp: prepayParams.timeStamp,
            nonceStr: prepayParams.nonceStr,
            package: prepayParams.package,
            signType: prepayParams.signType || 'RSA',
            paySign: prepayParams.paySign,
            success: resolve,
            fail: function(err) {
              // errMsg 含 "requestPayment:fail cancel" 表示用户取消
              if (err && err.errMsg && err.errMsg.indexOf('cancel') > -1) {
                reject({ cancelled: true });
              } else {
                reject(err);
              }
            }
          });
        });

        // 主动触发后端查单，补偿微信异步回调可能丢失的情况
        try {
          await post('/payment/order/' + order.outTradeNo + '/query');
        } catch (e) {
          console.warn('[settings] 主动查单失败 outTradeNo=' + order.outTradeNo, e);
        }
      }

      // 3. 轮询订单直到履约完成(FULFILLED)，确保服务端已把 agent 配置落库后再刷新
      var app = getApp();
      await this._waitOrderFulfilled(order.outTradeNo);

      // 4. 刷新全局订阅状态
      if (app.fetchSubscription) {
        await app.fetchSubscription();
      }

      // 5. 档位变更(首次/升级/降级)才需要断开重连以加载最新 agent 配置；同档续费无需断开
      if (app.globalData.planCode !== previousPlanCode) {
        app.globalData.needReconnectAfterSub = true;
      }

      // 6. 刷新页面状态
      this.setData({
        showContractPopup: false,
        selectedPlanId: null
      });
      await this.loadSubscription();

      wx.hideLoading();
      wx.showToast({ title: '契约签订成功', icon: 'success', duration: 2000 });
    } catch (err) {
      wx.hideLoading();
      if (err && err.cancelled) {
        wx.showToast({ title: '已取消支付', icon: 'none', duration: 1500 });
      } else {
        console.warn('[settings] sign contract failed:', err);
        wx.showToast({ title: '操作失败，请重试', icon: 'none', duration: 2000 });
      }
    }
  },

  // 轮询订单状态直到履约完成(FULFILLED=2)，确保服务端已落库 agent 新配置。
  // Mock 模式下 mock 回调已同步履约，首次查询即命中；真实支付下等待微信异步回调。
  async _waitOrderFulfilled(outTradeNo) {
    var FULFILLED = 2;
    var attempts = 12;
    var interval = 1000;
    for (var i = 0; i < attempts; i++) {
      try {
        var res = await get('/payment/order/' + outTradeNo);
        if (res && res.code === 0 && res.data && res.data.status === FULFILLED) {
          return true;
        }
      } catch (e) {
        // 单次查询失败不中断轮询
      }
      await new Promise(function (r) { setTimeout(r, interval); });
    }
    console.warn('[settings] 订单履约轮询超时 outTradeNo=' + outTradeNo);
    return false;
  },

  onBackpackTap() {
    wx.navigateTo({ url: '/pages/backpack/backpack' });
  },

  onCompanionTap() {
    wx.navigateTo({ url: '/pages/companion/profile/profile' });
  },

  onThemeChange() {
    toggleTheme(this);
  },

  onLetterTap() {
    this.setData({ showLetterPopup: true });
  },

  onLetterOverlayTap() {
    this.setData({ showLetterPopup: false });
  },

  onLetterPanelTap() {
    // prevent bubbling
  }
});
