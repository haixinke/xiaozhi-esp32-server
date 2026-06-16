/**
 * voice-catalog.js
 *
 * 女友音色目录。换声音页与新人引导共用，便于后续上新。
 * id 与后端 companion.voice / agent.tts_voice_id 对齐。
 */

var CDN = 'https://636c-cloud1-9ghrcw8127746c64-1391989435.tcb.qcloud.la';

var DEFAULT_VOICES = [
  { id: 'TTS_HSDSTTS_V2_0001', label: '温糯', audioUrl: CDN + '/girlfriend/voice/female_xiaohe.mp3', tag: 'default' },
  { id: 'TTS_HSDSTTS_V2_0020', label: '撒娇', audioUrl: CDN + '/girlfriend/voice/female_sajiao.mp3', tag: 'default' },
  { id: 'TTS_HSDSTTS_V2_0017', label: '知性', audioUrl: CDN + '/girlfriend/voice/female_sophie.mp3', tag: 'default' },
  { id: 'TTS_HSDSTTS_V2_0022', label: '甜美', audioUrl: CDN + '/girlfriend/voice/female_tianmei.mp3', tag: 'default' }
];

// 扩展位：后续上新的高级/订阅音色在此追加，无需改页面结构。
var EXTRA_VOICES = [];

function all() {
  return DEFAULT_VOICES.concat(EXTRA_VOICES);
}

function findById(id) {
  var list = all();
  for (var i = 0; i < list.length; i++) {
    if (list[i].id === id) return list[i];
  }
  return null;
}

module.exports = {
  DEFAULT_VOICES: DEFAULT_VOICES,
  EXTRA_VOICES: EXTRA_VOICES,
  all: all,
  findById: findById
};
