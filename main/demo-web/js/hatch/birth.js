/**
 * 出生展示：调用 API、展示宠物信息卡、过渡到聊天
 */

import { createRabbit } from '../rabbit/rabbit.js?v=0508';

/** 星座名称映射 */
const ZODIAC_NAMES = {
    aries: '白羊座', taurus: '金牛座', gemini: '双子座', cancer: '巨蟹座',
    leo: '狮子座', virgo: '处女座', libra: '天秤座', scorpio: '天蝎座',
    sagittarius: '射手座', capricorn: '摩羯座', aquarius: '水瓶座', pisces: '双鱼座',
};

/** 默认宠物数据（API 失败时使用） */
const DEFAULT_PET = {
    nickname: '小团子',
    mbti: 'INFP',
    zodiac: 'taurus',
    birthDate: new Date().toISOString(),
};

/**
 * 展示出生场景
 * @param {object} deps
 * @param {HTMLElement} deps.birthScene
 * @param {HTMLElement} deps.birthRabbitContainer
 * @param {HTMLElement} deps.petNameEl
 * @param {HTMLElement} deps.petMbtiEl
 * @param {HTMLElement} deps.petZodiacEl
 * @param {HTMLElement} deps.petBirthDateEl
 * @param {Function} deps.showToast
 * @param {string} otaUrl - OTA 服务器地址，用于推导 API 地址
 * @param {string} deviceId - 设备 ID
 * @returns {Promise<{ petData: object }>}
 */
export async function showBirthScene(deps, otaUrl, deviceId) {
    const {
        birthScene, birthRabbitContainer, petNameEl,
        petMbtiEl, petZodiacEl, petBirthDateEl, showToast
    } = deps;

    // 切换到出生场景
    birthScene.classList.add('active');

    // 创建兔子
    const rabbit = createRabbit(birthRabbitContainer);

    // 调用出生 API
    let petData;
    try {
        petData = await callBirthApi(otaUrl, deviceId);
    } catch (err) {
        showToast(`出生信息加载失败: ${err.message}，使用默认数据`);
        petData = DEFAULT_PET;
    }

    // 填充宠物信息卡
    petNameEl.textContent = petData.nickname;
    petMbtiEl.textContent = petData.mbti;
    petZodiacEl.textContent = ZODIAC_NAMES[petData.zodiac] || petData.zodiac;
    petBirthDateEl.textContent = formatDate(petData.birthDate);

    return { petData, rabbit };
}

/**
 * 调用出生 API
 * @param {string} otaUrl - 如 "http://127.0.0.1:8002/xiaozhi/ota/"
 * @param {string} deviceId
 * @returns {Promise<object>}
 */
async function callBirthApi(otaUrl, deviceId) {
    if (!deviceId) {
        throw new Error('设备ID为空，请先在设置中配置设备MAC');
    }

    // 从 OTA URL 推导 API 地址
    const apiBase = otaUrl.replace(/\/xiaozhi\/ota\/?$/, '');
    const url = `${apiBase}/xiaozhi/pet/birth`;

    const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ deviceId }),
    });

    if (!response.ok) {
        const errorBody = await response.text().catch(() => '');
        if (response.status === 404 || errorBody.includes('10205')) {
            throw new Error('设备未找到，请先注册设备');
        }
        if (response.status === 409 || errorBody.includes('10206')) {
            throw new Error('该设备已有宠物');
        }
        throw new Error(`API 返回 ${response.status}`);
    }

    const result = await response.json();

    // 适配后端响应格式（可能是 { data: {...} } 或直接返回数据）
    if (result.code !== undefined && result.data) {
        return result.data;
    }
    return result;
}

/**
 * 格式化日期
 * @param {string} dateStr
 * @returns {string}
 */
function formatDate(dateStr) {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    const h = String(date.getHours()).padStart(2, '0');
    const min = String(date.getMinutes()).padStart(2, '0');
    return `${y}-${m}-${d} ${h}:${min}`;
}
