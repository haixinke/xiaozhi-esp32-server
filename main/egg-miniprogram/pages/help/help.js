Page({
  data: {
    cats: { device: true, account: true, chat: true, other: true },

    deviceFaqs: [
      {
        q: '如何激活我的蛋宝宝？',
        a: '将蛋宝宝包装盒内的二维码贴纸贴到蛋宝宝底部，打开小程序后点击「添加蛋宝宝」扫码即可激活。激活后会进入孵化期，等待破壳时刻。',
        open: false
      },
      {
        q: '蛋宝宝连不上手机怎么办？',
        a: '请先确认手机蓝牙已开启，并让蛋宝宝保持在 1 米范围内。若仍无法连接，尝试重启小程序，或长按蛋宝宝底部按钮 3 秒重启设备后再试。',
        open: false
      },
      {
        q: '如何重置设备？',
        a: '长按蛋宝宝底部按钮 8 秒直至指示灯闪烁三次，即可恢复出厂设置。重置后设备会自动解绑，需重新扫码激活，此前的对话与记忆不会保留。',
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
        a: '15 天冷静期后，设备将自动解绑并回到未激活状态。你可以用其他账号重新扫码激活，但重新激活后是一只全新的蛋宝宝，原有的记忆不会保留。',
        open: false
      }
    ],

    chatFaqs: [
      {
        q: '蛋宝宝为什么没有回应？',
        a: '可能是设备蓝牙未连接或网络异常。请检查手机蓝牙是否开启，并确认蛋宝宝设备电量充足。',
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
