var petStore = require('../../utils/pet-store');
var petApi = require('../../utils/pet-api');
var WISH_QUESTIONS = require('../../config/wish-questions');

Page({
  data: {
    question: null,
    selected: '',
    allDone: false,
    loading: true
  },

  async onShow() {
    await this._loadQuestion();
  },

  async _loadQuestion() {
    var pet = petStore.getPet();
    if (!pet) {
      wx.navigateBack();
      return;
    }
    this.setData({ loading: true });

    var answeredIds = await this._getAnsweredToday(pet);
    var nextQuestion = null;
    for (var i = 0; i < WISH_QUESTIONS.length; i++) {
      if (answeredIds.indexOf(WISH_QUESTIONS[i].id) === -1) {
        nextQuestion = WISH_QUESTIONS[i];
        break;
      }
    }

    if (nextQuestion) {
      this.setData({ question: nextQuestion, selected: '', allDone: false, loading: false });
    } else {
      this.setData({ question: null, selected: '', allDone: true, loading: false });
    }
  },

  async _getAnsweredToday(pet) {
    var today = petStore.todayKey();
    var ids = [];

    // 1. 本地记录（demo 模式主源，非 demo 补充）
    var wishes = (pet.preferences && pet.preferences.wishes) || [];
    wishes.forEach(function (w) {
      if (w.date === today && w.questionId) ids.push(w.questionId);
    });

    // 2. 后端记录（非 demo 模式，后端唯一索引仅存当日首条）
    if (!pet.demoMode && pet.hatchStatus !== 'HATCHED') {
      try {
        var actions = await petApi.listHatchActions(pet.id);
        if (Array.isArray(actions)) {
          actions.forEach(function (a) {
            if (a.actionType === 'WISH' && a.actionDate === today) {
              try {
                var payload = typeof a.payload === 'string' ? JSON.parse(a.payload) : a.payload;
                if (payload && payload.questionId) ids.push(payload.questionId);
              } catch (e) { /* payload 解析失败忽略 */ }
            }
          });
        }
      } catch (e) { /* 拉取失败沿用本地记录 */ }
    }

    // 去重
    var unique = [];
    ids.forEach(function (id) { if (unique.indexOf(id) === -1) unique.push(id); });
    return unique;
  },

  onSelect(e) {
    this.setData({ selected: e.currentTarget.dataset.value });
  },

  async onSubmit() {
    if (!this.data.selected) return wx.showToast({ title: '先选一个愿望吧', icon: 'none' });
    var question = this.data.question;
    var payload = { questionId: question.id, value: this.data.selected };
    var result = await petStore.completeWish(payload);
    if (!result.ok) return wx.showToast({ title: result.message, icon: 'none' });
    wx.showToast({ title: result.alreadyDone ? '今天已经许过愿啦' : '它记住了', icon: 'none' });
    // 刷新出下一题或进入 allDone 状态
    var self = this;
    setTimeout(function () { self._loadQuestion(); }, 800);
  },

  onBack() {
    wx.navigateBack();
  }
});
