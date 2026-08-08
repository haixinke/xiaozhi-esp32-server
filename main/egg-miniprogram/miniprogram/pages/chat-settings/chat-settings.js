const request = require('../../utils/request');

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const EXPORT_EMAIL_KEY = 'export_email';

Page({
  data: {
    email: '',
    emailFocus: false,
    error: '',
    submitting: false,
    deleting: false
  },

  onLoad() {
    const savedEmail = wx.getStorageSync(EXPORT_EMAIL_KEY);
    if (savedEmail) {
      this.setData({ email: savedEmail });
    }
  },

  onEmailInput(e) {
    this.setData({ email: e.detail.value.trim(), error: '' });
  },

  onEmailFocus() {
    this.setData({ emailFocus: true });
  },

  onEmailBlur() {
    this.setData({ emailFocus: false });
  },

  onExport() {
    const { email, submitting } = this.data;
    if (submitting) return;

    if (!email) {
      this.setData({ error: '请输入接收邮箱' });
      return;
    }
    if (!EMAIL_PATTERN.test(email)) {
      this.setData({ error: '邮箱格式不正确' });
      return;
    }

    this.setData({ submitting: true });
    request.post('/wechat/chat-history/export', { email })
      .then(() => {
        wx.setStorageSync(EXPORT_EMAIL_KEY, email);
        this.setData({ email: '' });
        wx.showModal({
          title: '导出成功',
          content: `聊天记录已开始导出，稍后将发送至 ${email}，请注意查收。`,
          showCancel: false,
          confirmText: '知道了',
          success: () => {
            wx.navigateBack({ delta: 1 });
          }
        });
      })
      .catch((err) => {
        const message = (err && err.userMessage) || '导出失败，请稍后重试';
        wx.showToast({ title: message, icon: 'none' });
      })
      .finally(() => {
        this.setData({ submitting: false });
      });
  },

  onDelete() {
    if (this.data.deleting) return;

    wx.showModal({
      title: '删除聊天记录',
      content: '删除后将清空你的全部对话记录且无法恢复，确定删除吗？',
      confirmText: '仍要删除',
      confirmColor: '#C33F36',
      cancelText: '取消',
      success: (res) => {
        if (res.confirm) {
          this.confirmDelete();
        }
      }
    });
  },

  confirmDelete() {
    this.setData({ deleting: true });
    request.post('/wechat/chat-history/delete')
      .then(() => {
        wx.showModal({
          title: '删除成功',
          content: '你的全部聊天记录已删除。',
          showCancel: false,
          confirmText: '知道了',
          success: () => {
            wx.navigateBack({ delta: 1 });
          }
        });
      })
      .catch((err) => {
        const message = (err && err.userMessage) || '删除失败，请稍后重试';
        wx.showToast({ title: message, icon: 'none' });
      })
      .finally(() => {
        this.setData({ deleting: false });
      });
  }
});
