const petApi = require('./pet-api');
const PET_KEY = 'eggbaby_mvp_pet_v1';
const USER_KEY = 'eggbaby_mvp_user_v1';
const IDENTITY_KEY = 'eggbaby_mvp_identity_v1';
const ACTIVE_PET_KEY = 'eggbaby_active_pet_v1';
// 涂鸦操作序列(shell)本地缓存：仅存操作 JSON，用于重开编辑器恢复画布继续编辑；云端只存合成 PNG
const DOODLE_SHELL_KEY = 'eggbaby_doodle_shell_v1';

const DAY = 24 * 60 * 60 * 1000;
const HATCH_TOTAL_MINUTES = 7 * 24 * 60;

const STATUS_LINES = {
  egg: {
    开心: ['蛋壳里传来轻轻的回应。', '它把你的声音藏进了壳里。'],
    平静: ['蛋壳里很安静，但很暖。', '它今天睡得很踏实。'],
    想念: ['它好像等了你很久。', '它把想你藏在壳里。'],
    兴奋: ['蛋壳里的动静变多了。', '裂纹好像又亮了一点。'],
    低落: ['它今天安静了很久。', '蛋壳里的声音变小了。']
  },
  pet: {
    开心: ['它把快乐摆在了脸上。', '它好像一直在等你来。'],
    平静: ['它今天把日子过得很慢。', '它安静地待在你身边。'],
    想念: ['它偷偷练习了怎么叫你。', '它把想你写进了今天。'],
    兴奋: ['它好像准备了一个小秘密。', '它今天比平时更坐不住。'],
    低落: ['它今天有一点点没精神。', '它安静了很久，像是在等你。']
  }
};

