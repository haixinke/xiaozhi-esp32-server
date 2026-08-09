const { post } = require('../../utils/request');
const petStore = require('../../utils/pet-store');
const auth = require('../../utils/auth');
const shareInvite = require('../../utils/share-invite');

const ACTIVATION_CODE_LENGTH = 5;

function activationCodeCells(code) {
  const value = String(code || '');
  return Array.from({ length: ACTIVATION_CODE_LENGTH }, (_, index) => ({
    value: value[index] || '',
    active: value.length < ACTIVATION_CODE_LENGTH && index === value.length
  }));
}

Page({
  data: {
    code: '',
    codeCells: activationCodeCells(''),
    codeFocused: false,
    error: '',
    canSubmit: false,
    success: null,
    submitting: false,
    sharedInvite: false
  },

  onLoad() {
    const session = auth.getSession();
    if (!session || auth.isExpired() || session.hasPhone !== true) {
      wx.reLaunch({ url: '/pages/home/home' });
      return;
    }
    const pending = shareInvite.getPending();
    if (pending && pending.code) {
      const code = String(pending.code)
        .replace(/[^a-z0-9]/gi, '')
        .toUpperCase()
        .slice(0, ACTIVATION_CODE_LENGTH);
      this.setData({
        code,
        codeCells: activationCodeCells(code),
        canSubmit: code.length === ACTIVATION_CODE_LENGTH,
        sharedInvite: true
      });
    }
  },

  onCodeInput(e) {
    const code = String(e.detail.value || '')
      .replace(/[^a-z0-9]/gi, '')
      .toUpperCase()
      .slice(0, ACTIVATION_CODE_LENGTH);
    const pending = this.data.sharedInvite ? shareInvite.getPending() : null;
    const sharedInvite = !!(pending && code === pending.code);
    if (this.data.sharedInvite && !sharedInvite) shareInvite.clearPending();
    this.setData({
      code,
      codeCells: activationCodeCells(code),
      error: '',
      canSubmit: code.length === ACTIVATION_CODE_LENGTH,
      sharedInvite
    });
  },

  onCodeFocus() {
    this.setData({ codeFocused: true });
  },

  onCodeBlur() {
    this.setData({ codeFocused: false });
  },

  onCodeTap() {
    this.setData({ codeFocused: true });
  },

  async onValidate() {
    if (!this.data.canSubmit || this.data.submitting) return;
    const inviteCode = this.data.code.trim();
    this.setData({ submitting: true, error: '' });
    try {
      const petVO = await post('/pet/adopt', { inviteCode });
      petStore.savePetFromVO(petVO);
      if (this.data.sharedInvite) shareInvite.clearPending();
      this.setData({ success: { prototype: petVO.prototype, petType: petVO.prototype } });
      setTimeout(() => wx.switchTab({ url: '/pages/home/home' }), 1150);
    } catch (error) {
      this.setData({ error: error.userMessage || '操作失败，请稍后重试', submitting: false });
    }
  }
});
