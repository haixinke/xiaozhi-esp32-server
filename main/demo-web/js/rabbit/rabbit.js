/**
 * 兔子 SVG 渲染和状态管理
 */

const RABBIT_SVG = `
<svg class="rabbit-svg idle" viewBox="0 0 160 200" xmlns="http://www.w3.org/2000/svg">
    <!-- 左耳 -->
    <g class="rabbit-ear-left" style="transform-origin: 50px 80px;">
        <ellipse cx="50" cy="40" rx="16" ry="40" fill="#FFFFFF" stroke="#F0D9B5" stroke-width="1.5"/>
        <ellipse cx="50" cy="40" rx="8" ry="28" fill="#FFB6C1"/>
    </g>
    <!-- 右耳 -->
    <g class="rabbit-ear-right" style="transform-origin: 110px 80px;">
        <ellipse cx="110" cy="40" rx="16" ry="40" fill="#FFFFFF" stroke="#F0D9B5" stroke-width="1.5"/>
        <ellipse cx="110" cy="40" rx="8" ry="28" fill="#FFB6C1"/>
    </g>
    <!-- 头 -->
    <circle cx="80" cy="95" r="42" fill="#FFFFFF" stroke="#F0D9B5" stroke-width="1.5"/>
    <!-- 左眼 -->
    <g class="rabbit-eye-lid" style="transform-origin: 65px 88px;">
        <circle cx="65" cy="88" r="5" fill="#333"/>
        <circle cx="63" cy="86" r="2" fill="#FFF"/>
    </g>
    <!-- 右眼 -->
    <g class="rabbit-eye-lid" style="transform-origin: 95px 88px;">
        <circle cx="95" cy="88" r="5" fill="#333"/>
        <circle cx="93" cy="86" r="2" fill="#FFF"/>
    </g>
    <!-- 鼻子 -->
    <ellipse cx="80" cy="98" rx="4" ry="3" fill="#FFB6C1"/>
    <!-- 嘴 -->
    <path class="rabbit-mouth" d="M76 102 Q80 107 84 102" fill="none" stroke="#F0D9B5" stroke-width="1.5" stroke-linecap="round" style="transform-origin: 80px 102px;"/>
    <!-- 腮红 -->
    <ellipse cx="52" cy="100" rx="10" ry="6" fill="rgba(255,180,180,0.5)"/>
    <ellipse cx="108" cy="100" rx="10" ry="6" fill="rgba(255,180,180,0.5)"/>
    <!-- 身体 -->
    <ellipse cx="80" cy="155" rx="35" ry="30" fill="#FFFFFF" stroke="#F0D9B5" stroke-width="1.5"/>
    <!-- 尾巴 -->
    <circle cx="80" cy="185" r="8" fill="#FFFFFF" stroke="#F0D9B5" stroke-width="1.5"/>
</svg>
`;

/**
 * 创建兔子元素并挂载到容器
 * @param {HTMLElement} container
 * @returns {{ el: SVGSVGElement, setState: (state: string) => void }}
 */
export function createRabbit(container) {
    container.innerHTML = RABBIT_SVG;
    const svg = container.querySelector('.rabbit-svg');

    return {
        el: svg,

        /**
         * 设置兔子状态
         * @param {'idle'|'speaking'|'listening'|'happy'} state
         */
        setState(state) {
            if (!svg) return;
            svg.classList.remove('idle', 'speaking', 'listening', 'happy');
            switch (state) {
                case 'speaking':
                    svg.classList.add('speaking');
                    break;
                case 'listening':
                    svg.classList.add('listening');
                    break;
                case 'happy':
                    svg.classList.add('happy');
                    // 跳动结束后恢复待机
                    svg.addEventListener('animationend', () => {
                        svg.classList.remove('happy');
                        svg.classList.add('idle');
                    }, { once: true });
                    break;
                default:
                    svg.classList.add('idle');
            }
        }
    };
}
