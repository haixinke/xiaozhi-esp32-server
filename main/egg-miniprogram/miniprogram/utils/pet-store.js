const PET_KEY = 'eggbaby_mvp_pet_v1';
const USER_KEY = 'eggbaby_mvp_user_v1';
const IDENTITY_KEY = 'eggbaby_mvp_identity_v1';
const EXHIBITION_BACKUP_KEY = 'eggbaby_exhibition_backup_v1';

const DAY = 24 * 60 * 60 * 1000;

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

function getIdentityId() {
  return read(IDENTITY_KEY);
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

function createInviteCodes(seed) {
  const suffix = String(seed).slice(-4);
  return Array.from({ length: 5 }, (_, index) => ({
    code: `EGG-${suffix}-${index + 1}`,
    used: false
  }));
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
    collectionCard: null,
    inviteCodes: createInviteCodes(createdAt),
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

function updateNickname(name) {
  const pet = getPet();
  if (!pet) return { ok: false, message: '还没有蛋宝宝' };
  const value = String(name || '').trim();
  if (!value) return { ok: false, message: '昵称不能为空' };
  if (Array.from(value).length > 10) return { ok: false, message: '昵称最多 10 个字符' };
  if (['违法', '诈骗', '赌博'].some(word => value.includes(word))) return { ok: false, message: '昵称含有不适合的内容，请换一个' };
  const first = !pet.tasks.nicknameDone;
  pet.name = value;
  if (first) addProgress(pet, 20);
  pet.tasks.nicknameDone = true;
  pet.lastInteractionAt = Date.now();
  savePet(pet);
  return { ok: true, added: first ? 20 : 0, pet };
}

function completeDailyTask(task, value) {
  const pet = getPet();
  if (!pet) return { ok: false, message: '还没有蛋宝宝' };
  const date = todayKey();
  const field = `${task}Date`;
  if (pet.tasks[field] === date) return { ok: true, added: 0, alreadyDone: true, pet };
  pet.tasks[field] = date;
  if (task === 'wish') pet.preferences.wishes.push({ date, value });
  if (task === 'lesson') pet.preferences.lessons.push({ date, value });
  addProgress(pet, 5);
  pet.lastInteractionAt = Date.now();
  savePet(pet);
  return { ok: true, added: 5, alreadyDone: false, pet };
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

function saveDoodle(color, colorName, pattern) {
  const pet = getPet();
  if (!pet) return { ok: false, message: '还没有蛋宝宝' };
  const first = !pet.tasks.doodleDone;
  pet.shell = { color, colorName, pattern };
  pet.tasks.doodleDone = true;
  if (first) addProgress(pet, 20);
  pet.lastInteractionAt = Date.now();
  savePet(pet);
  return { ok: true, added: first ? 20 : 0, pet };
}

function getStage(pet, now) {
  if (!pet) return 'empty';
  if (pet.collectionCard) return 'hatched';
  const current = now || Date.now();
  if (current >= pet.hatchAt) return 'ready';
  if (pet.hatchAt - current <= DAY) return 'soon';
  if (pet.progress >= 100) return 'prepared';
  return pet.progress > 0 ? 'hatching' : 'waiting';
}

const STAGE_PRESENTATION = {
  waiting: { homeText: '它还在睡觉，试着叫醒它吧', actionLabel: '孵化修炼手册', myStage: '待激活' },
  hatching: { homeText: '它正在慢慢长大', actionLabel: '孵化修炼手册', myStage: '孵化中' },
  prepared: { homeText: '准备好了，等待破壳日', actionLabel: '等待破壳日', myStage: '孵化中 · 已准备' },
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
  const stagePool = pet.collectionCard ? STATUS_LINES.pet : STATUS_LINES.egg;
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

function createCollectionCard() {
  const pet = getPet();
  if (!pet) return { ok: false, message: '还没有蛋宝宝' };
  if (Date.now() < pet.hatchAt) return { ok: false, message: '还没到预设破壳时间' };
  if (pet.collectionCard) return { ok: true, created: false, card: pet.collectionCard, pet };
  const isKoi = pet.prototype === '锦鲤';
  const personality = derivePersonality(pet);
  pet.collectionCard = {
    id: `card-${pet.id}`,
    serial: cardSerial(pet),
    prototype: pet.prototype,
    style: isKoi ? '好运红白款' : '月白桂花款',
    name: pet.name || pet.prototype,
    birthday: todayKey(pet.hatchAt),
    zodiac: getZodiac(pet.hatchAt),
    gender: simpleHash(pet.id) % 2 ? '♀' : '♂',
    mbti: personality.mbti,
    bloodType: ['A', 'B', 'O', 'AB'][simpleHash(pet.id) % 4],
    personality: personality.text,
    collectible: '普通',
    hatchQuality: pet.progress >= 80 ? '完整孵化' : '轻量孵化',
    originalOwner: (getUser() && getUser().nickname) || '蛋友3024'
  };
  pet.stage = 'hatched';
  savePet(pet);
  return { ok: true, created: true, card: pet.collectionCard, pet };
}

function saveMessage(message) {
  const pet = getPet();
  if (!pet) return;
  pet.messages = (pet.messages || []).concat(message).slice(-40);
  savePet(pet);
}

function resetDemo() {
  try { wx.removeStorageSync(PET_KEY); wx.removeStorageSync(EXHIBITION_BACKUP_KEY); } catch (error) {}
}

function startExhibitionDemo() {
  const current = read(PET_KEY);
  if (current && current.demoMode) return current;
  write(EXHIBITION_BACKUP_KEY, { pet: current || null });
  const createdAt = Date.now();
  const pet = {
    id: `expo-${createdAt}`,
    ownerId: (getUser() && getUser().id) || '',
    prototype: '玉兔',
    name: '月团',
    createdAt,
    hatchAt: createdAt - 1000,
    progress: 85,
    stage: 'ready',
    demoMode: true,
    lastInteractionAt: createdAt,
    tasks: { nicknameDone: true, cuddleDate: todayKey(), wishDate: todayKey(), lessonDate: todayKey(), doodleDone: true },
    preferences: {
      wishes: [{ date: todayKey(), value: '安静陪伴你' }],
      lessons: [{ date: todayKey(), value: '学会撒娇' }]
    },
    shell: { color: '#EDE78E', colorName: '奶油白', pattern: '星星' },
    dailyStatus: null,
    collectionCard: null,
    inviteCodes: createInviteCodes(createdAt),
    messages: []
  };
  savePet(pet);
  createCollectionCard();
  const hatchedPet = getPet();
  hatchedPet.demoMode = true;
  savePet(hatchedPet);
  return hatchedPet;
}

function endExhibitionDemo() {
  const backup = read(EXHIBITION_BACKUP_KEY);
  try {
    if (backup && backup.pet) wx.setStorageSync(PET_KEY, backup.pet);
    else wx.removeStorageSync(PET_KEY);
    wx.removeStorageSync(EXHIBITION_BACKUP_KEY);
  } catch (error) {}
  return getPet();
}

module.exports = {
  getUser,
  saveUser,
  clearUser,
  getIdentityId,
  getPet,
  savePet,
  isBound,
  bindPet,
  updateNickname,
  completeCuddle,
  completeWish,
  completeLesson,
  saveDoodle,
  getStage,
  getStagePresentation,
  getCountdown,
  getDailyStatus,
  recordTouch,
  createCollectionCard,
  saveMessage,
  resetDemo,
  startExhibitionDemo,
  endExhibitionDemo,
  todayKey
};
