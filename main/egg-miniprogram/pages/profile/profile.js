const MBTI_LIST = [
  'INFP', 'INFJ', 'INTJ', 'INTP',
  'ENFP', 'ENFJ', 'ENTJ', 'ENTP',
  'ISFP', 'ISFJ', 'ISTJ', 'ISTP',
  'ESFP', 'ESFJ', 'ESTJ', 'ESTP'
];

Page({
  data: {
    nickname: '蛋友3024',
    userId: '038291847562',
    gender: '未设置',
    birthday: '未设置',
    zodiac: '——',
    city: '上海 · 浦东新区',
    mbti: 'INFP',
    genderLocked: false,
    birthdayLocked: false
  },

  onChooseAvatar(e) {
    const { avatarUrl } = e.detail;
    // TODO: wx.uploadFile 把本地临时路径 avatarUrl 上传到你们的对象存储，
    // 拿到正式 URL 后再写回用户资料接口，这里仅做本地演示反馈。
    wx.showToast({ title: '头像已更新', icon: 'success' });
  },

  onEditNickname() {
    wx.showModal({
      title: '修改昵称',
      editable: true,
      placeholderText: '最多 16 个字',
      content: this.data.nickname,
      success: (res) => {
        if (res.confirm && res.content) {
          this.setData({ nickname: res.content.slice(0, 16) });
          // TODO: 调用接口保存昵称
        }
      }
    });
  },

  onEditGender() {
    if (this.data.genderLocked) return; // 一次性设置字段，锁定后禁止再次修改
    wx.showModal({
      title: '设置性别',
      content: '性别设置后不可修改，确认继续吗？',
      success: (res) => {
        if (!res.confirm) return;
        wx.showActionSheet({
          itemList: ['男', '女'],
          success: (r) => {
            this.setData({
              gender: r.tapIndex === 0 ? '男' : '女',
              genderLocked: true
            });
            // TODO: 调用接口保存性别（服务端也应校验一次性设置规则）
          }
        });
      }
    });
  },

  onEditBirthday() {
    if (this.data.birthdayLocked) return; // 一次性设置字段，锁定后禁止再次修改
    wx.showModal({
      title: '设置生日',
      content: '生日设置后不可修改，确认继续吗？',
      success: (res) => {
        if (!res.confirm) return;
        // TODO: 生产环境这里应打开 <picker mode="date"> 弹层选择具体日期，
        // 示例直接演示"设置后锁定"的最终态。
        this.setData({
          birthday: '2000-01-01',
          zodiac: '摩羯座',
          birthdayLocked: true
        });
      }
    });
  },

  onEditCity() {
    // TODO: 接入城市选择器（省市区三级联动 / wx.chooseLocation 地图选点二选一）
    wx.showToast({ title: '城市选择器待接入', icon: 'none' });
  },

  onEditMbti() {
    wx.showActionSheet({
      itemList: MBTI_LIST,
      success: (r) => {
        this.setData({ mbti: MBTI_LIST[r.tapIndex] });
        // TODO: 调用接口保存 MBTI
      }
    });
  }
});
