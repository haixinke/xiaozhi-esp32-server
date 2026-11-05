// 一起画业务接口复用统一鉴权，不开放客户端指定金额的结算能力。
const request = require('../../utils/request');
module.exports = {
  config: petId => request.get('/app/drawing/config', { petId }),
  start: body => request.post('/app/drawing/start', body),
  complete: sessionId => request.post(`/app/drawing/${encodeURIComponent(sessionId)}/complete`)
};
