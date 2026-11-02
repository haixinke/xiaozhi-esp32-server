const { get, post } = require('./request');

// 听你说（见 GLOSSARY「听你说题库」「答题」）
// 业务码约定（后端 HTTP 200 + envelope.code 区分）：
//   10382 LISTEN_ALL_ANSWERED        所有题目分组都已答过
//   10384 LISTEN_DAILY_LIMIT_REACHED 今日答题组数已达上限（默认每日 5 组）
// request.js 对 code!==0 一律 reject 为 business 错误，错误对象上带 code 字段，页面据此分流文案。

// 随机抽取一组当前用户未答过的题目分组。
// 成功返回 ListenDrawVO: { groupId, groupName, questions:[{ id, text, options:[{key,text}] }] }；
// 所有组都答过/额度用尽时 reject business 错误（code=10382/10384）
function draw() {
  return get('/listen/draw');
}

// 整组提交答案（幂等）。返回 ListenSubmitResultVO:
//   { awardedStars, rewardStatus }
// awardedStars 为本次实际新增星星数（当日已领奖或无奖励时为 0）；
// rewardStatus: NEW_REWARD 本次新发奖 / ALREADY_REWARDED 当日已领取 / NO_REWARD 无需星星变动
// answers: [{ questionId, optionKey, customText? }]
function submitAnswers(groupId, answers) {
  return post('/listen/answers', { groupId, answers });
}

// 查询某组的个性化反馈文案（LLM 异步生成，5 分钟内可取）。
// 返回 ListenFeedbackVO: { feedback }；未就绪/已过期/生成失败时 feedback 为 null，
// 前端应回落到固定文案。
function getFeedback(groupId) {
  return get('/listen/feedback', { groupId });
}

module.exports = {
  draw,
  submitAnswers,
  getFeedback
};
