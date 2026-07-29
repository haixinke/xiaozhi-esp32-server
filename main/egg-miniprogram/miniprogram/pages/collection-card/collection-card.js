const petStore = require('../../utils/pet-store');

const ZODIAC_SYMBOLS = { 白羊座: '♈', 金牛座: '♉', 双子座: '♊', 巨蟹座: '♋', 狮子座: '♌', 处女座: '♍', 天秤座: '♎', 天蝎座: '♏', 射手座: '♐', 摩羯座: '♑', 水瓶座: '♒', 双鱼座: '♓' };

// 与 app.wxss 全局 font-family 保持一致，确保 Canvas 保存图片字体与页面渲染一致
const FONT_FAMILY = "'PingFang SC', 'Helvetica Neue', 'Microsoft YaHei', sans-serif";

function birthdayLabel(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(value || ''));
  return match ? `${Number(match[1])}年${Number(match[2])}月${Number(match[3])}日` : String(value || '');
}

// 性别符号映射：Canvas 分享卡与未知性别的文本回退使用，页面正常情况走 SVG 图标（见 genderClass）。
// 页面不能直接用字符渲染：iOS 真机对 ♀/♂ 强制回退到 Apple 符号/emoji 字体，
// 字形在行框内基线偏低，CSS 无法修正垂直居中。
function genderLabel(value) {
  if (value === 'FEMALE') return '♀';
  if (value === 'MALE') return '♂';
  return value || '—';
}

// 返回性别 SVG 图标的修饰类；未知性别返回空，页面回退到文本 genderLabel
function genderClass(value) {
  if (value === 'FEMALE') return 'gender-icon--female';
  if (value === 'MALE') return 'gender-icon--male';
  return '';
}

function signatureClass(value) {
  const length = Array.from(String(value || '')).length;
  if (length > 60) return 'card-signature--dense';
  if (length > 32) return 'card-signature--compact';
  return '';
}

