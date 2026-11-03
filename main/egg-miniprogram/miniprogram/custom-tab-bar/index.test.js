const assert = require('assert');

const originalComponent = global.Component;
const originalWx = global.wx;
let definition;
const navigation = [];
global.Component = value => { definition = value; };
global.wx = {
  navigateTo: options => navigation.push(['page', options.url]),
  switchTab: options => navigation.push(['tab', options.url]),
  showToast: options => navigation.push(['toast', options.title])
};
require('./index');
const component = {
  ...definition.methods,
  data: JSON.parse(JSON.stringify(definition.data)),
  setData(value) { Object.assign(this.data, value); }
};
try {
  component.onToggleToolbox();
  assert.strictEqual(component.data.toolboxVisible, true, '箱子点击后展开');
  component.onToggleToolbox();
  assert.strictEqual(component.data.toolboxVisible, false, '再次点击收起');
  component.onToggleToolbox();
  component.onCloseToolbox();
  assert.strictEqual(component.data.toolboxVisible, false, '点击遮罩关闭');
  component.onToggleToolbox();
  component.onOpenActivities();
  assert.deepStrictEqual(navigation.pop(), ['page', '/pages/iaa-activities/iaa-activities']);
  assert.strictEqual(component.data.toolboxVisible, false, '跳转前关闭浮窗');
  component.onToggleToolbox();
  component.onOpenSettings();
  assert.deepStrictEqual(navigation.pop(), ['tab', '/pages/my/my']);
  assert.strictEqual(component.data.toolboxVisible, false);
  component.setData({ photoPetId: 'pet-001' });
  component.onToggleToolbox();
  component.onOpenPhoto();
  assert.deepStrictEqual(navigation.pop(), ['page', '/pages/photo-gallery/photo-gallery?petId=pet-001']);
  assert.strictEqual(component.data.toolboxVisible, false, '打开写真前收起浮窗');
  component.setData({ photoPetId: '' });
  component.onOpenPhoto();
  assert.strictEqual(navigation.length, 0, '未破壳或无宠物时不打开写真');
  component.setData({ selected: 1 });
  component.onToggleToolbox();
  assert.strictEqual(component.data.toolboxVisible, false, '非首页不展开百宝箱');
  component.setData({ selected: 0, hidden: true });
  component.onToggleToolbox();
  assert.strictEqual(component.data.toolboxVisible, false, '隐藏导航时不能展开');
  console.log('custom-tab-bar tests passed');
} finally {
  global.Component = originalComponent;
  global.wx = originalWx;
}
