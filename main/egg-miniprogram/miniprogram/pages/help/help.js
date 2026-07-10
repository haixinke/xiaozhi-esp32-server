Page({
  data: {
    cats: { device: true, account: true, chat: true, other: true },

    deviceFaqs: [
      {
        q: '如何激活我的蛋宝宝？',
        a: '打开小程序后点击「添加蛋宝宝」，手动输入购买、活动或好友提供的激活码。校验成功后会绑定当前账号唯一的一只蛋宝宝。',
        open: false
      },
      {
        q: '孵化进度会让实体装置提前打开吗？',
        a: '不会。实体装置按激活码预设的破壳时间打开；孵化进度只会丰富互动反馈、性格倾向和收藏卡内容。',
        open: false
      },
      {
        q: '为什么不能添加第二只蛋宝宝？',
        a: '当前 MVP 是单蛋版本，每个账号最多绑定 1 只。已绑定账号再次输入激活码时不会消耗新激活码。',
        open: false
      }
    ],

    accountFaqs: [
      {
        q: '如何修改昵称 / 头像？',
        a: '进入「我的」→「个人信息」。点击头像区域可更换头像（微信会弹出头像选择面板，第一项即你的微信头像）；点击昵称旁的文字即可修改昵称，最多 16 个字。',
        open: true
      },
      {
        q: '如何注销账号？',
        a: '进入「我的」→「账号」→「注销账号」，按提示阅读风险告知并确认。提交后进入 15 天冷静期，期间重新登录可随时撤销。',
        open: false
      },
      {
        q: '注销后还能重新激活蛋宝宝吗？',
        a: '冷静期结束并完成注销后，原账号资料、蛋宝宝、卡册和对话记录会按协议处理。正式发布前需以后端和法务确认的注销规则为准。',
        open: false
      }
    ],

    chatFaqs: [
      {
        q: '为什么现在不能和蛋宝宝对话？',
        a: '文字对话会在蛋宝宝破壳后开放。孵化期可以轻触蛋壳获得回应，也可以完成修炼手册里的动作。',
        open: false
      },
      {
        q: '对话记录会保存多久？',
        a: '对话记录会长期保留，作为蛋宝宝记忆与成长的一部分，除非你主动注销账号（注销后将被永久删除）。',
        open: false
      },
      {
        q: '什么是破壳？',
        a: '破壳是蛋宝宝孵化期结束、正式苏醒的时刻——从这一刻起，它会开口说话，和你开始真正的陪伴与对话。',
        open: false
      }
    ],

    otherFaqs: [
      {
        q: '如何联系客服？',
        a: '点击本页底部「联系客服」，会跳转至企业微信客服，工作日 9:00–21:00 有专人回复。',
        open: false
      },
      {
        q: '隐私数据如何处理？',
        a: '你的数据仅用于提供蛋宝宝服务，具体收集与使用范围见「我的」→「隐私协议」。你可随时在账号页申请注销以删除全部数据。',
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

  onSearchTap() {
    // TODO: 接入真实的 FAQ 关键词搜索/高亮，目前仅占位提示
    wx.showToast({ title: '搜索功能待接入', icon: 'none' });
  },

  onContactCS() {
    // 企业微信客服接入方式二选一：
    // 1) wx.openCustomerServiceChat（需要企业微信 corpId + kfId/客服链接）
    // 2) <button open-type="contact"> 原生客服按钮
    wx.openCustomerServiceChat({
      extInfo: { url: 'https://work.weixin.qq.com/kfid/YOUR_KF_ID' },
      corpId: 'YOUR_CORP_ID',
      fail: () => {
        wx.showToast({ title: '客服功能待接入', icon: 'none' });
      }
    });
  }
});
