const { withPhoneGate } = require('../../utils/phone-gate');
const inviteApi = require('../../utils/invite-api');
const { saveLocalImage } = require('../../utils/save-image');

let qrFileSequence = 0;

function getExpirationMillis(value) {
  // 服务端时间字符串使用北京时间，但响应中省略了时区。
  const backendFormat = typeof value === 'string' && /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(value);
  return new Date(backendFormat ? `${value.replace(' ', 'T')}+08:00` : value).getTime();
}

function getStatusLabel(invite) {
  if (!invite) return '';
  if (invite.status !== 1) return '已停用';
  if (invite.expireTime && getExpirationMillis(invite.expireTime) <= Date.now()) return '已过期';
  if (invite.remaining <= 0) return '已用完';
  return '可邀请';
}

function writeQrImage(png) {
  if (typeof png !== 'string' || !png.startsWith('iVBORw0KGgo')) {
    throw new Error('二维码响应无效');
  }
  const filePath = `${wx.env.USER_DATA_PATH}/invite-qr-${Date.now()}-${++qrFileSequence}.png`;
  wx.getFileSystemManager().writeFileSync(filePath, png, 'base64');
  return filePath;
}

function removeQrImage(filePath) {
  wx.getFileSystemManager().unlink({ filePath, fail() {} });
}

Page(withPhoneGate({
  data: {
    inviteCode: null,
    statusLabel: '',
    qrImage: '',
    loading: true,
    saving: false,
    error: ''
  },

  onShow() {
    return this.loadInviteCode();
  },

  onHide() {
    this.releaseQrImage();
  },

  onUnload() {
    this.disposed = true;
    this.releaseQrImage();
  },

  async loadInviteCode() {
    this.releaseQrImage();
    const requestId = this.inviteRequestId;
    this.setData({ inviteCode: null, statusLabel: '', qrImage: '', loading: true, error: '' });
    try {
      const invite = await inviteApi.getMine();
      if (this.inviteRequestId !== requestId) return;
      const statusLabel = getStatusLabel(invite);
      const inviteCode = invite && { usedCount: invite.usedCount, quota: invite.quota, remaining: invite.remaining };
      this.setData({ inviteCode, statusLabel });
      if (statusLabel === '可邀请') {
        const png = await inviteApi.getMineQrCode();
        if (this.inviteRequestId !== requestId) return;
        this.qrFilePath = writeQrImage(png);
        this.setData({ qrImage: this.qrFilePath });
      }
      this.setData({ loading: false });
    } catch (error) {
      if (this.inviteRequestId !== requestId) return;
      this.setData({ loading: false, error: (error && error.userMessage) || '激活码加载失败，请重试' });
    }
  },

  releaseQrImage() {
    this.inviteRequestId = (this.inviteRequestId || 0) + 1;
    if (!this.qrFilePath) return;
    if (this.qrFilePath !== this.savingQrPath) removeQrImage(this.qrFilePath);
    this.qrFilePath = '';
    this.setData({ qrImage: '' });
  },

  async onSaveQrImage() {
    const filePath = this.qrFilePath;
    if (!filePath || this.savingQrPath) return;
    this.savingQrPath = filePath;
    this.setData({ saving: true });
    const title = await saveLocalImage(filePath);
    this.savingQrPath = '';
    if (this.qrFilePath !== filePath) removeQrImage(filePath);
    if (this.disposed) return;
    this.setData({ saving: false });
    if (this.qrFilePath === filePath) wx.showToast({ title, icon: title === '已保存到相册' ? 'success' : 'none' });
  },

  onRetry() {
    return this.loadInviteCode();
  },

  onQrImageError(event) {
    if (event.currentTarget.dataset.path !== this.qrFilePath) return;
    this.releaseQrImage();
    this.setData({ loading: false, error: '二维码加载失败，请重试' });
  }
}));
