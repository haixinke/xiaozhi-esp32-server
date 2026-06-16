/**
 * companion-codes.js
 *
 * AI 伴侣选项的编码配置。
 * 前后端统一使用 id 编码，前端根据 id 动态显示中文 label。
 */

var SOUL_TRAITS = [
  { id: 'clingy', label: '粘人精' },
  { id: 'flirty', label: '撒娇狂魔' },
  { id: 'toughSoft', label: '嘴硬心软' },
  { id: 'protective', label: '护短狂魔' },
  { id: 'straightShooter', label: '直球选手' },
  { id: 'rational', label: '人间清醒' },
];

var QUIRKS = [
  { id: 'grumpyMorning', label: '重度起床气' },
  { id: 'jealous', label: '小醋坛子' },
  { id: 'noDirection', label: '路痴晚期' },
  { id: 'gamerNoob', label: '游戏黑洞' },
  { id: 'nightOwl', label: '熬夜修仙党' },
  { id: 'indecisive', label: '选择困难症' },
  { id: 'chaoticLogic', label: '逻辑泥石流' },
  { id: 'kitchenDisaster', label: '炸厨房选手' },
];

var RELATION_TYPES = [
  { id: 'childhood', label: '青梅竹马' },
  { id: 'bickering', label: '欢喜冤家' },
  { id: 'loveAtFirst', label: '一见钟情' },
];

var PET_TYPES = [
  { id: 'cat', label: '猫' },
  { id: 'dog', label: '狗' },
];

var ROLES = [
  { id: 'baiyueguang', label: '高冷白月光' },
  { id: 'linjiamei', label: '元气邻家妹' },
  { id: 'zhixingyujie', label: '知性御姐' },
  { id: 'erciyuan', label: '潮酷二次元' },
];

var CDN_BASE_URL = 'https://636c-cloud1-9ghrcw8127746c64-1391989435.tcb.qcloud.la';

var CHARACTER_AVATARS = {
  baiyueguang: CDN_BASE_URL + '/girlfriend/head-img/baiyueguang.png',
  erciyuan: CDN_BASE_URL + '/girlfriend/head-img/erciyuan.png',
  linjiamei: CDN_BASE_URL + '/girlfriend/head-img/linjiamei.png',
  zhixingyujie: CDN_BASE_URL + '/girlfriend/head-img/zhichangjie.png',
};

var CHARACTER_IMAGES = {
  baiyueguang: CDN_BASE_URL + '/girlfriend/bg-img/baiyueguang.png',
  erciyuan: CDN_BASE_URL + '/girlfriend/bg-img/erciyuan.png',
  linjiamei: CDN_BASE_URL + '/girlfriend/bg-img/linjiamei.png',
  zhixingyujie: CDN_BASE_URL + '/girlfriend/bg-img/zhichangjie.png',
};

var VOICE_STYLES = {
  'TTS_HSDSTTS_V2_0001': 'wennuo',
  'TTS_HSDSTTS_V2_0020': 'sajiao',
  'TTS_HSDSTTS_V2_0017': 'zhixing',
  'TTS_HSDSTTS_V2_0022': 'tianmei',
};

var VIDEO_BASE_URL = CDN_BASE_URL + '/girlfriend/video';

/**
 * 根据编码获取显示标签
 * @param {Array} list - 选项列表
 * @param {string} id - 编码
 * @returns {string} 显示标签，找不到则返回编码本身
 */
function getLabel(list, id) {
  for (var i = 0; i < list.length; i++) {
    if (list[i].id === id) {
      return list[i].label;
    }
  }
  return id || '';
}

module.exports = {
  CDN_BASE_URL: CDN_BASE_URL,
  SOUL_TRAITS: SOUL_TRAITS,
  QUIRKS: QUIRKS,
  RELATION_TYPES: RELATION_TYPES,
  PET_TYPES: PET_TYPES,
  ROLES: ROLES,
  CHARACTER_AVATARS: CHARACTER_AVATARS,
  CHARACTER_IMAGES: CHARACTER_IMAGES,
  VOICE_STYLES: VOICE_STYLES,
  VIDEO_BASE_URL: VIDEO_BASE_URL,
  getLabel: getLabel,
};
