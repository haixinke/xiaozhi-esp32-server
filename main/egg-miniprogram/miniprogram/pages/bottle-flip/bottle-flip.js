const { withPhoneGate } = require('../../utils/phone-gate');

Page(withPhoneGate({
  data: { gameUrl: '' },

  onLoad() {
    this.setData({ gameUrl: 'https://game.eggbabe.com/games/bottle-flip/' });
  }
}));
