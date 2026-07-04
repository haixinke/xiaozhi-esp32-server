// pages/subscription/subscription.js
var { getTheme, applyTheme } = require('../../utils/theme');
var { get, post } = require('../../utils/request');
var logger = require('../../utils/logger');

var PLAN_RANK = { silver: 1, gold: 2 };

function featureLabel(code) {
  var labels = {
    'long_term_memory': '消息漫游天数',
    'chat_no_limit': '每日对话无限畅聊',
    'voice_input': '语音输入，解放双手',
    'superpower': '女友超能力(天气)',
    'voice_call': '语音通话无限畅聊',
    'memory_enhance': '记忆增强',
    'message_speed': '消息回复速度提升',
    'message_delete': '历史消息撤回'
  };
  return labels[code] || code;
}

function normalizePlan(raw) {
  var promoYuan = (raw.promoPriceFen / 100).toFixed(2);
  var priceYuan = (raw.priceFen / 100).toFixed(2);
  var periodLabel = '月卡';
  if (raw.durationDays === 90) periodLabel = '季卡';
  else if (raw.durationDays === 365) periodLabel = '年卡';
  var monthlyYuan = null;
  if (raw.durationDays > 30) {
    var months = raw.durationDays === 90 ? 3 : 12;
    monthlyYuan = (raw.promoPriceFen / 100 / months).toFixed(1);
  }
  return {
    id: raw.id,
    planCode: raw.planCode,
    planName: raw.planName,
    durationDays: raw.durationDays,
    priceYuan: priceYuan,
    promoYuan: promoYuan,
    sort: raw.sort,
    periodLabel: periodLabel,
    monthlyYuan: monthlyYuan,
    features: (raw.features || []).map(function(code) {
      return { code: code, label: featureLabel(code) };
    })
  };
}

function buildFeatureTable(silverPlans, goldPlans) {
  var silverFeatures = {};
  var goldFeatures = {};
  if (silverPlans.length > 0) {
    silverPlans[0].features.forEach(function(f) { silverFeatures[f.code] = true; });
  }
  if (goldPlans.length > 0) {
    goldPlans[0].features.forEach(function(f) { goldFeatures[f.code] = true; });
  }
  // Use gold features as superset for ordering
  var allCodes = [];
  if (goldPlans.length > 0) {
    goldPlans[0].features.forEach(function(f) { allCodes.push(f.code); });
  }
  if (silverPlans.length > 0) {
    silverPlans[0].features.forEach(function(f) {
      if (allCodes.indexOf(f.code) === -1) allCodes.push(f.code);
    });
  }
  var displayValues = {
    'long_term_memory': { silver: '120', gold: '180' }
  };
  return allCodes.map(function(code) {
    var custom = displayValues[code];
    var hasSilver = !!silverFeatures[code];
    var hasGold = !!goldFeatures[code];
    return {
      code: code,
      label: featureLabel(code),
      silver: hasSilver,
      gold: hasGold,
      silverDisplay: custom && hasSilver ? custom.silver : (hasSilver ? '✓' : '—'),
      goldDisplay: custom && hasGold ? custom.gold : (hasGold ? '✓' : '—')
    };
  });
}

function getActionInfo(selectedPlan, currentSub) {
  if (!currentSub || !currentSub.isActive) {
    return { text: '¥' + selectedPlan.promoYuan + ' 签订契约', action: 'subscribe' };
  }
  if (selectedPlan.planCode === currentSub.planCode) {
    return { text: '¥' + selectedPlan.promoYuan + ' 续费', action: 'renew' };
  }
  var currentRank = PLAN_RANK[currentSub.planCode] || 0;
  var targetRank = PLAN_RANK[selectedPlan.planCode] || 0;
  if (targetRank > currentRank) {
    return { text: '升级到' + selectedPlan.planName + ' ¥' + selectedPlan.promoYuan, action: 'upgrade' };
  }
  return { text: '当前已拥有更高档位', action: 'disabled' };
}

