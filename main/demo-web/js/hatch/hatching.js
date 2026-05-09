/**
 * 孵化互动逻辑：点击事件、进度管理、爱心粒子
 */

import { playBounce, updateWobble, updateCracks } from './egg.js';

/**
 * 创建孵化互动控制器
 * @param {object} deps
 * @param {SVGSVGElement} deps.eggSvg
 * @param {SVGGElement} deps.crackGroup
 * @param {HTMLElement} deps.progressContainer
 * @param {HTMLElement} deps.progressFill
 * @param {HTMLElement} deps.progressText
 * @param {HTMLElement} deps.particleContainer
 * @param {HTMLElement} deps.hintEl
 * @param {() => void} deps.onComplete - 进度100%时的回调
 * @returns {{ start: () => void, stop: () => void, getProgress: () => number }}
 */
export function createHatchingController(deps) {
    const {
        eggSvg, crackGroup, progressContainer, progressFill,
        progressText, particleContainer, hintEl, onComplete
    } = deps;

    let progress = 0;
    let prevProgress = 0;
    let autoTimer = null;
    let isActive = false;

    function updateUI() {
        progressFill.style.width = `${progress}%`;
        progressText.textContent = `${Math.round(progress)}%`;
        updateWobble(eggSvg, progress);
        updateCracks(crackGroup, progress, prevProgress);
        prevProgress = progress;
    }

    function addProgress(amount) {
        if (!isActive) return;
        progress = Math.min(100, progress + amount);
        updateUI();

        if (progress >= 100) {
            stop();
            if (onComplete) onComplete();
        }
    }

    function onEggClick(e) {
        if (!isActive) return;
        const amount = 3 + Math.random() * 2; // 3~5%
        addProgress(amount);
        playBounce(eggSvg);
        spawnHeartParticle(particleContainer, e);
        spawnFloatText(particleContainer, `+${Math.round(amount)}%`);
    }

    function onEggTouchEnd(e) {
        e.preventDefault();
        onEggClick({ clientX: e.changedTouches[0].clientX, clientY: e.changedTouches[0].clientY });
    }

    function spawnHeartParticle(container, e) {
        const rect = container.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;
        const hearts = ['❤', '💕', '💗', '✨'];

        for (let i = 0; i < 3; i++) {
            const particle = document.createElement('div');
            particle.className = 'heart-particle';
            particle.textContent = hearts[Math.floor(Math.random() * hearts.length)];
            particle.style.left = `${x + (Math.random() - 0.5) * 30}px`;
            particle.style.top = `${y}px`;
            container.appendChild(particle);
            particle.addEventListener('animationend', () => particle.remove());
        }
    }

    function spawnFloatText(container, text) {
        const rect = container.getBoundingClientRect();
        const el = document.createElement('div');
        el.className = 'float-text';
        el.textContent = text;
        el.style.left = `${rect.width / 2 - 15}px`;
        el.style.top = `${rect.height / 2 - 40}px`;
        container.appendChild(el);
        el.addEventListener('animationend', () => el.remove());
    }

    function start() {
        isActive = true;
        progress = 0;
        prevProgress = 0;
        progressContainer.style.display = 'flex';
        if (hintEl) hintEl.textContent = '点击蛋来加速孵化！';

        // 自动增长 0.5%/秒
        autoTimer = setInterval(() => {
            addProgress(0.5);
        }, 1000);

        // 绑定点击事件
        eggSvg.closest('.egg-wrapper').addEventListener('click', onEggClick);
        eggSvg.closest('.egg-wrapper').addEventListener('touchend', onEggTouchEnd);

        updateUI();
    }

    function stop() {
        isActive = false;
        if (autoTimer) {
            clearInterval(autoTimer);
            autoTimer = null;
        }
        const wrapper = eggSvg.closest('.egg-wrapper');
        if (wrapper) {
            wrapper.removeEventListener('click', onEggClick);
            wrapper.removeEventListener('touchend', onEggTouchEnd);
        }
    }

    function getProgress() {
        return progress;
    }

    return { start, stop, getProgress };
}
