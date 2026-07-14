const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const miniprogram = path.join(root, 'miniprogram');
const errors = [];

function readJson(file) {
  try { return JSON.parse(fs.readFileSync(file, 'utf8')); }
  catch (error) { errors.push(`${path.relative(root, file)}: ${error.message}`); return {}; }
}

const project = readJson(path.join(root, 'project.config.json'));
if (project.miniprogramRoot !== 'miniprogram/') errors.push('project.config.json 必须设置 miniprogramRoot 为 miniprogram/');

const app = readJson(path.join(miniprogram, 'app.json'));
const pages = app.pages || [];
if (!pages.length) errors.push('app.json 没有注册页面');
if (pages.some(page => page.includes('scene-picker') || page.includes('scene-live'))) errors.push('最新 PRD 禁止编译孵化场景选择页面');

for (const page of pages) {
  for (const extension of ['js', 'json', 'wxml', 'wxss']) {
    const file = path.join(miniprogram, `${page}.${extension}`);
    if (!fs.existsSync(file)) errors.push(`缺少页面文件：${page}.${extension}`);
  }
  const pageJsonFile = path.join(miniprogram, `${page}.json`);
  const config = readJson(pageJsonFile);
  for (const [name, relative] of Object.entries(config.usingComponents || {})) {
    const componentBase = path.resolve(path.dirname(pageJsonFile), relative);
    for (const extension of ['js', 'json', 'wxml', 'wxss']) {
      if (!fs.existsSync(`${componentBase}.${extension}`)) errors.push(`${page}: 组件 ${name} 缺少 ${extension} 文件`);
    }
  }
  const wxmlFile = path.join(miniprogram, `${page}.wxml`);
  if (fs.existsSync(wxmlFile)) {
    const wxml = fs.readFileSync(wxmlFile, 'utf8');
    if (/<\/?(?:div|span|b)(?:\s|>)/.test(wxml)) errors.push(`${page}.wxml 使用了非小程序 HTML 标签`);
  }
}

const homeSource = fs.readFileSync(path.join(miniprogram, 'pages/home/home.wxml'), 'utf8');
if (/孵化场景|scene-picker|scene-live/.test(homeSource)) errors.push('首页仍包含旧版孵化场景入口');


if (errors.length) {
  console.error(`项目校验失败（${errors.length} 项）：`);
  errors.forEach(error => console.error(`- ${error}`));
  process.exit(1);
}

console.log(`项目校验通过：${pages.length} 个页面，工程结构与关键 PRD 约束正常。`);
