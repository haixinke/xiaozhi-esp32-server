/* mood-badge：心情标签
   传入 mood 中文文案，组件内部映射到色调 tone。 */
const TONE_MAP = {
  开心: 'positive', 兴奋: 'positive',
  平静: 'calm',
  想念: 'longing',
  低落: 'low'
};
Component({
  properties: {
    mood: { type: String, value: '平静' }
  },
  data: { tone: 'calm' },
  observers: {
    mood(v) { this.setData({ tone: TONE_MAP[v] || 'calm' }); }
  }
});
