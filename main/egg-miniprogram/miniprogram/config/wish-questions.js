/**
 * 许愿池题目静态配置（PRD 5.2.1）
 * 共 7 道单选题，每道题选项带 emoji。
 * 题目为产品文案，放在前端本地，不建后端表。
 * 用 id 唯一标识题目，便于匹配已答记录。
 */
module.exports = [
  {
    id: 'world-color',
    title: '你希望蛋宝宝破壳后，第一眼看到的"世界"是什么颜色？',
    options: [
      { emoji: '🌿', text: '森林绿' },
      { emoji: '🌊', text: '海洋蓝' },
      { emoji: '🌸', text: '樱花粉' },
      { emoji: '🌅', text: '夕阳橙' },
      { emoji: '🌌', text: '星空紫' }
    ]
  },
  {
    id: 'sleep-type',
    title: '你希望蛋宝宝是早睡早起型，还是陪你熬夜型？',
    options: [
      { emoji: '🐦', text: '早鸟型' },
      { emoji: '🦉', text: '夜猫型' },
      { emoji: '🌓', text: '跟随型' }
    ]
  },
  {
    id: 'gender',
    title: '你希望蛋宝宝破壳后，性别是？',
    options: [
      { emoji: '🧢', text: '小男孩' },
      { emoji: '🎀', text: '小女孩' },
      { emoji: '🌈', text: '让蛋宝宝自己选' }
    ]
  },
  {
    id: 'life-type',
    title: '你更希望蛋宝宝像哪种小生命？',
    options: [
      { emoji: '🌞', text: '小太阳' },
      { emoji: '🌙', text: '小月亮' },
      { emoji: '🌪️', text: '小风' },
      { emoji: '🪨', text: '小石头' }
    ]
  },
  {
    id: 'animal-soul',
    title: '你希望蛋宝宝是哪种小动物的灵魂？',
    options: [
      { emoji: '🐶', text: '小狗型' },
      { emoji: '🐱', text: '小猫型' },
      { emoji: '🐰', text: '小兔型' },
      { emoji: '🦥', text: '小树懒型' }
    ]
  },
  {
    id: 'world-taste',
    title: '你希望蛋宝宝睁开眼睛时，这个世界是什么味道的？',
    options: [
      { emoji: '🍃', text: '雨后青草味' },
      { emoji: '🍞', text: '烤面包味' },
      { emoji: '🌸', text: '晒过的被子味' },
      { emoji: '🍊', text: '橘子汽水味' }
    ]
  },
  {
    id: 'inner-weather',
    title: '如果蛋宝宝身体里住着一种天气，你希望是？',
    options: [
      { emoji: '🌤️', text: '晴天' },
      { emoji: '🌧️', text: '小雨' },
      { emoji: '⛅', text: '多云' },
      { emoji: '🌈', text: '彩虹' }
    ]
  }
];
