const assert = require('assert');

const calls = [];
const requestPath = require.resolve('./request');
require.cache[requestPath] = {
  id: requestPath,
  filename: requestPath,
  loaded: true,
  exports: {
    post(url, data) {
      calls.push({ url, data });
      return Promise.resolve({ phone: '13800000000' });
    }
  }
};

const wechatApi = require('./wechat-api');

(async () => {
  const result = await wechatApi.bindPhone('phone-code');
  assert.deepStrictEqual(calls, [{
    url: '/wechat/bindPhone',
    data: { phoneCode: 'phone-code' }
  }]);
  assert.deepStrictEqual(result, { phone: '13800000000' });
  console.log('wechat-api.test.js: ALL PASS');
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
