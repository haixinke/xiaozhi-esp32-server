const petStore = require('../../utils/pet-store');
const PROFILE_KEY = 'eggbaby_profile_v1';
const MBTI_LIST = ['INFP','INFJ','INTJ','INTP','ENFP','ENFJ','ENTJ','ENTP','ISFP','ISFJ','ISTJ','ISTP','ESFP','ESFJ','ESTJ','ESTP'];

Page({
  data: {
    nickname: '蛋友3024', userId: '038291847562', gender: '未设置', birthday: '未设置',
    zodiac: '——', city: '未设置', mbti: '未设置', genderLocked: false, birthdayLocked: false,
    avatarUrl: ''
  },

  onLoad() {
    const user = petStore.getUser() || {};
    const profile = wx.getStorageSync(PROFILE_KEY) || {};
    this.setData(Object.assign({}, profile, {
      nickname: profile.nickname || user.nickname || '蛋友3024',
      avatarUrl: profile.avatarUrl || user.avatarUrl || ''
    }));
  },

  save(changes) {
    const next = Object.assign({}, this.data, changes);
    this.setData(changes);
    wx.setStorageSync(PROFILE_KEY, next);
    const user = petStore.getUser();
    if (user && (changes.nickname || changes.avatarUrl)) petStore.saveUser(Object.assign({}, user, changes));
  },

  onChooseAvatar(e) {
    const avatarUrl = e.detail.avatarUrl;
    if (!avatarUrl) return;
    this.save({ avatarUrl });
    wx.showToast({ title: '头像已更新', icon: 'success' });
  },

  onEditNickname() {
    wx.showModal({
      title: '修改昵称', editable: true, placeholderText: '最多 16 个字', content: this.data.nickname,
      success: (res) => { if (res.confirm && res.content.trim()) this.save({ nickname: res.content.trim().slice(0, 16) }); }
    });
  },

  onEditGender() {
    if (this.data.genderLocked) return wx.showToast({ title: '性别设置后不可修改', icon: 'none' });
    wx.showModal({
      title: '设置性别', content: '性别设置后不可修改，确认继续吗？',
      success: (res) => {
        if (!res.confirm) return;
        wx.showActionSheet({ itemList: ['男', '女'], success: (result) => this.save({ gender: result.tapIndex === 0 ? '男' : '女', genderLocked: true }) });
      }
    });
  },

  onEditBirthday() {
    if (this.data.birthdayLocked) return wx.showToast({ title: '生日设置后不可修改', icon: 'none' });
    wx.showModal({
      title: '设置演示生日', content: '预览版将示例生日设为 2000-01-01，且设置后不可修改。',
      success: (res) => { if (res.confirm) this.save({ birthday: '2000-01-01', zodiac: '摩羯座', birthdayLocked: true }); }
    });
  },

  onEditCity() {
    wx.showActionSheet({ itemList: ['上海', '北京', '深圳', '杭州'], success: (result) => this.save({ city: ['上海', '北京', '深圳', '杭州'][result.tapIndex] }) });
  },

  onEditMbti() {
    wx.showActionSheet({ itemList: MBTI_LIST, success: (result) => this.save({ mbti: MBTI_LIST[result.tapIndex] }) });
  }
});
