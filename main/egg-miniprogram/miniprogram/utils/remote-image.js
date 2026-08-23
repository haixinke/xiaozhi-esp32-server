// 远程图统一下载通道：先 wx.downloadFile 落本地临时路径，再交给调用方喂 <image>。
// 背景：已证实 <image> 内置加载器对部分 OSS URL 会挂起（既不 load 也不 error），
// 而同一 URL downloadFile 正常；本地临时路径不存在该问题。
const TIMEOUT_MS = 15000;

// 会话级缓存：URL -> 本地临时路径（临时文件随进程生命周期，无需主动清理）
const cache = Object.create(null);
// URL -> 等待回调队列：同 URL 并发请求去重，只发一次下载
const inflight = Object.create(null);

/**
 * 下载远程图到本地临时路径。
 * @param {string} url 远程图 URL；空串直接回调 null
 * @param {(localPath: string|null) => void} onDone 成功回调本地路径；失败回调 null；
 *        无 wx 环境（单元测试）退化回调原 URL 直连
 */
function loadRemoteImage(url, onDone) {
  if (!url) {
    onDone(null);
    return;
  }
  const cached = cache[url];
  if (cached) {
    onDone(cached);
    return;
  }
  if (typeof wx === 'undefined' || !wx.downloadFile) {
    onDone(url);
    return;
  }
  if (inflight[url]) {
    inflight[url].push(onDone);
    return;
  }
  const waiters = (inflight[url] = [onDone]);
  const settle = (result) => {
    delete inflight[url];
    // 只缓存成功结果，失败允许下次重试
    if (result) cache[url] = result;
    waiters.forEach((cb) => cb(result));
  };
  wx.downloadFile({
    url,
    timeout: TIMEOUT_MS,
    success: (res) => {
      if (res.statusCode === 200 && res.tempFilePath) {
        settle(res.tempFilePath);
        return;
      }
      settle(null);
    },
    fail: () => settle(null)
  });
}

module.exports = { loadRemoteImage };
