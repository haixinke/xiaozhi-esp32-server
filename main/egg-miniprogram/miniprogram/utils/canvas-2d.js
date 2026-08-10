function createLayer(page, selector) {
  return new Promise(resolve => {
    wx.createSelectorQuery().in(page).select(selector).fields({ node: true, size: true, rect: true }).exec(result => {
      const target = result && result[0];
      if (!target || !target.node || !target.width || !target.height) {
        resolve(null);
        return;
      }
      const windowInfo = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
      const pixelRatio = Number(windowInfo.pixelRatio || 1);
      const canvas = target.node;
      const context = canvas.getContext('2d');
      canvas.width = Math.round(target.width * pixelRatio);
      canvas.height = Math.round(target.height * pixelRatio);
      context.scale(pixelRatio, pixelRatio);
      resolve({
        canvas,
        context,
        width: target.width,
        height: target.height,
        left: target.left || 0,
        top: target.top || 0
      });
    });
  });
}

function loadImage(layer, source) {
  return new Promise(resolve => {
    if (!layer || !layer.canvas || !layer.canvas.createImage) {
      resolve(null);
      return;
    }
    const image = layer.canvas.createImage();
    image.onload = () => resolve(image);
    image.onerror = () => resolve(null);
    image.src = source;
  });
}

function exportImage(layer) {
  return new Promise(resolve => {
    if (!layer || !layer.canvas || !wx.canvasToTempFilePath) {
      resolve('');
      return;
    }
    wx.canvasToTempFilePath({
      canvas: layer.canvas,
      x: 0,
      y: 0,
      width: layer.width,
      height: layer.height,
      destWidth: layer.canvas.width,
      destHeight: layer.canvas.height,
      fileType: 'png',
      quality: 1,
      success: result => resolve(result.tempFilePath || ''),
      fail: () => resolve('')
    });
  });
}

module.exports = { createLayer, loadImage, exportImage };
