# 03: JSON 增量导入（含全部校验）

**What to build:** 运营在题库运营页面点击导入，上传 JSON 文件（结构见仓库根目录 `听你说_题库.json`：大类 → 题目分组 → 题目，每组 5 题）完成增量导入。校验规则：大类 id 必须已存在于 LISTEN_CATEGORY 字典，否则整单拒绝；同大类下组名重复或同组内题干重复（含与库内已有数据比对），任一冲突整个文件拒绝并返回冲突明细列表，前端弹窗展示，库内零写入（单事务）。JSON 内组 id、题 id 不入库；order 追加——新组接在该大类现有最大 order 之后，新题接在组内末尾。导入成功返回写入统计（N 组 M 题），前端明确提示。

**Blocked by:** 02 (题组浏览)

**Status:** resolved

## Answer

已实现（merge 46cf59c3 入 integration）。ListenImportController/Service：解析→结构校验→字典校验→判重→单事务写入；ListenImportException 携冲突明细（10373）；错误码 10372-10374 + 7 i18n。前端导入按钮+成功统计+冲突明细弹窗。29/29 后端、148/148 前端绿。

- [ ] `POST /admin/listen/import`（multipart JSON），超管权限
- [ ] 大类字典校验：不存在 → 整单拒绝
- [ ] 判重：组名大类内、题干组内，冲突返回明细，单事务零写入
- [ ] order 追加语义：不覆盖现有大类/组/题顺序
- [ ] 成功返回统计；错误码 10370 段 + 7 个 i18n properties 同步
- [ ] 前端：el-upload 导入按钮、成功统计提示、失败冲突明细展示
- [ ] Service 层单测：字典缺失拒绝 / 组名重复拒绝 / 题干重复拒绝 / 冲突明细内容 / 拒绝零写入 / order 追加
