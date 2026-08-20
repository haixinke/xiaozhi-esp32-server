// 展开时先撑宽度再显示文案的延迟，收起时先隐文案再收宽度的延迟
// 与 wxss 中 width/max-height 过渡时长配合，避免文字被裁切跳动
const CONTENT_REVEAL_DELAY_MS = 90;
const COLLAPSE_WIDTH_DELAY_MS = 160;

Component({
  properties: {
    petName: { type: String, value: '还没有名字' },
    moodLabel: { type: String, value: '' },
    moodText: { type: String, value: '' },
    nameInteractive: { type: Boolean, value: false }
  },

  data: {
    expanded: false,
    contentVisible: false
  },

  lifetimes: {
    detached() {
      this.componentAttached = false;
      this.clearTransitionTimers();
    },
    attached() {
      this.componentAttached = true;
    }
  },

  methods: {
    clearTransitionTimers() {
      clearTimeout(this.contentRevealTimer);
      clearTimeout(this.widthCollapseTimer);
      this.contentRevealTimer = null;
      this.widthCollapseTimer = null;
      this.collapsePending = false;
    },

    reveal() {
      this.clearTransitionTimers();
      this.setData({ expanded: true, contentVisible: false });
      this.contentRevealTimer = setTimeout(() => {
        this.contentRevealTimer = null;
        if (this.componentAttached && this.data.expanded) this.setData({ contentVisible: true });
      }, CONTENT_REVEAL_DELAY_MS);
    },

    collapse() {
      this.clearTransitionTimers();
      if (!this.data.expanded && !this.data.contentVisible) return;
      this.collapsePending = true;
      this.setData({ contentVisible: false });
      this.widthCollapseTimer = setTimeout(() => {
        this.widthCollapseTimer = null;
        this.collapsePending = false;
        if (this.componentAttached) this.setData({ expanded: false });
      }, COLLAPSE_WIDTH_DELAY_MS);
    },

    onToggle() {
      if (this.data.expanded && !this.collapsePending) this.collapse();
      else this.reveal();
    },

    onNameTap() {
      if (!this.properties.nameInteractive) return;
      this.triggerEvent('nametap');
    }
  }
});
