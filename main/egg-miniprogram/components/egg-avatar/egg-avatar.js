/* egg-avatar：蛋形头像占位
   真实素材接入前的品牌化占位图形，用渐变 + 非对称圆角模拟蛋壳形状
   （对应设计系统 --radius-egg: 50% 50% 50% 50% / 58% 58% 42% 42%）。
   size：传入设计稿里的「宽度 px」（如 28/34/56/96），组件内部按
   固定宽高比 96:112 换算成 rpx（1px = 2rpx），任意 size 均可用。 */
Component({
  properties: {
    size: { type: Number, value: 72 },
    gradientFrom: { type: String, value: '#EDE78E' }, // --egg-yellow
    gradientTo: { type: String, value: '#F4B9AE' }    // --egg-pink
  },
  data: { w: 144, h: 168 },
  lifetimes: {
    attached() { this.compute(); }
  },
  observers: {
    size() { this.compute(); }
  },
  methods: {
    compute() {
      const px = this.data.size || 72;
      const w = Math.round(px * 2);            // 1px = 2rpx
      const h = Math.round(px * 2 * 112 / 96); // 宽高比 96:112
      this.setData({ w, h });
    }
  }
});
