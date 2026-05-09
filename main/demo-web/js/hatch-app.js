/**
 * 孵化演示页面入口
 * 整合状态机、蛋动画、孵化互动、破壳、出生展示、聊天
 */

import { checkOpusLoaded, initOpusEncoder } from './core/audio/opus-codec.js';
import { getAudioPlayer } from './core/audio/player.js';
import { checkMicrophoneAvailability, isHttpNonLocalhost } from './core/audio/recorder.js';
import { initMcpTools } from './core/mcp/tools.js';
import { loadConfig, saveConfig, getConfig } from './config/manager.js';
import { log } from './utils/logger.js';
import { HatchManager } from './hatch/hatch-manager.js';
import { startBreathing, stopBreathing, resetEgg, clearCracks } from './hatch/egg.js';
import { createHatchingController } from './hatch/hatching.js';
import { playCrackingAnimation } from './hatch/cracking.js';
import { showBirthScene } from './hatch/birth.js';
import { createRabbit } from './rabbit/rabbit.js';
import { uiController } from './ui/controller.js';
import { getWebSocketHandler } from './core/network/websocket.js';
import { getAudioRecorder } from './core/audio/recorder.js';

class HatchApp {
    constructor() {
        this.hatchManager = new HatchManager();
        this.hatchingController = null;
        this.rabbitCompanion = null;
    }

    async init() {
        log('正在初始化孵化演示...', 'info');

        // 缓存 DOM 元素
        this.els = {
            hatchScene: document.getElementById('hatchScene'),
            birthScene: document.getElementById('birthScene'),
            chatScene: document.getElementById('chatScene'),
            eggSvg: document.getElementById('eggSvg'),
            crackGroup: document.getElementById('crackGroup'),
            eggWrapper: document.getElementById('eggWrapper'),
            hatchStartBtn: document.getElementById('hatchStartBtn'),
            hatchProgress: document.getElementById('hatchProgress'),
            progressFill: document.getElementById('progressFill'),
            progressText: document.getElementById('progressText'),
            particleContainer: document.getElementById('particleContainer'),
            hatchHint: document.getElementById('hatchHint'),
            birthRabbitContainer: document.getElementById('birthRabbit'),
            petName: document.getElementById('petName'),
            petMbti: document.getElementById('petMbti'),
            petZodiac: document.getElementById('petZodiac'),
            petBirthDate: document.getElementById('petBirthDate'),
            rabbitCompanionEl: document.getElementById('rabbitCompanion'),
            rehatchBtn: document.getElementById('rehatchBtn'),
            toastContainer: document.getElementById('toastContainer'),
            connectionStatus: document.getElementById('connectionStatus'),
            connectionStatus2: document.getElementById('connectionStatus2'),
        };

        // 初始化核心模块
        checkOpusLoaded();
        initOpusEncoder();
        const audioPlayer = getAudioPlayer();
        await audioPlayer.start();
        initMcpTools();

        // 检查麦克风
        await this.checkMicrophone();

        // 初始化 UI 控制器
        uiController.init();
        this.initRehatchButton();

        // 注册状态机阶段
        this.registerStages();

        // 进入蛋阶段
        await this.hatchManager.goTo('egg');

        log('孵化演示初始化完成', 'success');
    }

    async checkMicrophone() {
        try {
            const available = await checkMicrophoneAvailability();
            const isHttp = isHttpNonLocalhost();
            window.microphoneAvailable = available;
            window.isHttpNonLocalhost = isHttp;
        } catch {
            window.microphoneAvailable = false;
            window.isHttpNonLocalhost = isHttpNonLocalhost();
        }
    }

