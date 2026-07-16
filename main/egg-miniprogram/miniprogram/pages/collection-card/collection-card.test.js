const assert = require('assert');
const fs = require('fs');
const path = require('path');

const pageDir = __dirname;
const template = fs.readFileSync(path.join(pageDir, 'collection-card.wxml'), 'utf8');
const styles = fs.readFileSync(path.join(pageDir, 'collection-card.wxss'), 'utf8');
const script = fs.readFileSync(path.join(pageDir, 'collection-card.js'), 'utf8');

assert.match(
  template,
  /wx:if="{{genderKind}}" class="gender-symbol gender-symbol--{{genderKind}}"/,
  '性别必须使用独立图标，不可依赖系统字体中的 Unicode 性别符号'
);
assert.doesNotMatch(
  template,
  /<text class="stat-value">{{genderLabel}}<\/text>/,
  '性别图标不得复用会裁剪字形的 stat-value 文本样式'
);
assert.match(script, /function genderKind\(value\)/, '页面必须提供可供模板渲染的性别图标类型');
['.gender-symbol', '.gender-symbol--female', '.gender-symbol--male'].forEach((selector) => {
  assert.ok(styles.includes(selector), `缺少跨字体渲染的 ${selector} 样式`);
});

console.log('collection-card.test.js: ALL PASS');
