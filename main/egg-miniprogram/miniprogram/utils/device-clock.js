// 设备时钟快照：为 room-clock 组件提供 文本时间/日期 与 指针角度
// 移植自 eggbabe-miniprogram 静态项目 services/device-clock.js
const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

function pad(value) {
  return String(value).padStart(2, '0');
}

function snapshot(input) {
  const candidate = input instanceof Date ? input : new Date(input === undefined ? Date.now() : input);
  const date = Number.isFinite(candidate.getTime()) ? candidate : new Date();
  const hours = date.getHours();
  const minutes = date.getMinutes();
  const seconds = date.getSeconds();
  const milliseconds = date.getMilliseconds();
  const preciseSeconds = seconds + milliseconds / 1000;

  return {
    timeText: `${pad(hours)}:${pad(minutes)}`,
    dateText: `${date.getMonth() + 1}月${date.getDate()}日 ${WEEKDAYS[date.getDay()]}`,
    // 时针/分针随秒连续走，避免每秒跳一格的机械感
    hourAngle: (hours % 12) * 30 + minutes * 0.5 + preciseSeconds / 120,
    minuteAngle: minutes * 6 + preciseSeconds * 0.1,
    secondAngle: preciseSeconds * 6
  };
}

// 距下一秒边界的毫秒数，用于把 1s 定时器对齐到整秒，防止指针抖动
function millisecondsUntilNextSecond(timestamp) {
  const value = Number.isFinite(Number(timestamp)) ? Number(timestamp) : Date.now();
  const remainder = ((value % 1000) + 1000) % 1000;
  return remainder === 0 ? 1000 : 1000 - remainder;
}

module.exports = {
  snapshot,
  millisecondsUntilNextSecond
};
