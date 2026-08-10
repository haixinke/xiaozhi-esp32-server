// 孵蛋房场景组件：整幅背景 + 窝垫 + 蛋 + 窗口光效 + 天气粒子 + 台灯 + 时钟。
// 纯展示组件：不请求任何业务 API，交互通过事件抛给页面。
const weatherCanvas = require('../../utils/window-weather-canvas');
const canvas2d = require('../../utils/canvas-2d');
const { EGG_DEPTH_OVERLAY, EGG_SPECULAR_OVERLAY } = require('../../config/pre-hatch-assets');

const CROSSFADE_MS = 600;
const CUDDLE_MS = 600;
const CLOCK_TICK_MS = 1000;

Component({
  properties: {
    environment: { type: Object, value: null },
    eggArtUrl: { type: String, value: '' },
    lampOn: { type: Boolean, value: false }
  },

  data: {
    depthOverlay: EGG_DEPTH_OVERLAY,
    specularOverlay: EGG_SPECULAR_OVERLAY,
    previousFullSceneImage: '',
    fullSceneImageFailed: false,
    sceneCrossfadeActive: false,
    clockMode: 'analog', // analog | digital，点击切换
    clockTimeText: '',
    clockDateText: '',
    clockHourStyle: '',
    clockMinuteStyle: '',
    clockSecondStyle: '',
    clockTopPx: 0,
    clockLeftPx: 0
  },

  observers: {
    'environment.sceneKey': function () {
      this.applySceneChange();
    }
  },

  lifetimes: {
    attached() {
      this.startClock();
      this.deriveClockPosition();
    },
    ready() {
      this.setupWeatherCanvas();
    },
    detached() {
      this.stopClock();
      this.stopWeatherCanvas();
      if (this.crossfadeTimer) {
        clearTimeout(this.crossfadeTimer);
        this.crossfadeTimer = null;
      }
      if (this.cuddleTimer) {
        clearTimeout(this.cuddleTimer);
        this.cuddleTimer = null;
      }
    }
  },

  methods: {
    // 场景切换：新图加载完成后交叉淡化，旧图垫底；加载失败保持旧场景
    applySceneChange() {
      const env = this.properties.environment || {};
      const nextImage = env.fullSceneImage || '';
      const previousImage = (this.data && this.data.previousFullSceneImage) || '';
      // 首次加载或当前没有场景图时不需要交叉淡化垫底
      if (!previousImage && !this.data.sceneCrossfadeActive) {
        this.setData({ previousFullSceneImage: nextImage, sceneCrossfadeActive: false });
        return;
      }
      // 把当前正在显示的图片作为旧图垫底，等待新图加载完成触发 onFullSceneImageLoad
      this.setData({ previousFullSceneImage: previousImage || nextImage, sceneCrossfadeActive: false });
    },

    onFullSceneImageLoad() {
      // 背景图加载成功后启动交叉淡化，600ms 后清除旧图
      this.setData({ sceneCrossfadeActive: true });
      if (this.crossfadeTimer) clearTimeout(this.crossfadeTimer);
      this.crossfadeTimer = setTimeout(() => {
        this.setData({ previousFullSceneImage: '', sceneCrossfadeActive: false });
      }, CROSSFADE_MS);
    },

    onFullSceneImageError() {
      this.setData({ fullSceneImageFailed: true });
    },

    onRetryFullSceneImage() {
      this.setData({ fullSceneImageFailed: false });
      this.triggerEvent('retryscene');
    },

    onIncubationAssetError(event) {
      // 窝垫等辅助素材加载失败：仅记录，不阻断主场景
      const dataset = (event && event.currentTarget && event.currentTarget.dataset) || {};
      const asset = dataset.asset || 'scene-asset';
      // 不抛错误事件，避免小素材失败导致整屏重试
    },

    onEggTap() {
      this.triggerEvent('eggtap');
    },

    onEggCuddle() {
      this.triggerEvent('eggcuddle');
      // 长按期间给蛋一个温暖态，600ms 后移除
      this.setData({ eggCuddling: true });
      if (this.cuddleTimer) clearTimeout(this.cuddleTimer);
      this.cuddleTimer = setTimeout(() => {
        this.setData({ eggCuddling: false });
      }, CUDDLE_MS);
    },

    onLampTap() {
      // 台灯开关由页面持有 lampOn，通过 lamptap 事件请求切换
      this.triggerEvent('lamptap', { lampOn: !this.properties.lampOn });
    },

    onClockTap() {
      const nextMode = this.data.clockMode === 'analog' ? 'digital' : 'analog';
      this.setData({ clockMode: nextMode });
      this.tickClock();
    },

    onWindowTap(event) {
      // 量取窗户区域矩形，随事件抛给页面用于每日窗景定位
      const query = this.createSelectorQuery ? this.createSelectorQuery() : wx.createSelectorQuery().in(this);
      query.select('.window-effects').boundingClientRect(rect => {
        const boundingRect = rect || { left: 0, top: 0, width: 0, height: 0 };
        this.triggerEvent('windowtap', boundingRect);
      }).exec();
    },

    setupWeatherCanvas() {
      const env = this.properties.environment || {};
      // 使用 canvas-2d 工具初始化窗口天气画布
      canvas2d.createLayer(this, '#windowWeatherCanvas').then(layer => {
        if (!layer) {
          this.weatherLayer = null;
          return;
        }
        this.weatherLayer = layer;
        this.weatherParticles = weatherCanvas.createParticles(layer.width, layer.height);
        this.startWeatherAnimation();
      });
    },

    startWeatherAnimation() {
      this.stopWeatherAnimation();
      const layer = this.weatherLayer;
      const particles = this.weatherParticles;
      if (!layer || !particles) return;
      const env = this.properties.environment || {};
      const environment = {
        weather: env.weather || 'sunny',
        season: env.season || 'spring',
        period: env.period || 'day'
      };
      const token = (this.animationToken || 0) + 1;
      this.animationToken = token;
      const render = () => {
        if (token !== this.animationToken) return;
        const now = Date.now();
        weatherCanvas.drawFrame(layer.context, { width: layer.width, height: layer.height }, particles, environment, {
          timestamp: now,
          reducedMotion: false,
          fogVisible: false,
          clipGlass: false
        });
        if (!weatherCanvas.needsAnimation(environment, false)) return;
        if (layer.canvas && layer.canvas.requestAnimationFrame) {
          this.frameId = layer.canvas.requestAnimationFrame(render);
        } else {
          this.frameTimer = setTimeout(render, 33);
        }
      };
      render();
    },

    stopWeatherCanvas() {
      this.stopWeatherAnimation();
      this.weatherLayer = null;
      this.weatherParticles = null;
    },

    stopWeatherAnimation() {
      this.animationToken = (this.animationToken || 0) + 1;
      if (this.weatherLayer && this.weatherLayer.canvas && this.weatherLayer.canvas.cancelAnimationFrame && this.frameId != null) {
        this.weatherLayer.canvas.cancelAnimationFrame(this.frameId);
      }
      clearTimeout(this.frameTimer);
      this.frameId = null;
      this.frameTimer = null;
    },

    startClock() {
      this.tickClock();
      this.clockTimer = setInterval(() => this.tickClock(), CLOCK_TICK_MS);
    },

    stopClock() {
      if (this.clockTimer) {
        clearInterval(this.clockTimer);
        this.clockTimer = null;
      }
    },

    tickClock() {
      const now = new Date();
      const hours = now.getHours();
      const minutes = now.getMinutes();
      const seconds = now.getSeconds();
      const pad = n => (n < 10 ? '0' + n : String(n));
      this.setData({
        clockTimeText: `${pad(hours)}:${pad(minutes)}`,
        clockDateText: `${now.getMonth() + 1}月${now.getDate()}日`
      });
      // 指针角度：时针需叠加分钟偏移
      const hourDeg = (hours % 12) * 30 + minutes * 0.5;
      const minuteDeg = minutes * 6 + seconds * 0.1;
      const secondDeg = seconds * 6;
      this.setData({
        clockHourStyle: `transform: rotate(${hourDeg}deg);`,
        clockMinuteStyle: `transform: rotate(${minuteDeg}deg);`,
        clockSecondStyle: `transform: rotate(${secondDeg}deg);`
      });
    },

    deriveClockPosition() {
      // 时钟默认摆放在右上角，保留与源页面一致的偏移
      const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
      const screenWidth = info.windowWidth || info.screenWidth || 375;
      const pxRatio = info.pixelRatio || 1;
      // 176rpx 宽度按 750rpx 基准换算
      const clockWidthPx = (176 / 750) * screenWidth;
      const rightGapPx = (24 / 750) * screenWidth;
      const topPx = (48 / 750) * screenWidth + (info.statusBarHeight || 0);
      const leftPx = screenWidth - clockWidthPx - rightGapPx;
      this.setData({ clockTopPx: Math.round(topPx), clockLeftPx: Math.round(leftPx) });
    }
  }
});
