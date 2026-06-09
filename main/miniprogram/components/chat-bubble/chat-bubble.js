/**
 * components/chat-bubble/chat-bubble.js
 *
 * 聊天气泡：区分用户/AI 消息，支持流式追加文本（typing 模式下显示光效），
 * 助手消息支持音频播放（audioId 不为空时显示播放按钮）。
 */
const { post, getBaseUrl } = require('../../utils/request');

Component({
  properties: {
    /** 'user' | 'assistant' */
    role: {
      type: String,
      value: 'assistant',
    },
    /** 文本内容 */
    content: {
      type: String,
      value: '',
    },
    /** 是否流式输出中（显示光效） */
    typing: {
      type: Boolean,
      value: false,
    },
    /** 音频ID，有值时显示播放按钮（仅助手消息） */
    audioId: {
      type: String,
      value: '',
    },
    /** 深色模式 */
    darkMode: {
      type: Boolean,
      value: false,
    },
  },

  data: {
    audioPlaying: false,
    audioLoading: false,
  },

  methods: {
    _onPlayTap() {
      if (this.data.audioPlaying) {
        this._stopAudio();
        return;
      }
      this._playAudio();
    },

    _playAudio() {
      const audioId = this.data.audioId;
      if (!audioId || this.data.audioLoading) return;
      this._stopAudio();
      this.setData({ audioLoading: true });

      const tempPath = `${wx.env.USER_DATA_PATH}/audio_${audioId}.wav`;
      const fs = wx.getFileSystemManager();

      // 本地已缓存则直接播放
      try {
        fs.accessSync(tempPath);
        this._playLocalFile(tempPath);
        return;
      } catch (_) {
        // 文件不存在，继续下载
      }

      // Step 1: 用 wx.request 换取一次性播放 UUID
      post('/agent/audio/' + audioId).then((res) => {
        const uuid = res.data;
        if (!uuid) {
          this.setData({ audioLoading: false });
          return;
        }

        // Step 2: 用 wx.request 下载音频二进制（走已验证的网络通道）
        return new Promise((resolve, reject) => {
          wx.request({
            url: getBaseUrl() + '/agent/play/' + uuid,
            method: 'GET',
            responseType: 'arraybuffer',
            success: (r) => {
              if (r.statusCode === 200 && r.data) {
                resolve(r.data);
              } else {
                reject(new Error('下载音频失败: ' + r.statusCode));
              }
            },
            fail: (err) => reject(err),
          });
        });
      }).then((arrayBuffer) => {
        if (!arrayBuffer) return;

        // Step 3: 写入本地缓存文件
        fs.writeFileSync(tempPath, arrayBuffer, 'binary');

        // Step 4: 播放
        this._playLocalFile(tempPath);
      }).catch(() => {
        this.setData({ audioLoading: false });
        wx.showToast({ title: '获取音频失败', icon: 'none' });
      });
    },

    _playLocalFile(filePath) {
      const innerAudio = wx.createInnerAudioContext();
      innerAudio.src = filePath;
      innerAudio.onPlay(() => {
        this.setData({ audioPlaying: true, audioLoading: false });
      });
      innerAudio.onStop(() => {
        this.setData({ audioPlaying: false });
      });
      innerAudio.onEnded(() => {
        this.setData({ audioPlaying: false });
      });
      innerAudio.onError(() => {
        this.setData({ audioPlaying: false, audioLoading: false });
        wx.showToast({ title: '播放失败', icon: 'none' });
      });
      innerAudio.play();
      this._innerAudio = innerAudio;
    },

    _stopAudio() {
      if (this._innerAudio) {
        this._innerAudio.stop();
        this._innerAudio.destroy();
        this._innerAudio = null;
      }
      this.setData({ audioPlaying: false, audioLoading: false });
    },
  },

  detached() {
    if (this._innerAudio) {
      this._innerAudio.stop();
      this._innerAudio.destroy();
      this._innerAudio = null;
    }
  },
});
