// 两个入口都读取点击时的当前宠物；是否仍有资格由绘画页及后端再次核验。
function openDrawing(pet, source, beforeNavigate) {
  if (!pet || !pet.id) {
    wx.showToast({ title: '请先领取蛋宝宝并陪它破壳', icon: 'none' });
    return false;
  }
  if (pet.hatchStatus !== 'HATCHED') {
    wx.showToast({ title: '请切换到已破壳的蛋宝宝，或陪它完成孵化', icon: 'none' });
    return false;
  }
  if (beforeNavigate) beforeNavigate();
  wx.navigateTo({
    url: `/drawing/pages/draw/draw?source=${source === 'letter' ? 'letter' : 'activity'}`,
    fail: () => wx.showToast({ title: '暂时打不开，请稍后再试', icon: 'none' })
  });
  return true;
}

module.exports = { openDrawing };
