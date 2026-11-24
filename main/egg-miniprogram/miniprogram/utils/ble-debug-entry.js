function isBleDebugMode(options) {
  if (!options || options.bleDebug !== '1' || typeof wx.getAccountInfoSync !== 'function') {
    return false;
  }

  try {
    const { miniProgram } = wx.getAccountInfoSync();
    return Boolean(miniProgram && ['develop', 'trial'].includes(miniProgram.envVersion));
  } catch {
    return false;
  }
}

module.exports = { isBleDebugMode };
