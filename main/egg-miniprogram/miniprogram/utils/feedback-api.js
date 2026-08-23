const { get, post } = require('./request');

// 诉求类型字典类型编码，与后端 sys_dict_type 对应
const FEEDBACK_TYPE_DICT = 'EGG_FEEDBACK_TYPE';

// 从后端字典拉取诉求类型，SysDictDataItem: [{ name, key }] -> [{ label, value }]
function listFeedbackTypes() {
  return get(`/admin/dict/data/type/${FEEDBACK_TYPE_DICT}`).then((items) => {
    if (!Array.isArray(items)) return [];
    return items
      .filter((item) => item && item.key && item.name)
      .map((item) => ({ label: item.name, value: item.key }));
  });
}

// 提交反馈，成功返回 { receiptNumber, createDate }
function submitFeedback(payload) {
  return post('/feedback', {
    type: payload.type,
    content: payload.content,
    consent: payload.consent === true
  });
}

module.exports = { listFeedbackTypes, submitFeedback };
