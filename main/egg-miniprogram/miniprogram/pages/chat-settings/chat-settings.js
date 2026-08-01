const request = require('../../utils/request');

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

Page({
  data: {
    email: '',
    emailFocus: false,
    error: '',
    submitting: false
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
  }
});