Page({
  data: { card: null, pet: null, isNew: false, subtitle: '', birthdayLabel: '', genderLabel: '', genderClass: '', zodiacSymbol: '', signatureClass: '' },

  onLoad(query) {
    const pet = petStore.getPet();
    if (!pet || !pet.collectionCards || pet.collectionCards.length === 0) {
      wx.showToast({ title: '还没有破壳收藏卡', icon: 'none' });
      setTimeout(() => wx.navigateBack(), 600);
      return;
    }
    const index = Math.min(parseInt(query.index || '0', 10), pet.collectionCards.length - 1);
    const cardData = pet.collectionCards[index] || pet.collectionCards[0];
    const proto = pet.prototype || '玉兔';
    const card = {
      ...cardData,
      prototype: proto,
      petType: proto,
      name: pet.name || proto,
      birthday: petStore.todayKey(pet.hatchedAt),
      zodiac: pet.zodiac || '',
      gender: pet.gender || '',
      mbti: pet.mbti || '',
      bloodType: pet.bloodType || '',
      personality: cardData.brief || pet.personalityBrief || '',
      serial: petStore.cardSerial ? petStore.cardSerial(pet) : ''
    };
    this.setData({
      pet,
      card,
      cardIndex: index,
      cardTotal: pet.collectionCards.length,
      subtitle: card.style ? `${proto} · ${card.style}` : proto,
      birthdayLabel: birthdayLabel(card.birthday),
      genderLabel: genderLabel(card.gender),
      genderClass: genderClass(card.gender),
      zodiacSymbol: ZODIAC_SYMBOLS[card.zodiac] || '',
      signatureClass: signatureClass(card.personality),
      isNew: query.new === '1'
    });
  },

  onReady() {
    if (this.data.card) this.drawShareCard();
  },

  drawShareCard() {
    const card = this.data.card;
    const draw = (imagePath) => {
      const ctx = wx.createCanvasContext('shareCanvas', this);

      // 画布与卡片尺寸（9:16 卡片比例）
      const C_W = 750;
      const C_H = 1280;
      const CARD_X = 36;
      const CARD_Y = 28;
      const CARD_W = 678;
      const CARD_H = 1205;
      const PAD = 24;
      const CX = CARD_X + CARD_W / 2;
      const CONTENT_X = CARD_X + PAD;
      const CONTENT_W = CARD_W - PAD * 2;

      // 页面背景
      ctx.setFillStyle('#F3F1E8');
      ctx.fillRect(0, 0, C_W, C_H);

      // 卡片阴影 + 背景
      ctx.setShadow(0, 26, 60, 'rgba(0,41,0,0.16)');
      ctx.setFillStyle('#FFFDF7');
      this._roundRect(ctx, CARD_X, CARD_Y, CARD_W, CARD_H, 40);
      ctx.fill();
      ctx.setShadow(0, 0, 0, 'rgba(0,0,0,0)');

      ctx.setTextBaseline('middle');

      // === 标题区域 (约 9%) ===
      const TITLE_H = Math.round(CARD_H * 0.09);
      const titleY = CARD_Y + PAD;
      const titleCY = titleY + TITLE_H / 2;

      const titleBg = ctx.createLinearGradient(CONTENT_X, titleY, CONTENT_X, titleY + TITLE_H);
      titleBg.addColorStop(0, '#FFFEFB');
      titleBg.addColorStop(1, '#FFFAF0');
      ctx.setFillStyle(titleBg);
      ctx.fillRect(CONTENT_X, titleY, CONTENT_W, TITLE_H);

      // 名字
      ctx.setTextAlign('center');
      ctx.setFillStyle('#3C2D24');
      ctx.font = `600 46px ${FONT_FAMILY}`;
      ctx.fillText(card.name || '', CX, titleCY - 14);
      ctx.font = `normal 16px ${FONT_FAMILY}`;

      // 子标题
      const subtitle = card.style ? `${card.petType} · ${card.style}` : card.petType;
      ctx.setFillStyle('#6E756D');
      ctx.font = `normal 17px ${FONT_FAMILY}`;
      ctx.fillText(subtitle, CX, titleCY + 28);

      // === 插图区域 ===
      const ILLUS_W = CONTENT_W - 60;
      const ILLUS_H = Math.round(ILLUS_W * 5 / 4);
      const ILLUS_X = CONTENT_X + 30;
      const ILLUS_Y = titleY + TITLE_H + 16;

      const illusBg = ctx.createLinearGradient(ILLUS_X, ILLUS_Y, ILLUS_X + ILLUS_W, ILLUS_Y + ILLUS_H);
      illusBg.addColorStop(0, '#EAF3F4');
      illusBg.addColorStop(0.58, '#F5F0DD');
      illusBg.addColorStop(1, '#ECE7F5');

      ctx.save();
      this._roundRect(ctx, ILLUS_X, ILLUS_Y, ILLUS_W, ILLUS_H, 28);
      ctx.setFillStyle(illusBg);
      ctx.fill();
      ctx.clip();
      if (imagePath) {
        ctx.drawImage(imagePath, ILLUS_X, ILLUS_Y, ILLUS_W, ILLUS_H);
      } else {
        const fbSize = 250;
        this._drawPetAvatar(ctx, card.prototype, ILLUS_X + (ILLUS_W - fbSize) / 2, ILLUS_Y + (ILLUS_H - fbSize) / 2, fbSize);
      }
      ctx.restore();

      // === 数据区域 ===
      const dataY = ILLUS_Y + ILLUS_H + 16 + 18;
      const GRID_GAP_V = 10;
      const GRID_GAP_H = 14;
      const CELL_W = (CONTENT_W - GRID_GAP_H) / 2;
      const CELL_H = 50;

      const cells = [
        { label: '类型', value: card.prototype || '' },
        { label: '生日', value: birthdayLabel(card.birthday) },
        { label: '星座', value: `${card.zodiac || ''} ${ZODIAC_SYMBOLS[card.zodiac] || ''}`.trim() },
        { label: '性别', value: genderLabel(card.gender) },
        { label: '血型', value: card.bloodType ? `${card.bloodType} 型` : '' },
        { label: 'MBTI', value: card.mbti || '' }
      ];

      cells.forEach((cell, index) => {
        const col = index % 2;
        const row = Math.floor(index / 2);
        const cellX = CONTENT_X + col * (CELL_W + GRID_GAP_H);
        const cellY = dataY + row * (CELL_H + GRID_GAP_V);

        ctx.setFillStyle('#F6F6F0');
        this._roundRect(ctx, cellX, cellY, CELL_W, CELL_H, 15);
        ctx.fill();

        ctx.setTextAlign('left');
        ctx.setFillStyle('#7A807A');
        ctx.font = `normal 22px ${FONT_FAMILY}`;
        ctx.fillText(cell.label, cellX + 14, cellY + CELL_H / 2);

        ctx.setTextAlign('right');
        ctx.setFillStyle('#2D251F');
        ctx.font = `600 24px ${FONT_FAMILY}`;
        ctx.fillText(cell.value, cellX + CELL_W - 14, cellY + CELL_H / 2);
        ctx.font = `normal 16px ${FONT_FAMILY}`;
      });

      // 签名
      if (card.personality) {
        const gridH = 3 * CELL_H + 2 * GRID_GAP_V;
        const sigY = dataY + gridH + 14;
        const sigText = `“${card.personality}”`;
        const sigLen = Array.from(String(card.personality)).length;
        const sigFontSize = sigLen > 60 ? 18 : sigLen > 32 ? 21 : 25;
        ctx.setTextAlign('center');
        ctx.setFillStyle('#536057');
        ctx.font = `normal ${sigFontSize}px ${FONT_FAMILY}`;
        const sigLines = this._wrapText(ctx, sigText, CONTENT_W - 28, 2);
        const sigLineHeight = sigFontSize * 1.4;
        sigLines.forEach((line, index) => {
          ctx.fillText(line, CX, sigY + index * sigLineHeight + sigFontSize / 2);
        });
      }

      ctx.draw();
    };

    if (card.imageUrl) {
      wx.downloadFile({
        url: card.imageUrl,
        success: (res) => draw(res.tempFilePath),
        fail: () => {
          wx.showToast({ title: '图片下载失败，使用默认头像', icon: 'none' });
          draw(null);
        }
      });
    } else {
      draw(null);
    }
  },

  _roundRect(ctx, x, y, w, h, r) {
    const radius = Math.min(r, w / 2, h / 2);
    ctx.beginPath();
    ctx.moveTo(x + radius, y);
    ctx.lineTo(x + w - radius, y);
    ctx.quadraticCurveTo(x + w, y, x + w, y + radius);
    ctx.lineTo(x + w, y + h - radius);
    ctx.quadraticCurveTo(x + w, y + h, x + w - radius, y + h);
    ctx.lineTo(x + radius, y + h);
    ctx.quadraticCurveTo(x, y + h, x, y + h - radius);
    ctx.lineTo(x, y + radius);
    ctx.quadraticCurveTo(x, y, x + radius, y);
    ctx.closePath();
  },

  _ellipse(ctx, x, y, rx, ry, rotation) {
    ctx.save();
    ctx.translate(x, y);
    ctx.rotate(rotation);
    ctx.scale(rx, ry);
    ctx.beginPath();
    ctx.arc(0, 0, 1, 0, Math.PI * 2);
    ctx.restore();
  },

  _wrapText(ctx, text, maxWidth, maxLines) {
    const chars = String(text || '').split('');
    const lines = [];
    let line = '';
    for (const ch of chars) {
      const test = line + ch;
      if (ctx.measureText(test).width > maxWidth && line) {
        lines.push(line);
        line = ch;
        if (lines.length >= maxLines) break;
      } else {
        line = test;
      }
    }
    if (line && lines.length < maxLines) lines.push(line);
    return lines;
  },

  _drawPetAvatar(ctx, type, x, y, size) {
    ctx.save();
    ctx.translate(x, y);
    const s = size;

    if (type === '玉兔') {
      // 耳朵（z-index 在 face 之下，先绘制）
      // 左耳
      ctx.save();
      ctx.translate(s * 0.28, s * 0.18);
      ctx.rotate(-9 * Math.PI / 180);
      ctx.beginPath();
      this._ellipse(ctx, 0, 0, s * 0.12, s * 0.28, 0);
      ctx.setFillStyle('#FFF9E4');
      ctx.fill();
      ctx.beginPath();
      this._ellipse(ctx, 0, s * 0.04, s * 0.05, s * 0.18, 0);
      ctx.setFillStyle('rgba(244,185,174,0.7)');
      ctx.fill();
      ctx.restore();

      // 右耳
      ctx.save();
      ctx.translate(s * 0.72, s * 0.18);
      ctx.rotate(9 * Math.PI / 180);
      ctx.beginPath();
      this._ellipse(ctx, 0, 0, s * 0.12, s * 0.28, 0);
      ctx.setFillStyle('#FFF9E4');
      ctx.fill();
      ctx.beginPath();
      this._ellipse(ctx, 0, s * 0.04, s * 0.05, s * 0.18, 0);
      ctx.setFillStyle('rgba(244,185,174,0.7)');
      ctx.fill();
      ctx.restore();

      // 脸部（z-index 在耳朵之上）
      ctx.beginPath();
      this._ellipse(ctx, s / 2, s * 0.55, s * 0.38, s * 0.32, 0);
      ctx.setFillStyle('#FFF9E4');
      ctx.fill();

      // 眼睛
      ctx.beginPath();
      ctx.arc(s * 0.38, s * 0.52, s * 0.04, 0, Math.PI * 2);
      ctx.arc(s * 0.62, s * 0.52, s * 0.04, 0, Math.PI * 2);
      ctx.setFillStyle('#002900');
      ctx.fill();

      // 鼻子
      ctx.beginPath();
      this._ellipse(ctx, s / 2, s * 0.58, s * 0.04, s * 0.035, 0);
      ctx.setFillStyle('#D8908B');
      ctx.fill();

      // 腮红
      ctx.beginPath();
      this._ellipse(ctx, s * 0.30, s * 0.62, s * 0.06, s * 0.04, 0);
      this._ellipse(ctx, s * 0.70, s * 0.62, s * 0.06, s * 0.04, 0);
      ctx.setFillStyle('rgba(244,185,174,0.45)');
      ctx.fill();
    } else {
      // 锦鲤
      ctx.save();
      ctx.translate(s / 2, s / 2);
      ctx.rotate(-7 * Math.PI / 180);
      ctx.translate(-s / 2, -s / 2);

      // 尾巴
      ctx.save();
      ctx.translate(s * 0.83, s * 0.50);
      ctx.rotate(45 * Math.PI / 180);
      ctx.beginPath();
      this._ellipse(ctx, 0, 0, s * 0.17, s * 0.28, 0);
      ctx.setFillStyle('#F4B9AE');
      ctx.fill();
      ctx.restore();

      // 身体（z-index 在 tail 之上、fin 之下）
      ctx.beginPath();
      this._ellipse(ctx, s * 0.48, s * 0.54, s * 0.36, s * 0.31, 0);
      ctx.setFillStyle('#FFF9E4');
      ctx.fill();

      // 鱼鳍（z-index 在身体之上）
      ctx.save();
      ctx.translate(s * 0.55, s * 0.74);
      ctx.rotate(18 * Math.PI / 180);
      ctx.beginPath();
      this._ellipse(ctx, 0, 0, s * 0.15, s * 0.13, 0);
      ctx.setFillStyle('#EDE78E');
      ctx.fill();
      ctx.restore();

      // 眼睛
      ctx.beginPath();
      ctx.arc(s * 0.28, s * 0.46, s * 0.04, 0, Math.PI * 2);
      ctx.setFillStyle('#002900');
      ctx.fill();

      // 斑点
      ctx.save();
      ctx.translate(s * 0.53, s * 0.30);
      ctx.rotate(22 * Math.PI / 180);
      ctx.beginPath();
      this._ellipse(ctx, 0, 0, s * 0.15, s * 0.21, 0);
      ctx.setFillStyle('#E77E72');
      ctx.fill();
      ctx.restore();

      ctx.beginPath();
      this._ellipse(ctx, s * 0.72, s * 0.66, s * 0.115, s * 0.17, 0);
      ctx.setFillStyle('#E77E72');
      ctx.fill();

      ctx.restore();
    }

    ctx.restore();
  },

  onAlbum() { wx.navigateTo({ url: '/pages/album/album' }); },

  onSave() {
    wx.canvasToTempFilePath({
      canvasId: 'shareCanvas',
      width: 750,
      height: 1280,
      destWidth: 1500,
      destHeight: 2560,
      success: ({ tempFilePath }) => {
        wx.saveImageToPhotosAlbum({
          filePath: tempFilePath,
          success: () => wx.showToast({ title: '收藏卡已保存', icon: 'success' }),
          fail: () => wx.showToast({ title: '请允许保存到相册', icon: 'none' })
        });
      },
      fail: () => wx.showToast({ title: '图片生成失败，请重试', icon: 'none' })
    }, this);
  },

  onShareAppMessage() {
    const card = this.data.card;
    if (!card) return false;
    return { title: `我孵化了${card.name}，编号 ${card.serial}`, path: '/pages/welcome/welcome' };
  }
});
