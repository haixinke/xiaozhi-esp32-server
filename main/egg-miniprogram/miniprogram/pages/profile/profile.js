const petStore = require('../../utils/pet-store');
const request = require('../../utils/request');
const auth = require('../../utils/auth');
const { API_BASE_URL } = require('../../config/api');

const PROFILE_KEY = 'eggbaby_profile_v1';
const MBTI_LIST = ['INFP','INFJ','INTJ','INTP','ENFP','ENFJ','ENTJ','ENTP','ISFP','ISFJ','ISTJ','ISTP','ESFP','ESFJ','ESTJ','ESTP'];
const GENDER_LIST = ['男', '女', '其他'];
const GENDER_MAP = { '男': 'MALE', '女': 'FEMALE', '其他': 'OTHER' };
const GENDER_REVERSE = { MALE: '男', FEMALE: '女', OTHER: '其他' };
const CITY_MAX_LENGTH = 10;

const ZODIAC_MAP = {
  aries: '白羊座', taurus: '金牛座', gemini: '双子座',
  cancer: '巨蟹座', leo: '狮子座', virgo: '处女座',
  libra: '天秤座', scorpio: '天蝎座', sagittarius: '射手座',
  capricorn: '摩羯座', aquarius: '水瓶座', pisces: '双鱼座'
};

function translateZodiac(zodiac) {
  if (!zodiac) return '';
  const code = String(zodiac).toLowerCase();
  return ZODIAC_MAP[code] || zodiac;
}

function maskUserId(userId) {
  const s = String(userId || '');
  return s.slice(0, 8);
}

function formatDisplay(profile) {
  const user = petStore.getUser() || {};
  const session = auth.getSession() || {};
  const userId = user.id || session.userId || profile.userId || '';
  return {
    nickname: profile.nickname || user.nickname || '蛋友',
    avatarUrl: profile.avatarUrl || user.avatarUrl || '',
    userId: maskUserId(userId),
    gender: profile.gender ? (GENDER_REVERSE[profile.gender] || '未设置') : '未设置',
    birthday: profile.birthday || '未设置',
    zodiac: translateZodiac(profile.zodiac) || '——',
    city: profile.city || '未设置',
    mbti: profile.mbti || '未设置'
  };
}

Page({
  data: {
    nickname: '蛋友',
    userId: '',
    gender: '未设置',
    birthday: '未设置',
    zodiac: '——',
    mbti: '未设置',
    mbtiList: MBTI_LIST,
    avatarUrl: ''
  },

  onLoad() {
    this.loadProfile();
  },

  loadProfile() {
    request.get('/wechat/profile')
      .then((profile) => {
        wx.setStorageSync(PROFILE_KEY, profile);
        petStore.syncUserProfile(profile);
        this.setData(formatDisplay(profile));
      })
      .catch(() => {
        const cached = wx.getStorageSync(PROFILE_KEY) || {};
        const user = petStore.getUser() || {};
        const session = auth.getSession() || {};
        this.setData(formatDisplay({ ...cached, userId: user.id || session.userId }));
      });
  },

  refreshProfile() {
    return request.get('/wechat/profile')
      .then((profile) => {
        wx.setStorageSync(PROFILE_KEY, profile);
        petStore.syncUserProfile(profile);
        this.setData(formatDisplay(profile));
        return profile;
      });
  },

  saveProfile(partial) {
    return request.put('/wechat/profile', partial)
      .then(() => {
        const cached = wx.getStorageSync(PROFILE_KEY) || {};
        const next = { ...cached, ...partial };
        wx.setStorageSync(PROFILE_KEY, next);
        petStore.syncUserProfile(next);
        this.setData(formatDisplay(next));
        wx.showToast({ title: '保存成功', icon: 'success' });
      })
      .catch((error) => {
        wx.showToast({ title: (error && error.userMessage) || '保存失败', icon: 'none' });
        throw error;
      });
  },

  onChooseAvatar(e) {
    const tempPath = e.detail.avatarUrl;
    if (!tempPath) return;
    const session = auth.getSession();
    if (!session || !session.token) {
      wx.showToast({ title: '登录状态已失效', icon: 'none' });
      return;
    }
    wx.uploadFile({
      url: `${API_BASE_URL}/wechat/avatar`,
      filePath: tempPath,
      name: 'file',
      header: { Authorization: `Bearer ${session.token}` },
      success: (res) => {
        if (res.statusCode !== 200) {
          wx.showToast({ title: '头像上传失败', icon: 'none' });
          return;
        }
        try {
          const envelope = JSON.parse(res.data);
          if (envelope.code !== 0 || !envelope.data) {
            wx.showToast({ title: envelope.msg || '头像上传失败', icon: 'none' });
            return;
          }
          const avatarUrl = envelope.data;
          this.saveProfile({ avatarUrl });
        } catch (error) {
          wx.showToast({ title: '头像上传失败', icon: 'none' });
        }
      },
      fail: () => wx.showToast({ title: '头像上传失败', icon: 'none' })
    });
  },

  onEditNickname() {
    wx.showModal({
      title: '修改昵称',
      editable: true,
      placeholderText: '最多 16 个字',
      content: this.data.nickname,
      success: (res) => {
        if (!res.confirm) return;
        const value = String(res.content || '').trim().slice(0, 16);
        if (!value) return;
        this.saveProfile({ nickname: value });
      }
    });
  },

  onEditGender() {
    wx.showActionSheet({
      itemList: GENDER_LIST,
      success: (result) => {
        const value = GENDER_MAP[GENDER_LIST[result.tapIndex]];
        this.saveProfile({ gender: value });
      }
    });
  },

  onEditBirthday(e) {
    const value = e.detail.value;
    if (!value) return;
    this.saveProfile({ birthday: value })
      .then(() => this.refreshProfile());
  },

  onEditCity() {
    wx.showModal({
      title: '修改常驻城市',
      editable: true,
      placeholderText: `最多 ${CITY_MAX_LENGTH} 个字`,
      content: this.data.city === '未设置' ? '' : this.data.city,
      success: (res) => {
        if (!res.confirm) return;
        const value = String(res.content || '').trim().slice(0, CITY_MAX_LENGTH);
        if (!value) return;
        this.saveProfile({ city: value });
      }
    });
  },

  onMbtiChange(e) {
    this.saveProfile({ mbti: MBTI_LIST[e.detail.value] });
  }
});
