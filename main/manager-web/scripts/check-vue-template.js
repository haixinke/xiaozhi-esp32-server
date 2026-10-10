#!/usr/bin/env node
// 校验 .vue 文件 <template> 的语法，供 PostToolUse hook 在编辑后即时调用。
// 只编译模板部分（vue-template-compiler），不做完整构建，毫秒级返回。
// 用法: node scripts/check-vue-template.js <file.vue> [more.vue...]
// 退出码: 0 = 全部通过; 1 = 存在模板错误（错误输出到 stderr，hook 会以退出码 2 转呈 agent）
const fs = require("fs");
const path = require("path");
const compiler = require("vue-template-compiler");

let failed = false;

for (const file of process.argv.slice(2)) {
  let src;
  try {
    src = fs.readFileSync(file, "utf8");
  } catch {
    continue; // 文件已被删除/移动（hook 场景常见），跳过
  }
  // 用 parseComponent 而非正则提取 template：SFC 内可能嵌套多个 <template>
  // （如 el-table 的插槽模板），正则的非贪婪匹配会截断导致误报
  const sfc = compiler.parseComponent(src, { pad: "line" });
  if (!sfc.template) continue; // 无 template 块的 SFC（纯脚本组件）跳过
  const result = compiler.compile(sfc.template.content);
  for (const error of result.errors) {
    failed = true;
    // pad: "line" 使模板行号即文件行号；error 自带行号时直接使用
    console.error(`${path.basename(file)}: ${error}`);
  }
}

process.exit(failed ? 1 : 0);
