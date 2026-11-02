// 陪伴星星的哑光造型：信件收星（award）与房间左上角（room）共用同一对透明 PNG。
// 只负责视觉，不参与奖励判定；声音与震动由页面在真实收星成功后触发。
const STAR_ASSET_ROOT = '/assets/scenes/lifecycle/post-hatch/40-interaction-fx/companion-star';

Component({
  properties: {
    mode: { type: String, value: 'room' }, // room | award | accent（静态点缀，如听你说「谢谢你说给我听」）
    size: { type: Number, value: 22 },
    pulse: { type: Boolean, value: false },
    reducedMotion: { type: Boolean, value: false }
  },

  data: {
    neutralSrc: `${STAR_ASSET_ROOT}/companion-star-neutral.png`,
    squeezedSrc: `${STAR_ASSET_ROOT}/companion-star-squeezed.png`
  }
});

module.exports = { STAR_ASSET_ROOT };
