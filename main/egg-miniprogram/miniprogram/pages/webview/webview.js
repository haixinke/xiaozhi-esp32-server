/*
 * webview 页：在小程序内打开外部 H5 游戏（或后续其他外部页面）。
 * 目标地址经路由参数 url 传入（encodeURIComponent 编码），本页 decode 后完整还原，
 * 查询参数与锚点原样保留；小程序凭证与账号信息不随链接传递——地址仅来自服务端运营配置。
 */
Page({
  data: {
    // web-view 目标地址；为空时不渲染 web-view（无效地址或加载失败场景）
    url: '',
    // 可理解的错误提示；非空时展示错误态并提供返回入口
    error: ''
  },

  onLoad(options) {
    const decoded = this.decodeUrl(options && options.url);
    if (!decoded) {
      // 无效地址（缺失/无法解码/非 HTTPS）：不渲染 web-view，用户可留在错误页或返回
      this.setData({ url: '', error: '页面地址无效，请返回后重试' });
      return;
    }
    this.setData({ url: decoded, error: '' });
  },

  // 还原路由参数中的目标地址；仅接受 HTTPS（与后端保存校验一致），其余视为无效
  decodeUrl(raw) {
    if (!raw || typeof raw !== 'string') return '';
    let decoded;
    try {
      decoded = decodeURIComponent(raw);
    } catch (e) {
      return '';
    }
    return decoded.startsWith('https://') ? decoded : '';
  },

  // web-view 加载失败（域名未配置、网络异常等）：提示并收起 web-view，避免停留在空白页
  onError() {
    this.setData({ url: '', error: '页面加载失败，请稍后再试' });
    wx.showToast({ title: '页面加载失败，请稍后再试', icon: 'none' });
  },

  onBack() {
    wx.navigateBack({ fail: () => {} });
  }
});
