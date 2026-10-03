// AI写真集：按天倒序的时间轴（含年份里程碑）+ 卡册翻页 + 全屏查看器（长按保存）。
// 分页单位是天（一页 10 天），只含成功写真；无写真时展示空状态引导制作。
const { getImageGenGallery, GALLERY_DIRTY_KEY } = require('../../utils/image-gen-api');
const { buildTimelineItems, cycleDeck } = require('../../utils/photo-gallery-helper');
const { saveRemoteImage } = require('../../utils/save-image');

const PAGE_LIMIT = 10;
// 顶卡滑出阈值（px）与判定为点击的最大位移（px）
const SWIPE_THRESHOLD = 80;
const TAP_TOLERANCE = 10;
// 顶卡过阈值后的飞出位移（px），足以离开屏幕
const FLY_OUT_DISTANCE = 500;
// 与 WXSS 过渡时长一致：滑出/回弹动画结束后再重排卡册
const DECK_ANIM_MS = 300;

Page({
  data: {
    loaded: false, // 首屏加载完成（区分加载中与空状态）
    timelineItems: [], // 含年份里程碑的渲染项
    decks: {}, // date -> { order: [...photos], dragX, animating }
    hasMore: false,
    loadingMore: false,
    viewer: null, // { photos, current } 非空即显示全屏查看器
    saving: false
  },

  onLoad() {
    this._todayStr = formatToday();
    this._days = [];
    this._loadPage(1);
  },

  onShow() {
    // 制作页生成成功后回到本页：刷新第一页让新写真进时间轴
    if (wx.getStorageSync(GALLERY_DIRTY_KEY)) {
      wx.removeStorageSync(GALLERY_DIRTY_KEY);
      this._days = [];
      this.setData({ decks: {}, hasMore: false });
      this._loadPage(1);
    }
  },

  onReachBottom() {
    const { hasMore, loadingMore, loaded } = this.data;
    if (!loaded || !hasMore || loadingMore) return;
    this._loadPage(Math.ceil(this._days.length / PAGE_LIMIT) + 1);
  },

  async _loadPage(page) {
    this.setData({ loadingMore: true });
    try {
      const res = await getImageGenGallery(page, PAGE_LIMIT);
      const newDays = (res.list || []).map((day) => ({
        date: day.date,
        photos: day.photos || []
      }));
      this._days = page === 1 ? newDays : this._days.concat(newDays);
      // 只给新来的日期初始化卡册，已有卡册保持当前翻页顺序
      const decks = { ...this.data.decks };
      newDays.forEach((day) => {
        if (!decks[day.date]) {
          decks[day.date] = { order: day.photos.slice(), dragX: 0, animating: false };
        }
      });
      this.setData({
        loaded: true,
        loadingMore: false,
        decks,
        timelineItems: buildTimelineItems(this._days, this._todayStr),
        hasMore: this._days.length < (res.total || 0)
      });
    } catch (error) {
      this.setData({ loaded: true, loadingMore: false });
      wx.showToast({ title: '写真集加载失败，请稍后重试', icon: 'none' });
    }
  },

  // ---- 卡册手势：左右拨动顶卡，超阈值滑出并循环至册底 ----

  onDeckTouchStart(e) {
    const { idx, count, date } = e.currentTarget.dataset;
    // 只响应多张卡册的顶卡；单卡与底层卡的手势直接忽略
    if (Number(idx) !== 0 || Number(count) <= 1) {
      this._drag = null;
      return;
    }
    const touch = e.touches[0];
    this._drag = { date, startX: touch.clientX, moved: false };
  },

  onDeckTouchMove(e) {
    const drag = this._drag;
    if (!drag) return;
    const dragX = e.touches[0].clientX - drag.startX;
    if (Math.abs(dragX) > TAP_TOLERANCE) drag.moved = true;
    this.setData({ [`decks.${drag.date}.dragX`]: dragX });
  },

  onDeckTouchEnd() {
    const drag = this._drag;
    if (!drag) return;
    this._drag = null;
    // tap 在 touchend 之后触发，记下本次位移供 onCardTap 抑制误触
    this._suppressTap = drag.moved;
    const deck = this.data.decks[drag.date];
    if (!deck || deck.order.length <= 1) {
      this.setData({ [`decks.${drag.date}.dragX`]: 0 });
      return;
    }
    if (Math.abs(deck.dragX) >= SWIPE_THRESHOLD) {
      // 滑出：继续飞出屏幕，动画结束后顶卡循环到册底
      const flyX = deck.dragX < 0 ? -FLY_OUT_DISTANCE : FLY_OUT_DISTANCE;
      this.setData({ [`decks.${drag.date}.dragX`]: flyX, [`decks.${drag.date}.animating`]: true });
      setTimeout(() => {
        this.setData({
          [`decks.${drag.date}.order`]: cycleDeck(deck.order),
          [`decks.${drag.date}.dragX`]: 0,
          [`decks.${drag.date}.animating`]: false
        });
      }, DECK_ANIM_MS);
    } else {
      // 未过阈值：回弹
      this.setData({ [`decks.${drag.date}.dragX`]: 0, [`decks.${drag.date}.animating`]: true });
    }
  },

  // ---- 全屏查看器 ----

  onCardTap(e) {
    // 拖拽（翻卡）结束会连带触发 tap，位移超限时抑制
    if (this._suppressTap) {
      this._suppressTap = false;
      return;
    }
    const { date, taskId } = e.currentTarget.dataset;
    const day = this._days.find((d) => d.date === date);
    if (!day) return;
    const current = Math.max(0, day.photos.findIndex((p) => p.taskId === taskId));
    this.setData({ viewer: { photos: day.photos, current } });
  },

  onViewerChange(e) {
    this.setData({ 'viewer.current': e.detail.current });
  },

  onCloseViewer() {
    this.setData({ viewer: null });
  },

  // 长按唤起底部保存（查看器无常驻按钮）
  onViewerLongPress() {
    if (this.data.saving) return;
    wx.showActionSheet({
      itemList: ['保存到相册'],
      success: (res) => {
        if (res.tapIndex === 0) this._saveCurrentPhoto();
      }
    });
  },

  async _saveCurrentPhoto() {
    const { viewer, saving } = this.data;
    if (!viewer || saving) return;
    const photo = viewer.photos[viewer.current];
    if (!photo || !photo.resultUrl) return;
    this.setData({ saving: true });
    const message = await saveRemoteImage(photo.resultUrl);
    this.setData({ saving: false });
    wx.showToast({ title: message, icon: 'none' });
  },

  // ---- 悬浮按钮 / 空状态：跳制作页 ----

  onMakePhoto() {
    wx.navigateTo({ url: '/pages/photo-gen/photo-gen' });
  },

  noop() {}
});

/** 今天（设备本地时区）的 yyyy-MM-dd */
function formatToday() {
  const now = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}
