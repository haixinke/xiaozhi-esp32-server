const inviteApi = require('../../utils/invite-api');

const MESSAGES = {
  load: '加载失败，请下拉重试',
  empty: '暂无可用激活码'
};

Page({
  data: {
    inviteCode: null,
    exhausted: false,
    loading: true,
    error: ''
  },

  onShow() {
    this.loadInviteCode();
  },

  async loadInviteCode() {
    this.setData({ loading: true, error: '' });
    try {
      const code = await inviteApi.getMine();
      const exhausted = this.isExhausted(code);
      this.setData({ inviteCode: code, exhausted, loading: false, error: '' });
    } catch (error) {
      this.setData({
        inviteCode: null,
        exhausted: true,
        loading: false,
        error: (error && error.userMessage) || MESSAGES.load
      });
    }
  },

  isExhausted(code) {
    if (!code) return true;
    return (code.remaining || 0) <= 0 || code.status !== 1;
  },

  onRetry() {
    this.loadInviteCode();
  },

  onCopy() {
    const { inviteCode, exhausted, loading } = this.data;
    if (loading || !inviteCode || exhausted) return;
    wx.setClipboardData({ data: inviteCode.code });
  }
});
