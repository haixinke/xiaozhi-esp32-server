const { getTheme, applyTheme } = require('../../utils/theme');
const { get } = require('../../utils/request');

// 订单状态码 → 文案 + 样式类名
var STATUS_MAP = {
  0: { text: '待支付', class: 'pending' },
  1: { text: '已支付', class: 'paid' },
  2: { text: '已发货', class: 'fulfilled' },
  3: { text: '已取消', class: 'cancelled' },
  4: { text: '已退款', class: 'refunded' },
  5: { text: '已超时', class: 'expired' }
};

// 商品类型 → 文案 + 样式类名
var TYPE_MAP = {
  SUBSCRIPTION: { text: '订阅套餐', class: 'subscription' },
  ITEM: { text: '道具', class: 'item' }
};

Page({
  data: {
    darkMode: getTheme(),
    tabs: [
      { key: 'all', label: '全部' },
      { key: 'SUBSCRIPTION', label: '订阅套餐' },
      { key: 'ITEM', label: '道具' }
    ],
    activeTab: 'all',
    loading: false,
    error: false,
    empty: false,
    emptyText: '暂无订单',
    allOrders: [],
    filteredOrders: []
  },

  onLoad() {
    applyTheme(this);
    this.loadOrders();
  },

  onShow() {
    applyTheme(this);
  },

  onPullDownRefresh() {
    this.loadOrders(true);
  },

  async loadOrders(isRefresh) {
    this.setData({ loading: true, error: false });
    try {
      var res = await get('/payment/orders');
      if (res && res.code === 0 && res.data) {
        var orders = res.data.map(this._decorate.bind(this));
        this.setData({
          allOrders: orders,
          loading: false,
          error: false
        });
        this._applyFilter();
      } else {
        this.setData({
          loading: false,
          error: false,
          allOrders: [],
          filteredOrders: [],
          empty: true,
          emptyText: '暂无订单'
        });
      }
    } catch (err) {
      console.warn('[orders] load failed:', err);
      this.setData({
        loading: false,
        error: true,
        allOrders: [],
        filteredOrders: []
      });
    } finally {
      if (isRefresh) {
        wx.stopPullDownRefresh();
      }
    }
  },

  _decorate(raw) {
    var statusInfo = STATUS_MAP[raw.status] || { text: '未知', class: 'unknown' };
    var typeInfo = TYPE_MAP[raw.productType] || { text: '商品', class: 'unknown' };
    return {
      id: raw.id,
      outTradeNo: raw.outTradeNo,
      productName: raw.productName || typeInfo.text,
      productType: raw.productType,
      typeLabel: typeInfo.text,
      typeClass: typeInfo.class,
      quantity: raw.quantity,
      amountYuan: this._yuan(raw.amountFen),
      status: raw.status,
      statusText: statusInfo.text,
      statusClass: statusInfo.class,
      createdText: this._formatTime(raw.createdAt)
    };
  },

  _yuan(fen) {
    var y = (fen || 0) / 100;
    return (y % 1 === 0) ? String(y) : y.toFixed(2);
  },

  _formatTime(dateStr) {
    if (!dateStr) return '';
    // iOS 不支持 "yyyy-MM-dd HH:mm:ss"，需替换为 ISO 8601 格式
    var d = new Date(String(dateStr).replace(/ /g, 'T'));
    var pad = function (n) { return n < 10 ? '0' + n : '' + n; };
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
      + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
  },

  onTabTap(e) {
    var key = e.currentTarget.dataset.key;
    if (key === this.data.activeTab) return;
    this.setData({ activeTab: key });
    this._applyFilter();
  },

  _applyFilter() {
    var tab = this.data.activeTab;
    var filtered;
    if (tab === 'all') {
      filtered = this.data.allOrders;
    } else {
      filtered = this.data.allOrders.filter(function (o) {
        return o.productType === tab;
      });
    }
    this.setData({
      filteredOrders: filtered,
      empty: filtered.length === 0,
      emptyText: tab === 'all' ? '暂无订单' :
        (tab === 'SUBSCRIPTION' ? '暂无订阅订单' : '暂无道具订单')
    });
  },

  onRetry() {
    this.loadOrders();
  }
});
