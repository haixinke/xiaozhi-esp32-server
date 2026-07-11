const { post } = require('./request');

function bindPhone(phoneCode) {
  return post('/wechat/bindPhone', { phoneCode });
}

module.exports = { bindPhone };
