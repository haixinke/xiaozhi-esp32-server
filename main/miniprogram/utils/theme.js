/**
 * Theme utility - Dark mode management
 */

const DARK_BG = '#121220';
const DARK_NAV_BG = '#1a1a2e';
const DARK_NAV_TEXT = 'white';
const LIGHT_NAV_BG = '#fbf9f8';
const LIGHT_NAV_TEXT = 'black';
const DARK_TAB_COLOR = '#8a8a9a';
const LIGHT_TAB_COLOR = '#999999';
const DARK_TAB_SELECTED = '#c49ba5';
const LIGHT_TAB_SELECTED = '#864e5a';

function getTheme() {
  try {
    return wx.getStorageSync('darkMode') === true;
  } catch (e) {
    console.warn('[theme] storage read failed:', e);
    return false;
  }
}

function applyGlobalTheme() {
  const darkMode = getTheme();

  try {
    wx.setBackgroundColor({ backgroundColor: darkMode ? DARK_BG : LIGHT_NAV_BG });
  } catch (e) { /* ignore */ }

  try {
    wx.setNavigationBarColor({
      frontColor: darkMode ? DARK_NAV_TEXT : LIGHT_NAV_TEXT,
      backgroundColor: darkMode ? DARK_NAV_BG : LIGHT_NAV_BG,
      animation: { duration: 0 }
    });
  } catch (e) { /* ignore */ }

  try {
    wx.setTabBarStyle({
      color: darkMode ? DARK_TAB_COLOR : LIGHT_TAB_COLOR,
      selectedColor: darkMode ? DARK_TAB_SELECTED : LIGHT_TAB_SELECTED,
      backgroundColor: darkMode ? DARK_NAV_BG : LIGHT_NAV_BG,
      borderStyle: darkMode ? 'black' : 'white'
    });
  } catch (e) { /* ignore */ }
}

function applyTheme(page) {
  const darkMode = getTheme();
  page.setData({ darkMode });
  applyGlobalTheme();
}

function toggleTheme(page) {
  const current = getTheme();
  const newValue = !current;

  try {
    wx.setStorageSync('darkMode', newValue);
  } catch (e) {
    wx.showToast({ title: '主题切换失败，存储异常', icon: 'none', duration: 1500 });
    return;
  }

  applyTheme(page);

  wx.showToast({
    title: newValue ? '已切换至深色模式' : '已切换至浅色模式',
    icon: 'none',
    duration: 1500
  });
}

module.exports = { getTheme, applyGlobalTheme, applyTheme, toggleTheme };
