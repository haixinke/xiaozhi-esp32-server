// 两种宠物图标使用固定线上地址，未知原型沿用玉兔兜底。
const FISH_URL = 'https://oss.eggbabe.com/default-ip/fish/fish-icon.png';
const RABBIT_URL = 'https://oss.eggbabe.com/default-ip/rabbit/rabbit-icon.png';
const TIMEOUT_MS = 15000;
let sessionCache = {};
let inflight = {};

/** 根据宠物原型返回线上图标地址，兼容后端原型编码。 */
function getPetIconUrl(prototype) {
  return prototype === '锦鲤' || prototype === 'KOI' ? FISH_URL : RABBIT_URL;
}

/** 获取平台文件系统；不支持时仍可使用本次下载的临时图片。 */
function fileSystem() {
  try { return wx.getFileSystemManager ? wx.getFileSystemManager() : null; }
  catch (error) { return null; }
}

/** 每次命中均检查文件，防止系统清理后仍返回失效的缓存路径。 */
function exists(fs, path) {
  if (typeof path !== 'string' || !path) return false;
  if (!fs || !fs.accessSync) return true;
  try { fs.accessSync(path); return true; }
  catch (error) { return false; }
}

/** 从持久元数据恢复缓存；storage 故障只影响缓存，不阻断图片展示。 */
function readSaved(fs, key, url) {
  try {
    const saved = wx.getStorageSync(key);
    return saved && saved.url === url && exists(fs, saved.path) ? saved.path : null;
  } catch (error) { return null; }
}

/** 保存到持久文件区；保存或元数据写入失败时保留仍可使用的本地路径。 */
function saveImage(fs, key, url, tempFilePath, done) {
  if (!fs || !fs.saveFile) {
    done(tempFilePath);
    return;
  }
  try {
    fs.saveFile({
      tempFilePath,
      success: (result) => {
        const path = result.savedFilePath || tempFilePath;
        if (result.savedFilePath) {
          try { wx.setStorageSync(key, { url, path }); }
          catch (error) { /* 文件已保存，会话缓存仍可复用，不阻断展示。 */ }
        }
        done(path);
      },
      fail: () => done(tempFilePath)
    });
  } catch (error) { done(tempFilePath); }
}

/** 下载并持久缓存宠物图标；并发去重，失败回调 null 且允许下次重试。 */
function loadPetIcon(prototype, onDone) {
  const url = getPetIconUrl(prototype);
  // 单元测试等无平台环境只降级原地址，避免把远程地址当成本地缓存。
  if (typeof wx === 'undefined' || !wx.downloadFile) {
    onDone(url);
    return;
  }
  const fs = fileSystem();
  const key = url === FISH_URL ? 'pet_icon_fish_v1' : 'pet_icon_rabbit_v1';
  const cached = exists(fs, sessionCache[url]) ? sessionCache[url] : readSaved(fs, key, url);
  if (cached) {
    onDone(cached);
    return;
  }
  if (inflight[url]) {
    inflight = { ...inflight, [url]: [...inflight[url], onDone] };
    return;
  }
  inflight = { ...inflight, [url]: [onDone] };
  const settle = (path) => {
    const callbacks = inflight[url];
    inflight = { ...inflight, [url]: null };
    // 只缓存成功路径，下载失败不会抑制后续重试。
    if (path) sessionCache = { ...sessionCache, [url]: path };
    callbacks.forEach((callback) => callback(path));
  };
  wx.downloadFile({
    url,
    timeout: TIMEOUT_MS,
    success: (result) => {
      if (result.statusCode !== 200 || !result.tempFilePath) {
        settle(null);
        return;
      }
      saveImage(fs, key, url, result.tempFilePath, settle);
    },
    fail: () => settle(null)
  });
}

module.exports = { getPetIconUrl, loadPetIcon };
