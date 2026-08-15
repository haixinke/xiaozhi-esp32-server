// 房间时钟组件：指针/数字双态，点击切换
// 定时器在组件内自管理，页面隐藏时停走，避免后台空转
const deviceClock = require('../../utils/device-clock');

Component({
  data: {
    clockMode: 'analog',
    clockTimeText: '--:--',
    clockDateText: '',
    clockHourStyle: 'transform:rotate(0deg);',
    clockMinuteStyle: 'transform:rotate(0deg);',
    clockSecondStyle: 'transform:rotate(0deg);'
  },

  lifetimes: {
    attached() {
      this.startClock();
    },
    detached() {
      this.stopClock();
    }
  },

  pageLifetimes: {
    show() {
      this.startClock();
    },
    hide() {
      this.stopClock();
    }
  },

  methods: {
    syncClock() {
      const clock = deviceClock.snapshot(new Date());
      this.setData({
        clockTimeText: clock.timeText,
        clockDateText: clock.dateText,
        clockHourStyle: `transform:rotate(${clock.hourAngle}deg);`,
        clockMinuteStyle: `transform:rotate(${clock.minuteAngle}deg);`,
        clockSecondStyle: `transform:rotate(${clock.secondAngle}deg);`
      });
    },

    startClock() {
      this.stopClock();
      this.syncClock();
      // 先对齐到下一秒边界，再进入 1s 周期，防止秒针抖动
      const delay = deviceClock.millisecondsUntilNextSecond(Date.now());
      this.clockBoundaryTimer = setTimeout(() => {
        this.clockBoundaryTimer = null;
        this.syncClock();
        this.clockTimer = setInterval(() => this.syncClock(), 1000);
      }, delay);
    },

    stopClock() {
      clearTimeout(this.clockBoundaryTimer);
      clearInterval(this.clockTimer);
      this.clockBoundaryTimer = null;
      this.clockTimer = null;
    },

    onClockTap() {
      this.setData({ clockMode: this.data.clockMode === 'analog' ? 'digital' : 'analog' });
      this.syncClock();
    }
  }
});
