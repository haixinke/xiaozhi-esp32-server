const petApi = require('./pet-api');
const PET_KEY = 'eggbaby_mvp_pet_v1';
const USER_KEY = 'eggbaby_mvp_user_v1';
const ACTIVE_PET_KEY = 'eggbaby_active_pet_v1';
const PETS_KEY = 'eggbaby_mvp_pets_v1';
let selectionRevision = 0;
let refreshRevision = 0;
let accountGeneration = 0;
let authoritativeList = false;
let dataRevision = 0;
let petRevisions = Object.create(null);

function read(key) {
  try { return wx.getStorageSync(key) || null; } catch (error) { return null; }
}

function write(key, value) {
  try { wx.setStorageSync(key, value); } catch (error) {}
  return value;
}

function getUser() { return read(USER_KEY); }

function getPet() {
  const pet = read(PET_KEY);
  const user = getUser();
  if (pet && pet.ownerId && user && pet.ownerId !== user.id) return null;
  return pet;
}

// 按 petId 从多宠物缓存读取宠物（不切换“当前宠物”），“我的卡册”跨宠物聚合与单卡页按 petId 取宠物使用。
// 与 getPet() 同款 ownerId 校验：换账号后旧缓存不可见
function getPetById(petId) {
  if (!petId) return null;
  const pet = readPetsMap()[String(petId)];
  const user = getUser();
  if (pet && pet.ownerId && user && pet.ownerId !== user.id) return null;
  return pet || null;
}

// 读取全部缓存宠物（“我的卡册”聚合用户名下所有宠物的收藏卡）；同样按 ownerId 过滤
function getAllPets() {
  const user = getUser();
  return Object.values(readPetsMap()).filter((pet) => pet && (!pet.ownerId || !user || pet.ownerId === user.id));
}

// 更新某只宠物的资料；只有首次写入或更新当前宠物时才改当前对象。
function savePet(pet) {
  if (!pet || !pet.id) return null;
  const user = getUser();
  if (pet.ownerId && user && pet.ownerId !== user.id) return null;
  if (authoritativeList && !getPetById(pet.id)) return null;
  savePetToMap(pet);
  const current = getPet();
  if (!current || String(current.id) === String(pet.id)) {
    write(PET_KEY, pet);
    write(ACTIVE_PET_KEY, pet.id);
  }
  return pet;
}

function readPetsMap() {
  const map = read(PETS_KEY);
  return map && typeof map === 'object' ? map : {};
}

function savePetToMap(pet) {
  if (!pet || !pet.id) return;
  const map = readPetsMap();
  petRevisions = { ...petRevisions, [String(pet.id)]: ++dataRevision };
  write(PETS_KEY, { ...map, [String(pet.id)]: pet });
}

// 兼容旧调用者的批量缓存写入；完整服务端列表统一由 refreshPets 接受。
function cachePets(pets) {
  if (!Array.isArray(pets)) return;
  const additions = Object.fromEntries(pets.filter((pet) => pet && pet.id).map((pet) => [String(pet.id), pet]));
  write(PETS_KEY, { ...readPetsMap(), ...additions });
}

// 按服务端返回的宠物列表整体重建缓存（合并语义的补充）：
// 不在列表里的宠物（服务端已删除）从缓存移除，防止“我的卡册”等聚合视图渲染幽灵宠物；
// 空列表同样生效（用户宠物全部删除时清空缓存）。不改动“当前宠物”。
function replacePets(pets) {
  if (!Array.isArray(pets)) return;
  const map = Object.fromEntries(pets.filter((pet) => pet && pet.id).map((pet) => [String(pet.id), pet]));
  write(PETS_KEY, map);
}

function setActivePet(pet) {
  if (!pet || !pet.id) return null;
  const user = getUser();
  if (pet.ownerId && user && pet.ownerId !== user.id) return null;
  if (!getPetById(pet.id)) refreshRevision += 1;
  selectionRevision += 1;
  savePetToMap(pet);
  write(PET_KEY, pet);
  write(ACTIVE_PET_KEY, pet.id);
  return pet;
}

function getActivePetId() { return read(ACTIVE_PET_KEY); }
function setActivePetId(id) { selectionRevision += 1; return write(ACTIVE_PET_KEY, id); }

function getPetSnapshot() {
  const current = getPet();
  const cached = getAllPets();
  const allPets = current && !cached.some((pet) => String(pet.id) === String(current.id)) ? cached.concat(current) : cached;
  const pets = allPets.sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0))
    .map((pet) => ({ ...pet, petType: pet.prototype || '' }));
  const index = pets.findIndex((pet) => current && String(pet.id) === String(current.id));
  return { pets, pet: index >= 0 ? pets[index] : null, currentPetIndex: Math.max(0, index) };
}

// 选择不算资料更新，否则在途刷新会误把服务端新资料判为过期。
function selectPet(petId) {
  const pet = getPetById(petId);
  if (!pet) return null;
  selectionRevision += 1;
  write(PET_KEY, pet);
  write(ACTIVE_PET_KEY, pet.id);
  return pet;
}

// 动作响应只可写回原账号仍持有的宠物，不以完成顺序改变当前选择。
function capturePetUpdate(petId) {
  const generation = accountGeneration;
  const owner = getUser() && getUser().id;
  return (responseId) => generation === accountGeneration && owner === (getUser() && getUser().id)
    && String(responseId) === String(petId)
    && !!(getPetById(petId) || (getPet() && String(getPet().id) === String(petId)));
}

function invalidateAccount() {
  selectionRevision = 0;
  accountGeneration += 1;
  refreshRevision += 1;
  authoritativeList = false;
  petRevisions = Object.create(null);
}

async function refreshPets(mapPetFromVO, { resetSelection = false } = {}) {
  const revision = ++refreshRevision;
  const owner = getUser() && getUser().id;
  const initialSelection = selectionRevision;
  const initialData = dataRevision;
  const isCurrent = () => revision === refreshRevision && owner === (getUser() && getUser().id);
  let list;
  try {
    list = await petApi.listPets();
  } catch (error) {
    if (!isCurrent()) return null;
    throw error;
  }
  if (!isCurrent()) return null;
  if (!Array.isArray(list)) throw new Error('宠物列表响应格式无效');
  const pets = list.map((vo) => {
    const cached = vo && getPetById(vo.id);
    return cached && petRevisions[String(vo.id)] > initialData ? cached : mapPetFromVO(vo);
  }).filter(Boolean)
    .sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0));
  const current = getPet();
  replacePets(pets);
  authoritativeList = true;
  // 首次主页展示只重置冷缓存；本进程已表达的切宠或 NFC 进入意图仍优先。
  const keepSelection = !resetSelection || initialSelection > 0 || initialSelection !== selectionRevision;
  const selected = (keepSelection && pets.find((pet) => current && String(pet.id) === String(current.id))) || pets[0];
  if (selected) {
    write(PET_KEY, selected);
    write(ACTIVE_PET_KEY, selected.id);
  } else {
    write(PET_KEY, null);
    write(ACTIVE_PET_KEY, null);
  }
  return getPetSnapshot();
}

module.exports = { getPet, getPetById, getAllPets, savePet, cachePets, replacePets,
  setActivePet, getActivePetId, setActivePetId, getPetSnapshot, selectPet, refreshPets, invalidateAccount, capturePetUpdate };