    registerStages() {
        const { hatchManager, els } = this;

        // 阶段1: 蛋（静态展示）
        hatchManager.registerStage('egg', {
            enter: () => {
                this.switchScene('hatchScene');
                resetEgg(els.eggSvg);
                clearCracks(els.crackGroup);
                startBreathing(els.eggSvg);
                els.hatchStartBtn.classList.remove('hidden');
                els.hatchProgress.style.display = 'none';
                if (els.hatchHint) els.hatchHint.textContent = '';
            },
            exit: () => {
                stopBreathing(els.eggSvg);
                els.hatchStartBtn.classList.add('hidden');
            }
        });

        // 阶段2: 孵化（用户互动）
        hatchManager.registerStage('hatching', {
            enter: () => {
                this.hatchingController = createHatchingController({
                    eggSvg: els.eggSvg,
                    crackGroup: els.crackGroup,
                    progressContainer: els.hatchProgress,
                    progressFill: els.progressFill,
                    progressText: els.progressText,
                    particleContainer: els.particleContainer,
                    hintEl: els.hatchHint,
                    onComplete: () => hatchManager.goTo('cracking'),
                });
                this.hatchingController.start();
            },
            exit: () => {
                if (this.hatchingController) {
                    this.hatchingController.stop();
                    this.hatchingController = null;
                }
            }
        });

        // 阶段3: 破壳
        let crackingApiTimer = null;
        hatchManager.registerStage('cracking', {
            enter: async () => {
                if (els.hatchHint) els.hatchHint.textContent = '即将破壳...';

                // 播放破壳动画（4秒），在1.5秒时分裂时调用API
                const crackingPromise = playCrackingAnimation({
                    eggSvg: els.eggSvg,
                    particleContainer: els.particleContainer,
                    eggWrapper: els.eggWrapper,
                }, 4000);

                // 蛋分裂时发起API请求（1.5秒后）
                crackingApiTimer = setTimeout(async () => {
                    crackingApiTimer = null;
                    try {
                        const config = getConfig();
                        const otaUrl = document.getElementById('otaUrl')?.value?.trim() || '';
                        this.birthResult = await showBirthScene({
                            birthScene: els.birthScene,
                            birthRabbitContainer: els.birthRabbitContainer,
                            petNameEl: els.petName,
                            petMbtiEl: els.petMbti,
                            petZodiacEl: els.petZodiac,
                            petBirthDateEl: els.petBirthDate,
                            showToast: (msg) => this.showToast(msg),
                        }, otaUrl, config.deviceId);
                    } catch (err) {
                        this.showToast(err.message);
                    }
                }, 1500);

                await crackingPromise;
                // 破壳动画完成后等待API结果（最多再等3秒）
                const waitStart = Date.now();
                while (!this.birthResult && crackingApiTimer !== null && Date.now() - waitStart < 3000) {
                    await new Promise(r => setTimeout(r, 100));
                }
                // 清除未执行的API定时器
                if (crackingApiTimer !== null) {
                    clearTimeout(crackingApiTimer);
                    crackingApiTimer = null;
                }
                // 如果API还没返回，等待一下再切换
                await new Promise(r => setTimeout(r, 1000));
                await hatchManager.goTo('birth');
            },
            exit: () => {
                if (crackingApiTimer !== null) {
                    clearTimeout(crackingApiTimer);
                    crackingApiTimer = null;
                }
            }
        });

        // 阶段4: 出生展示
        let birthTransitionTimer = null;
        hatchManager.registerStage('birth', {
            enter: () => {
                // 出生场景已在 cracking 阶段显示
                // 展示3.5秒后过渡到聊天
                birthTransitionTimer = setTimeout(async () => {
                    birthTransitionTimer = null;
                    await hatchManager.goTo('chat');
                }, 3500);
            },
            exit: () => {
                if (birthTransitionTimer !== null) {
                    clearTimeout(birthTransitionTimer);
                    birthTransitionTimer = null;
                }
                els.birthScene.classList.remove('active');
            }
        });

        // 阶段5: 聊天
        hatchManager.registerStage('chat', {
            enter: () => {
                this.switchScene('chatScene');

                // 创建兔子伴侣
                if (els.rabbitCompanionEl && !this.rabbitCompanion) {
                    this.rabbitCompanion = createRabbit(els.rabbitCompanionEl);
                }

                // 显示宠物信息面板
                this.fillPetInfoPanel();

                // 初始化 UI 控制器的事件（拨号、录音等）
                this.initChatControls();

                // 预填 OTA 地址为默认值
                this.prefillOtaUrl();
            }
        });

        // 绑定开始按钮
        els.hatchStartBtn.addEventListener('click', () => {
            hatchManager.goTo('hatching');
        });
    }

