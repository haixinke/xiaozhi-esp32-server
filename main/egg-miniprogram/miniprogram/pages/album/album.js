const petStore = require('../../utils/pet-store');
const petApi = require('../../utils/pet-api');

// "我的卡册"聚合用户名下全部宠物的收藏卡（不按当前宠物），
// 按获得时间倒序混排；进入时拉一次 /pet/list 刷新缓存，失败回退本地缓存
Page({
  data: { cards: [] },

  async onShow() {
    this._render();
    try {
      const list = await petApi.listPets();
      const pets = (Array.isArray(list) ? list : [])
        .map((vo) => petStore.mapPetFromVO(vo))
        .filter((pet) => !!pet);
      if (pets.length > 0) petStore.cachePets(pets);
      // 刷新结果落地后重渲染（可能拉到别处破壳的新卡）
      this._render();
    } catch (error) {
      // 刷新失败：保持缓存渲染，不阻塞页面
    }
  },

  // 拍平全部宠物的 collectionCards：createDate 倒序（最新获得在前），同刻按 sortOrder 升序
  _render() {
    const cards = [];
    petStore.getAllPets().forEach((pet) => {
      (pet.collectionCards || []).forEach((card, index) => {
        cards.push({
          ...card,
          petId: pet.id,
          petType: pet.prototype || '玉兔',
          name: pet.name || pet.prototype || '蛋宝宝',
          // index 指向该宠物自己的 collectionCards，单卡页按 petId+index 定位
          index
        });
      });
    });
    cards.sort((a, b) => {
      const dateDiff = String(b.createDate || '').localeCompare(String(a.createDate || ''));
      return dateDiff !== 0 ? dateDiff : (a.sortOrder || 0) - (b.sortOrder || 0);
    });
    this.setData({ cards });
  },

  onOpen(e) {
    const { index, petId } = e.currentTarget.dataset;
    const query = `petId=${petId || ''}&index=${index || 0}`;
    wx.navigateTo({ url: `/pages/collection-card/collection-card?${query}` });
  }
});
