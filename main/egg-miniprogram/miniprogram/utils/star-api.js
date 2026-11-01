const { get } = require('./request');

// 星星罐（用户级虚拟货币账户，见 GLOSSARY「星星罐」）
// StarBalanceVO: { balance: Long }，余额为整数星星、永不过期、无负值
function getBalance() {
  return get('/star/balance');
}

module.exports = {
  getBalance
};
