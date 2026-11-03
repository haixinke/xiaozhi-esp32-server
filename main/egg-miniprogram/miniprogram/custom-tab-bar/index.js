// 自定义悬浮 tabBar：孵化期全屏场景下不遮挡画面，仅以圆形按钮悬浮于右下。
// 首页右下角显示百宝箱；「我的」页沿用原导航行为。
Component({
  data: {
    selected: 0,
    hidden: false,
    toolboxVisible: false,
    toolboxClosedIcon: '/assets/ui/3d-actions/ui_3d_scene_toolbox_closed_chest_96_v02_p8_v01.png',
    toolboxOpenIcon: '/assets/ui/3d-actions/ui_3d_scene_toolbox_open_chest_96_v01_p8_v01.png',
    activityIcon: '/assets/ui/3d-actions/ui_3d_toolbox_activity_star_96_v01_p8_v01.png',
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

  observers: {
    'hidden, selected'(hidden, selected) {
      if (hidden || selected !== 0) this.onCloseToolbox();
    }
  },

  methods: {
    onToggleToolbox() {
      if (this.data.hidden || this.data.selected !== 0) return;
      this.setData({ toolboxVisible: !this.data.toolboxVisible });
    },

    onCloseToolbox() {
      if (this.data.toolboxVisible) this.setData({ toolboxVisible: false });
    },

    onOpenActivities() {
      this.onCloseToolbox();
      wx.navigateTo({
        url: '/pages/iaa-activities/iaa-activities',
        fail: () => wx.showToast({ title: '暂时打不开，请重试', icon: 'none' })
      });
    },

    onOpenSettings() {
      this.onCloseToolbox();
      wx.switchTab({
        url: '/pages/my/my',
        fail: () => wx.showToast({ title: '暂时打不开，请重试', icon: 'none' })
      });
    },

    onSwitch(event) {
      const index = Number(event.currentTarget.dataset.index);
      const item = this.data.items[index];
      if (!item || index === this.data.selected) return;
      wx.switchTab({ url: item.pagePath });
    }
  }
});
