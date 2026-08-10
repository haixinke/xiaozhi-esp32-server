// 自定义悬浮 tabBar：孵化期全屏场景下不遮挡画面，仅以圆形按钮悬浮于右下。
// 当前所在 tab 不渲染（在蛋宝宝首页只露出「我的」齿轮入口）。
Component({
  data: {
    selected: 0,
    hidden: false,
    items: [
      {
        pagePath: '/pages/home/home',
        text: '蛋宝宝',
        iconPath: '/assets/tab/egg.png',
        selectedIconPath: '/assets/tab/egg-active.png'
      },
      {
        pagePath: '/pages/my/my',
        text: '我的',
        iconPath: '/assets/ui/3d-actions/ui_3d_tabbar_interaction_gear_flat_96_v04.png',
        selectedIconPath: '/assets/ui/3d-actions/ui_3d_tabbar_interaction_gear_flat_96_v04.png'
      }
    ]
  },

  methods: {
    onSwitch(event) {
      const index = Number(event.currentTarget.dataset.index);
      const item = this.data.items[index];
      if (!item || index === this.data.selected) return;
      wx.switchTab({ url: item.pagePath });
    }
  }
});
