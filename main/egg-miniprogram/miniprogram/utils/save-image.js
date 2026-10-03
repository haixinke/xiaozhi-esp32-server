// 保存网络图片到相册：先下载到临时文件再写相册；用户拒绝授权时返回引导文案。
// photo-gen 与 photo-gallery 共用，避免两处各写一份下载/授权/提示链。

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
        wx.saveImageToPhotosAlbum({
          filePath: res.tempFilePath,
          success: () => resolve('已保存到相册'),
          fail: (err) => {
            // 用户拒绝相册授权时引导去设置页开启
            const denied = err && err.errMsg && err.errMsg.includes('auth');
            resolve(denied ? '请在设置中允许保存到相册' : '保存失败，请重试');
          }
        });
      },
      fail: () => resolve('保存失败，请重试')
    });
  });
}

module.exports = { saveRemoteImage };
