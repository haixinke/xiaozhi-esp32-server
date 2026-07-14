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

    var wishedToday = await this._hasWishedToday(pet);
    if (wishedToday) {
      this.setData({ question: null, selected: '', allDone: true, loading: false });
      return;
    }

    // 题目轮转：按历史许愿总次数取模 7，每天展示下一题
    var totalWishes = this._countTotalWishes(pet);
    var index = totalWishes % WISH_QUESTIONS.length;
    this.setData({ question: WISH_QUESTIONS[index], selected: '', allDone: false, loading: false });
  },

  async _hasWishedToday(pet) {
    var today = petStore.todayKey();

    // demo 模式：检查 pet.tasks.wishDate
    if (pet.demoMode) {
      return !!(pet.tasks && pet.tasks.wishDate === today);
    }

    // 非 demo：检查后端 hatch-actions
    if (pet.hatchStatus === 'HATCHED') return true;
    try {
      var actions = await petApi.listHatchActions(pet.id);
      if (Array.isArray(actions)) {
        return actions.some(function (a) {
          return a.actionType === 'WISH' && a.actionDate === today;
        });
      }
    } catch (e) { /* 拉取失败沿用本地 */ }

    // fallback：检查本地记录
    var wishes = (pet.preferences && pet.preferences.wishes) || [];
    return wishes.some(function (w) { return w.date === today; });
  },

  _countTotalWishes(pet) {
    var wishes = (pet.preferences && pet.preferences.wishes) || [];
    return wishes.length;
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
    wx.showToast({ title: '它记住了', icon: 'none' });
    // 许愿后跳回蛋宝宝主页面
    setTimeout(function () { wx.navigateBack(); }, 800);
  },

  onBack() {
    wx.navigateBack();
  }
});