Page({
  data: {
    darkMode: getTheme(),
    loading: true,
    activeTab: 'silver',
    silverPlans: [],
    goldPlans: [],
    currentPlans: [],
    selectedPlanId: null,
    selectedPlan: null,
    allFeatures: [],
    currentSub: null,
    from: 'settings',
    paying: false,
    actionInfo: { text: '', action: '' },
    featureCount: 0
  },

  onLoad(options) {
    this.setData({
      from: options.from || 'settings',
      activeTab: options.tab || 'silver'
    });
    applyTheme(this);
    this.loadPageData();
  },

  onShow() {
    applyTheme(this);
  },

  async loadPageData() {
    this.setData({ loading: true });
    try {
      var results = await Promise.all([
        get('/subscription/plans'),
        get('/subscription/me')
      ]);
      var plansRes = results[0];
      var subRes = results[1];
      var rawPlans = (plansRes && plansRes.code === 0 && plansRes.data) ? plansRes.data : [];
      var silverPlans = rawPlans.filter(function(p) { return p.planCode === 'silver'; }).map(normalizePlan);
      var goldPlans = rawPlans.filter(function(p) { return p.planCode === 'gold'; }).map(normalizePlan);
      var allFeatures = buildFeatureTable(silverPlans, goldPlans);

      // Current subscription
      var currentSub = null;
      if (subRes && subRes.code === 0 && subRes.data && subRes.data.status === 1) {
        var sub = subRes.data;
        var remainingText = '';
        if (sub.remainingSeconds > 0) {
          var days = Math.floor(sub.remainingSeconds / 86400);
          var hours = Math.floor((sub.remainingSeconds % 86400) / 3600);
          remainingText = days > 0 ? days + '天' + hours + '小时' : hours + '小时';
        } else {
          remainingText = '已过期';
        }
        currentSub = {
          planCode: sub.planCode,
          planId: sub.planId,
          planName: sub.planCode === 'gold' ? '灵魂共鸣' : '心动互联',
          remainingText: remainingText,
          isActive: sub.remainingSeconds > 0
        };
      }

      // Default tab: if user has sub, default to their planCode
      var activeTab = this.data.activeTab;
      if (currentSub && currentSub.isActive && !this.options.tab) {
        activeTab = currentSub.planCode;
      }

      var currentTabPlans = activeTab === 'gold' ? goldPlans : silverPlans;
      var defaultPlan = currentTabPlans.filter(function(p) { return p.durationDays === 30; })[0] || currentTabPlans[0];
      var featureCount = currentTabPlans.length > 0 ? currentTabPlans[0].features.length : 0;

      this.setData({
        silverPlans: silverPlans,
        goldPlans: goldPlans,
        allFeatures: allFeatures,
        currentSub: currentSub,
        activeTab: activeTab,
        currentPlans: currentTabPlans,
        selectedPlanId: defaultPlan ? defaultPlan.id : null,
        selectedPlan: defaultPlan || null,
        featureCount: featureCount,
        actionInfo: defaultPlan ? getActionInfo(defaultPlan, currentSub) : { text: '', action: '' },
        loading: false
      });
    } catch (err) {
      logger.warn('[subscription] load failed:', err);
      wx.showToast({ title: '加载失败', icon: 'none', duration: 1500 });
      this.setData({ loading: false });
    }
  },

  onTabSwitch(e) {
    var tab = e.currentTarget.dataset.tab;
    if (tab === this.data.activeTab) return;
    var plans = tab === 'gold' ? this.data.goldPlans : this.data.silverPlans;
    var defaultPlan = plans.filter(function(p) { return p.durationDays === 30; })[0] || plans[0];
    var featureCount = plans.length > 0 ? plans[0].features.length : 0;
    this.setData({
      activeTab: tab,
      currentPlans: plans,
      selectedPlanId: defaultPlan ? defaultPlan.id : null,
      selectedPlan: defaultPlan || null,
      featureCount: featureCount,
      actionInfo: defaultPlan ? getActionInfo(defaultPlan, this.data.currentSub) : { text: '', action: '' }
    });
  },

  onPeriodSelect(e) {
    var id = e.currentTarget.dataset.id;
    var plans = this.data.currentPlans;
    var plan = plans.filter(function(p) { return p.id === id; })[0];
    if (plan) {
      this.setData({
        selectedPlanId: id,
        selectedPlan: plan,
        actionInfo: getActionInfo(plan, this.data.currentSub)
      });
    }
  },

  onPurchaseTap() {
    if (this.data.paying) return;
    var actionInfo = this.data.actionInfo;
    if (actionInfo.action === 'disabled') return;
    if (!this.data.selectedPlanId) return;
    this._doSignContract(this.data.selectedPlanId);
  },

  async _doSignContract(planId) {
    this.setData({ paying: true });
    wx.showLoading({ title: '正在下单...', mask: true });
    var previousPlanCode = getApp().globalData.planCode;
    try {
      var order = await this._createSubscriptionOrder(planId);
      await this._processPayment(order);
      await this._finalizePurchase(order, previousPlanCode);
    } catch (err) {
      wx.hideLoading();
      if (err && err.cancelled) {
        wx.showToast({ title: '已取消支付', icon: 'none', duration: 1500 });
      } else {
        logger.warn('[subscription] sign contract failed:', err);
        wx.showToast({ title: err.message || '操作失败，请重试', icon: 'none', duration: 2000 });
      }
    } finally {
      this.setData({ paying: false });
    }
  },

  async _createSubscriptionOrder(planId) {
    var orderRes = await post('/payment/order', {
      productType: 'SUBSCRIPTION',
      productRefId: planId,
      quantity: 1
    });
    if (!orderRes || orderRes.code !== 0 || !orderRes.data) {
      throw new Error((orderRes && orderRes.msg) || '下单失败');
    }
    return orderRes.data;
  },

  async _processPayment(order) {
    var prepayParams = order.prepayParams || {};
    if (prepayParams.mockNotifyUrl) {
      await this._processMockPayment(order);
    } else {
      await this._processWechatPayment(order, prepayParams);
    }
  },

  async _processMockPayment(order) {
    var notifyRes = await post('/payment/notify/mock', {
      outTradeNo: order.outTradeNo,
      transactionId: 'MOCK_TX_' + Date.now(),
      amountFen: order.amountFen
    });
    if (!notifyRes || notifyRes.code !== 'SUCCESS') {
      throw new Error('支付失败，请重试');
    }
  },

  async _processWechatPayment(order, prepayParams) {
    var required = ['timeStamp', 'nonceStr', 'package', 'paySign'];
    var missing = required.filter(function(k) { return !prepayParams[k]; });
    if (missing.length) {
      throw new Error('支付参数异常，请重试');
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
          if (err && err.errMsg && err.errMsg.indexOf('cancel') > -1) {
            reject({ cancelled: true });
          } else {
            reject(err);
          }
        }
      });
    });
    // Query order to compensate async callback
    try {
      await post('/payment/order/' + order.outTradeNo + '/query');
    } catch (e) {
      logger.warn('[subscription] query failed outTradeNo=' + order.outTradeNo, e);
    }
  },

  async _finalizePurchase(order, previousPlanCode) {
    var app = getApp();
    await this._waitOrderFulfilled(order.outTradeNo);

    if (app.fetchSubscription) {
      await app.fetchSubscription();
    }

    if (app.globalData.planCode !== previousPlanCode) {
      app.globalData.needReconnectAfterSub = true;
    }

    wx.hideLoading();
    wx.showToast({ title: '契约签订成功', icon: 'success', duration: 2000 });

    setTimeout(function() {
      wx.navigateBack();
    }, 1500);
  },

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
        // single query failure doesn't break polling
      }
      await new Promise(function(r) { setTimeout(r, interval); });
    }
    logger.warn('[subscription] order fulfillment poll timeout outTradeNo=' + outTradeNo);
    return false;
  }
});
