const { get } = require('./request');

// 故事引擎当前状态 PetStoryStateVO：petPrototype, bigSceneId, bigSceneName,
// smallSceneId, smallSceneName, actionId, actionName, actionImageId, weightPeriod,
// imageTimeOfDay, imageUrl, tagImageUrl, caption, durationHours, startedAt, expectedEndAt
// 未破壳或原型无激活状态时后端返回 null
function getStoryState(petId) {
  return get(`/pet/${petId}/story-state`);
}

module.exports = {
  getStoryState
};
