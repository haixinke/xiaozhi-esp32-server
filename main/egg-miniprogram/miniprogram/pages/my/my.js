const petStore = require('../../utils/pet-store');
const request = require('../../utils/request');
const inviteApi = require('../../utils/invite-api');

const SHARE_TITLE = '一起来养蛋宝宝吧';
const SHARE_HOME_PATH = '/pages/home/home?v=1&source=home_share';

Page({
  data: { userName: '蛋友', avatarUrl: '', eggCount: 0, shareReady: false },

  onShow() {
    // 自定义悬浮 tabBar：标记当前 tab；在「我的」页不渲染任何圆形按钮
    if (typeof this.getTabBar === 'function') {
      const tabBar = this.getTabBar();
      if (tabBar) tabBar.setData({ selected: 1, hidden: true });
    }
    this.loadUserProfile();
    this.loadPetStatus();
    this.prepareShare();
  },

  prepareShare() {
    const requestId = (this._shareRequestId || 0) + 1;
    this._shareRequestId = requestId;
    this.setData({ shareReady: false });
    if (typeof wx !== 'undefined' && wx.hideShareMenu) {
      wx.hideShareMenu({ menus: ['shareAppMessage', 'shareTimeline'] });
    }
    this.loadShareInviteCode(requestId);
  },

  async loadShareInviteCode(requestId) {
    const currentRequestId = requestId === undefined
      ? (this._shareRequestId = (this._shareRequestId || 0) + 1)
      : requestId;
    this._shareInviteCode = null;
    try {
      const inviteCode = await inviteApi.getMine();
      if (this._shareRequestId !== currentRequestId) return;
      if (this.isUsableShareInvite(inviteCode)) this._shareInviteCode = inviteCode.code;
    } catch (error) {
      if (this._shareRequestId !== currentRequestId) return;
      this._shareInviteCode = null;
    } finally {
      if (this._shareRequestId !== currentRequestId) return;
      this.setData({ shareReady: true });
      if (typeof wx !== 'undefined' && wx.showShareMenu) {
        wx.showShareMenu({ menus: ['shareAppMessage', 'shareTimeline'] });
      }
    }
  },

  isUsableShareInvite(inviteCode) {
    return !!(inviteCode && inviteCode.code && inviteCode.remaining > 0 && inviteCode.status === 1);
  },

  loadUserProfile() {
    request.get('/wechat/profile')
      .then((profile) => {
        petStore.syncUserProfile(profile);
        this.applyUserData();
      })
      .catch(() => this.applyUserData());
  },

  applyUserData() {
    const user = petStore.getUser() || {};
    this.setData({ userName: user.nickname || '蛋友', avatarUrl: user.avatarUrl || '' });
  },

  loadPetStatus() {
    const pet = petStore.getPet();
    this.setData({ eggCount: pet ? 1 : 0 });
  },

  onTapUserCard() { wx.navigateTo({ url: '/pages/profile/profile' }); },
  onNavAlbum() { wx.navigateTo({ url: '/pages/album/album' }); },
  onNavCodes() { wx.navigateTo({ url: '/pages/invite-codes/invite-codes' }); },
  onNavSettings() { wx.navigateTo({ url: '/pages/settings/settings' }); },
  onNavChatSettings() { wx.navigateTo({ url: '/pages/chat-settings/chat-settings' }); },
  onNavAccount() { wx.navigateTo({ url: '/pages/account/account' }); },
  onNavPrivacy() { wx.navigateTo({ url: '/pages/privacy/privacy' }); },
  onNavHelp() { wx.navigateTo({ url: '/pages/help/help' }); },

  onShareAppMessage() {
    if (!this.data.shareReady) return false;
    const inviteCode = this._shareInviteCode;
    return {
      title: SHARE_TITLE,
      path: inviteCode ? `${SHARE_HOME_PATH}&inviteCode=${encodeURIComponent(inviteCode)}` : SHARE_HOME_PATH
    };
  },

  onShareTimeline() {
    if (!this.data.shareReady) return false;
    return { title: SHARE_TITLE };
  }
});
