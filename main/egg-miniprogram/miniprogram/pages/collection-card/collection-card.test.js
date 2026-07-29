const assert = require('assert');
const fs = require('fs');
const path = require('path');

const pageDir = __dirname;
const template = fs.readFileSync(path.join(pageDir, 'collection-card.wxml'), 'utf8');
const styles = fs.readFileSync(path.join(pageDir, 'collection-card.wxss'), 'utf8');
const script = fs.readFileSync(path.join(pageDir, 'collection-card.js'), 'utf8');

// 性别必须用 SVG 图标渲染：iOS 真机对 ♀/♂ 强制回退到符号/emoji 字体且基线偏低，
// 字体方案（去 FE0F、调行高）均无法垂直居中；未知性别回退文本 genderLabel
assert.match(
  template,
  /<view wx:if="{{genderClass}}" class="gender-icon {{genderClass}}" aria-label="{{genderLabel}}"><\/view>/,
  '性别必须通过 gender-icon SVG 图标展示'
);
assert.match(
  template,
  /<text wx:else class="stat-value">{{genderLabel}}<\/text>/,
  '未知性别必须回退到文本 genderLabel'
);
assert.match(styles, /\.gender-icon--female \{ background-image: url\("data:image\/svg\+xml/, '女性图标必须是内联 SVG');
assert.match(styles, /\.gender-icon--male \{ background-image: url\("data:image\/svg\+xml/, '男性图标必须是内联 SVG');
// 星座符号（♈ 等）仍是字符渲染，必须保留放开裁切的修饰类，避免 iOS 裁掉下半截
assert.match(
  styles,
  /\.stat-value--symbol \{ overflow: visible; line-height: normal; \}/,
  '符号修饰类必须放开 overflow 裁切并使用自然行高'
);
// genderLabel 仅供 Canvas 分享卡与文本回退，必须是文本形态 ♀/♂（不带 U+FE0F）
assert.match(script, /if \(value === 'FEMALE'\) return '♀';/, '女性文本回退必须是文本形态♀');
assert.match(script, /if \(value === 'MALE'\) return '♂';/, '男性文本回退必须是文本形态♂');
assert.doesNotMatch(script, /\ufe0f/, '性别符号不得带 U+FE0F 变体选择符');
assert.match(script, /function genderClass\(value\)/, '必须提供 genderClass 映射 SVG 图标修饰类');

console.log('collection-card.test.js: ALL PASS');
