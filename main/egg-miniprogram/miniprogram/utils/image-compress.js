// AI写真照片客户端压缩：超过 5MB 时先 wx.compressImage 降质，仍超再 canvas 缩到最长边 2048 转 JPEG。
// 后端 UploadScene.AI_GEN 上限 5MB（png/jpeg/webp），压缩只为减少大图误拒，上传链路不变。
// 生图 prompt 中照片仅作场景/氛围参考（人脸虚化），2048px 参考图对模型足够。
const MAX_FILE_SIZE = 5 * 1024 * 1024;
const MAX_DIMENSION = 2048;
const COMPRESS_QUALITY = 80; // wx.compressImage 的 quality 取值 0-100
const JPEG_QUALITY = 0.8;    // wx.canvasToTempFilePath 的 quality 取值 0-1

/** 查询文件大小，失败返回 0（视为未知） */
function getFileSize(filePath) {
  return new Promise((resolve) => {
    if (typeof wx.getFileInfo !== 'function') {
      resolve(0);
      return;
    }
    wx.getFileInfo({
      filePath,
      success: (res) => resolve(res.size || 0),
      fail: () => resolve(0)
    });
  });
}

/** 第一级：quality 压缩。仅对 JPEG 有效，PNG 靠第二级；失败返回空串 */
function compressImageQuality(src) {
  return new Promise((resolve) => {
    if (typeof wx.compressImage !== 'function') {
      resolve('');
      return;
    }
    wx.compressImage({
      src,
      quality: COMPRESS_QUALITY,
      success: (res) => resolve(res.tempFilePath || ''),
      fail: () => resolve('')
    });
  });
}

/** 第二级：canvas 等比缩到最长边 MAX_DIMENSION 并重编码 JPEG。PNG 透明通道填白底；不可用返回 null */
function downscaleWithCanvas(page, src) {
  return new Promise((resolve) => {
    if (!page || typeof wx.createSelectorQuery !== 'function' || typeof wx.canvasToTempFilePath !== 'function') {
      resolve(null);
      return;
    }
    let query;
    try {
      query = wx.createSelectorQuery().in(page);
    } catch (error) {
      resolve(null);
      return;
    }
    query.select('#compress-canvas').fields({ node: true }).exec((result) => {
      const target = result && result[0];
      const canvas = target && target.node;
      if (!canvas || typeof canvas.createImage !== 'function') {
        resolve(null);
        return;
      }
      const image = canvas.createImage();
      image.onload = () => {
        const longEdge = Math.max(image.width, image.height);
        if (!longEdge) {
          resolve(null);
          return;
        }
        const scale = Math.min(1, MAX_DIMENSION / longEdge);
        canvas.width = Math.round(image.width * scale);
        canvas.height = Math.round(image.height * scale);
        const ctx = canvas.getContext('2d');
        // 先铺白底再绘制：PNG 转 JPEG 时透明通道有着色
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        ctx.drawImage(image, 0, 0, canvas.width, canvas.height);
        wx.canvasToTempFilePath({
          canvas,
          fileType: 'jpg',
          quality: JPEG_QUALITY,
          destWidth: canvas.width,
          destHeight: canvas.height,
          success: (res) => resolve(res.tempFilePath || null),
          fail: () => resolve(null)
        });
      };
      image.onerror = () => resolve(null);
      image.src = src;
    });
  });
}

/**
 * 按需压缩：不超 5MB 原样返回；超限走两级压缩。
 * @param {{ tempFilePath: string, size?: number, page?: object }} input page 用于定位 #compress-canvas 节点
 * @returns {Promise<{ ok: boolean, tempFilePath: string, size: number }>}
 *   ok=true 表示可用于上传（size 为压缩后大小，未知时为 0）；ok=false 表示两级后仍超限
 */
async function compressIfNeeded({ tempFilePath, size, page }) {
  if (!tempFilePath) {
    return { ok: false, tempFilePath: '', size: size || 0 };
  }
  let currentSize = size || await getFileSize(tempFilePath);
  // size 未知时放行，与现状一致（超限由后端拒绝）
  if (!currentSize || currentSize <= MAX_FILE_SIZE) {
    return { ok: true, tempFilePath, size: currentSize };
  }

  let currentPath = tempFilePath;
  const compressedPath = await compressImageQuality(currentPath);
  if (compressedPath) {
    currentPath = compressedPath;
    currentSize = await getFileSize(compressedPath);
    if (currentSize && currentSize <= MAX_FILE_SIZE) {
      return { ok: true, tempFilePath: currentPath, size: currentSize };
    }
  }

  const downscaledPath = await downscaleWithCanvas(page, currentPath);
  if (downscaledPath) {
    currentSize = await getFileSize(downscaledPath);
    // 缩放产物是 2048px JPEG，必然远小于 5MB；查不到大小按未知放行（与入口策略一致，后端兜底）
    if (!currentSize || currentSize <= MAX_FILE_SIZE) {
      return { ok: true, tempFilePath: downscaledPath, size: currentSize };
    }
  }

  return { ok: false, tempFilePath, size: currentSize };
}

module.exports = { compressIfNeeded, MAX_FILE_SIZE };
