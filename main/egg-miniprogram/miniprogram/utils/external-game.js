/*
 * 外部游戏打开辅助：小纸条与百宝箱两个入口共用的 EXTERNAL 分流逻辑。
 * 目标地址仅来自服务端运营配置（后端已校验 HTTPS），小程序凭证不随链接传递；
 * 地址经 encodeURIComponent 编码后由 webview 页完整还原，查询参数与锚点原样保留。
 * 页面自身的弹层/状态处理（如小纸条保持弹层打开）留在各页面，不进入本模块。
 */

const OPEN_FAIL_TOAST = '暂时打不开，请稍后再试';

/**
 * 在小程序 webview 页打开外部游戏。
 * @param {Object} game 游戏配置（需含 jumpUrl）
 * @returns {boolean} 是否已发起导航；链接缺失或纯空白时提示并返回 false
 */
function openExternalGame(game) {
  // 纯空白与缺失同义：配置脏数据不应导航到空白页
  const jumpUrl = game && typeof game.jumpUrl === 'string' ? game.jumpUrl.trim() : '';
  if (!jumpUrl) {
    wx.showToast({ title: OPEN_FAIL_TOAST, icon: 'none' });
    return false;
  }
  wx.navigateTo({
    url: `/pages/webview/webview?url=${encodeURIComponent(jumpUrl)}`,
    fail: () => wx.showToast({ title: OPEN_FAIL_TOAST, icon: 'none' })
  });
  return true;
}

module.exports = { openExternalGame, OPEN_FAIL_TOAST };
