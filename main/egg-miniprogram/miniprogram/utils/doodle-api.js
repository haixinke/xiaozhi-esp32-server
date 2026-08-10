// 涂鸦图片上传与回显：图片经 /wechat/avatar 通道传 OSS，画作出处存 hatch-action payload。
const { post, get } = require('./request');
const auth = require('./auth');
const { API_BASE_URL } = require('../config/api');

/**
 * 上传涂鸦图片到 OSS。
 * 复用头像上传通道 POST /wechat/avatar，接口返回通用 envelope { code, data, msg }。
 * @param {string} tempFilePath 本地临时文件路径
 * @returns {Promise<string>} OSS 图片 URL
 */
function uploadDoodleImage(tempFilePath) {
  return new Promise((resolve, reject) => {
    const session = auth.getSession();
    if (!session || !session.token) {
      reject({ userMessage: '登录状态已失效' });
      return;
    }
    wx.uploadFile({
      url: `${API_BASE_URL}/wechat/avatar`,
      filePath: tempFilePath,
      name: 'file',
      header: { Authorization: `Bearer ${session.token}` },
      success: (res) => {
        if (res.statusCode !== 200) {
          reject({ userMessage: '画作上传失败' });
          return;
        }
        try {
          const envelope = JSON.parse(res.data);
          if (envelope.code !== 0 || !envelope.data) {
            reject({ userMessage: envelope.msg || '画作上传失败' });
            return;
          }
          resolve(envelope.data);
        } catch (error) {
          reject({ userMessage: '画作上传失败' });
        }
      },
      fail: () => reject({ userMessage: '画作上传失败' })
    });
  });
}

/**
 * 查询某只宠物的最新涂鸦图片 URL。
 * 兼容 HatchActionVO 的两种字段命名（actionType / action_type）以及两种 payload 类型（字符串 / 对象）。
 * @param {string} petId 宠物 id
 * @returns {Promise<string>} 最新涂鸦 artUrl，无则返回空字符串
 */
async function getLatestDoodleArtUrl(petId) {
  const actions = await get(`/pet/${petId}/hatch-actions`);
  const doodles = (actions || []).filter((a) => (a.actionType || a.action_type) === 'DOODLE');
  if (!doodles.length) return '';
  const latest = doodles[doodles.length - 1];
  const payload = latest.payload;
  if (!payload) return '';
  const parsed = typeof payload === 'string' ? JSON.parse(payload) : payload;
  return parsed.artUrl || '';
}

module.exports = { uploadDoodleImage, getLatestDoodleArtUrl };
