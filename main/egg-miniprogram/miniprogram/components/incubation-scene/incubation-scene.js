// 孵蛋房场景组件：整幅背景 + 窝垫 + 蛋 + 窗口光效 + 天气粒子 + 台灯 + 时钟。
// 纯展示组件：不请求任何业务 API，交互通过事件抛给页面。
const weatherCanvas = require('../../utils/window-weather-canvas');
const canvas2d = require('../../utils/canvas-2d');
const remoteImage = require('../../utils/remote-image');
const { EGG_DEPTH_OVERLAY, EGG_SPECULAR_OVERLAY } = require('../../config/pre-hatch-assets');

const CROSSFADE_MS = 600;
const CUDDLE_MS = 600;
// 轻触蛋的晃动时长，对齐静态 UI 项目 wobble 动效节奏
const WOBBLE_MS = 760;
const CLOCK_TICK_MS = 1000;
// 背景图加载看门狗超时：须大于 remote-image 的下载超时（15s），否则慢网下误落错误态
const SCENE_LOAD_TIMEOUT_MS = 20000;

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
    // 远程 OSS 图经 downloadFile 落地后的本地临时路径；<image> 只绑定本地路径
    localFullSceneImage: '',
    localNestImage: '',
    localEggImage: '',
    localEggArt: '',
    clockMode: 'analog', // analog | digital，点击切换
    clockTimeText: '',
    clockDateText: '',
    clockHourStyle: '',
    clockMinuteStyle: '',
    clockSecondStyle: '',
    clockTopPx: 0,
    clockLeftPx: 0,
    // 轻触蛋时的左右晃动态，760ms 后自动复位
    eggWobbling: false
  },

  observers: {
    'environment.sceneKey': function () {
      this.applySceneChange();
    },
    'eggArtUrl': function (artUrl) {
      // 涂鸦蛋图同为远程 OSS 图，走同一条 downloadFile 通道
      this.loadRemoteImage(artUrl || '', 'localEggArt');
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
      this.clearSceneLoadWatchdog();
      if (this.crossfadeTimer) {
        clearTimeout(this.crossfadeTimer);
        this.crossfadeTimer = null;
      }
      if (this.cuddleTimer) {
        clearTimeout(this.cuddleTimer);
        this.cuddleTimer = null;
      }
      if (this.wobbleTimer) {
        clearTimeout(this.wobbleTimer);
        this.wobbleTimer = null;
      }
    }
  },

  methods: {
    // 场景切换：新图经 downloadFile 落地后交叉淡化，旧图垫底；加载失败保持旧场景
    applySceneChange() {
      const env = this.properties.environment || {};
      const nextImage = env.fullSceneImage || '';
      const displayed = this.data.localFullSceneImage || '';
      // 把当前正在显示的本地图作为旧图垫底，等待新图落地后触发 onFullSceneImageLoad
      this.setData({
        previousFullSceneImage: displayed || this.data.previousFullSceneImage || '',
        sceneCrossfadeActive: false
      });
      this.resolveSceneImages(env);
      // 每次场景切换都重新武装看门狗，防止新图挂起导致整屏空白
      if (nextImage) {
        this.startSceneLoadWatchdog();
      } else {
        this.clearSceneLoadWatchdog();
      }
    },

    // 统一解析场景内三张远程图：整幅背景/窝垫/蛋
    resolveSceneImages(env) {
      const source = env || {};
      this.loadRemoteImage(source.fullSceneImage || '', 'localFullSceneImage');
      this.loadRemoteImage(source.nestImage || '', 'localNestImage');
      this.loadRemoteImage(source.eggImage || '', 'localEggImage');
    },

    // 远程图经共享 downloadFile 通道落本地再喂 <image>；组件只保留槽位乱序防护
    loadRemoteImage(url, dataKey) {
      this._pendingRemote = this._pendingRemote || {};
      this._pendingRemote[dataKey] = url;
      if (!url) {
        this.setData({ [dataKey]: '' });
        return;
      }
      remoteImage.loadRemoteImage(url, (localPath) => {
        // 乱序防护：回调到达时该槽位已指向更新的 URL 则丢弃
        if (this._pendingRemote[dataKey] !== url) return;
        if (localPath) {
          this.setData({ [dataKey]: localPath });
          return;
        }
        this.onRemoteImageFail(dataKey);
      });
    },

    onRemoteImageFail(dataKey) {
      // 只有整幅背景失败才进错误态；窝垫/蛋等辅助素材保持既有静默策略
      if (dataKey === 'localFullSceneImage') {
        this.onFullSceneImageError();
      }
    },

    // 背景图加载看门狗：覆盖 image 既不触发 load 也不触发 error 的挂起场景
    startSceneLoadWatchdog() {
      this.clearSceneLoadWatchdog();
      // 错误态下背景图已卸载，无需看门狗
      if (this.data.fullSceneImageFailed) return;
      const env = this.properties.environment || {};
      if (!env.fullSceneImage) return;
      this.sceneLoadTimer = setTimeout(() => {
        this.sceneLoadTimer = null;
        this.onSceneLoadTimeout();
      }, SCENE_LOAD_TIMEOUT_MS);
    },

    clearSceneLoadWatchdog() {
      if (this.sceneLoadTimer) {
        clearTimeout(this.sceneLoadTimer);
        this.sceneLoadTimer = null;
      }
    },

    onSceneLoadTimeout() {
      if (this.data.fullSceneImageFailed) return;
      // 已有成功加载过的旧图垫底：保留旧场景静默等待，不打扰用户
      if (this._fullSceneLoadedOnce && this.data.previousFullSceneImage) return;
      // [DEBUG-night-blank] 临时日志：抓挂起的图片 URL，定位夜间空白根因后删除
      console.warn('[DEBUG-night-blank] full scene image pending timeout:', (this.properties.environment || {}).fullSceneImage);
      // 至今没有任何背景图加载成功：落入错误页，给用户重试入口
      this.setData({ fullSceneImageFailed: true });
    },

    onFullSceneImageLoad() {
      // 记录本次会话至少成功加载过一张背景图，供看门狗判断是否有旧图可垫底
      this._fullSceneLoadedOnce = true;
      this.clearSceneLoadWatchdog();
      // 背景图加载成功后启动交叉淡化，600ms 后清除旧图
      this.setData({ sceneCrossfadeActive: true });
      if (this.crossfadeTimer) clearTimeout(this.crossfadeTimer);
      this.crossfadeTimer = setTimeout(() => {
        this.setData({ previousFullSceneImage: '', sceneCrossfadeActive: false });
      }, CROSSFADE_MS);
    },

    onFullSceneImageError() {
      this.clearSceneLoadWatchdog();
      // [DEBUG-night-blank] 临时日志：抓加载失败的图片 URL，定位夜间空白根因后删除
      console.warn('[DEBUG-night-blank] full scene image load error:', (this.properties.environment || {}).fullSceneImage);
      this.setData({ fullSceneImageFailed: true });
    },

    onRetryFullSceneImage() {
      this.setData({ fullSceneImageFailed: false });
      // 重试重新拉取远程图（downloadFile 通道）并重新武装看门狗
      this.resolveSceneImages(this.properties.environment);
      this.startSceneLoadWatchdog();
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
      // 轻触给蛋一个左右晃动，760ms 后复位（对齐静态 UI 项目点蛋动效）
      this.setData({ eggWobbling: true });
      if (this.wobbleTimer) clearTimeout(this.wobbleTimer);
      this.wobbleTimer = setTimeout(() => {
        this.setData({ eggWobbling: false });
      }, WOBBLE_MS);
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
      // 时钟位于左上角：顶部对齐微信胶囊下沿再下探一行名字区高度，左侧留安全边距
      try {
        const windowInfo = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
        const menuRect = wx.getMenuButtonBoundingClientRect ? wx.getMenuButtonBoundingClientRect() : null;
        const statusBarHeight = Number(windowInfo.statusBarHeight || 20);
        const safeLeft = windowInfo.safeArea ? Number(windowInfo.safeArea.left || 0) : 0;
        const nameTopPx = menuRect && Number(menuRect.bottom)
          ? Number(menuRect.bottom) + 8
          : statusBarHeight + 42;
        this.setData({
          clockTopPx: Math.round(nameTopPx + 44),
          clockLeftPx: Math.round(safeLeft + 18)
        });
      } catch (error) {
        this.setData({ clockTopPx: 132, clockLeftPx: 18 });
      }
    }
  }
});
