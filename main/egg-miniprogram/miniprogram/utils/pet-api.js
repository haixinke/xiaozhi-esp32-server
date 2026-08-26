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

function listChatHistory(agentId, deviceId, page, limit) {
  return get('/agent/chat-history/list', {
    agentId,
    macAddress: deviceId,
    page: page || 1,
    limit: limit || 20
  });
}

// 每日用户聊天依赖提醒状态：{ todayCount, minor, chatLimited }
// todayCount 为当日用户发送消息数（chat_type=1，Asia/Shanghai 日界）；
// chatLimited=true 表示未成年人（≤14 周岁）当日已超阈值，前端需弹窗并退出聊天页。
function getDailyUserChatCount() {
  return get('/agent/chat-history/daily-user-count');
}

module.exports = {
  adoptPet,
  submitHatchAction,
  listHatchActions,
  hatchPet,
  getPet,
  listPets,
  updateNickname,
  listChatHistory,
  getDailyUserChatCount
};
