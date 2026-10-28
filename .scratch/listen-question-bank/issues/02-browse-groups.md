# 02: 题组浏览：大类 Tab + 题组列表

**What to build:** 运营在智控台「运营管理」菜单下看到「题库运营」入口，进入页面后：顶部大类 Tab（从字典 LISTEN_CATEGORY 实时读取并翻译名称）、当前大类的题组分页列表（组名、题数）、点击题组可查看组内全部题目（题干 + A-E 选项）。本期为只读浏览——运营能完整查看题库内容，但还不能导入、编辑、删除。后端提供题组分页与组详情接口，全部仅超管可用。

**Blocked by:** 01 (字典与建表)

**Status:** resolved

## Answer

已实现（commit d569d290，merge 5770a930 入 integration）。后端：page+detail 接口、字典翻译带 code 回落、10370/10371 + 7 i18n。前端：ListenQuestionBank.vue 大类 Tab+题组分页+只读弹窗、路由、菜单三处接线。后端 58 测试绿，前端 146/146 绿。

- [ ] `GET /admin/listen/group/page?categoryCode=` 题组分页，超管权限
- [ ] `GET /admin/listen/group/{id}` 组详情含组内题目与选项，超管权限
- [ ] 菜单：HeaderBar 运营管理下拉新增「题库运营」（dropdown-item、routerPaths、active-tab OR 链三处）
- [ ] 路由注册（requiresAuth），页面组件创建
- [ ] 页面：大类 Tab + 题组列表 + 组内题目查看
- [ ] 后端 Service 层单测：分页、组详情、越权拒绝
- [ ] 前端 node:test：路由、菜单项、API 模块注册接线断言
