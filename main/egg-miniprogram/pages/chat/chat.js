/* 对话页
   模拟蛋宝宝回复：真实项目改为调用后端 / 大模型接口，保留 typing 态与
   scroll-into-view 到底部的交互。开场白取自设备的 chatGreeting（对话专属，
   不等于主页的每日一句话）。 */
const REPLIES = ['我在的，一直都在。', '刚刚风有点大，我听了好久的雨声。', '说给我听吧，我记得住的。', '嗯，我在认真听。'];

Page({
  data: {
    pet: {
      id: 'd1', name: '玉兔', mood: '平静',
      dailyLine: '今天把日子过得很慢。',
      chatGreeting: '你来啦。今晚风很好，我一直在听雨声。',
      gradientFrom: '#EDE78E', gradientTo: '#F4B9AE'
    },
    messages: [],
    draft: '',
    typing: false,
    scrollAnchor: ''
  },

  onLoad(query) {
    // TODO: 按 query.id 拉取设备信息与最近对话
    const first = { id: 'm0', from: 'egg', text: this.data.pet.chatGreeting };
    this.setData({ messages: [first], scrollAnchor: 'msg-m0' });
  },

  onInput(e) { this.setData({ draft: e.detail.value }); },

  onSend() {
    const text = this.data.draft.trim();
    if (!text) return;
    const uid = 'u' + Date.now();
    const messages = this.data.messages.concat([{ id: uid, from: 'user', text }]);
    this.setData({ messages, draft: '', typing: true, scrollAnchor: 'msg-' + uid });

    // 模拟回复
    setTimeout(() => {
      const eid = 'e' + Date.now();
      const reply = REPLIES[Math.floor(Math.random() * REPLIES.length)];
      const next = this.data.messages.concat([{ id: eid, from: 'egg', text: reply }]);
      this.setData({ messages: next, typing: false, scrollAnchor: 'msg-' + eid });
    }, 1100);
  },

  noop() {}
});
