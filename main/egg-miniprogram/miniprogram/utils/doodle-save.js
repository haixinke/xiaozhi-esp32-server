const doodleApi = require('./doodle-api');
const petStore = require('./pet-store');

const DEFAULT_ERROR_MESSAGE = '画作没有保存好，请再试一次';

function errorMessage(error) {
  return (error && (error.userMessage || error.message)) || DEFAULT_ERROR_MESSAGE;
}

async function saveArtwork({ petId, operations, exportArtwork } = {}) {
  let artUrl = '';

  try {
    if (!petId || !Array.isArray(operations)) {
      return { ok: false, message: DEFAULT_ERROR_MESSAGE };
    }
    const empty = operations.length === 0;
    if (!empty) {
      const tempFilePath = await exportArtwork();
      artUrl = await doodleApi.uploadDoodleImage(tempFilePath);
      if (typeof artUrl !== 'string' || !artUrl.trim()) {
        return { ok: false, message: DEFAULT_ERROR_MESSAGE };
      }
    }

    const result = await petStore.saveDoodle(artUrl, petId);
    if (!result || !result.ok) {
      return { ok: false, message: (result && result.message) || DEFAULT_ERROR_MESSAGE };
    }

    if (!petStore.saveDoodleShell(petId, operations)) {
      return { ok: false, message: '画作已上传，但本地编辑记录保存失败，请重试' };
    }
    return { ok: true, petId: String(petId), artUrl, operations, empty };
  } catch (error) {
    return { ok: false, message: errorMessage(error) };
  }
}

module.exports = { saveArtwork };
