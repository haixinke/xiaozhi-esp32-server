const gameApi = require('../../utils/game-api');
const starApi = require('../../utils/star-api');
const petStore = require('../../utils/pet-store');
const { openDrawing } = require('../../utils/drawing-entry');

function gameRow(game) {
  const earn = Number(game.earnStars) || 0;
  const cost = Number(game.consumeStars) || 0;
  return {
    ...game,
    title: game.gameTypeName || game.gameType,
    ctaText: earn > 0 ? `赚 ${earn}` : cost > 0 ? `花 ${cost}` : '去玩',
    hasStars: earn > 0 || cost > 0
  };
}

Page({
  data: {
    starBalance: '—',
    starBalanceIcon: '/assets/ui/3d-actions/ui_activity_star_balance_80_v01.png',
    playable: [],
    loading: false,
    error: '',
    balanceError: false
  },

  onShow() { return this.refresh(); },

  // 隐藏或销毁后使在途响应失效；返回页面时重新拉取最新运营配置。
  onHide() { this._requestId = (this._requestId || 0) + 1; },
  onUnload() { this.onHide(); },

  async refresh() {
    const requestId = (this._requestId || 0) + 1;
    this._requestId = requestId;
    this.setData({ loading: true, error: '', playable: [], starBalance: '—', balanceError: false });
    // 两路请求独立呈现错误，余额失败不影响游戏；编号防止旧响应覆盖重试结果。
    await Promise.all([
      this.loadGames(requestId), this.loadBalance(requestId)
    ]);
  },

  async loadGames(requestId) {
    try {
      const games = await gameApi.listActivityGames();
      if (requestId !== this._requestId) return;
      if (!Array.isArray(games)) throw new Error('Invalid games response');
      this.setData({ playable: games.map(gameRow), loading: false });
    } catch (error) {
      if (requestId !== this._requestId) return;
      this.setData({ error: '活动加载失败，请重试', loading: false });
    }
  },

  async loadBalance(requestId) {
    try {
      const result = await starApi.getBalance();
      if (requestId !== this._requestId) return;
      // 后端将 Long 序列化为十进制字符串；空值不能经 Number 转换后误显示为 0。
      const rawBalance = result && result.balance;
      const balance = typeof rawBalance === 'number'
        || (typeof rawBalance === 'string' && /^\d+$/.test(rawBalance))
        ? Number(rawBalance) : NaN;
      if (!Number.isInteger(balance) || balance < 0) {
        throw new Error('Invalid balance response');
      }
      this.setData({ starBalance: balance });
    } catch (error) {
      if (requestId !== this._requestId) return;
      this.setData({ balanceError: true });
    }
  },

  onTapRow(event) {
    const game = this.data.playable.find(item => String(item.id) === String(event.currentTarget.dataset.id));
    if (!game) return;
    // 进入绘画页不执行扣费；收费确认、额度与资格以后端成功开局为准。
    if (game.gameType === 'DRAW_TOGETHER') {
      openDrawing(petStore.getPetSnapshot().pet, 'activity');
      return;
    }
    // 智控台可扩展类型；未接入的玩法保留展示，列表点击不参与星星结算。
    if (game.gameType !== 'LISTEN_TO_YOU') {
      wx.showToast({ title: '暂未开放', icon: 'none' });
      return;
    }
    wx.navigateTo({
      url: '/pages/listen/listen',
      fail: () => wx.showToast({ title: '暂时打不开，请稍后再试', icon: 'none' })
    });
  }
});
