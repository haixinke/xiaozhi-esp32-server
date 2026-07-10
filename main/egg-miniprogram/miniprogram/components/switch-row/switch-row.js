/* switch-row：通知开关行
   包裹微信原生 <switch> —— 原生组件自带无障碍语义与系统级动效，
   不需要也不建议自己画开关滑块。color 用品牌绿 --egg-green。
   切换后向父级派发 'change' 事件，detail: { value: boolean }。
*/
Component({
  properties: {
    title: { type: String, value: '' },
    desc: { type: String, value: '' },
    checked: { type: Boolean, value: false },
    showBorder: { type: Boolean, value: true }
  },
  methods: {
    onChange(e) {
      this.triggerEvent('change', { value: e.detail.value });
    }
  }
});
