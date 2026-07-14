const petStore = require('../../utils/pet-store');

Page({
  data: { card: null, pet: null, isNew: false },

  onLoad(query) {
    const pet = petStore.getPet();
    if (!pet || !pet.collectionCard) {
      wx.showToast({ title: '还没有破壳收藏卡', icon: 'none' });
      setTimeout(() => wx.navigateBack(), 600);
      return;
    }
    this.setData({ pet, card: { ...pet.collectionCard, petType: pet.collectionCard.prototype }, isNew: query.new === '1' });
  },

  onReady() {
    if (this.data.card) this.drawShareCard();
  },

  drawShareCard() {
    const card = this.data.card;
    const draw = (imagePath) => {
      const ctx = wx.createCanvasContext('shareCanvas', this);

      // 画布与卡片尺寸（按 750rpx 设计稿 1:1 映射为 px）
      const C_W = 750;
      const C_H = 1100;
      const CARD_X = 36;
      const CARD_Y = 28;
      const CARD_W = 678;
      const CARD_H = 980;
      const PADDING_X = 34;
      const PADDING_Y = 36;
      const CX = CARD_X + CARD_W / 2;

      // 页面背景
      ctx.setFillStyle('#F3F1E8');
      ctx.fillRect(0, 0, C_W, C_H);

      // 卡片阴影
      ctx.setShadow(0, 26, 60, 'rgba(0,41,0,0.16)');

      // 卡片渐变背景
      const gradient = ctx.createLinearGradient(CARD_X, CARD_Y, CARD_X + CARD_W, CARD_Y + CARD_H);
      gradient.addColorStop(0, '#FFFDF2');
      gradient.addColorStop(0.56, '#FAF7D8');
      gradient.addColorStop(1, '#F9D8D1');
      ctx.setFillStyle(gradient);
      this._roundRect(ctx, CARD_X, CARD_Y, CARD_W, CARD_H, 38);
      ctx.fill();

      // 重置阴影，避免影响后续绘制
      ctx.setShadow(0, 0, 0, 'rgba(0,0,0,0)');

      // 白色边框
      ctx.setStrokeStyle('#FFFFFF');
      ctx.setLineWidth(4);
      this._roundRect(ctx, CARD_X, CARD_Y, CARD_W, CARD_H, 38);
      ctx.stroke();

      // 装饰圆环（right: -140rpx; top: 120rpx; size: 300rpx）
      ctx.beginPath();
      ctx.arc(CARD_X + CARD_W + 140 - 150, CARD_Y + 120 + 150, 150, 0, Math.PI * 2);
      ctx.setStrokeStyle('rgba(0,41,0,0.09)');
      ctx.setLineWidth(2);
      ctx.stroke();

      ctx.setTextBaseline('middle');

      // 顶部标题与收藏标签
      let y = CARD_Y + PADDING_Y;
      ctx.setTextAlign('left');
      ctx.setFillStyle('#56612E');
      ctx.setFontSize(18);
      ctx.fillText('EGGBABY · BIRTH CARD', CARD_X + PADDING_X, y + 9);

      const tagText = card.collectible || '普通';
      ctx.setFontSize(19);
      const tagTextWidth = ctx.measureText(tagText).width;
      const tagWidth = tagTextWidth + 32;
      const tagHeight = 32;
      const tagX = CARD_X + CARD_W - PADDING_X - tagWidth;
      this._roundRect(ctx, tagX, y, tagWidth, tagHeight, 16);
      ctx.setStrokeStyle('#78863D');
      ctx.setLineWidth(1);
      ctx.stroke();
      ctx.setFillStyle('#56612E');
      ctx.setTextAlign('center');
      ctx.fillText(tagText, tagX + tagWidth / 2, y + tagHeight / 2);

      y += tagHeight + 32;

      // 头像区域
      const P_SIZE = 270;
      const P_X = CX - P_SIZE / 2;
      const P_Y = y;
      const P_CY = P_Y + P_SIZE / 2;

      // 头像光晕（旧版 canvas 不支持 createRadialGradient，用同心圆模拟径向渐变）
      const steps = 16;
      const maxR = P_SIZE / 2;
      for (let i = steps; i >= 0; i--) {
        const ratio = i / steps;
        const r = ratio * maxR;
        const alpha = 0.1 + (1 - ratio) * 0.9;
        ctx.setGlobalAlpha(alpha);
        ctx.beginPath();
        ctx.arc(CX, P_CY, r, 0, Math.PI * 2);
        ctx.setFillStyle('#FFFFFF');
        ctx.fill();
      }
      ctx.setGlobalAlpha(1);

      // 头像图片 / 默认宠物头像
      ctx.save();
      ctx.beginPath();
      ctx.arc(CX, P_CY, P_SIZE / 2, 0, Math.PI * 2);
      ctx.clip();
      if (imagePath) {
        ctx.drawImage(imagePath, P_X, P_Y, P_SIZE, P_SIZE);
      } else {
        this._drawPetAvatar(ctx, card.petType || card.prototype, P_X, P_Y, P_SIZE);
      }
      ctx.restore();

      y += P_SIZE + 24;

      // 名字 + 性别
      ctx.setTextAlign('center');
      ctx.setFillStyle('#002900');
      ctx.font = '600 46px sans-serif';
      const nameText = card.name || '';
      const genderText = card.gender || '';
      const nameWidth = ctx.measureText(nameText).width;
      const genderWidth = ctx.measureText(genderText).width;
      const gap = 8;
      const nameX = CX - (gap + genderWidth) / 2;
      const genderX = CX + nameWidth / 2 + gap / 2;
      ctx.fillText(nameText, nameX, y + 23);
      ctx.setFillStyle('#7D8945');
      ctx.font = 'normal 29px sans-serif';
      ctx.fillText(genderText, genderX, y + 23);
      ctx.font = 'normal 16px sans-serif';

      y += 46 + 10;

      // 风格（与 WXML 一致，为空时不显示）
      if (card.style) {
        ctx.setFillStyle('#737168');
        ctx.setFontSize(24);
        ctx.fillText(`${card.petType || card.prototype} · ${card.style}`, CX, y + 12);
        y += 24 + 26;
      } else {
        y += 26;
      }

      // 个性签名（最多两行）
      if (card.personality) {
        ctx.setFillStyle('#48473F');
        ctx.setFontSize(25);
        const lines = this._wrapText(ctx, card.personality, 540, 2);
        const lineHeight = 40;
        lines.forEach((line, index) => {
          ctx.fillText(line, CX, y + index * lineHeight + 12.5);
        });
        y += lines.length * lineHeight + 28;
      } else {
        y += 24;
      }

      // 信息四宫格
      const GRID_W = CARD_W - PADDING_X * 2;
      const CELL_H = 91;
      const GAP = 2;
      const GRID_H = CELL_H * 2 + GAP;
      const CELL_W = (GRID_W - GAP) / 2;

      ctx.save();
      this._roundRect(ctx, CARD_X + PADDING_X, y, GRID_W, GRID_H, 24);
      ctx.setFillStyle('rgba(255,255,255,0.65)');
      ctx.fill();
      ctx.clip();

      const cells = [
        { label: '生日', value: card.birthday || '' },
        { label: '星座', value: card.zodiac || '' },
        { label: 'MBTI', value: card.mbti || '' },
        { label: '血型', value: card.bloodType ? `${card.bloodType} 型` : '' }
      ];
      cells.forEach((cell, index) => {
        const col = index % 2;
        const row = Math.floor(index / 2);
        const cellX = CARD_X + PADDING_X + col * (CELL_W + GAP);
        const cellY = y + row * (CELL_H + GAP);
        ctx.setFillStyle('rgba(255,255,255,0.48)');
        ctx.fillRect(cellX, cellY, CELL_W, CELL_H);

        ctx.setTextAlign('left');
        ctx.setFillStyle('#929087');
        ctx.setFontSize(19);
        ctx.fillText(cell.label, cellX + 24, cellY + 20 + 9.5);

        ctx.setFillStyle('#31362A');
        ctx.setFontSize(25);
        ctx.font = '600 25px sans-serif';
        ctx.fillText(cell.value, cellX + 24, cellY + 20 + 19 + 7 + 12.5);
        ctx.font = 'normal 16px sans-serif';
      });
      ctx.restore();

      y += GRID_H + 24;

      // 孵化记录
      ctx.setTextAlign('left');
      ctx.setFillStyle('#6B704E');
      ctx.setFontSize(21);
      const recordLeft = card.hatchQuality || '';
      const recordRight = `初始主人 · ${card.originalOwner || '蛋友'}`;
      ctx.fillText(recordLeft, CARD_X + PADDING_X, y + 10.5);
      const rightWidth = ctx.measureText(recordRight).width;
      ctx.fillText(recordRight, CARD_X + CARD_W - PADDING_X - rightWidth, y + 10.5);

      y += 21 + 24;

      // 编号行分隔线
      ctx.beginPath();
      ctx.moveTo(CARD_X + PADDING_X, y);
      ctx.lineTo(CARD_X + CARD_W - PADDING_X, y);
      ctx.setStrokeStyle('rgba(0,41,0,0.14)');
      ctx.setLineWidth(1);
      ctx.stroke();

      // 编号
      ctx.setFillStyle('#5D633F');
      ctx.setFontSize(19);
      ctx.fillText(card.serial || '', CARD_X + PADDING_X, y + 22 + 9.5);

      // 小程序码占位
      const CODE_SIZE = 48;
      const codeX = CARD_X + CARD_W - PADDING_X - CODE_SIZE;
      const codeY = y + 22 + (19 - CODE_SIZE) / 2;
      ctx.setFillStyle('#FFFFFF');
      ctx.fillRect(codeX, codeY, CODE_SIZE, CODE_SIZE);
      ctx.setFillStyle('#002900');
      ctx.fillRect(codeX + 5, codeY + 5, 17, 17);
      ctx.fillRect(codeX + 26, codeY + 5, 17, 17);
      ctx.fillRect(codeX + 5, codeY + 26, 17, 17);
      ctx.beginPath();
      ctx.arc(codeX + 26 + 8.5, codeY + 26 + 8.5, 8.5, 0, Math.PI * 2);
      ctx.setFillStyle('#9DB65B');
      ctx.fill();

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
      height: 1100,
      destWidth: 1500,
      destHeight: 2200,
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
    return { title: `我孵化了${this.data.card.name}，编号 ${this.data.card.serial}`, path: '/pages/welcome/welcome' };
  }
});