    initChatControls() {
        // 拨号按钮事件由 uiController.init() 统一处理，不再重复绑定

        // 录音按钮
        const recordBtn = document.getElementById('recordBtn');
        if (recordBtn && !recordBtn._hatchBound) {
            recordBtn._hatchBound = true;
            recordBtn.addEventListener('click', () => {
                const audioRecorder = getAudioRecorder();
                if (audioRecorder.isRecording) {
                    audioRecorder.stop();
                    recordBtn.classList.remove('recording');
                    recordBtn.querySelector('.btn-text').textContent = '录音';
                } else {
                    recordBtn.classList.add('recording');
                    recordBtn.querySelector('.btn-text').textContent = '录音中';
                    setTimeout(() => audioRecorder.start(), 100);
                }
            });
        }

        // 聊天输入
        const chatIpt = document.getElementById('chatIpt');
        if (chatIpt && !chatIpt._hatchBound) {
            chatIpt._hatchBound = true;
            chatIpt.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' && e.target.value) {
                    getWebSocketHandler().sendTextMessage(e.target.value);
                    e.target.value = '';
                }
            });
        }
    }

    initRehatchButton() {
        const { els, hatchManager } = this;
        if (els.rehatchBtn) {
            els.rehatchBtn.addEventListener('click', () => {
                // 断开 WebSocket
                const wsHandler = getWebSocketHandler();
                if (wsHandler.isConnected()) {
                    wsHandler.disconnect();
                }

                // 清理兔子伴侣
                this.rabbitCompanion = null;
                if (els.rabbitCompanionEl) els.rabbitCompanionEl.innerHTML = '';

                // 重置聊天流
                const chatStream = document.getElementById('chatStream');
                if (chatStream) chatStream.innerHTML = '';

                // 隐藏宠物信息面板
                const petInfoPanel = document.getElementById('petInfoPanel');
                if (petInfoPanel) petInfoPanel.style.display = 'none';

                // 重置到蛋阶段
                hatchManager.reset();
            });
        }
    }

    switchScene(sceneId) {
        document.querySelectorAll('.scene').forEach(s => s.classList.remove('active'));
        const scene = document.getElementById(sceneId);
        if (scene) scene.classList.add('active');
    }

    /** 星座英文→中文映射 */
    static ZODIAC_NAMES = {
        aries: '白羊座', taurus: '金牛座', gemini: '双子座', cancer: '巨蟹座',
        leo: '狮子座', virgo: '处女座', libra: '天秤座', scorpio: '天蝎座',
        sagittarius: '射手座', capricorn: '摩羯座', aquarius: '水瓶座', pisces: '双鱼座',
    };

    /** 五行英文→中文映射 */
    static WUXING_NAMES = {
        metal: '金', wood: '木', water: '水', fire: '火', earth: '土',
    };

    /** 八字key→中文映射 */
    static BAZI_LABELS = {
        year: '年柱', month: '月柱', day: '日柱', hour: '时柱',
    };

    /** 填充聊天场景中的宠物信息面板 */
    fillPetInfoPanel() {
        const panel = document.getElementById('petInfoPanel');
        if (!panel) return;

        const petData = this.birthResult?.petData;
        if (!petData) {
            panel.style.display = 'none';
            return;
        }

        document.getElementById('petInfoPanelName').textContent = petData.nickname || '';
        document.getElementById('petInfoPanelBirthDate').textContent = this.formatBirthDate(petData.birthDate);
        document.getElementById('petInfoPanelZodiac').textContent =
            HatchApp.ZODIAC_NAMES[petData.zodiac] || petData.zodiac || '';
        document.getElementById('petInfoPanelMbti').textContent = petData.mbti || '';
        document.getElementById('petInfoPanelBazi').textContent = this.formatBazi(petData.bazi);
        document.getElementById('petInfoPanelWuxing').textContent = this.formatWuxing(petData.wuxing);
        document.getElementById('petInfoPanelPersonality').textContent = petData.personality || '';

        panel.style.display = 'block';
    }

    /** 格式化出生日期 */
    formatBirthDate(dateStr) {
        if (!dateStr) return '';
        const d = new Date(dateStr);
        const pad = n => String(n).padStart(2, '0');
        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
    }

    /** 格式化八字 JSON → "年柱-丙午、月柱-壬辰、日柱-乙亥、时柱-丙戌" */
    formatBazi(baziStr) {
        if (!baziStr) return '';
        try {
            const bazi = typeof baziStr === 'string' ? JSON.parse(baziStr) : baziStr;
            const labels = HatchApp.BAZI_LABELS;
            const order = ['year', 'month', 'day', 'hour'];
            return order
                .map(k => `${labels[k]}-${bazi[k] || ''}`)
                .join('、');
        } catch {
            return '';
        }
    }

    /** 格式化五行 JSON → "火-5分、水-4分、土-3分、木-2分、金-0分"（按分数降序，全部显示） */
    formatWuxing(wuxingStr) {
        if (!wuxingStr) return '';
        try {
            const wuxing = typeof wuxingStr === 'string' ? JSON.parse(wuxingStr) : wuxingStr;
            const names = HatchApp.WUXING_NAMES;
            return Object.entries(wuxing)
                .map(([k, v]) => ({ name: names[k] || k, count: v }))
                .sort((a, b) => b.count - a.count)
                .map(item => `${item.name}-${item.count}分`)
                .join('、');
        } catch {
            return '';
        }
    }

    async prefillOtaUrl() {
        const otaUrlInput = document.getElementById('otaUrl');
        if (otaUrlInput && !otaUrlInput.value.trim()) {
            otaUrlInput.value = 'http://localhost:8002/xiaozhi/ota/';
        }

        // 注册 WebSocket 事件回调（不自动连接）
        const wsHandler = getWebSocketHandler();
        wsHandler.onConnectionStateChange = (isConnected) => {
            uiController.updateConnectionUI(isConnected);
            uiController.updateDialButton(isConnected);
        };
        wsHandler.onChatMessage = (text, isUser) => {
            uiController.addChatMessage(text, isUser);
        };
        wsHandler.onRecordButtonStateChange = (isRecording) => {
            const recordBtn = document.getElementById('recordBtn');
            if (recordBtn) {
                if (isRecording) {
                    recordBtn.classList.add('recording');
                    recordBtn.querySelector('.btn-text').textContent = '录音中';
                } else {
                    recordBtn.classList.remove('recording');
                    recordBtn.querySelector('.btn-text').textContent = '录音';
                }
            }
        };

        const chatIpt = document.getElementById('chatIpt');
        if (chatIpt) chatIpt.style.display = 'flex';
    }

    showToast(message) {
        const toast = document.createElement('div');
        toast.className = 'toast';
        toast.textContent = message;
        this.els.toastContainer.appendChild(toast);
        setTimeout(() => toast.remove(), 3000);
    }
}

// 启动
const hatchApp = new HatchApp();
window.chatApp = hatchApp;
window.hatchApp = hatchApp;
document.addEventListener('DOMContentLoaded', () => hatchApp.init());
