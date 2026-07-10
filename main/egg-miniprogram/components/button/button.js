/* button（在页面 json 里建议注册为 "app-button"，避免和原生 <button>
   标签重名造成混淆——本组件本身文件名/文件夹沿用 button 即可，
   usingComponents 里的 key 才是 wxml 里实际使用的标签名）。
   variant: 'primary' | 'secondary' | 'danger'
*/
Component({
  properties: {
    text: { type: String, value: '' },
    variant: { type: String, value: 'primary' },
    disabled: { type: Boolean, value: false }
  },
  methods: {
    onTap() {
      if (this.data.disabled) return;
      this.triggerEvent('tap');
    }
  }
});
