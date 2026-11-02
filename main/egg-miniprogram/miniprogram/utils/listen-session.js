const listenApi = require('./listen-api');

const FEEDBACK_PENDING_TEXT = '正在听你说…';
const FEEDBACK_FALLBACK_TEXT = '谢谢你愿意说给我听。';
const FEEDBACK_BUDGET_MS = 5000;
const FEEDBACK_INTERVAL_MS = 500;
const defaultClock = {
  now: () => Date.now(),
  setTimeout: (callback, delay) => setTimeout(callback, delay),
  clearTimeout: (id) => clearTimeout(id)
};
const clone = (value) => JSON.parse(JSON.stringify(value));

function createListenSession({ api = listenApi, clock = defaultClock, onChange = () => {}, onAward = () => {} } = {}) {
  let active = false;
  let disposed = false;
  let generation = 0;
  let groupId = null;
  let questions = [];
  let answers = {};
  let waiting = false;
  let remaining = FEEDBACK_BUDGET_MS;
  let startedAt = null;
  let deadlineTimer = null;
  let pollTimer = null;
  let feedbackInFlight = false;
  const initialState = () => ({
    view: 'loading', navTitle: '听你说', groupName: '', question: null,
    questionNumber: 1, questionTotal: 0, progress: 0, selected: '', supplement: '',
    noteOpen: false, error: '', busy: false, awardedStars: 0,
    feedbackText: FEEDBACK_PENDING_TEXT, endTitle: '', endText: '', scrollTarget: ''
  });
  let state = initialState();

  const getSnapshot = () => clone(state);
  const valid = (requestGeneration) => !disposed && generation === requestGeneration;
  function notify() { if (active && !disposed) onChange(getSnapshot()); }
  function update(changes) { state = { ...state, ...changes }; notify(); }
  function stopFeedbackClock() {
    if (startedAt !== null) remaining = Math.max(0, remaining - (clock.now() - startedAt));
    startedAt = null;
    clock.clearTimeout(deadlineTimer);
    clock.clearTimeout(pollTimer);
    deadlineTimer = null;
    pollTimer = null;
  }
  function finishFeedback(text) {
    stopFeedbackClock();
    waiting = false;
    update({ feedbackText: text });
  }
  function timeLeft() {
    return remaining - (startedAt === null ? 0 : clock.now() - startedAt);
  }
  async function pollFeedback() {
    if (!active || disposed || !waiting || feedbackInFlight) return;
    const requestGeneration = generation;
    feedbackInFlight = true;
    let response;
    try { response = await api.getFeedback(groupId); } catch (error) { response = null; }
    if (!valid(requestGeneration)) return;
    feedbackInFlight = false;
    if (!waiting) return;
    if (timeLeft() <= 0) { finishFeedback(FEEDBACK_FALLBACK_TEXT); return; }
    const text = response && typeof response.feedback === 'string' ? response.feedback.trim() : '';
    if (text) { finishFeedback(text); return; }
    if (active) {
      pollTimer = clock.setTimeout(() => { pollTimer = null; pollFeedback(); }, FEEDBACK_INTERVAL_MS);
    }
  }
  function resumeFeedback() {
    if (!waiting || !active || disposed) return;
    if (timeLeft() <= 0) { finishFeedback(FEEDBACK_FALLBACK_TEXT); return; }
    startedAt = clock.now();
    const requestGeneration = generation;
    deadlineTimer = clock.setTimeout(() => {
      if (valid(requestGeneration) && active && waiting) finishFeedback(FEEDBACK_FALLBACK_TEXT);
    }, remaining);
    pollFeedback();
  }
  function renderQuestion(index) {
    const question = questions[index];
    const answer = answers[question.id] || {};
    update({
      view: 'question', question, questionNumber: index + 1, questionTotal: questions.length,
      progress: Math.round(index / questions.length * 100), selected: answer.optionKey || '',
      supplement: answer.customText || '', noteOpen: Boolean(answer.customText || answer.optionKey === 'E'),
      scrollTarget: '', error: '', busy: false
    });
  }
  function saveCurrent(changes) {
    update(changes);
    answers = { ...answers, [state.question.id]: { optionKey: state.selected, customText: state.supplement } };
  }
  async function drawGroup() {
    if (!active || disposed || state.busy) return;
    const requestGeneration = ++generation;
    stopFeedbackClock();
    waiting = false;
    feedbackInFlight = false;
    questions = [];
    answers = {};
    groupId = null;
    update({ view: 'loading', navTitle: '听你说', busy: true, error: '', question: null,
      selected: '', supplement: '', noteOpen: false, groupName: '', awardedStars: 0,
      feedbackText: FEEDBACK_PENDING_TEXT, endTitle: '', endText: '', scrollTarget: '' });
    try {
      const group = await api.draw();
      if (!valid(requestGeneration)) return;
      if (!group || !Array.isArray(group.questions) || !group.questions.length) {
        update({ view: 'end', busy: false, endTitle: '这些小事都听你说过啦', endText: '等有新的小事，我们再慢慢聊。' });
        return;
      }
      groupId = group.groupId;
      questions = clone(group.questions);
      state = { ...state, groupName: group.groupName || '' };
      renderQuestion(0);
    } catch (error) {
      if (!valid(requestGeneration)) return;
      if (error && (error.code === 10382 || error.code === 10384)) {
        const allAnswered = error.code === 10382;
        update({ view: 'end', busy: false, navTitle: '明天再来',
          endTitle: allAnswered ? '这些小事都听你说过啦' : '明天再来',
          endText: allAnswered ? '等有新的小事，我们再慢慢聊。' : '今天先聊到这里。' });
      } else {
        update({ view: 'blocked', busy: false, error: (error && error.userMessage) || '这个话题暂时没有新的问题。' });
      }
    }
  }
  function canAnswer() { return active && !disposed && state.view === 'question' && !state.busy; }
  function select(key) {
    if (!canAnswer() || !state.question.options.some((option) => option.key === key)) return;
    saveCurrent({ selected: key, error: '', noteOpen: state.noteOpen || key === 'E',
      scrollTarget: key === 'E' ? 'listen-supplement' : '' });
  }
  function supplement(text) {
    if (!canAnswer()) return;
    saveCurrent({ supplement: String(text || '').slice(0, 200) });
  }
  function addNote() {
    if (canAnswer()) update({ noteOpen: true, scrollTarget: 'listen-supplement' });
  }
  async function next() {
    if (!canAnswer() || !state.selected) return;
    const index = state.questionNumber - 1;
    if (index < questions.length - 1) { renderQuestion(index + 1); return; }
    const requestGeneration = generation;
    update({ busy: true, error: '' });
    const payload = questions.map((question) => {
      const answer = answers[question.id] || {};
      return { questionId: question.id, optionKey: answer.optionKey, customText: answer.customText || undefined };
    });
    try {
      const result = await api.submitAnswers(groupId, payload);
      if (!valid(requestGeneration)) return;
      const amount = Number(result && result.awardedStars);
      const stars = Number.isFinite(amount) && amount > 0 ? amount : 0;
      waiting = true;
      remaining = FEEDBACK_BUDGET_MS;
      update({ view: 'feedback', busy: false, error: '', awardedStars: stars, feedbackText: FEEDBACK_PENDING_TEXT });
      if (active && stars > 0) onAward(stars);
      resumeFeedback();
    } catch (error) {
      if (valid(requestGeneration)) update({ busy: false, error: (error && error.userMessage) || '刚才没有保存好，请再试一次。' });
    }
  }
  function pause() {
    if (disposed || !active) return;
    active = false;
    stopFeedbackClock();
    if (waiting && remaining <= 0) finishFeedback(FEEDBACK_FALLBACK_TEXT);
  }
  function resume() {
    if (disposed || active) return;
    active = true;
    notify();
    resumeFeedback();
  }
  function dispose() {
    if (disposed) return;
    active = false;
    disposed = true;
    generation += 1;
    stopFeedbackClock();
    waiting = false;
    questions = [];
    answers = {};
    groupId = null;
    state = initialState();
  }
  return { getSnapshot, drawGroup, select, supplement, addNote, next, pause, resume, dispose };
}

module.exports = { createListenSession, FEEDBACK_PENDING_TEXT, FEEDBACK_FALLBACK_TEXT };
