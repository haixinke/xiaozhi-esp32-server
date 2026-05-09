/**
 * 破壳动画：蛋壳裂开 → 分裂两半 → 兔子从蛋中升起
 */

/**
 * 播放破壳动画
 * @param {object} deps
 * @param {SVGSVGElement} deps.eggSvg
 * @param {HTMLElement} deps.particleContainer
 * @param {HTMLElement} deps.eggWrapper
 * @param {number} duration - 动画总时长(ms)，默认4000
 * @returns {Promise<void>}
 */
export function playCrackingAnimation(deps, duration = 4000) {
    const { eggSvg, particleContainer, eggWrapper } = deps;

    return new Promise(resolve => {
        // 阶段1：剧烈摇晃 + 添加横向裂纹（0-1500ms）
        eggSvg.classList.remove(...[...eggSvg.classList].filter(c => c.startsWith('wobble-')));
        eggSvg.classList.add('cracking');

        // 在蛋中间画横向裂纹
        addCenterCrack(eggSvg);

        // 阶段2：蛋分裂成两半（1500ms）
        setTimeout(() => {
            eggSvg.classList.remove('cracking');
            eggSvg.style.display = 'none';

            // 创建分裂容器
            createSplitEgg(eggWrapper, eggSvg);

            // 创建光芒
            const glow = document.createElement('div');
            glow.className = 'egg-split-glow';
            eggWrapper.appendChild(glow);

            // 少量碎片
            spawnShellFragments(particleContainer);
        }, 1500);

        // 阶段3：爆裂粒子（2500ms）
        setTimeout(() => {
            spawnBurstParticles(particleContainer);
        }, 2500);

        // 动画结束（3500ms）
        setTimeout(() => {
            // 清理分裂元素
            const splitContainer = eggWrapper.querySelector('.egg-split-container');
            if (splitContainer) splitContainer.remove();
            const glow = eggWrapper.querySelector('.egg-split-glow');
            if (glow) glow.remove();

            eggSvg.style.display = '';
            eggSvg.classList.remove('cracking');

            // 清理横向裂纹
            const centerCrack = eggSvg.querySelector('#centerCrack');
            if (centerCrack) centerCrack.remove();

            resolve();
        }, duration);
    });
}

/**
 * 在蛋中间添加横向裂纹线
 * @param {SVGSVGElement} eggSvg
 */
function addCenterCrack(eggSvg) {
    // 蛋的中心 y=100, 从左到右的裂纹
    const crackGroup = document.createElementNS('http://www.w3.org/2000/svg', 'g');
    crackGroup.id = 'centerCrack';

    const cracks = [
        'M 25 100 Q 45 95 55 100 Q 65 105 70 98 Q 75 92 85 100 Q 95 108 115 100',
        'M 30 97 Q 50 102 60 96 Q 70 90 80 97 Q 90 104 110 97',
        'M 35 103 Q 55 97 65 103 Q 75 109 85 103 Q 95 97 105 103',
    ];

    cracks.forEach((d, i) => {
        const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        path.setAttribute('d', d);
        path.classList.add('egg-crack-line');
        path.style.animationDelay = `${i * 0.2}s`;
        crackGroup.appendChild(path);
    });

    eggSvg.appendChild(crackGroup);
}

/**
 * 创建分裂的蛋壳两半
 * @param {HTMLElement} wrapper - egg-wrapper 容器
 * @param {SVGSVGElement} eggSvg - 原始蛋 SVG
 */
function createSplitEgg(wrapper, eggSvg) {
    const container = document.createElement('div');
    container.className = 'egg-split-container';

    // 克隆蛋 SVG 创建上半
    const topHalf = document.createElement('div');
    topHalf.className = 'egg-half egg-half-top';
    topHalf.appendChild(eggSvg.cloneNode(true));
    container.appendChild(topHalf);

    // 克隆蛋 SVG 创建下半
    const bottomHalf = document.createElement('div');
    bottomHalf.className = 'egg-half egg-half-bottom';
    bottomHalf.appendChild(eggSvg.cloneNode(true));
    container.appendChild(bottomHalf);

    wrapper.appendChild(container);
}

/**
 * 生成蛋壳碎片（少量）
 * @param {HTMLElement} container
 */
function spawnShellFragments(container) {
    const rect = container.getBoundingClientRect();
    const centerX = rect.width / 2;
    const centerY = rect.height / 2;

    for (let i = 0; i < 5; i++) {
        const fragment = document.createElement('div');
        fragment.className = 'shell-fragment';

        const angle = (Math.PI * 2 * i) / 5 + (Math.random() - 0.5) * 0.5;
        const distance = 40 + Math.random() * 50;
        const endX = centerX + Math.cos(angle) * distance;
        const endY = centerY + Math.sin(angle) * distance;
        const rotation = Math.random() * 360;

        fragment.style.left = `${centerX}px`;
        fragment.style.top = `${centerY}px`;
        fragment.style.transition = 'all 1s cubic-bezier(0.25, 0.46, 0.45, 0.94)';
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
        }, 800);
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
