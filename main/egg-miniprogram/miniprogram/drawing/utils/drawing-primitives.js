// 保留静态工程的像素笔触及三种像素贴纸，避免迁移后改变绘画风格。
const PIXELS = Object.freeze({
  star: ['0001000', '0001000', '1101011', '0111110', '0011100', '0110110', '1100011'],
  heart: ['0110110', '1111111', '1111111', '0111110', '0011100', '0001000'],
  leaf: ['0000110', '0001110', '0011100', '0111000', '1110000', '0100000']
});

function drawSticker(ctx, operation, width, height) {
  const pixels = PIXELS[operation.sticker] || PIXELS.star;
  const size = Math.max(2, Math.round(Math.min(width, height) * 0.012));
  const point = operation.points[0];
  const x = Math.round(Math.max(0.12, Math.min(0.88, point.x)) * width - pixels[0].length * size / 2);
  const y = Math.round(Math.max(0.12, Math.min(0.9, point.y)) * height - pixels.length * size / 2);
  ctx.fillStyle = 'rgba(70,91,62,.58)';
  pixels.forEach((row, rowIndex) => Array.from(row).forEach((pixel, columnIndex) => {
    if (pixel === '1') ctx.fillRect(x + columnIndex * size, y + rowIndex * size, size, size);
  }));
}

function drawBrush(ctx, operation, width, height) {
  const size = Math.max(1, Math.round(operation.width));
  const stamp = point => ctx.fillRect(Math.round(point.x * width - size / 2),
    Math.round(point.y * height - size / 2), size, size);
  const points = operation.points;
  if (points.length === 1) { stamp(points[0]); return; }
  points.slice(1).forEach((point, index) => {
    const previous = points[index];
    const distance = Math.hypot((point.x - previous.x) * width, (point.y - previous.y) * height);
    const steps = Math.max(1, Math.ceil(distance / Math.max(1, size * 0.55)));
    for (let step = 0; step <= steps; step += 1) {
      const ratio = step / steps;
      stamp({ x: previous.x + (point.x - previous.x) * ratio,
        y: previous.y + (point.y - previous.y) * ratio });
    }
  });
}

module.exports = { drawSticker, drawBrush };
