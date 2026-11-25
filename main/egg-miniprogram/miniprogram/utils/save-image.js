// 用户拒绝相册授权时返回引导文案，由调用方展示。
function saveLocalImage(filePath) {
  return new Promise((resolve) => {
    wx.saveImageToPhotosAlbum({
      filePath,
      success: () => resolve('已保存到相册'),
      fail: (err) => {
        const denied = err && err.errMsg && err.errMsg.includes('auth');
        resolve(denied ? '请在设置中允许保存到相册' : '保存失败，请重试');
      }
    });
  }).catch(() => '保存失败，请重试');
}

/**
 * @param {string} url 图片 URL（http/https）
 * @returns {Promise<string>} 结果提示文案（成功或失败原因），由调用方 toast
 */
function saveRemoteImage(url) {
  return new Promise((resolve) => {
    wx.downloadFile({
      url,
      success: (res) => {
        if (res.statusCode !== 200 || !res.tempFilePath) {
          resolve('保存失败，请重试');
          return;
        }
        resolve(saveLocalImage(res.tempFilePath));
      },
      fail: () => resolve('保存失败，请重试')
    });
  });
}

module.exports = { saveLocalImage, saveRemoteImage };
