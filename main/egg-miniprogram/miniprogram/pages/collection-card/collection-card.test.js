const assert = require('assert');
const fs = require('fs');
const path = require('path');

const pageDir = __dirname;
const template = fs.readFileSync(path.join(pageDir, 'collection-card.wxml'), 'utf8');
const styles = fs.readFileSync(path.join(pageDir, 'collection-card.wxss'), 'utf8');
const script = fs.readFileSync(path.join(pageDir, 'collection-card.js'), 'utf8');

assert.match(
  template,
  /<text class="stat-value">{{genderLabel}}<\/text>/,
  '性别必须通过 genderLabel 统一展示'
);
assert.doesNotMatch(
  template,
  /gender-symbol/,
  '性别不应使用独立的符号图标节点'
);
assert.match(script, /if \(value === 'FEMALE'\) return '♀️';/, '女性必须显示为♀️ emoji');
assert.match(script, /if \(value === 'MALE'\) return '♂️';/, '男性必须显示为♂️ emoji');
assert.doesNotMatch(script, /function genderKind\(value\)/, '页面不应保留性别图标类型');
assert.doesNotMatch(styles, /\.gender-symbol/, '页面不应保留性别符号样式');

console.log('collection-card.test.js: ALL PASS');
