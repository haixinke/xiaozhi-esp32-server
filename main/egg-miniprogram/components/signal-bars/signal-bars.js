/* signal-bars：信号强度 3 格
   level: 1 弱 / 2 中 / 3 强。业务侧把 dBm 或强度枚举映射为 1~3 传入。 */
Component({
  properties: {
    level: { type: Number, value: 3 }
  }
});
