/**
 * 破壳动画：蛋壳裂开 + 粒子飞散
 */

/**
 * 播放破壳动画
 * @param {object} deps
 * @param {SVGSVGElement} deps.eggSvg
 * @param {HTMLElement} deps.particleContainer
 * @param {number} duration - 动画总时长(ms)，默认2000
 * @returns {Promise<void>}
 */
export function playCrackingAnimation(deps, duration = 2000) {
    const { eggSvg, particleContainer } = deps;

    return new Promise(resolve => {
        // 阶段1：剧烈摇晃
        eggSvg.classList.remove(...[...eggSvg.classList].filter(c => c.startsWith('wobble-')));
        eggSvg.classList.add('cracking');

        // 阶段2（duration/2时）：蛋壳爆裂
        setTimeout(() => {
            eggSvg.classList.remove('cracking');
            eggSvg.classList.add('crack-burst');
            spawnShellFragments(particleContainer);
            spawnBurstParticles(particleContainer);
        }, duration / 2);

        // 动画结束
        setTimeout(() => {
            eggSvg.classList.remove('crack-burst');
            resolve();
        }, duration);
    });
}

/**
 * 生成蛋壳碎片
 * @param {HTMLElement} container
 */
function spawnShellFragments(container) {
    const rect = container.getBoundingClientRect();
    const centerX = rect.width / 2;
    const centerY = rect.height / 2;

    for (let i = 0; i < 8; i++) {
        const fragment = document.createElement('div');
        fragment.className = 'shell-fragment';

        const angle = (Math.PI * 2 * i) / 8 + (Math.random() - 0.5) * 0.5;
        const distance = 60 + Math.random() * 40;
        const endX = centerX + Math.cos(angle) * distance;
        const endY = centerY + Math.sin(angle) * distance;
        const rotation = Math.random() * 360;

        fragment.style.left = `${centerX}px`;
        fragment.style.top = `${centerY}px`;
        fragment.style.transition = 'all 0.8s cubic-bezier(0.25, 0.46, 0.45, 0.94)';
        fragment.style.opacity = '1';
        container.appendChild(fragment);

        requestAnimationFrame(() => {
            fragment.style.left = `${endX}px`;
            fragment.style.top = `${endY}px`;
            fragment.style.transform = `rotate(${rotation}deg)`;
        });

        setTimeout(() => {
            fragment.style.opacity = '0';
            setTimeout(() => fragment.remove(), 300);
        }, 600);
    }
}

/**
 * 生成爆裂粒子（星星 + 爱心）
 * @param {HTMLElement} container
 */
function spawnBurstParticles(container) {
    const rect = container.getBoundingClientRect();
    const centerX = rect.width / 2;
    const centerY = rect.height / 2;
    const particles = ['✨', '⭐', '💖', '🌟', '💫'];

    for (let i = 0; i < 12; i++) {
        const particle = document.createElement('div');
        particle.textContent = particles[Math.floor(Math.random() * particles.length)];
        particle.style.position = 'absolute';
        particle.style.fontSize = `${12 + Math.random() * 10}px`;
        particle.style.left = `${centerX}px`;
        particle.style.top = `${centerY}px`;
        particle.style.pointerEvents = 'none';
        particle.style.transition = 'all 0.8s cubic-bezier(0.25, 0.46, 0.45, 0.94)';
        particle.style.opacity = '1';
        container.appendChild(particle);

        const angle = (Math.PI * 2 * i) / 12;
        const distance = 50 + Math.random() * 60;
        const endX = centerX + Math.cos(angle) * distance;
        const endY = centerY + Math.sin(angle) * distance;

        requestAnimationFrame(() => {
            particle.style.left = `${endX}px`;
            particle.style.top = `${endY}px`;
        });

        setTimeout(() => {
            particle.style.opacity = '0';
            setTimeout(() => particle.remove(), 300);
        }, 700);
    }
}
