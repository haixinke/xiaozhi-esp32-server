/* card：通用卡片容器（对应设计系统 Card 组件）
   tint: 'base' | 'tint-green' | 'tint-yellow'
   页面内如需覆盖外边距等布局属性，使用 externalClasses 机制：
     <card ext-class="my-margin">...</card>
   并在页面自己的 wxss 里定义 .my-margin { margin: ... }
   —— 组件默认样式隔离（styleIsolation），页面 wxss 无法直接
   影响组件内部类名，这是官方推荐的跨组件传样式方式。
*/
Component({
  properties: {
    tint: { type: String, value: 'base' }
  }
});
