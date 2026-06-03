/**
 * pages/destiny/destiny.js
 *
 * "命运初见"页面：新用户选择 AI 伴侣的角色、职业、音色和个性。
 * 当用户没有 agent 时展示，完成后创建个性化 agent。
 */

const { post, put } = require('../../utils/request');
const { createPet } = require('../../utils/device');

const app = getApp();

// 角色预设
const CHARACTERS = [
  {
    name: '高冷白月光',
    image: 'https://636c-cloud1-9ghrcw8127746c64-1391989435.tcb.qcloud.la/girlfriend/bg-img/baiyueguang.png',
    basePrompt: '你是一个外表高冷、内心温柔的女生。你说话简洁，偶尔流露关心，让人忍不住想靠近。你的温柔藏在细节里，不会轻易表露。',
  },
  {
    name: '元气邻家妹',
    image: 'https://636c-cloud1-9ghrcw8127746c64-1391989435.tcb.qcloud.la/girlfriend/bg-img/linjiamei.png',
    basePrompt: '你是一个活力满满的邻家女孩，热情开朗，喜欢用可爱的语气说话，会用各种可爱的称呼叫用户。你的世界总是充满阳光和正能量。',
  },
  {
    name: '知性职场姐',
    image: 'https://636c-cloud1-9ghrcw8127746c64-1391989435.tcb.qcloud.la/girlfriend/bg-img/zhichangjie.png',
    basePrompt: '你是一个温柔知性的职场姐姐。你说话轻柔，善解人意，总能在用户需要的时候给出恰到好处的建议和安慰。你有一种让人放松的魔力。',
  },
  {
    name: '潮酷二次元',
    image: 'https://636c-cloud1-9ghrcw8127746c64-1391989435.tcb.qcloud.la/girlfriend/bg-img/erciyuan.png',
    basePrompt: '你是一个酷酷的二次元女生，热爱动漫和游戏，说话夹带二次元梗，个性十足又有趣。偶尔傲娇，但其实很在乎身边的人。',
  },
];

// 职业预设
const OCCUPATIONS = [
  { label: '大厂设计师', icon: 'design', prompt: '你是一名大厂设计师，对美学有极致追求，生活中处处体现设计感' },
  { label: '自由摄影师', icon: 'camera', prompt: '你是一名自由摄影师，善于发现生活中的美，经常分享你看到的精彩瞬间' },
  { label: '白衣天使', icon: 'medical', prompt: '你是一名护士，关心他人的健康，看到不健康的生活习惯会忍不住提醒' },
  { label: '幼儿园老师', icon: 'child', prompt: '你是一名幼儿园老师，温柔有耐心，说话带着童趣，喜欢用可爱的比喻' },
  { label: '瑜伽教练', icon: 'yoga', prompt: '你是一名瑜伽教练，注重身心平衡，经常分享健康生活方式的小建议' },
  { label: '情感电台主播', icon: 'radio', prompt: '你是一名情感电台主播，善于倾听，说话有磁性，总能在夜里给人温暖的陪伴' },
  { label: '邻家学妹', icon: 'school', prompt: '你是一个活泼的大学女生，偶尔撒娇，喜欢分享校园生活的小趣事' },
  { label: '独立音乐人', icon: 'music', prompt: '你是一名独立音乐人，文艺且有个性，说话带着诗意的节奏感' },
  { label: '知名Coser', icon: 'cosplay', prompt: '你是一名知名Coser，热爱二次元文化，说话偶尔夹带动漫梗，活泼有趣' },
];

// 音色预设
const VOICES = ['邻家', '可爱', '调皮'];

Page({
  data: {
    characters: CHARACTERS,
    currentCharIdx: 0,
    occupations: OCCUPATIONS,
    selectedOccupation: -1,
    voices: VOICES,
    selectedVoice: 1,
    quirks: '',
    quirksCount: 0,
    submitting: false,
  },

  // 角色切换
  onCharChange(e) {
    this.setData({ currentCharIdx: e.detail.current });
  },

  // 职业选择
  onOccupationTap(e) {
    const idx = e.currentTarget.dataset.index;
    this.setData({ selectedOccupation: idx === this.data.selectedOccupation ? -1 : idx });
  },

  // 音色选择（picker-view）
  onVoiceChange(e) {
    const val = e.detail.value[0];
    if (val !== undefined && val !== null) {
      this.setData({ selectedVoice: val });
    }
  },

  // 职业病输入
  onQuirksInput(e) {
    const val = e.detail.value;
    this.setData({ quirks: val, quirksCount: val.length });
  },

  // 提交 - 创建 agent
  async onSubmit() {
    if (this.data.submitting) return;

    const { selectedOccupation, quirks, characters, currentCharIdx, occupations } = this.data;
    if (selectedOccupation < 0) {
      wx.showToast({ title: '请选择一个职业身份', icon: 'none' });
      return;
    }

    this.setData({ submitting: true });

    try {
      const character = characters[currentCharIdx];
      const occupation = occupations[selectedOccupation];

      // 1. 创建 agent
      const createRes = await post('/agent', { agentName: character.name });
      const agentId = createRes.data;

      // 2. 构建 systemPrompt 并更新 agent
      let systemPrompt = character.basePrompt;
      systemPrompt += '\n\n' + occupation.prompt + '。';
      if (quirks.trim()) {
        systemPrompt += '\n\n她有一个可爱的"职业病"：' + quirks.trim();
      }

      await put('/agent/' + agentId, { systemPrompt });

      // 3. 更新全局状态
      app.globalData.agentId = agentId;
      app.globalData.agentName = character.name;
      wx.setStorageSync('agentId', agentId);

      // 4. 继续设备绑定和宠物创建（如果尚未完成）
      if (!app.globalData.isDeviceBound) {
        await app.checkDeviceStatus();
      }

      try {
        await createPet(app.globalData.virtualMAC);
      } catch (_) {}

      // 5. 清除 needsDestiny 标记并导航到首页
      app.globalData.needsDestiny = false;
      wx.switchTab({ url: '/pages/index/index' });
    } catch (err) {
      console.error('创建 agent 失败:', err);
      wx.showToast({ title: '创建失败，请重试', icon: 'none' });
      this.setData({ submitting: false });
    }
  },
});
