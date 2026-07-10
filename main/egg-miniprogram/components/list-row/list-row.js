/* list-row：通用列表行
   label + 可选 value + 可选右侧 chevron。点击时向父级派发自定义事件
   'rowtap'（父级用 bind:rowtap="xxx" 监听），避免与原生 tap 事件混淆。
*/
Component({
  properties: {
    label: { type: String, value: '' },
    value: { type: String, value: '' },
    showChevron: { type: Boolean, value: true },
    showBorder: { type: Boolean, value: true },
    labelColor: { type: String, value: '#1A1A1A' } // --text，账号页的「注销账号」会传 --error
  },
  methods: {
    onTap() {
      this.triggerEvent('rowtap');
    }
  }
});
