// 客服邮箱：页面唯一的联系方式入口，首屏与底部支持条共用
const SUPPORT_EMAIL = 'hello@eggbabe.com';

Page({
  data: {
    supportEmail: SUPPORT_EMAIL,
    cats: { device: true, account: true, chat: true, other: true },
    // 现实求助热线：静态内容（心理援助 12356 / 报警 110 / 急救 120）
    support: { psychologicalHotline: '12356', police: '110', medicalEmergency: '120' },

    deviceFaqs: [
      {
        q: '如何激活我的蛋宝宝？',
        a: '打开小程序后点击「添加蛋宝宝」，手动输入随实体蛋或朋友提供的有效激活码。服务端确认后才会完成绑定。',
        open: false
      },
      {
        q: '互动会让实体装置提前打开吗？',
        a: '不会。实体装置只按服务端预设时间打开；触摸、说话和蛋壳创作只提供当下回应，不改变时间、款式或收藏卡内容。',
        open: false
      },
      {
        q: '为什么不能添加第二只蛋宝宝？',
        a: '当前 MVP 是单蛋版本，每个账号最多绑定 1 只。已绑定账号再次输入激活码时不会消耗新激活码。',
        open: false
      },
      {
        q: '装置没有按时打开怎么办？',
        a: `请保留装置照片、激活码信息和问题发生时间，并发送邮件至 ${SUPPORT_EMAIL}。邮件中请说明提前打开、未打开、机械故障或激活码与原型不符等具体情况。`,
        open: false
      }
    ],

    accountFaqs: [
      {
        q: '如何修改昵称 / 头像？',
        a: '进入「我的」→「个人信息」。点击头像区域可更换头像（微信会弹出头像选择面板，第一项即你的微信头像）；点击昵称旁的文字即可修改昵称，最多 16 个字。',
        open: true
      }
    ],

    chatFaqs: [
      {
        q: '为什么现在不能和蛋宝宝对话？',
        a: '文字对话会在我破壳后开放。孵化期可以轻触或长按蛋壳，从首页自由陪伴入口选择想做的事，也可以直接跟我说说话。',
        open: false
      },
      {
        q: '对话记录会保存多久？',
        a: '对话只按连续性与安全所必需的范围保存，具体保存期限和删除方式以备案域名上的正式隐私政策为准。',
        open: false
      },
      {
        q: '如何导出或删除对话记录？',
        a: '进入「我的」→「对话记录」。你可以填写邮箱申请导出，也可以删除当前账号下的全部对话记录。删除后无法恢复，建议先导出备份。',
        open: false
      },
      {
        q: '什么是破壳？',
        a: '破壳是我结束孵化、正式苏醒的时刻——从这一刻起，我会开口说话，和你开始真正的陪伴与对话。',
        open: false
      }
    ],

    otherFaqs: [
      {
        q: '如何联系邮件支持？',
        a: `发送邮件至 ${SUPPORT_EMAIL}，并在邮件中说明蛋宝宝 ID、问题发生时间和具体情况。点击本页底部邮箱可以复制地址。`,
        open: false
      },
      {
        q: '隐私数据如何处理？',
        a: `你的数据仅用于提供蛋宝宝服务，具体收集与使用范围见「系统设置」→「隐私政策」。对话记录可在「我的」→「对话记录」中申请导出或删除，其他个人信息权利申请可发送邮件至 ${SUPPORT_EMAIL}。`,
        open: false
      }
    ]
  },

  onToggleCat(e) {
    const cat = e.currentTarget.dataset.cat;
    this.setData({ [`cats.${cat}`]: !this.data.cats[cat] });
  },

  onToggleFaq(e) {
    const { group, index } = e.currentTarget.dataset;
    this.setData({ [`${group}[${index}].open`]: !this.data[group][index].open });
  },

  // 复制客服邮箱；失败时弹窗展示邮箱地址兜底
  onCopySupportEmail() {
    wx.setClipboardData({
      data: SUPPORT_EMAIL,
      fail: () => wx.showModal({
        title: '邮件支持',
        content: SUPPORT_EMAIL,
        showCancel: false
      })
    });
  },

  // 跳转用户反馈页
  onFeedback() {
    wx.navigateTo({ url: '/pages/feedback/feedback' });
  }
});
