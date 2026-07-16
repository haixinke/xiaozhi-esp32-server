/**
 * 生活场景配置
 * 图片来自 OSS，路径格式：
 *   https://oss.eggbabe.com/default-scenes/{type}/scenes-{type}-{index}.jpg
 *   {type} 为角色标识（如 fish/rabbit），{index} 为场景索引
 * 索引与场景 key 的对应关系：
 *   0 → grass, 1 → snow, 2 → room, 3 → seaside, 4 → desk, 5 → roof
 */

var SCENES = [
  { key: 'grass', label: '草地', subtitle: '晒一下午太阳', petLine: '它把尾巴埋进草丛里，晒了一下午太阳。' },
  { key: 'snow', label: '雪地', subtitle: '踩出一串小脚印', petLine: '它踩着雪印，走一步，回头看你一眼。' },
  { key: 'room', label: '房间', subtitle: '窝在窗边听钟声', petLine: '它窝在窗边，听着屋里的钟走得很慢。' },
  { key: 'seaside', label: '海边', subtitle: '看海浪来了又退', petLine: '它蹲在礁石上，看海浪来了又退。' },
  { key: 'desk', label: '桌面', subtitle: '陪你写下今天', petLine: '它趴在桌上，尾巴扫过你的笔记本。' },
  { key: 'roof', label: '屋顶', subtitle: '一起数路过的云', petLine: '它坐在屋顶边缘，数着路过的云。' }
];

var HOTSPOTS = {
  grass: [{ x: '73%', y: '80%', label: '小花', line: '小花轻轻摇晃了一下。' }, { x: '50%', y: '16%', label: '蝴蝶', line: '一只蝴蝶刚好飞了过去。' }, { x: '65%', y: '20%', label: '阳光', line: '光斑在草叶上闪了一下。' }],
  snow: [{ x: '50%', y: '12%', label: '雪花', line: '雪花又轻轻飘落了几片。', effect: 'snowfall' }, { x: '20%', y: '60%', label: '雪堆', line: '雪堆抖落了一点雪。', effect: 'snow-puff' }, { x: '63%', y: '50%', label: '白气', line: '它呼出了一小口白气。', effect: 'breath' }],
  room: [{ x: '81%', y: '70%', label: '壁灯', line: '壁灯轻轻亮了一下。', effect: 'lamp-glow' }, { x: '32%', y: '80%', label: '小被子', line: '小被子鼓起来一点。', effect: 'blanket-lift' }, { x: '13%', y: '24%', label: '窗帘', line: '窗帘被风吹动了一下。', effect: 'curtain-sway' }],
  seaside: [{ x: '50%', y: '56%', label: '海浪', line: '海浪轻轻拍上了岸边。', effect: 'wave-splash' }, { x: '66%', y: '76%', label: '贝壳', line: '贝壳在阳光下亮了一下。', effect: 'shell-sparkle' }, { x: '66%', y: '28%', label: '小船', line: '远处的小船轻轻晃了一下。', effect: 'boat-bob' }],
  desk: [{ x: '25%', y: '70%', label: '纸张', line: '纸张被轻轻翻动了一页。', effect: 'paper-flip' }, { x: '42%', y: '76%', label: '便签', line: '便签上冒出了一句小小的话。', effect: 'note-pop' }, { x: '82%', y: '54%', label: '杯子', line: '杯子冒出了一点热气。', effect: 'steam-rise' }],
  roof: [{ x: '18%', y: '20%', label: '风铃', line: '风铃轻轻响了一下。', effect: 'chime-sway' }, { x: '36%', y: '40%', label: '纸飞机', line: '一架纸飞机飞了过去。', effect: 'plane-flight' }, { x: '65%', y: '18%', label: '云', line: '云朵慢慢飘远了一点。', effect: 'cloud-drift' }]
};

/** 场景 key → OSS URL 后缀索引的映射 */
var SCENE_KEY_TO_INDEX = {
  grass: 0, snow: 1, room: 2, seaside: 3, desk: 4, roof: 5
};

/** OSS URL 后缀索引 → 场景 key 的映射（用于 home 页面跳转解析） */
var SCENE_INDEX_TO_KEY = ['grass', 'snow', 'room', 'seaside', 'desk', 'roof'];

/**
 * 根据 scene key 获取场景配置
 * @param {string} key - 场景 key（grass/snow/room/seaside/desk/roof）
 * @returns {object} 场景配置对象
 */
function getScene(key) {
  var scene = SCENES.find(function (item) { return item.key === key; });
  return scene || SCENES[0];
}

/**
 * 获取全部场景列表
 */
function getScenesForCharacter() {
  return SCENES.slice();
}

/**
 * 从 sceneUrl 中提取索引并映射到场景 key
 * @param {string} sceneUrl - 如 https://oss.eggbabe.com/default-scenes/fish/scenes-fish-3.jpg
 * @returns {string} 场景 key（如 seaside），无法解析时返回 'grass'
 */
function getSceneKeyFromUrl(sceneUrl) {
  if (!sceneUrl) return 'grass';
  var match = sceneUrl.match(/scenes-\w+-(\d+)\./);
  if (!match) return 'grass';
  var index = parseInt(match[1], 10);
  return SCENE_INDEX_TO_KEY[index] || 'grass';
}

module.exports = {
  SCENES: SCENES,
  HOTSPOTS: HOTSPOTS,
  SCENE_KEY_TO_INDEX: SCENE_KEY_TO_INDEX,
  SCENE_INDEX_TO_KEY: SCENE_INDEX_TO_KEY,
  getScene: getScene,
  getScenesForCharacter: getScenesForCharacter,
  getSceneKeyFromUrl: getSceneKeyFromUrl
};
