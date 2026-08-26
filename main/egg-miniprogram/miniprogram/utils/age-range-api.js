const { get, put } = require('./request');

// 年龄区间字典类型编码，与后端 sys_dict_type 对应
const AGE_RANGE_DICT = 'EGG_AGE_RANGE';

// 从后端字典拉取年龄区间选项，SysDictDataItem: [{ name, key }] -> [{ label, value }]
function listAgeRanges() {
  return get(`/admin/dict/data/type/${AGE_RANGE_DICT}`).then((items) => {
    if (!Array.isArray(items)) return [];
    return items
      .filter((item) => item && item.key && item.name)
      .map((item) => ({ label: item.name, value: item.key }));
  });
}

// 读取用户资料（含 ageRange 字段）
function getProfile() {
  return get('/wechat/profile');
}

// 保存年龄区间，复用资料更新接口的部分字段更新能力
function saveAgeRange(ageRange) {
  return put('/wechat/profile', { ageRange });
}

module.exports = { listAgeRanges, getProfile, saveAgeRange };
