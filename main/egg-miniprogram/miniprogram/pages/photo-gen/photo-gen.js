// AI写真页：拍照/选图 → 上传 → 异步生成（轮询任务状态）→ 结果展示/保存/分享。
// 后端链路为 照片审核(PENDING) → 生成(RUNNING) → 结果审核(REVIEWING) → SUCCEEDED/FAILED，
// 期间任何中间态都展示"生成中"与审核提示。
const { uploadGenPhoto, createImageGenTask, getImageGenTask } = require('../../utils/image-gen-api');

const POLL_INTERVAL_MS = 2500;
// 生成 + 两轮审核的宽限时长（mediaCheckAsync 推送标称 30 分钟内，实际通常秒级）
const POLL_TIMEOUT_MS = 3 * 60 * 1000;
const MAX_FILE_SIZE = 5 * 1024 * 1024;

Page({
  data: {
    phase: 'pick', // pick 选图 | working 生成中 | result 结果 | failed 失败
    photoPreview: '',
    submitting: false,
    statusText: '',
    resultUrl: '',
    failReason: '',
    caption: '',
    saving: false
  },

  onUnload() {
    this._stopPolling();
  },

  onHide() {
    this._stopPolling();
  },

  onShow() {
    // 回到前台时若任务仍在进行，恢复轮询
    if (this.data.phase === 'working' && this._taskId) {
      this._startPolling();
    }
  },

  onChoosePhoto() {
    if (this.data.submitting) return;
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const file = res.tempFiles && res.tempFiles[0];
        if (!file) return;
        if (file.size > MAX_FILE_SIZE) {
          wx.showToast({ title: '图片不能超过 5MB', icon: 'none' });
          return;
        }
        this.setData({ photoPreview: file.tempFilePath });
      }
    });
  },

  async onStartGenerate() {
    const { photoPreview, submitting } = this.data;
    if (!photoPreview || submitting) return;
    this.setData({ submitting: true, statusText: '照片上传中…' });
    try {
      const photoUrl = await uploadGenPhoto(photoPreview);
      this.setData({ statusText: '照片审核中…' });
      const task = await createImageGenTask(photoUrl);
      this._taskId = task.taskId;
      this._pollStartedAt = Date.now();
      this.setData({ phase: 'working', statusText: 'AI 生成中，大约需要半分钟…' });
      this._startPolling();
    } catch (error) {
      wx.showToast({ title: (error && error.userMessage) || '生成失败，请重试', icon: 'none' });
      this.setData({ phase: 'pick' });
    } finally {
      this.setData({ submitting: false });
    }
  },

  onRetry() {
    this._stopPolling();
    this._taskId = '';
    this.setData({ phase: 'pick', statusText: '', resultUrl: '', failReason: '', caption: '' });
  },

  onSaveImage() {
    const { resultUrl, saving } = this.data;
    if (!resultUrl || saving) return;
    this.setData({ saving: true });
    wx.downloadFile({
      url: resultUrl,
      success: (res) => {
        if (res.statusCode !== 200 || !res.tempFilePath) {
          this._saveDone('保存失败，请重试');
          return;
        }
        wx.saveImageToPhotosAlbum({
          filePath: res.tempFilePath,
          success: () => this._saveDone('已保存到相册'),
          fail: (err) => {
            // 用户拒绝相册授权时引导去设置页开启
            const denied = err && err.errMsg && err.errMsg.includes('auth');
            this._saveDone(denied ? '请在设置中允许保存到相册' : '保存失败，请重试');
          }
        });
      },
      fail: () => this._saveDone('保存失败，请重试')
    });
  },

  _saveDone(message) {
    this.setData({ saving: false });
    wx.showToast({ title: message, icon: 'none' });
  },

  // 分享小程序卡片：标题用抽中的预置文案（与图内渲染文字同一条）
  onShareAppMessage() {
    return {
      title: this.data.caption || '我和蛋宝宝的 AI 写真',
      path: '/pages/home/home'
    };
  },

  _startPolling() {
    this._stopPolling();
    this._pollTimer = setInterval(() => this._pollOnce(), POLL_INTERVAL_MS);
  },

  _stopPolling() {
    if (this._pollTimer) {
      clearInterval(this._pollTimer);
      this._pollTimer = null;
    }
  },

  async _pollOnce() {
    if (!this._taskId) return;
    if (Date.now() - this._pollStartedAt > POLL_TIMEOUT_MS) {
      this._stopPolling();
      this.setData({ phase: 'failed', failReason: '生成超时，请稍后再试' });
      return;
    }
    try {
      const task = await getImageGenTask(this._taskId);
      if (task.status === 'SUCCEEDED') {
        this._stopPolling();
        this.setData({ phase: 'result', resultUrl: task.resultUrl || '', caption: task.caption || '' });
      } else if (task.status === 'FAILED') {
        this._stopPolling();
        this.setData({ phase: 'failed', failReason: task.failReason || '生成失败，请重试' });
      } else if (task.status === 'REVIEWING' && this.data.statusText !== '生成完成，审核中…') {
        this.setData({ statusText: '生成完成，审核中…' });
      }
    } catch (error) {
      // 单次轮询失败（网络抖动）不打断，等待下一次
    }
  }
});
