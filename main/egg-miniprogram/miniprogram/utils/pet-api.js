const { get, post, put } = require('./request');

// 后端 PetVO 字段：id, userId, deviceId, nickname, birthDate, bazi, wuxing, zodiac,
// mbti, personality, personalityBrief, todayMood, todayMoodDate, todayMoodSentence,
// hatchStatus(EGG/HATCHED), hatchStartTime, expectedHatchTime, hatchedAt, acceleratedMinutes,
// avatarUrl, prototype, gender, bloodType, createDate
function adoptPet(inviteCode) {
  return post('/pet/adopt', { inviteCode });
}

// HatchActionResultVO: { addedMinutes, alreadyDone, readyToHatch, pet }
function submitHatchAction(petId, type, payload) {
  return post(`/pet/${petId}/hatch-action`, { type, payload });
}

// HatchActionVO[]: [{ id, actionType, payload, actionDate, acceleratedMinutes, createDate }]
function listHatchActions(petId) {
  return get(`/pet/${petId}/hatch-actions`);
}

function hatchPet(petId) {
  return post(`/pet/${petId}/hatch`);
}

function getPet(petId) {
  return get(`/pet/${petId}`);
}

function listPets() {
  return get('/pet/list');
}

function updateNickname(petId, nickname) {
  return put('/pet/update', { id: petId, nickname });
}

// 更换场景图：后端按原型随机生成新 URL 并持久化，返回更新后的 PetVO
function changeScene(petId) {
  return put(`/pet/${petId}/scene`);
}

function listChatHistory(agentId, deviceId, page, limit) {
  return get('/agent/chat-history/list', {
    agentId,
    macAddress: deviceId,
    page: page || 1,
    limit: limit || 4
  });
}

module.exports = {
  adoptPet,
  submitHatchAction,
  listHatchActions,
  hatchPet,
  getPet,
  listPets,
  updateNickname,
  changeScene,
  listChatHistory
};
