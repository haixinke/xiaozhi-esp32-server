// AI生图：照片上传（scene=ai_gen）、任务创建与轮询查询。
// 任务为异步链路（照片审核 → 生成 → 结果审核），创建后按 taskId 轮询状态。
const { post, get } = require('./request');
const auth = require('./auth');
const { API_BASE_URL } = require('../config/api');

/**
 * 上传用户照片到 OSS。
 * 调用通用上传接口 POST /upload/image（scene=ai_gen），接口返回通用 envelope { code, data, msg }。
 * @param {string} tempFilePath 本地临时文件路径
 * @returns {Promise<string>} OSS 图片 URL
 */
function uploadGenPhoto(tempFilePath) {
  return new Promise((resolve, reject) => {
    const session = auth.getSession();
    if (!session || !session.token) {
      reject({ userMessage: '登录状态已失效' });
      return;
    }
    wx.uploadFile({
      url: `${API_BASE_URL}/upload/image`,
      filePath: tempFilePath,
      name: 'file',
      // 场景码对应后端 UploadScene.AI_GEN 枚举常量名，必须下划线（连字符解析失败会报“不支持的上传场景”）
      formData: { scene: 'ai_gen' },
      header: { Authorization: `Bearer ${session.token}` },
      success: (res) => {
        if (res.statusCode !== 200) {
          reject({ userMessage: '照片上传失败' });
          return;
        }
        try {
          const envelope = JSON.parse(res.data);
          if (envelope.code !== 0 || !envelope.data) {
            reject({ userMessage: envelope.msg || '照片上传失败' });
            return;
          }
          resolve(envelope.data);
        } catch (error) {
          reject({ userMessage: '照片上传失败' });
        }
      },
      fail: () => reject({ userMessage: '照片上传失败' })
    });
  });
}

/**
 * 创建 AI 生图任务。
 * @param {string} photoUrl 用户照片 OSS URL（uploadGenPhoto 返回值）
 * @returns {Promise<{taskId: string, status: string}>}
 */
function createImageGenTask(photoUrl) {
  return post('/pet/image-gen/tasks', { photoUrl });
}

/**
 * 查询任务状态。
 * @param {string} taskId
 * @returns {Promise<{taskId: string, status: string, resultUrl?: string, failReason?: string, caption?: string}>}
 */
function getImageGenTask(taskId) {
  return get(`/pet/image-gen/tasks/${taskId}`);
}

module.exports = { uploadGenPhoto, createImageGenTask, getImageGenTask };
