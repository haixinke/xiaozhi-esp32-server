/* collapse-item：手风琴条目
   同一个组件复用两种形态（variant 控制样式）：
   - 'category'：帮助中心的分类标题（加粗、小字号），本身也可展开/收起整组
   - 'question' （默认）：单条问答的问题行 + 展开后的答案 slot
   两者嵌套使用即可实现"分类 > 问题"两级手风琴，见 help 页面。
*/
Component({
  properties: {
    title: { type: String, value: '' },
    expanded: { type: Boolean, value: false },
    variant: { type: String, value: 'question' }, // 'category' | 'question'
    showBorder: { type: Boolean, value: true }
  },
  methods: {
    onToggle() {
      this.triggerEvent('toggle');
    }
  }
});
