const petStore = require('../../utils/pet-store');
const REPLIES = ['我在的，一直都在。', '说给我听吧，我会认真记住。', '今天见到你，我很开心。', '嗯，我在认真听。'];

Page({
  data: { pet: null, card: null, dailyStatus: null, messages: [], draft: '', typing: false, scrollAnchor: '' },

  onLoad() {
    const pet = petStore.getPet();
    if (!pet || !pet.collectionCard) {
      wx.showToast({ title: '破壳后才可以对话', icon: 'none' });
      setTimeout(() => wx.navigateBack(), 600);
      return;
    }
    let messages = pet.messages || [];
    if (!messages.length) messages = [{ id: 'hello', from: 'egg', text: '你来啦。我已经等你好一会儿了。' }];
    this.setData({ pet, card: pet.collectionCard, dailyStatus: petStore.getDailyStatus(), messages, scrollAnchor: `msg-${messages[messages.length - 1].id}` });
  },

  onInput(e) { this.setData({ draft: e.detail.value }); },

  onSend() {
    const text = this.data.draft.trim();
    if (!text || this.data.typing) return;
    const userMessage = { id: `u${Date.now()}`, from: 'user', text };
    const messages = this.data.messages.concat(userMessage);
    petStore.saveMessage(userMessage);
    this.setData({ messages, draft: '', typing: true, scrollAnchor: `msg-${userMessage.id}` });
    this.replyTimer = setTimeout(() => {
      const reply = { id: `e${Date.now()}`, from: 'egg', text: REPLIES[Math.floor(Math.random() * REPLIES.length)] };
      petStore.saveMessage(reply);
      const next = this.data.messages.concat(reply);
      this.setData({ messages: next, typing: false, scrollAnchor: `msg-${reply.id}` });
    }, 900);
  },

  onUnload() { clearTimeout(this.replyTimer); },

  noop() {}
});
