const weatherCanvas = require('../../utils/window-weather-canvas');

const FEEDBACK_MESSAGES = [
  '我陪你一起看窗外',
  '我在这里，和你看同一片天空',
  '我想和你再安静地待一会儿',
  '我也在看，云走得好慢呀',
  '我听见风了，你听见了吗',
  '我把这一刻轻轻记住啦',
  '我喜欢这样陪你看窗外',
  '我想把窗外的光分给你一点',
  '我和你一起等天空慢慢变色',
  '我会陪着你，看风景慢慢经过',
  '我在想，今天的天空也很温柔',
  '我想和你一起听窗外安静下来'
];

function reducedMotionEnabled() {
  try {
    const system = wx.getSystemInfoSync ? wx.getSystemInfoSync() : {};
    return Boolean(system.reducedMotion || system.enableReduceMotion);
  } catch (error) {
    return false;
  }
}

Component({
  properties: {
    visible: { type: Boolean, value: false },
    image: { type: String, value: '' },
    weather: { type: String, value: 'sunny' },
    season: { type: String, value: 'spring' },
    period: { type: String, value: 'day' },
    lightPhase: { type: String, value: 'midday' },
    weatherLabel: { type: String, value: '晴朗' },
    periodLabel: { type: String, value: '日间' },
    originStyle: { type: String, value: '' }
  },

  data: {
    rendered: false,
    phase: 'idle',
    displayImage: '',
    loading: false,
    empty: false,
    failed: false,
    reducedMotion: false,
    transitionDuration: 320,
    feedbackTransitionDuration: 220,
    feedbackText: FEEDBACK_MESSAGES[0],
    feedbackPhase: 'in',
    topbarTopPx: 96
  },

  observers: {
    visible(value) {
      if (!this.componentReady) return;
      if (value) this.open();
      else this.closeImmediately();
    },
    'image,weather,season,period,lightPhase'(image) {
      if (!this.componentReady || !this.properties.visible) return;
      this.prepareContent(image);
    }
  },

  lifetimes: {
    ready() {
      this.componentReady = true;
      this.configureTopbar();
      if (this.properties.visible) this.open();
    },
    detached() {
      this.componentReady = false;
      this.cleanup();
    }
  },

  methods: {
    noop() {},

    configureTopbar() {
      try {
        const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
        const menu = wx.getMenuButtonBoundingClientRect ? wx.getMenuButtonBoundingClientRect() : null;
        const statusBarHeight = Number(info.statusBarHeight || 20);
        const menuBottom = Number(menu && menu.bottom || 0);
        this.setData({ topbarTopPx: Math.round(menuBottom > statusBarHeight ? menuBottom + 14 : statusBarHeight + 64) });
      } catch (error) {
        this.setData({ topbarTopPx: 96 });
      }
    },

    open() {
      if (this.data.rendered && this.data.phase !== 'idle') return;
      const reducedMotion = reducedMotionEnabled();
      const transitionDuration = reducedMotion ? 20 : 320;
      const feedbackTransitionDuration = reducedMotion ? 20 : 220;
      const feedbackText = this.nextFeedbackMessage();
      const token = this.openToken = (this.openToken || 0) + 1;
      this.configureTopbar();
      this.setData({
        rendered: true,
        phase: 'entering',
        reducedMotion,
        transitionDuration,
        feedbackTransitionDuration,
        feedbackText,
        feedbackPhase: 'in'
      }, () => {
        if (token !== this.openToken) return;
        this.prepareContent(this.properties.image);
        clearTimeout(this.transitionTimer);
        this.transitionTimer = setTimeout(() => {
          if (token === this.openToken && this.properties.visible) this.setData({ phase: 'open' });
        }, transitionDuration + 40);
      });
    },

    prepareContent(image) {
      const source = String(image || '');
      this.stopAnimation();
      this.setData({
        displayImage: source,
        loading: Boolean(source),
        empty: false,
        failed: false
      }, () => {
        if (wx.nextTick) wx.nextTick(() => this.setupCanvas());
        else setTimeout(() => this.setupCanvas(), 0);
      });
    },

    onImageLoad() {
      if (!this.properties.visible) return;
      this.setData({ loading: false, empty: false, failed: false });
    },

    onImageError() {
      if (!this.properties.visible) return;
      this.setData({ loading: false, empty: false, failed: true });
      this.stopAnimation();
    },

    onRetry() {
      if (!this.properties.visible || this.data.loading) return;
      const source = String(this.properties.image || '');
      if (!source) {
        this.triggerEvent('retry');
        return;
      }
      this.setData({ displayImage: '', loading: true, empty: false, failed: false }, () => {
        if (!this.properties.visible) return;
        this.setData({ displayImage: source }, () => this.setupCanvas());
      });
    },

    setupCanvas() {
      this.stopAnimation();
      if (!this.properties.visible || !this.data.rendered || this.data.failed) return;
      const setupToken = this.canvasSetupToken = (this.canvasSetupToken || 0) + 1;
      const query = this.createSelectorQuery ? this.createSelectorQuery() : wx.createSelectorQuery().in(this);
      query.select('#dailyWindowWeatherCanvas').fields({ node: true, size: true }).exec(result => {
        if (setupToken !== this.canvasSetupToken || !this.properties.visible) return;
        const target = result && result[0];
        if (!target || !target.node || !target.width || !target.height) {
          this.canvas = null;
          this.context = null;
          return;
        }
        const canvas = target.node;
        const context = canvas.getContext('2d');
        const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
        const pixelRatio = Number(info.pixelRatio || 1);
        canvas.width = Math.round(target.width * pixelRatio);
        canvas.height = Math.round(target.height * pixelRatio);
        context.scale(pixelRatio, pixelRatio);
        this.canvas = canvas;
        this.context = context;
        this.canvasSize = { width: target.width, height: target.height };
        this.particles = weatherCanvas.createParticles(target.width, target.height);
        this.startAnimation();
      });
    },

    environment() {
      return {
        weather: this.properties.weather || 'sunny',
        season: this.properties.season || 'spring',
        period: this.properties.period || 'day'
      };
    },

    drawFrame(timestamp, reducedMotion) {
      weatherCanvas.drawFrame(this.context, this.canvasSize, this.particles, this.environment(), {
        timestamp,
        reducedMotion,
        fogVisible: false,
        clipGlass: false
      });
    },

    startAnimation() {
      this.stopAnimation();
      if (!this.canvas || !this.context || !this.properties.visible) return;
      const token = this.animationToken = (this.animationToken || 0) + 1;
      if (this.data.reducedMotion) {
        this.drawFrame(4200, true);
        return;
      }
      const render = () => {
        if (token !== this.animationToken || !this.properties.visible || !this.componentReady) return;
        const now = Date.now();
        if (!this.lastDrawAt || now - this.lastDrawAt >= 33) {
          this.drawFrame(now, false);
          this.lastDrawAt = now;
        }
        if (!weatherCanvas.needsAnimation(this.environment(), false)) return;
        if (this.canvas.requestAnimationFrame) this.frameId = this.canvas.requestAnimationFrame(render);
        else this.frameTimer = setTimeout(render, 33);
      };
      render();
    },

    stopAnimation() {
      this.animationToken = (this.animationToken || 0) + 1;
      if (this.canvas && this.canvas.cancelAnimationFrame && this.frameId != null) {
        this.canvas.cancelAnimationFrame(this.frameId);
      }
      clearTimeout(this.frameTimer);
      this.frameId = null;
      this.frameTimer = null;
      this.lastDrawAt = 0;
    },

    nextFeedbackMessage() {
      const currentIndex = Number.isInteger(this.feedbackIndex) ? this.feedbackIndex : -1;
      let nextIndex;
      if (currentIndex < 0 || FEEDBACK_MESSAGES.length < 2) {
        nextIndex = Math.floor(Math.random() * FEEDBACK_MESSAGES.length);
      } else {
        const offset = 1 + Math.floor(Math.random() * (FEEDBACK_MESSAGES.length - 1));
        nextIndex = (currentIndex + offset) % FEEDBACK_MESSAGES.length;
      }
      this.feedbackIndex = nextIndex;
      return FEEDBACK_MESSAGES[nextIndex];
    },

    onFeedbackTap() {
      if (!this.properties.visible || !this.data.rendered || this.feedbackChanging) return;
      this.feedbackChanging = true;
      const token = this.feedbackTransitionToken = (this.feedbackTransitionToken || 0) + 1;
      const duration = this.data.feedbackTransitionDuration;
      this.setData({ feedbackPhase: 'out' });
      clearTimeout(this.feedbackTimer);
      this.feedbackTimer = setTimeout(() => {
        if (token !== this.feedbackTransitionToken || !this.properties.visible || !this.data.rendered) return;
        this.setData({
          feedbackText: this.nextFeedbackMessage(),
          feedbackPhase: 'in'
        });
        this.feedbackTimer = setTimeout(() => {
          if (token === this.feedbackTransitionToken) this.feedbackChanging = false;
        }, duration);
      }, duration);
    },

    requestClose() {
      if (!this.data.rendered || this.data.phase === 'exiting') return;
      const token = this.openToken = (this.openToken || 0) + 1;
      clearTimeout(this.transitionTimer);
      this.setData({ phase: 'exiting' });
      this.transitionTimer = setTimeout(() => {
        if (token !== this.openToken) return;
        this.cleanup();
        this.setData({ rendered: false, phase: 'idle' });
        this.triggerEvent('close');
      }, this.data.transitionDuration + 30);
    },

    closeImmediately() {
      if (!this.data.rendered && this.data.phase === 'idle') return;
      this.openToken = (this.openToken || 0) + 1;
      this.cleanup();
      this.setData({ rendered: false, phase: 'idle' });
    },

    cleanup() {
      clearTimeout(this.transitionTimer);
      this.transitionTimer = null;
      clearTimeout(this.feedbackTimer);
      this.feedbackTimer = null;
      this.feedbackChanging = false;
      this.feedbackTransitionToken = (this.feedbackTransitionToken || 0) + 1;
      this.stopAnimation();
      this.canvasSetupToken = (this.canvasSetupToken || 0) + 1;
      this.canvas = null;
      this.context = null;
      this.canvasSize = null;
      this.particles = null;
    }
  }
});
