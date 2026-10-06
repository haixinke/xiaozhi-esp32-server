// 明信片页：好友点开分享卡片直达，免授权查看（照片 + 文字）。
// 本页刻意不依赖登录态：app.js 的 authReady/silentLogin 在后台静默进行，
// 页面 onLoad 只按 shareId 拉公开数据渲染，未被 redirectUnboundToWelcome 劫持。
const { getPublicPostcard } = require('../../utils/postcard-api');

const SHARE_TITLE = '蛋宝宝给我寄了一张明信片';

Page({
  data: {
    phase: 'loading', // loading | ready | missing
    postcard: null
  },

  onLoad(query) {
    const shareId = query && query.id;
    if (!shareId) {
      this.setData({ phase: 'missing' });
      return;
    }
    this._shareId = String(shareId);
    this.loadPostcard();
  },

  async loadPostcard() {
    try {
      const postcard = await getPublicPostcard(this._shareId);
      this.setData({ phase: 'ready', postcard });
    } catch (error) {
      // 明信片不存在/已删除/网络异常，统一缺页态（不区分原因，避免对外探测）
      this.setData({ phase: 'missing' });
    }
  },

  onRetry() {
    this.setData({ phase: 'loading' });
    this.loadPostcard();
  },

  // 好友侧引导：注册用户回首页看自己的蛋宝宝，未注册走欢迎页转化
  onEnter() {
    const app = getApp();
    if (app && app.globalData && app.globalData.hasPhone === true) {
      wx.switchTab({ url: '/pages/home/home' });
      return;
    }
    wx.reLaunch({ url: '/pages/welcome/welcome' });
  },

  // 转发即接力：好友可把明信片再分享给自己的好友（同样免授权直达）
  onShareAppMessage() {
    const postcard = this.data.postcard;
    return {
      title: postcard && postcard.caption ? postcard.caption : SHARE_TITLE,
      path: `/pages/postcard/postcard?id=${encodeURIComponent(this._shareId || '')}`,
      imageUrl: postcard && postcard.imageUrl
    };
  },

  onShareTimeline() {
    const postcard = this.data.postcard;
    return {
      title: postcard && postcard.caption ? postcard.caption : SHARE_TITLE,
      query: this._shareId ? `id=${encodeURIComponent(this._shareId)}` : ''
    };
  }
});
