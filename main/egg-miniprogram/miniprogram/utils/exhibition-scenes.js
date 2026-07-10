const SCENES = [
  { key: 'grass', label: '草地', subtitle: '晒一下午太阳', image: '/assets/scenes/grass_with_egg.jpg', petLine: '它把尾巴埋进草丛里，晒了一下午太阳。' },
  { key: 'snow', label: '雪地', subtitle: '踩出一串小脚印', image: '/assets/scenes/snow_with_egg.jpg', petLine: '它踩着雪印，走一步，回头看你一眼。' },
  { key: 'room', label: '房间', subtitle: '窝在窗边听钟声', image: '/assets/scenes/room_with_egg.jpg', petLine: '它窝在窗边，听着屋里的钟走得很慢。' },
  { key: 'seaside', label: '海边', subtitle: '看海浪来了又退', image: '/assets/scenes/sea_with_egg.jpg', petLine: '它蹲在礁石上，看海浪来了又退。' },
  { key: 'desk', label: '桌面', subtitle: '陪你写下今天', image: '/assets/scenes/desk_with_egg.jpg', petLine: '它趴在桌上，尾巴扫过你的笔记本。' },
  { key: 'roof', label: '屋顶', subtitle: '一起数路过的云', image: '/assets/scenes/rooftop_with_egg.jpg', petLine: '它坐在屋顶边缘，数着路过的云。' }
];

const HOTSPOTS = {
  grass: [{ x: '73%', y: '80%', label: '小花', line: '小花轻轻摇晃了一下。' }, { x: '50%', y: '16%', label: '蝴蝶', line: '一只蝴蝶刚好飞了过去。' }, { x: '84%', y: '10%', label: '阳光', line: '光斑在草叶上闪了一下。' }],
  snow: [{ x: '50%', y: '12%', label: '雪花', line: '雪花又轻轻飘落了几片。' }, { x: '20%', y: '60%', label: '雪堆', line: '雪堆抖落了一点雪。' }, { x: '63%', y: '50%', label: '白气', line: '它呼出了一小口白气。' }],
  room: [{ x: '81%', y: '70%', label: '壁灯', line: '壁灯轻轻亮了一下。' }, { x: '32%', y: '80%', label: '小被子', line: '小被子鼓起来一点。' }, { x: '13%', y: '24%', label: '窗帘', line: '窗帘被风吹动了一下。' }],
  seaside: [{ x: '50%', y: '56%', label: '海浪', line: '海浪轻轻拍上了岸边。' }, { x: '66%', y: '88%', label: '贝壳', line: '贝壳在阳光下亮了一下。' }, { x: '88%', y: '24%', label: '小船', line: '远处的小船轻轻晃了一下。' }],
  desk: [{ x: '25%', y: '70%', label: '纸张', line: '纸张被轻轻翻动了一页。' }, { x: '42%', y: '88%', label: '便签', line: '便签上冒出了一句小小的话。' }, { x: '88%', y: '54%', label: '杯子', line: '杯子冒出了一点热气。' }],
  roof: [{ x: '18%', y: '20%', label: '风铃', line: '风铃轻轻响了一下。' }, { x: '36%', y: '40%', label: '纸飞机', line: '一架纸飞机飞了过去。' }, { x: '78%', y: '10%', label: '云', line: '云朵慢慢飘远了一点。' }]
};

function getScene(key) {
  return SCENES.find(scene => scene.key === key) || SCENES[0];
}

module.exports = { SCENES, HOTSPOTS, getScene };
