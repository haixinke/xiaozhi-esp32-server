Page({
  data: {
    notifs: { daily: true, hatch: true, growth: true, bday: false }
  },

  onToggleDaily(e) {
    this.setData({ 'notifs.daily': e.detail.value });
    this.syncNotifs();
  },
  onToggleHatch(e) {
    this.setData({ 'notifs.hatch': e.detail.value });
    this.syncNotifs();
  },
  onToggleGrowth(e) {
    this.setData({ 'notifs.growth': e.detail.value });
    this.syncNotifs();
  },
  onToggleBday(e) {
    if (e.detail.value) {
      // 首次开启建议引导用户完成微信订阅消息授权，模板 ID 需替换成
      // 你在微信公众平台「订阅消息」里申请到的真实模板 ID。
      wx.requestSubscribeMessage({
        tmplIds: ['YOUR_BIRTHDAY_TEMPLATE_ID'],
        complete: () => {
          this.setData({ 'notifs.bday': e.detail.value });
          this.syncNotifs();
        }
      });
    } else {
      this.setData({ 'notifs.bday': e.detail.value });
      this.syncNotifs();
    }
  },

  syncNotifs() {
    // TODO: 调用后端接口持久化通知偏好，例如：
    // wx.request({ url: 'https://your-api/notif-prefs', method: 'POST', data: this.data.notifs })
  }
});