function todayKey(now) {
  const date = now ? new Date(now) : new Date();
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function read(key) {
  try { return wx.getStorageSync(key) || null; } catch (error) { return null; }
}

function write(key, value) {
  try { wx.setStorageSync(key, value); } catch (error) {}
  return value;
}

function getUser() {
  return read(USER_KEY);
}

function saveUser(user) {
  if (user && user.id) write(IDENTITY_KEY, user.id);
  return write(USER_KEY, user);
}

/**
 * 把后端用户资料的 nickname/avatarUrl 同步到本地 USER_KEY 缓存。
 * @param {Object} profile GET /wechat/profile 响应
 */
function syncUserProfile(profile) {
  if (!profile) return;
  const user = getUser() || {};
  if (profile.nickname !== undefined) user.nickname = profile.nickname;
  if (profile.avatarUrl !== undefined) user.avatarUrl = profile.avatarUrl;
  saveUser(user);
}

function getIdentityId() {
  return read(IDENTITY_KEY);
}

// 账号级缓存 key，登出/换账号时必须全部清除，防止上一个账号的数据泄漏给下一个账号。
// 后两个 key 分别由 pages/profile 与 pages/deregister 写入，这里统一纳入清理。
const ACCOUNT_KEYS = [
  PET_KEY, USER_KEY, IDENTITY_KEY, ACTIVE_PET_KEY,
  DOODLE_SHELL_KEY,
  'eggbaby_profile_v1',
  'eggbaby_deregister_request_v1'
];

function clearAccountData() {
  ACCOUNT_KEYS.forEach((key) => {
    try { wx.removeStorageSync(key); } catch (error) {}
  });
}

function clearUser() {
  try { wx.removeStorageSync(USER_KEY); } catch (error) {}
}

function getPet() {
  const pet = read(PET_KEY);
  const user = getUser();
  if (pet && pet.ownerId && user && pet.ownerId !== user.id) return null;
  return pet;
}

function savePet(pet) {
  return write(PET_KEY, pet);
}

function isBound() {
  return !!getPet();
}

function toTimestamp(value) {
  if (!value) return null;
  const normalized = typeof value === 'string' ? value.trim().replace(' ', 'T') : value;
  const ms = new Date(normalized).getTime();
  return Number.isFinite(ms) ? ms : null;
}

// 把后端 PetVO 映射成本地 pet 形状并缓存。
// 非 demo 修炼动作已接后端：后端 PetVO 不返回前端独占字段(shell/tasks/preferences/messages/inviteCodes/dailyStatus)，
// 故这些字段从已缓存 pet 合并(首次领养无缓存时回退默认值)，避免每次 action 调用清空本地状态。
// 后端派生字段(id/hatchStatus/acceleratedMinutes/时间戳/身份字段/todayMood/deviceId)以 vo 为唯一事实源。
// stage/进度/倒计时由 getStage/getCountdown 从 hatchStatus/hatchAt/acceleratedMinutes 派生。
function savePetFromVO(vo) {
  if (!vo || !vo.id) return null;
  const user = getUser();
  const existing = read(PET_KEY);
  const accelerated = vo.acceleratedMinutes || 0;
  const progress = Math.max(0, Math.min(100, Math.round((accelerated / HATCH_TOTAL_MINUTES) * 100)));
  const hatchStartTime = toTimestamp(vo.hatchStartTime);
  const hatchAt = toTimestamp(vo.expectedHatchTime)
    || (hatchStartTime ? hatchStartTime + 7 * DAY : Date.now() + 7 * DAY);
  const createdAt = toTimestamp(vo.createDate) || hatchStartTime || Date.now();
  const isHatched = vo.hatchStatus === 'HATCHED';
  const pet = {
    id: vo.id,
    ownerId: (user && user.id) || null,
    prototype: vo.prototype || '玉兔',
    name: vo.nickname || (existing ? existing.name : '') || '',
    createdAt,
    hatchAt,
    progress,
    stage: 'waiting',
    // 保留本地 recordTouch 记录的互动时间，避免每次后端刷新都重置回 createdAt，
    // 否则老宠物走本地 fallback 心情时 inactiveDays 恒为巨大值 → 永远"低落"
    lastInteractionAt: Math.max((existing && existing.lastInteractionAt) || 0, createdAt),
    tasks: existing && existing.tasks !== undefined ? existing.tasks : { nicknameDone: false, cuddleDate: '', wishDate: '', lessonDate: '', doodleDone: false },
    preferences: existing && existing.preferences !== undefined ? existing.preferences : { wishes: [], lessons: [] },
    shell: existing && existing.shell !== undefined ? existing.shell : { color: '#EDE78E', colorName: '奶油白', pattern: '星星' },
    dailyStatus: existing && existing.dailyStatus !== undefined ? existing.dailyStatus : null,
    inviteCodes: existing && existing.inviteCodes !== undefined ? existing.inviteCodes : [],
    messages: existing && existing.messages !== undefined ? existing.messages : [],
    todayMood: vo.todayMood || '',
    todayMoodSentence: vo.todayMoodSentence || '',
    todayMoodDate: vo.todayMoodDate || '',
    hatchStatus: vo.hatchStatus || 'EGG',
    acceleratedMinutes: accelerated,
    hatchStartTime: toTimestamp(vo.hatchStartTime),
    expectedHatchTime: toTimestamp(vo.expectedHatchTime),
    hatchedAt: toTimestamp(vo.hatchedAt),
    deviceId: vo.deviceId || null,
    agentId: vo.agentId || null,
    bazi: vo.bazi || '',
    wuxing: vo.wuxing || '',
    zodiac: translateZodiac(vo.zodiac) || '',
    mbti: vo.mbti || '',
    personality: vo.personality || '',
    personalityBrief: vo.personalityBrief || '',
    gender: vo.gender || '',
    bloodType: vo.bloodType || '',
    avatarUrl: vo.avatarUrl || '',
    collectionCards: vo.collectionCards || [],
    sceneUrl: vo.sceneUrl || ''
  };
  if (existing && Array.isArray(existing._hatchActions)) pet._hatchActions = existing._hatchActions;
  savePet(pet);
  setActivePetId(pet.id);
  return pet;
}

function getActivePetId() {
  return read(ACTIVE_PET_KEY);
}

function setActivePetId(id) {
  return write(ACTIVE_PET_KEY, id);
}

function mockCodeError(code) {
  const value = code.toUpperCase();
  const errors = {
    INVALID: '激活码无效，请检查后重试',
    USED: '该激活码已被使用',
    FULL: '该激活码名额已满',
    PAUSED: '该激活码暂不可用'
  };
  return errors[value] || '';
}

function createInviteCode(seed) {
  // 与后端 ai_invite_code 单码配额模型对齐：个人仅 1 个邀请码，有总配额/已用/剩余。
  // 此处为 mock 演示值（1 已用 / 5 配额），真实数据由 GET /invite/mine 返回。
  const suffix = String(seed).slice(-4);
  const quota = 5;
  const usedCount = 1;
  return {
    code: `EGG-${suffix}-X`,
    quota,
    usedCount,
    remaining: quota - usedCount,
    status: 1
  };
}

function bindPet(code, now) {
  if (isBound()) {
    return { ok: false, reason: 'BOUND', message: '当前版本一个账号只能绑定 1 只蛋宝宝，本次激活码未被消耗' };
  }
  const normalized = String(code || '').trim().toUpperCase();
  if (!normalized) return { ok: false, reason: 'EMPTY', message: '请输入激活码' };
  const error = mockCodeError(normalized);
  if (error) return { ok: false, reason: normalized, message: error };

  const createdAt = now || Date.now();
  const prototype = normalized.includes('KOI') ? '锦鲤' : '玉兔';
  const hatchAt = normalized === 'HATCH-NOW' ? createdAt : createdAt + 7 * DAY;
  const id = `egg-${createdAt}`;
  const pet = {
    id,
    ownerId: (getUser() && getUser().id) || '',
    prototype,
    name: '',
    createdAt,
    hatchAt,
    progress: 0,
    stage: 'waiting',
    lastInteractionAt: createdAt,
    tasks: {
      nicknameDone: false,
      cuddleDate: '',
      wishDate: '',
      lessonDate: '',
      doodleDone: false
    },
    preferences: { wishes: [], lessons: [] },
    shell: { color: '#EDE78E', colorName: '奶油白', pattern: '星星' },
    dailyStatus: null,
    collectionCards: [],
    inviteCode: createInviteCode(createdAt),
    messages: []
  };
  savePet(pet);
  return { ok: true, pet };
}

function addProgress(pet, amount) {
  pet.progress = Math.min(100, pet.progress + amount);
  pet.stage = pet.progress > 0 ? 'hatching' : 'waiting';
  return pet;
}

async function updateNickname(name) {
  const pet = getPet();
  if (!pet) return { ok: false, message: '还没有蛋宝宝' };
  const value = String(name || '').trim();
  if (!value) return { ok: false, message: '昵称不能为空' };
  if (Array.from(value).length > 10) return { ok: false, message: '昵称最多 10 个字符' };
  if (['违法', '诈骗', '赌博'].some(word => value.includes(word))) return { ok: false, message: '昵称含有不适合的内容，请换一个' };
  try {
    const result = await petApi.submitHatchAction(pet.id, 'NICKNAME', { nickname: value });
    let updated;
    if (result.alreadyDone) {
      // 后端 HatchActionService 仅在首次提交时持久化 nickname；二次提交走 PUT /pet/update 兜底。
      try {
        const vo = await petApi.updateNickname(pet.id, value);
        updated = savePetFromVO(vo);
      } catch (putError) {
        return { ok: false, message: (putError && putError.userMessage) || '昵称保存失败，请稍后重试' };
      }
    } else {
      updated = savePetFromVO(result.pet);
    }
    updated.name = value;
    savePet(updated);
    return { ok: true, alreadyDone: !!result.alreadyDone, pet: updated };
  } catch (error) {
    return { ok: false, message: (error && error.userMessage) || '提交失败，请稍后重试' };
  }
}

const ACTION_TYPE = { cuddle: 'CUDDLE', wish: 'WISH', lesson: 'LESSON' };

async function completeDailyTask(task, value) {
  const pet = getPet();
  if (!pet) return { ok: false, message: '还没有蛋宝宝' };

  // 许愿支持对象入参 { questionId, value }，兼容旧字符串入参
  var wishData = null;
  if (task === 'wish') {
    if (typeof value === 'object' && value !== null) {
      wishData = { questionId: value.questionId || 'legacy', value: value.value || '' };
    } else {
      wishData = { questionId: 'legacy', value: String(value || '') };
    }
  }

  try {
    const payload = task === 'cuddle' ? {} : (task === 'wish' ? { questionId: wishData.questionId, value: wishData.value } : { value });
    const result = await petApi.submitHatchAction(pet.id, ACTION_TYPE[task], payload);
    const updated = savePetFromVO(result.pet);
    if (!result.alreadyDone) {
      if (task === 'wish') updated.preferences.wishes.push({ date: todayKey(), questionId: wishData.questionId, value: wishData.value });
      if (task === 'lesson') updated.preferences.lessons.push({ date: todayKey(), value });
    }
    savePet(updated);
    return { ok: true, alreadyDone: !!result.alreadyDone, pet: updated };
  } catch (error) {
    return { ok: false, message: (error && error.userMessage) || '提交失败，请稍后重试' };
  }
}

function completeCuddle() {
  return completeDailyTask('cuddle', '贴贴');
}

function completeWish(value) {
  return completeDailyTask('wish', value);
}

function completeLesson(value) {
  return completeDailyTask('lesson', value);
}

async function saveDoodle(artUrl, petId) {
  const pet = getPet();
  if (!pet) return { ok: false, message: '还没有蛋宝宝' };
  if (!petId || String(pet.id) !== String(petId)) {
    return { ok: false, message: '蛋宝宝状态已变化，请返回后重试' };
  }
  try {
    const result = await petApi.submitHatchAction(petId, 'DOODLE', { artUrl });
    const currentPet = getPet();
    const responsePet = result && result.pet;
    if (!currentPet || String(currentPet.id) !== String(petId)
      || !responsePet || String(responsePet.id) !== String(petId)) {
      return { ok: false, message: '蛋宝宝状态已变化，请返回后重试' };
    }
    const updated = savePetFromVO(result.pet);
    savePet(updated);
    return { ok: true, alreadyDone: !!result.alreadyDone, pet: updated, petId: String(petId) };
  } catch (error) {
    return { ok: false, message: (error && error.userMessage) || '提交失败，请稍后重试' };
  }
}

// 涂鸦操作序列(shell)写入本地缓存，按 petId 关联；返回值用于保存事务确认本地记录已落盘
function saveDoodleShell(petId, operations) {
  if (!petId || !Array.isArray(operations)) return false;
  try {
    wx.setStorageSync(DOODLE_SHELL_KEY, { petId, operations, savedAt: Date.now() });
    return true;
  } catch (error) {
    return false;
  }
}

// 读取本地涂鸦操作序列；petId 不匹配(换了宠物/数据串号)视为无缓存返回 null
function getDoodleShell(petId) {
  const record = read(DOODLE_SHELL_KEY);
  if (!record || record.petId !== petId || !Array.isArray(record.operations)) return null;
  return record.operations;
}

// 修炼任务完成态：无 _hatchActions 缓存时从 pet.tasks 读取；有缓存则按当日派生。
function getHatchActionState(pet) {
  if (!pet) return { nicknameDone: false, cuddleDone: false, wishDone: false, lessonDone: false, doodleDone: false };
  if (!Array.isArray(pet._hatchActions) || pet._hatchActions.length === 0) {
    const today = todayKey();
    return {
      nicknameDone: !!(pet.tasks && pet.tasks.nicknameDone),
      cuddleDone: !!(pet.tasks && pet.tasks.cuddleDate === today),
      wishDone: !!(pet.tasks && pet.tasks.wishDate === today),
      lessonDone: !!(pet.tasks && pet.tasks.lessonDate === today),
      doodleDone: !!(pet.tasks && pet.tasks.doodleDone)
    };
  }
  const today = todayKey();
  const done = {};
  pet._hatchActions.forEach((a) => {
    if (a.actionDate === today || a.actionType === 'NICKNAME' || a.actionType === 'DOODLE') {
      done[a.actionType] = true;
    }
  });
  return {
    nicknameDone: !!done.NICKNAME,
    cuddleDone: !!done.CUDDLE,
    wishDone: !!done.WISH,
    lessonDone: !!done.LESSON,
    doodleDone: !!done.DOODLE
  };
}

function getStage(pet, now) {
  if (!pet) return 'empty';
  if (pet.hatchStatus === 'HATCHED') return 'hatched';
  const current = now || Date.now();
  if (current >= pet.hatchAt) return 'ready';
  if (pet.hatchAt - current <= DAY) return 'soon';
  // 单轨 Model X：进度满即时间到（落到 ready），不保留 prepared 中间态
  return pet.acceleratedMinutes > 0 || pet.progress > 0 ? 'hatching' : 'waiting';
}

const STAGE_PRESENTATION = {
  waiting: { homeText: '它还在睡觉，试着叫醒它吧', actionLabel: '孵化修炼手册', myStage: '待激活' },
  hatching: { homeText: '它正在慢慢长大', actionLabel: '孵化修炼手册', myStage: '孵化中' },
  soon: { homeText: '蛋壳里传来了动静', actionLabel: '孵化修炼手册', myStage: '即将破壳' },
  ready: { homeText: '它准备好见你了', actionLabel: '查看破壳结果', myStage: '待破壳' },
  hatched: { homeText: '它终于来到你身边了', actionLabel: '和它说说话', myStage: '已破壳' }
};

function getStagePresentation(stage) {
  return STAGE_PRESENTATION[stage] || STAGE_PRESENTATION.waiting;
}

function getCountdown(pet, now) {
  if (!pet) return '';
  const remaining = pet.hatchAt - (now || Date.now());
  if (remaining <= 0) return '破壳时刻已到';
  const days = Math.floor(remaining / DAY);
  const hours = Math.floor((remaining % DAY) / (60 * 60 * 1000));
  return days > 0 ? `还剩 ${days} 天 ${hours} 小时` : `还剩 ${hours} 小时`;
}

function simpleHash(value) {
  return Array.from(String(value)).reduce((sum, char) => sum + char.charCodeAt(0), 0);
}

function getDailyStatus() {
  const pet = getPet();
  if (!pet) return null;
  // 1. 优先用后端 PetVO 已懒生成的今日心情(adopt/list 返回)，
  //    必须校验 todayMoodDate 是今天，防止跨天后的旧缓存心情被当作今日状态展示
  if (pet.todayMood && pet.todayMoodSentence && pet.todayMoodDate === todayKey()) {
    return { date: pet.todayMoodDate, mood: pet.todayMood, line: pet.todayMoodSentence, source: 'backend' };
  }
  // 2. 本地 fallback(无后端心情时)
  const date = todayKey();
  if (pet.dailyStatus && pet.dailyStatus.date === date) return pet.dailyStatus;
  const inactiveDays = Math.floor((Date.now() - (pet.lastInteractionAt || pet.createdAt)) / DAY);
  const stage = getStage(pet);
  let mood;
  if (inactiveDays >= 4) mood = '低落';
  else if (inactiveDays >= 2) mood = '想念';
  else if (stage === 'soon' || stage === 'ready') mood = '兴奋';
  else if (Date.now() - (pet.lastInteractionAt || 0) < 12 * 60 * 60 * 1000) mood = '开心';
  else if (pet.preferences.wishes.some(item => item.value === '活泼逗你开心')) mood = '兴奋';
  else if (pet.preferences.wishes.some(item => item.value === '安静陪伴你')) mood = '平静';
  else mood = ['开心', '平静'][simpleHash(`${pet.id}-${date}`) % 2];
  const stagePool = pet.hatchStatus === 'HATCHED' ? STATUS_LINES.pet : STATUS_LINES.egg;
  const lines = stagePool[mood];
  const line = lines[simpleHash(date) % lines.length];
  pet.dailyStatus = { date, mood, line, source: 'local-fallback' };
  savePet(pet);
  return pet.dailyStatus;
}

function recordTouch() {
  const pet = getPet();
  if (!pet) return;
  pet.lastInteractionAt = Date.now();
  savePet(pet);
}

function cardSerial(pet) {
  const date = new Date(pet.hatchAt);
  const compact = `${date.getFullYear()}${String(date.getMonth() + 1).padStart(2, '0')}${String(date.getDate()).padStart(2, '0')}`;
  const prefix = pet.prototype === '锦鲤' ? 'KOI' : 'RABBIT';
  const number = String(simpleHash(pet.id) % 999999).padStart(6, '0');
  return `EGG-${prefix}-${compact}-${number}`;
}

const ZODIAC_MAP = {
  aries: '白羊座', taurus: '金牛座', gemini: '双子座',
  cancer: '巨蟹座', leo: '狮子座', virgo: '处女座',
  libra: '天秤座', scorpio: '天蝎座', sagittarius: '射手座',
  capricorn: '摩羯座', aquarius: '水瓶座', pisces: '双鱼座'
};

function translateZodiac(zodiac) {
  if (!zodiac) return '';
  const code = String(zodiac).toLowerCase();
  return ZODIAC_MAP[code] || zodiac;
}

function getZodiac(timestamp) {
  const date = new Date(timestamp);
  const key = (date.getMonth() + 1) * 100 + date.getDate();
  if (key >= 120 || key <= 218) return '水瓶座';
  if (key <= 320) return '双鱼座';
  if (key <= 419) return '白羊座';
  if (key <= 520) return '金牛座';
  if (key <= 621) return '双子座';
  if (key <= 722) return '巨蟹座';
  if (key <= 822) return '狮子座';
  if (key <= 922) return '处女座';
  if (key <= 1023) return '天秤座';
  if (key <= 1122) return '天蝎座';
  if (key <= 1221) return '射手座';
  return '摩羯座';
}

function derivePersonality(pet) {
  const latestLesson = pet.preferences.lessons.length ? pet.preferences.lessons[pet.preferences.lessons.length - 1].value : '';
  const latestWish = pet.preferences.wishes.length ? pet.preferences.wishes[pet.preferences.wishes.length - 1].value : '';
  if (latestLesson === '学会勇敢') return { mbti: 'ENTJ', text: '勇敢、有主见，也会把你护在身后。' };
  if (latestLesson === '学会讲冷笑话') return { mbti: 'ENFP', text: '热烈又古灵精怪，总想逗你开心。' };
  if (latestLesson === '学会撒娇') return { mbti: 'ESFP', text: '亲近、柔软，很会表达对你的喜欢。' };
  if (latestWish === '安静陪伴你') return { mbti: 'INFP', text: '温柔、细腻，擅长安静地陪伴。' };
  if (latestWish === '聪明帮你出主意') return { mbti: 'INTJ', text: '冷静又聪明，喜欢陪你把事情想清楚。' };
  return pet.prototype === '锦鲤'
    ? { mbti: 'ENFP', text: '热烈、好奇，喜欢把好运分给你。' }
    : { mbti: 'INFP', text: '温柔、细腻，擅长安静地陪伴。' };
}

async function createCollectionCard() {
  const pet = getPet();
  if (!pet) return { ok: false, message: '还没有蛋宝宝' };
  if (Date.now() < pet.hatchAt) return { ok: false, message: '还没到破壳时间' };
  if (pet.hatchStatus === 'HATCHED') return { ok: true, created: false, pet };
  try {
    const vo = await petApi.hatchPet(pet.id);
    const updated = savePetFromVO(vo);
    return { ok: true, created: true, pet: updated };
  } catch (error) {
    return { ok: false, message: (error && error.userMessage) || '破壳失败，请稍后重试' };
  }
}

async function changeScene() {
  const pet = getPet();
  if (!pet) return { ok: false, message: '还没有蛋宝宝' };
  try {
    const vo = await petApi.changeScene(pet.id);
    const updated = savePetFromVO(vo);
    return { ok: true, sceneUrl: updated.sceneUrl, pet: updated };
  } catch (error) {
    return { ok: false, message: (error && error.userMessage) || '更换场景失败，请稍后重试' };
  }
}

function getMessages(options) {
  const pet = getPet();
  if (!pet) return { list: [], total: 0, hasMore: false };
  const all = pet.messages || [];
  const total = all.length;
  const page = Math.max(1, options && options.page ? options.page : 1);
  const pageSize = Math.max(1, options && options.pageSize ? options.pageSize : 4);
  const end = Math.max(0, total - (page - 1) * pageSize);
  const start = Math.max(0, end - pageSize);
  const list = all.slice(start, end);
  const hasMore = start > 0;
  return { list, total, hasMore };
}

function saveMessage(message, options = {}) {
  const pet = getPet();
  if (!pet) return;
  const existing = pet.messages || [];
  let messages;
  if (options && options.upsert && message && message.id) {
    const idx = existing.findIndex((m) => m && m.id === message.id);
    if (idx >= 0) {
      messages = existing.slice();
      messages[idx] = message;
    } else {
      messages = existing.concat(message);
    }
  } else {
    messages = existing.concat(message);
  }
  pet.messages = messages.slice(-40);
  savePet(pet);
}

module.exports = {
  getUser,
  saveUser,
  syncUserProfile,
  clearUser,
  clearAccountData,
  getIdentityId,
  getPet,
  savePet,
  isBound,
  savePetFromVO,
  getActivePetId,
  setActivePetId,
  bindPet,
  updateNickname,
  completeCuddle,
  completeWish,
  completeLesson,
  saveDoodle,
  saveDoodleShell,
  getDoodleShell,
  getHatchActionState,
  getStage,
  getStagePresentation,
  getCountdown,
  getDailyStatus,
  recordTouch,
  cardSerial,
  createCollectionCard,
  changeScene,
  saveMessage,
  getMessages,
  todayKey
};
