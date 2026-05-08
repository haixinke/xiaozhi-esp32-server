/**
 * 蛋的渲染和动画控制
 */

/**
 * 裂纹 SVG path 数据（按进度阈值分组）
 * 每组裂纹在对应进度时显示
 */
const CRACK_PATHS = [
    // 30%: 细小裂纹
    { threshold: 30, paths: [
        'M 70 30 Q 65 50 68 70',
        'M 70 30 Q 75 45 72 65',
    ]},
    // 50%: 更多裂纹
    { threshold: 50, paths: [
        'M 55 35 Q 60 55 58 75',
        'M 85 35 Q 80 55 82 75',
    ]},
    // 70%: 裂纹变粗
    { threshold: 70, paths: [
        'M 40 50 Q 50 60 45 80',
        'M 100 50 Q 90 60 95 80',
        'M 70 25 L 70 50',
    ]},
    // 90%: 大量裂纹
    { threshold: 90, paths: [
        'M 35 40 Q 45 55 40 75',
        'M 105 40 Q 95 55 100 75',
        'M 60 25 Q 65 40 58 55',
        'M 80 25 Q 75 40 82 55',
    ]},
];

/**
 * 初始化蛋的呼吸动画
 * @param {SVGSVGElement} eggSvg
 */
export function startBreathing(eggSvg) {
    eggSvg.classList.add('breathing');
    eggSvg.closest('.egg-wrapper').classList.add('glowing');
}

/**
 * 停止呼吸动画
 * @param {SVGSVGElement} eggSvg
 */
export function stopBreathing(eggSvg) {
    eggSvg.classList.remove('breathing');
    eggSvg.closest('.egg-wrapper').classList.remove('glowing');
}

/**
 * 根据进度更新蛋的摇晃等级
 * @param {SVGSVGElement} eggSvg
 * @param {number} progress 0-100
 */
export function updateWobble(eggSvg, progress) {
    eggSvg.classList.remove('wobble-light', 'wobble-medium', 'wobble-heavy', 'wobble-extreme');

    if (progress >= 90) {
        eggSvg.classList.add('wobble-extreme');
    } else if (progress >= 60) {
        eggSvg.classList.add('wobble-heavy');
    } else if (progress >= 30) {
        eggSvg.classList.add('wobble-medium');
    } else if (progress > 0) {
        eggSvg.classList.add('wobble-light');
    }

    // 90%+ 添加发光效果
    if (progress >= 90) {
        eggSvg.classList.add('glow-crack');
    } else {
        eggSvg.classList.remove('glow-crack');
    }
}

/**
 * 根据进度更新裂纹显示
 * @param {SVGGElement} crackGroup
 * @param {number} progress 0-100
 * @param {number} prevProgress 上一次的进度（避免重复添加）
 */
export function updateCracks(crackGroup, progress, prevProgress) {
    CRACK_PATHS.forEach(({ threshold, paths }) => {
        if (progress >= threshold && prevProgress < threshold) {
            paths.forEach(d => {
                const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
                path.setAttribute('d', d);
                path.classList.add('crack-path');
                crackGroup.appendChild(path);
            });
        }
    });
}

/**
 * 播放点击弹性反馈
 * @param {SVGSVGElement} eggSvg
 */
export function playBounce(eggSvg) {
    // 先移除摇晃动画，播放弹性，再恢复摇晃
    const currentWobble = [...eggSvg.classList].find(c => c.startsWith('wobble-'));
    eggSvg.classList.remove('breathing', ...[...eggSvg.classList].filter(c => c.startsWith('wobble-')));
    eggSvg.classList.add('bounce');

    eggSvg.addEventListener('animationend', () => {
        eggSvg.classList.remove('bounce');
        if (currentWobble) {
            eggSvg.classList.add(currentWobble);
        }
    }, { once: true });
}

/**
 * 清除所有裂纹
 * @param {SVGGElement} crackGroup
 */
export function clearCracks(crackGroup) {
    crackGroup.innerHTML = '';
}

/**
 * 清除蛋的所有动画类
 * @param {SVGSVGElement} eggSvg
 */
export function resetEgg(eggSvg) {
    eggSvg.classList.remove(
        'breathing', 'wobble-light', 'wobble-medium',
        'wobble-heavy', 'wobble-extreme', 'glow-crack', 'bounce'
    );
    const wrapper = eggSvg.closest('.egg-wrapper');
    if (wrapper) wrapper.classList.remove('glowing');
}
