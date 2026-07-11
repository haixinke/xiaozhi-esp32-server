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

module.exports = {
  adoptPet,
  submitHatchAction,
  listHatchActions,
  hatchPet,
  getPet,
  listPets,
  updateNickname
};
