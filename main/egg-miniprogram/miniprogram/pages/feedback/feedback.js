const feedbackApi = require('../../utils/feedback-api');
const { FALLBACK_FEEDBACK_TYPES } = require('../../config/feedback-types');

const MIN_SUBMITTING_VISIBLE_MS = 600;
// 客服热线：老年人人工反馈渠道，知情同意书弹窗展示与拨号共用
const HOTLINE = '18201931204';

function buildTypeOptions(types) {
  return [{ value: '', label: '请选择诉求类型' }].concat(types);
}

Page({
  data: {
    typeOptions: buildTypeOptions(FALLBACK_FEEDBACK_TYPES),
    typeIndex: 0,
    type: '',
    description: '',
    consent: false,
    typePanelOpen: false,
    submitting: false,
    submitError: '',
    receiptNumber: '',
    canSubmit: false,
    consentVisible: false,
    hotline: HOTLINE
  },

  onLoad() {
    this.loadFeedbackTypes();
  },

  onUnload() {
    this.submissionInFlight = false;
    this.submitRequestToken = (Number(this.submitRequestToken) || 0) + 1;
    clearTimeout(this.submitDelayTimer);
    this.submitDelayTimer = null;
  },

  // 诉求类型来自后端字典；拉取失败沿用本地兜底列表
  loadFeedbackTypes() {
    feedbackApi.listFeedbackTypes()
      .then((types) => {
        if (types.length > 0) {
          this.setData({ typeOptions: buildTypeOptions(types) });
        }
      })
      .catch(() => {});
  },

  refreshCanSubmit(fields) {
    const next = Object.assign({}, this.data, fields || {});
    this.setData(Object.assign({}, fields || {}, {
      canSubmit: Boolean(next.type && String(next.description || '').trim() && next.consent && !next.submitting)
    }));
  },

  onToggleTypePanel() {
    if (this.data.submitting) return;
    this.setData({ typePanelOpen: !this.data.typePanelOpen, submitError: '' });
  },

  onSelectType(event) {
    if (this.data.submitting) return;
    const typeIndex = Number(event.currentTarget.dataset.index);
    const option = this.data.typeOptions[typeIndex];
    if (!option || !option.value) return;
    this.refreshCanSubmit({ typeIndex, type: option.value, typePanelOpen: false, submitError: '' });
  },

  onDescriptionInput(event) {
    this.refreshCanSubmit({ description: String(event.detail.value || ''), submitError: '' });
  },

  onToggleConsent() {
    if (this.data.submitting) return;
    this.refreshCanSubmit({ consent: !this.data.consent, submitError: '' });
  },

  onOpenConsent() {
    this.setData({ consentVisible: true });
  },

  onCloseConsent() {
    this.setData({ consentVisible: false });
  },

  // 弹窗内明确点击"同意"才勾选，查看行为本身不构成同意
  onAgreeConsent() {
    this.refreshCanSubmit({ consent: true, consentVisible: false, submitError: '' });
  },

  onCallHotline() {
    wx.makePhoneCall({ phoneNumber: HOTLINE });
  },

  // 拦截弹窗遮罩的触摸穿透与点击冒泡，无实际逻辑
  noop() {},

  onSubmit() {
    if (this.data.submitting || this.submissionInFlight) return;
    if (!this.data.canSubmit) {
      let submitError = '';
      if (!this.data.type) submitError = '请选择诉求类型';
      else if (!String(this.data.description || '').trim()) submitError = '请填写问题详细描述';
      else if (!this.data.consent) submitError = '请勾选同意知情同意书';
      else submitError = '请完善必填信息后提交';
      this.setData({ submitError });
      wx.showToast({ title: submitError, icon: 'none' });
      return;
    }
    const payload = {
      type: this.data.type,
      content: this.data.description.trim(),
      consent: true
    };
    this.submissionInFlight = true;
    this.refreshCanSubmit({ submitting: true, submitError: '' });
    const requestToken = (Number(this.submitRequestToken) || 0) + 1;
    this.submitRequestToken = requestToken;
    const minimumVisibleDelay = new Promise(resolve => {
      this.submitDelayTimer = setTimeout(() => {
        this.submitDelayTimer = null;
        resolve();
      }, MIN_SUBMITTING_VISIBLE_MS);
    });
    const submission = feedbackApi.submitFeedback(payload)
      .then((data) => ({ ok: true, receiptNumber: data && data.receiptNumber }))
      .catch((error) => ({ ok: false, message: (error && error.userMessage) || '提交失败，请重试' }));
    Promise.all([submission, minimumVisibleDelay]).then(([result]) => {
      if (this.submitRequestToken !== requestToken) return;
      this.submissionInFlight = false;
      if (!result || !result.ok) {
        this.refreshCanSubmit({ submitting: false, submitError: result && result.message || '提交失败，请重试' });
        return;
      }
      this.setData({
        typeIndex: 0,
        type: '',
        description: '',
        consent: false,
        typePanelOpen: false,
        submitting: false,
        submitError: '',
        receiptNumber: result.receiptNumber,
        canSubmit: false
      });
    });
  }
});
