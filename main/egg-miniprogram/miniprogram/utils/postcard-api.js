// 明信片 API：公开分享查看（免授权，好友点开分享卡片即达此页）。
// 后端 PetPostcardPublicVO：{ petName, prototype, imageUrl, caption, createDate }
const { get } = require('./request');

/**
 * 明信片公开查看（匿名接口，无 Bearer token 也能请求成功）。
 * @param {string} shareId 对外分享随机串（不可枚举）
 * @returns {Promise<{petName: string, prototype: string, imageUrl: string, caption: string, createDate: string}>}
 */
function getPublicPostcard(shareId) {
  return get(`/pet/postcard/public/${encodeURIComponent(shareId)}`, null, { anonymous: true });
}

module.exports = { getPublicPostcard };
