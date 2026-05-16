// UI controller module
import { loadConfig, saveConfig, getConfig } from '../config/manager.js';
import { getAudioPlayer } from '../core/audio/player.js';
import { getAudioRecorder } from '../core/audio/recorder.js';
import { getWebSocketHandler } from '../core/network/websocket.js';
import { dataClient } from '../api/data-client.js';

// UI controller class
class UIController {
    constructor() {
        this.isEditing = false;
        this.visualizerCanvas = null;
        this.visualizerContext = null;
        this.audioStatsTimer = null;
        this.currentBackgroundIndex = localStorage.getItem('backgroundIndex') ? parseInt(localStorage.getItem('backgroundIndex')) : 0;
        this.backgroundImages = ['4.jpg', '5.jpg', '6.png'];
        this.dialBtnDisabled = false;

        // Data tabs state
        this.dataTabsState = {
            'chat-history': { currentPage: 1, totalPages: 1, data: null },
            'memory': { currentPage: 1, totalPages: 1, data: null }
        };

        // Bind methods
        this.init = this.init.bind(this);
        this.initEventListeners = this.initEventListeners.bind(this);
        this.updateDialButton = this.updateDialButton.bind(this);
        this.addChatMessage = this.addChatMessage.bind(this);
        this.switchBackground = this.switchBackground.bind(this);
        this.switchLive2DModel = this.switchLive2DModel.bind(this);
        this.showModal = this.showModal.bind(this);
        this.hideModal = this.hideModal.bind(this);
        this.switchTab = this.switchTab.bind(this);
    }

    // Initialize
    init() {
        console.log('UIController init started');

        this.visualizerCanvas = document.getElementById('audioVisualizer');
        if (this.visualizerCanvas) {
            this.visualizerContext = this.visualizerCanvas.getContext('2d');
            this.initVisualizer();
        }

        // Check if connect button exists during initialization
        const connectBtn = document.getElementById('connectBtn');
        console.log('connectBtn during init:', connectBtn);

        this.initEventListeners();
        this.startAudioStatsMonitor();
        this.initDataTabs();
        loadConfig();

        // Register recording callback
        const audioRecorder = getAudioRecorder();
        audioRecorder.onRecordingStart = (seconds) => {
            this.updateRecordButtonState(true, seconds);
        };

        // Initialize status display
        this.updateConnectionUI(false);
        // Apply saved background
        const backgroundContainer = document.querySelector('.background-container');
        if (backgroundContainer) {
            backgroundContainer.style.backgroundImage = `url('/images/${this.backgroundImages[this.currentBackgroundIndex]}')`;
        }

        this.updateDialButton(false);

        console.log('UIController init completed');
    }

    // Initialize visualizer
    initVisualizer() {
        if (this.visualizerCanvas) {
            this.visualizerCanvas.width = this.visualizerCanvas.clientWidth;
            this.visualizerCanvas.height = this.visualizerCanvas.clientHeight;
            this.visualizerContext.fillStyle = '#fafafa';
            this.visualizerContext.fillRect(0, 0, this.visualizerCanvas.width, this.visualizerCanvas.height);
        }
    }

    // Initialize event listeners
    initEventListeners() {
        // Settings button
        const settingsBtn = document.getElementById('settingsBtn');
        if (settingsBtn) {
            settingsBtn.addEventListener('click', () => {
                this.showModal('settingsModal');
            });
        }

        // Background switch button
        const backgroundBtn = document.getElementById('backgroundBtn');
        if (backgroundBtn) {
            backgroundBtn.addEventListener('click', this.switchBackground);
        }

        // Model select change event
        const modelSelect = document.getElementById('live2dModelSelect');
        if (modelSelect) {
            modelSelect.addEventListener('change', () => {
                this.switchLive2DModel();
            });
        }

        // Camera switch button
        const cameraSwitch = document.getElementById('cameraSwitch');
        const cameraSwitchMask = document.getElementById('cameraSwitchMask');
        if (cameraSwitchMask) {
            cameraSwitchMask.addEventListener('click', () => {
                const isCameraActive = cameraSwitch.classList.contains('active');
                if (isCameraActive) {
                    window.switchCamera();
                }
            })
        }

        // Dial button
        const dialBtn = document.getElementById('dialBtn');
        if (dialBtn) {
            dialBtn.addEventListener('click', () => {
                dialBtn.disabled = true;
                this.dialBtnDisabled = true;
                setTimeout(() => {
                    dialBtn.disabled = false;
                    this.dialBtnDisabled = false;
                }, 3000);

                const wsHandler = getWebSocketHandler();
                const isConnected = wsHandler.isConnected();

                if (isConnected) {
                    wsHandler.disconnect();
                    this.updateDialButton(false);
                    if (cameraSwitch) cameraSwitch.classList.remove('active');
                    this.addChatMessage('Disconnected, see you next time~😊', false);
                } else {
                    // Check if OTA URL is filled
                    const otaUrlInput = document.getElementById('otaUrl');
                    if (!otaUrlInput || !otaUrlInput.value.trim()) {
                        // If OTA URL is not filled, show settings modal and switch to device tab
                        this.showModal('settingsModal');
                        this.switchTab('device');
                        this.addChatMessage('Please fill in OTA server URL', false);
                        return;
                    }

                    // Start connection process
                    this.handleConnect();
                }
            });
        }

        // Camera button
        const cameraBtn = document.getElementById('cameraBtn');
        let cameraTimer = null;
        if (cameraBtn) {
            cameraBtn.addEventListener('click', () => {
                if (cameraTimer) {
                    clearTimeout(cameraTimer);
                    cameraTimer = null;
                }
                cameraTimer = setTimeout(() => {
                    const cameraContainer = document.getElementById('cameraContainer');
                    if (!cameraContainer) {
                        log('摄像头容器不存在', 'warning');
                        return;
                    }

                    const isActive = cameraContainer.classList.contains('active');
                    if (isActive) {
                        // 关闭摄像头
                        if (typeof window.stopCamera === 'function') {
                            if (cameraSwitch) cameraSwitch.classList.remove('active');
                            window.stopCamera();
                        }
                        cameraContainer.classList.remove('active');
                        cameraBtn.classList.remove('camera-active');
                        cameraBtn.querySelector('.btn-text').textContent = '摄像头';
                        log('摄像头已关闭', 'info');
                    } else {
                        // 打开摄像头
                        if (typeof window.startCamera === 'function') {
                            window.startCamera().then(success => {
                                if (success) {
                                    cameraBtn.classList.add('camera-active');
                                    cameraBtn.querySelector('.btn-text').textContent = '关闭';
                                } else {
                                    this.addChatMessage('⚠️ 摄像头启动失败，请检查浏览器权限', false);
                                }
                            }).catch(error => {
                                log(`启动摄像头异常: ${error.message}`, 'error');
                            });
                        } else {
                            log('startCamera函数未定义', 'warning');
                        }
                    }
                }, 300);
            });
        }

        // Record button
        const recordBtn = document.getElementById('recordBtn');
        if (recordBtn) {
            let recordTimer = null;
            recordBtn.addEventListener('click', () => {
                if (recordTimer) {
                    clearTimeout(recordTimer);
                    recordTimer = null;
                }
                recordTimer = setTimeout(() => {
                    const audioRecorder = getAudioRecorder();
                    if (audioRecorder.isRecording) {
                        audioRecorder.stop();
                        // Restore record button to normal state
                        recordBtn.classList.remove('recording');
                        recordBtn.querySelector('.btn-text').textContent = '录音';
                    } else {
                        // Update button state to recording
                        recordBtn.classList.add('recording');
                        recordBtn.querySelector('.btn-text').textContent = '录音中';

                        // Start recording, update button state after delay
                        setTimeout(() => {
                            audioRecorder.start();
                        }, 100);
                    }
                }, 300);
            });
        }

        // Chat input event listener
        const chatIpt = document.getElementById('chatIpt');
        if (chatIpt) {
            const wsHandler = getWebSocketHandler();
            chatIpt.addEventListener('keydown', (e) => {
                if (e.key === 'Enter') {
                    if (e.target.value) {
                        wsHandler.sendTextMessage(e.target.value);
                        e.target.value = '';
                        return;
                    }
                }
            });
        }

        // Close button
        const closeButtons = document.querySelectorAll('.close-btn');
        closeButtons.forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const modal = e.target.closest('.modal');
                if (modal) {
                    if (modal.id === 'settingsModal') {
                        saveConfig();
                    }
                    this.hideModal(modal.id);
                }
            });
        });

        // Settings tab switch
        const tabBtns = document.querySelectorAll('.tab-btn');
        tabBtns.forEach(btn => {
            btn.addEventListener('click', (e) => {
                this.switchTab(e.target.dataset.tab);
            });
        });

        // 点击模态框背景关闭（仅对特定模态框禁用此功能）
        const modals = document.querySelectorAll('.modal');
        modals.forEach(modal => {
            modal.addEventListener('click', (e) => {
                if (e.target === modal) {
                    // settingsModal、mcpToolModal、mcpPropertyModal 只能通过点击X关闭
                    const nonClosableModals = ['settingsModal', 'mcpToolModal', 'mcpPropertyModal'];
                    if (nonClosableModals.includes(modal.id)) {
                        return; // 禁止点击背景关闭
                    }
                    this.hideModal(modal.id);
                }
            });
        });

        // Add MCP tool button
        const addMCPToolBtn = document.getElementById('addMCPToolBtn');
        if (addMCPToolBtn) {
            addMCPToolBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.addMCPTool();
            });
        }

        // Connect button and send button are not removed, can be added to dial button later
    }

    // Update connection status UI
    updateConnectionUI(isConnected) {
        const connectionStatus = document.getElementById('connectionStatus');
        const statusDot = document.querySelector('.status-dot');

        if (connectionStatus) {
            if (isConnected) {
                connectionStatus.textContent = '已连接';
                if (statusDot) {
                    statusDot.className = 'status-dot status-connected';
                }
            } else {
                connectionStatus.textContent = '离线';
                if (statusDot) {
                    statusDot.className = 'status-dot status-disconnected';
                }
            }
        }

        // 同步更新第二个连接状态元素（孵化页面）
        const connectionStatus2 = document.getElementById('connectionStatus2');
        if (connectionStatus2) {
            connectionStatus2.textContent = isConnected ? '已连接' : '离线';
        }
    }

    // Update dial button state
    updateDialButton(isConnected) {
        const dialBtn = document.getElementById('dialBtn');
        const recordBtn = document.getElementById('recordBtn');
        const cameraBtn = document.getElementById('cameraBtn');

        if (dialBtn) {
            if (isConnected) {
                dialBtn.classList.add('dial-active');
                dialBtn.querySelector('.btn-text').textContent = '挂断';
                // Update dial button icon to hang up icon
                dialBtn.querySelector('svg').innerHTML = `
                    <path d="M12,9C10.4,9 9,10.4 9,12C9,13.6 10.4,15 12,15C13.6,15 15,13.6 15,12C15,10.4 13.6,9 12,9M12,17C9.2,17 7,14.8 7,12C7,9.2 9.2,7 12,7C14.8,7 17,9.2 17,12C17,14.8 14.8,17 12,17M12,4.5C7,4.5 2.7,7.6 1,12C2.7,16.4 7,19.5 12,19.5C17,19.5 21.3,16.4 23,12C21.3,7.6 17,4.5 12,4.5Z"/>
                `;
            } else {
                dialBtn.classList.remove('dial-active');
                dialBtn.querySelector('.btn-text').textContent = '拨号';
                // Restore dial button icon
                dialBtn.querySelector('svg').innerHTML = `
                    <path d="M6.62,10.79C8.06,13.62 10.38,15.94 13.21,17.38L15.41,15.18C15.69,14.9 16.08,14.82 16.43,14.93C17.55,15.3 18.75,15.5 20,15.5A1,1 0 0,1 21,16.5V20A1,1 0 0,1 20,21A17,17 0 0,1 3,4A1,1 0 0,1 4,3H7.5A1,1 0 0,1 8.5,4C8.5,5.25 8.7,6.45 9.07,7.57C9.18,7.92 9.1,8.31 8.82,8.59L6.62,10.79Z"/>
                `;
            }
        }

        // Update camera button state - reset to default when disconnected
        if (cameraBtn && !isConnected) {
            const cameraContainer = document.getElementById('cameraContainer');
            if (cameraContainer && cameraContainer.classList.contains('active')) {
                cameraContainer.classList.remove('active');
            }
            cameraBtn.classList.remove('camera-active');
            cameraBtn.querySelector('.btn-text').textContent = '摄像头';
            cameraBtn.disabled = true;
            cameraBtn.title = '请先连接服务器';
            // 关闭摄像头
            if (typeof window.stopCamera === 'function') {
                window.stopCamera();
            }
        }

        // Update camera button state - enable when connected and camera is available
        if (cameraBtn && isConnected) {
            if (window.cameraAvailable) {
                cameraBtn.disabled = false;
                cameraBtn.title = '打开/关闭摄像头';
            } else {
                cameraBtn.disabled = true;
                cameraBtn.title = '请先绑定验证码';
            }
        }

        // Update record button state
        if (recordBtn) {
            const microphoneAvailable = window.microphoneAvailable !== false;
            if (isConnected && microphoneAvailable) {
                recordBtn.disabled = false;
                recordBtn.title = '开始录音';
                // Restore record button to normal state
                recordBtn.querySelector('.btn-text').textContent = '录音';
                recordBtn.classList.remove('recording');
            } else {
                recordBtn.disabled = true;
                if (!microphoneAvailable) {
                    recordBtn.title = window.isHttpNonLocalhost ? '当前由于是http访问，无法录音，只能用文字交互' : '麦克风不可用';
                } else {
                    recordBtn.title = '请先连接服务器';
                }
                // Restore record button to normal state
                recordBtn.querySelector('.btn-text').textContent = '录音';
                recordBtn.classList.remove('recording');
            }
        }
    }

    // Update record button state
    updateRecordButtonState(isRecording, seconds = 0) {
        const recordBtn = document.getElementById('recordBtn');
        if (recordBtn) {
            if (isRecording) {
                recordBtn.querySelector('.btn-text').textContent = `录音中`;
                recordBtn.classList.add('recording');
            } else {
                recordBtn.querySelector('.btn-text').textContent = '录音';
                recordBtn.classList.remove('recording');
            }
            // Only enable button when microphone is available
            recordBtn.disabled = window.microphoneAvailable === false;
        }
    }

    /**
     * Update microphone availability state
     * @param {boolean} isAvailable - Whether microphone is available
     * @param {boolean} isHttpNonLocalhost - Whether it is HTTP non-localhost access
     */
    updateMicrophoneAvailability(isAvailable, isHttpNonLocalhost) {
        const recordBtn = document.getElementById('recordBtn');
        if (!recordBtn) return;
        if (!isAvailable) {
            // Disable record button
            recordBtn.disabled = true;
            // Update button text and title
            recordBtn.querySelector('.btn-text').textContent = '录音';
            recordBtn.title = isHttpNonLocalhost ? '当前由于是http访问，无法录音，只能用文字交互' : '麦克风不可用';

        } else {
            // If connected, enable record button
            const wsHandler = getWebSocketHandler();
            if (wsHandler && wsHandler.isConnected()) {
                recordBtn.disabled = false;
                recordBtn.title = '开始录音';
            }
        }
    }

    // Add chat message
    addChatMessage(content, isUser = false) {
        const chatStream = document.getElementById('chatStream');
        if (!chatStream) return;

        const messageDiv = document.createElement('div');
        messageDiv.className = `chat-message ${isUser ? 'user' : 'ai'}`;
        messageDiv.innerHTML = `<div class="message-bubble">${content}</div>`;
        chatStream.appendChild(messageDiv);

        // Scroll to bottom
        chatStream.scrollTop = chatStream.scrollHeight;
    }

    // Switch background
    switchBackground() {
        this.currentBackgroundIndex = (this.currentBackgroundIndex + 1) % this.backgroundImages.length;
        const backgroundContainer = document.querySelector('.background-container');
        if (backgroundContainer) {
            backgroundContainer.style.backgroundImage = `url('/images/${this.backgroundImages[this.currentBackgroundIndex]}')`;
        }
        localStorage.setItem('backgroundIndex', this.currentBackgroundIndex);
    }

    // Switch Live2D model
    switchLive2DModel() {
        const modelSelect = document.getElementById('live2dModelSelect');
        if (!modelSelect) {
            console.error('模型选择下拉框不存在');
            return;
        }

        const selectedModel = modelSelect.value;
        const app = window.chatApp;

        if (app && app.live2dManager) {
            app.live2dManager.switchModel(selectedModel)
                .then(success => {
                    if (success) {
                        this.addChatMessage(`已切换到模型: ${selectedModel}`, false);
                    } else {
                        this.addChatMessage('模型切换失败', false);
                    }
                })
                .catch(error => {
                    console.error('模型切换错误:', error);
                    this.addChatMessage('模型切换出错', false);
                });
        } else {
            this.addChatMessage('Live2D管理器未初始化', false);
        }
    }

    // Show modal
    showModal(modalId) {
        const modal = document.getElementById(modalId);
        if (modal) {
            modal.style.display = 'flex';
        }
    }

    // Hide modal
    hideModal(modalId) {
        const modal = document.getElementById(modalId);
        if (modal) {
            modal.style.display = 'none';
        }
    }

    // Switch tab
    switchTab(tabName) {
        // Remove active class from all tabs
        const tabBtns = document.querySelectorAll('.tab-btn');
        const tabContents = document.querySelectorAll('.tab-content');

        tabBtns.forEach(btn => btn.classList.remove('active'));
        tabContents.forEach(content => content.classList.remove('active'));

        // Activate selected tab
        const activeTabBtn = document.querySelector(`[data-tab="${tabName}"]`);
        const activeTabContent = document.getElementById(`${tabName}Tab`);

        if (activeTabBtn && activeTabContent) {
            activeTabBtn.classList.add('active');
            activeTabContent.classList.add('active');
        }

        // Fetch data when switching to data tabs
        if (['chat-history', 'memory'].includes(tabName)) {
            this.fetchDataForTab(tabName);
        } else if (tabName === 'profile') {
            this.fetchProfile();
        }
    }

    // Start AI chat session after connection
    startAIChatSession() {
        this.addChatMessage('连接成功，开始聊天吧~😊', false);
        // Check microphone availability and show error messages if needed
        if (!window.microphoneAvailable) {
            if (window.isHttpNonLocalhost) {
                this.addChatMessage('⚠️ 当前由于是http访问，无法录音，只能用文字交互', false);
            } else {
                this.addChatMessage('⚠️ 麦克风不可用，请检查权限设置，只能用文字交互', false);
            }
        }
        // Start recording only if microphone is available
        if (window.microphoneAvailable) {
            const recordBtn = document.getElementById('recordBtn');
            if (recordBtn) {
                recordBtn.click();
            }
        }
        // Start camera only if camera is available (bound with verification code)
        if (window.cameraAvailable && typeof window.startCamera === 'function') {
            window.startCamera().then(success => {
                if (success) {
                    const cameraBtn = document.getElementById('cameraBtn');
                    if (cameraBtn) {
                        cameraBtn.classList.add('camera-active');
                        cameraBtn.querySelector('.btn-text').textContent = '关闭';
                    }
                } else {
                    this.addChatMessage('⚠️ 摄像头启动失败，可能被浏览器拒绝', false);
                }
            }).catch(error => {
                log(`启动摄像头异常: ${error.message}`, 'error');
            });
        }
    }

    // Handle connect button click
    async handleConnect() {
        console.log('handleConnect called');

        // Switch to device settings tab
        this.switchTab('device');

        // Wait for DOM update
        await new Promise(resolve => setTimeout(resolve, 50));

        const otaUrlInput = document.getElementById('otaUrl');

        console.log('otaUrl element:', otaUrlInput);

        if (!otaUrlInput || !otaUrlInput.value) {
            this.addChatMessage('请输入OTA服务器地址', false);
            return;
        }

        const otaUrl = otaUrlInput.value;
        console.log('otaUrl value:', otaUrl);

        // Update dial button state to connecting
        const dialBtn = document.getElementById('dialBtn');
        if (dialBtn) {
            dialBtn.classList.add('dial-active');
            dialBtn.querySelector('.btn-text').textContent = '连接中...';
            dialBtn.disabled = true;
        }

        // Show connecting message
        this.addChatMessage('正在连接服务器...', false);

        const chatIpt = document.getElementById('chatIpt');
        if (chatIpt) {
            chatIpt.style.display = 'flex';
        }

        try {

            // Get WebSocket handler instance
            const wsHandler = getWebSocketHandler();

            // Register connection state callback BEFORE connecting
            wsHandler.onConnectionStateChange = (isConnected) => {
                this.updateConnectionUI(isConnected);
                this.updateDialButton(isConnected);
            };

            // Register chat message callback BEFORE connecting
            wsHandler.onChatMessage = (text, isUser) => {
                this.addChatMessage(text, isUser);
            };

            // Register record button state callback BEFORE connecting
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

            const isConnected = await wsHandler.connect();

            if (isConnected) {
                // Initialize data client with connection parameters
                const config = getConfig();
                const otaUrlInput = document.getElementById('otaUrl');
                if (otaUrlInput && otaUrlInput.value) {
                    dataClient.init(otaUrlInput.value, config.deviceMac, config.clientId);
                }

                // Check microphone availability (check again after connection)
                const { checkMicrophoneAvailability } = await import('../core/audio/recorder.js');
                const micAvailable = await checkMicrophoneAvailability();

                if (!micAvailable) {
                    const isHttp = window.isHttpNonLocalhost;
                    if (isHttp) {
                        this.addChatMessage('⚠️ 当前由于是http访问，无法录音，只能用文字交互', false);
                    }
                    // Update global state
                    window.microphoneAvailable = false;
                }

                // Update dial button state
                const dialBtn = document.getElementById('dialBtn');
                if (dialBtn) {
                    if (!this.dialBtnDisabled) {
                        dialBtn.disabled = false;
                    }
                    dialBtn.querySelector('.btn-text').textContent = '挂断';
                    dialBtn.classList.add('dial-active');
                }

                this.hideModal('settingsModal');
            } else {
                throw new Error('OTA连接失败');
            }
        } catch (error) {
            console.error('Connection error details:', {
                message: error.message,
                stack: error.stack,
                name: error.name
            });

            // Show error message
            const errorMessage = error.message.includes('Cannot set properties of null')
                ? '连接失败：请检查设备连接'
                : `连接失败: ${error.message}`;

            this.addChatMessage(errorMessage, false);

            // Restore dial button state
            const dialBtn = document.getElementById('dialBtn');
            if (dialBtn) {
                if (!this.dialBtnDisabled) {
                    dialBtn.disabled = false;
                }
                dialBtn.querySelector('.btn-text').textContent = '拨号';
                dialBtn.classList.remove('dial-active');
                console.log('Dial button state restored successfully');
            }
        }
    }

    // Add MCP tool
    addMCPTool() {
        const mcpToolsList = document.getElementById('mcpToolsList');
        if (!mcpToolsList) return;

        const toolId = `mcp-tool-${Date.now()}`;
        const toolDiv = document.createElement('div');
        toolDiv.className = 'properties-container';
        toolDiv.innerHTML = `
            <div class="property-item">
                <input type="text" placeholder="工具名称" value="新工具">
                <input type="text" placeholder="工具描述" value="工具描述">
                <button class="remove-property" onclick="uiController.removeMCPTool('${toolId}')">删除</button>
            </div>
        `;

        mcpToolsList.appendChild(toolDiv);
    }

    // Remove MCP tool
    removeMCPTool(toolId) {
        const toolElement = document.getElementById(toolId);
        if (toolElement) {
            toolElement.remove();
        }
    }

    // Update audio statistics display
    updateAudioStats() {
        const audioPlayer = getAudioPlayer();
        if (!audioPlayer) return;

        const stats = audioPlayer.getAudioStats();
        // Here can add audio statistics UI update logic
    }

    // Start audio statistics monitor
    startAudioStatsMonitor() {
        // Update audio statistics every 100ms
        this.audioStatsTimer = setInterval(() => {
            this.updateAudioStats();
        }, 100);
    }

    // Stop audio statistics monitor
    stopAudioStatsMonitor() {
        if (this.audioStatsTimer) {
            clearInterval(this.audioStatsTimer);
            this.audioStatsTimer = null;
        }
    }

    // Draw audio visualizer waveform
    drawVisualizer(dataArray) {
        if (!this.visualizerContext || !this.visualizerCanvas) return;

        this.visualizerContext.fillStyle = '#fafafa';
        this.visualizerContext.fillRect(0, 0, this.visualizerCanvas.width, this.visualizerCanvas.height);

        const barWidth = (this.visualizerCanvas.width / dataArray.length) * 2.5;
        let barHeight;
        let x = 0;

        for (let i = 0; i < dataArray.length; i++) {
            barHeight = dataArray[i] / 2;

            // Create gradient color: from purple to blue to green
            const gradient = this.visualizerContext.createLinearGradient(0, 0, 0, this.visualizerCanvas.height);
            gradient.addColorStop(0, '#8e44ad');
            gradient.addColorStop(0.5, '#3498db');
            gradient.addColorStop(1, '#1abc9c');

            this.visualizerContext.fillStyle = gradient;
            this.visualizerContext.fillRect(x, this.visualizerCanvas.height - barHeight, barWidth, barHeight);
            x += barWidth + 1;
        }
    }

    // Update session status UI
    updateSessionStatus(isSpeaking) {
        // Here can add session status UI update logic
        // For example: update Live2D model's mouth movement status
    }

    // Update session emotion
    updateSessionEmotion(emoji) {
        // Here can add emotion update logic
        // For example: display emoji in status indicator
    }

    // ==================== Data Tabs Methods ====================

    /**
     * Fetch data for a specific tab
     * @param {string} tabName - Tab name (chat-history, memory, profile)
     */
    async fetchDataForTab(tabName) {
        if (!dataClient.isInitialized()) {
            this.showDataError(tabName, '请先连接设备');
            return;
        }

        try {
            this.showDataLoading(tabName, true);
            const state = this.dataTabsState[tabName];
            const page = state.currentPage;

            let data;
            switch (tabName) {
                case 'chat-history':
                    data = await dataClient.fetchChatHistory(dataClient.deviceId, page);
                    break;
                case 'memory':
                    data = await dataClient.fetchMemoryList(dataClient.deviceId, page);
                    break;
            }

            state.data = data;
            state.totalPages = Math.ceil(data.total / 20) || 1;

            this.renderDataForTab(tabName);
        } catch (error) {
            console.error(`Failed to fetch ${tabName}:`, error);
            this.showDataError(tabName, `加载失败: ${error.message}`);
        }
    }

    /**
     * Fetch user profile
     */
    async fetchProfile() {
        if (!dataClient.isInitialized()) {
            this.showProfileError('请先连接设备');
            return;
        }

        try {
            this.showProfileLoading(true);

            const profile = await dataClient.fetchProfile(dataClient.deviceId);

            if (!profile) {
                this.showProfileEmpty(true);
                this.showProfileLoading(false);
                this.showProfileError(null, false);
                return;
            }

            // Hide loading and empty states
            this.showProfileLoading(false);
            this.showProfileEmpty(false);
            this.showProfileError(null, false);

            // Render profile
            this.renderProfile(profile);
        } catch (error) {
            console.error('Failed to fetch profile:', error);
            this.showProfileError(`加载失败: ${error.message}`);
        }
    }

    /**
     * Render data for a specific tab
     * @param {string} tabName - Tab name
     */
    renderDataForTab(tabName) {
        const state = this.dataTabsState[tabName];
        const data = state.data;

        if (!data || !data.list || data.list.length === 0) {
            this.showDataEmpty(tabName);
            return;
        }

        // Hide loading and empty states
        this.showDataLoading(tabName, false);
        this.showDataEmpty(tabName, false, false);
        this.showDataError(tabName, null, false);

        // Render data
        switch (tabName) {
            case 'chat-history':
                this.renderChatHistory(data.list);
                break;
            case 'memory':
                this.renderMemoryList(data.list);
                break;
        }

        // Update pagination
        this.updatePagination(tabName);
    }

    /**
     * Render user profile
     * @param {Object} profile - Profile object
     */
    renderProfile(profile) {
        const container = document.getElementById('profileList');
        if (!container) return;

        container.innerHTML = '';

        const card = document.createElement('div');
        card.className = 'profile-card';

        const createdTime = this.formatDate(profile.createdAt);
        const updatedTime = this.formatDate(profile.updatedAt);

        // Parse topics if it's a JSON string or comma-separated
        let topics = [];
        if (profile.topics) {
            try {
                topics = JSON.parse(profile.topics);
            } catch {
                topics = profile.topics.split(',').map(t => t.trim()).filter(t => t);
            }
        }

        const topicsHtml = topics.map(topic =>
            `<span class="profile-topic-tag">${this.escapeHtml(topic)}</span>`
        ).join('');

        card.innerHTML = `
            <div class="profile-header">
                <span style="font-weight: 600;">用户画像</span>
                <span class="profile-time">${createdTime}</span>
            </div>
            <div class="profile-content">${this.escapeHtml(profile.profileContent || '')}</div>
            ${topics.length > 0 ? `<div class="profile-topics">${topicsHtml}</div>` : ''}
            ${profile.updatedAt ? `<div class="profile-updated">更新于 ${updatedTime}</div>` : ''}
        `;

        container.appendChild(card);
    }

    /**
     * Render chat history
     * @param {Array} list - Chat history list
     */
    renderChatHistory(list) {
        const container = document.getElementById('chatHistoryList');
        if (!container) return;

        container.innerHTML = '';

        list.forEach(item => {
            const messageDiv = document.createElement('div');
            const isUser = item.chatType === 1;

            messageDiv.className = `chat-message-item ${isUser ? 'user' : 'ai'}`;

            const timeStr = this.formatDate(item.createdAt);
            const typeLabel = isUser ? '用户' : 'AI助手';

            messageDiv.innerHTML = `
                <div class="chat-message-header">
                    <span>${typeLabel}</span>
                    <span class="chat-message-time">${timeStr}</span>
                </div>
                <div class="chat-message-content">${this.escapeHtml(item.content || '')}</div>
            `;

            container.appendChild(messageDiv);
        });
    }

    /**
     * Render memory list
     * @param {Array} list - Memory list
     */
    renderMemoryList(list) {
        const container = document.getElementById('memoryList');
        if (!container) return;

        container.innerHTML = '';

        list.forEach(item => {
            const card = document.createElement('div');
            card.className = 'memory-card';

            const createdTime = this.formatDate(item.createdAt);
            const updatedTime = this.formatDate(item.updatedAt);

            card.innerHTML = `
                <div class="memory-header">
                    <span class="memory-category">${this.escapeHtml(item.category || '未分类')}</span>
                    <span class="memory-time">${createdTime}</span>
                </div>
                <div class="memory-content">${this.escapeHtml(item.document || '')}</div>
                ${item.updatedAt ? `<div class="memory-updated">更新于 ${updatedTime}</div>` : ''}
            `;

            container.appendChild(card);
        });
    }

    /**
     * Render profile list
     * @param {Array} list - Profile list
     */
    renderProfileList(list) {
        const container = document.getElementById('profileList');
        if (!container) return;

        container.innerHTML = '';

        list.forEach(item => {
            const card = document.createElement('div');
            card.className = 'profile-card';

            const createdTime = this.formatDate(item.createdAt);
            const updatedTime = this.formatDate(item.updatedAt);

            // Parse topics if it's a JSON string or comma-separated
            let topics = [];
            if (item.topics) {
                try {
                    topics = JSON.parse(item.topics);
                } catch {
                    topics = item.topics.split(',').map(t => t.trim()).filter(t => t);
                }
            }

            const topicsHtml = topics.map(topic =>
                `<span class="profile-topic-tag">${this.escapeHtml(topic)}</span>`
            ).join('');

            card.innerHTML = `
                <div class="profile-header">
                    <span style="font-weight: 600;">用户画像</span>
                    <span class="profile-time">${createdTime}</span>
                </div>
                <div class="profile-content">${this.escapeHtml(item.profileContent || '')}</div>
                ${topics.length > 0 ? `<div class="profile-topics">${topicsHtml}</div>` : ''}
                ${item.updatedAt ? `<div class="profile-updated">更新于 ${updatedTime}</div>` : ''}
            `;

            container.appendChild(card);
        });
    }

    /**
     * Show loading state
     * @param {string} tabName - Tab name
     * @param {boolean} show - Show or hide
     */
    showDataLoading(tabName, show = true) {
        const loadingEl = document.getElementById(`${tabName.replace('-', '')}Loading`);
        if (loadingEl) {
            loadingEl.style.display = show ? 'flex' : 'none';
        }
    }

    /**
     * Show empty state
     * @param {string} tabName - Tab name
     * @param {boolean} show - Show or hide
     */
    showDataEmpty(tabName, show = true) {
        const emptyEl = document.getElementById(`${tabName.replace('-', '')}Empty`);
        if (emptyEl) {
            emptyEl.style.display = show ? 'block' : 'none';
        }
    }

    /**
     * Show error state
     * @param {string} tabName - Tab name
     * @param {string} message - Error message
     * @param {boolean} show - Show or hide
     */
    showDataError(tabName, message, show = true) {
        const errorEl = document.getElementById(`${tabName.replace('-', '')}Error`);
        if (errorEl) {
            if (show && message) {
                errorEl.textContent = message;
                errorEl.style.display = 'block';
            } else {
                errorEl.style.display = 'none';
            }
        }
    }

    /**
     * Show profile loading state
     * @param {boolean} show - Show or hide
     */
    showProfileLoading(show = true) {
        const loadingEl = document.getElementById('profileLoading');
        if (loadingEl) {
            loadingEl.style.display = show ? 'flex' : 'none';
        }
    }

    /**
     * Show profile empty state
     * @param {boolean} show - Show or hide
     */
    showProfileEmpty(show = true) {
        const emptyEl = document.getElementById('profileEmpty');
        if (emptyEl) {
            emptyEl.style.display = show ? 'block' : 'none';
        }
    }

    /**
     * Show profile error state
     * @param {string} message - Error message
     * @param {boolean} show - Show or hide
     */
    showProfileError(message, show = true) {
        const errorEl = document.getElementById('profileError');
        if (errorEl) {
            if (show && message) {
                errorEl.textContent = message;
                errorEl.style.display = 'block';
            } else {
                errorEl.style.display = 'none';
            }
        }
    }

    /**
     * Update pagination controls
     * @param {string} tabName - Tab name
     */
    updatePagination(tabName) {
        const state = this.dataTabsState[tabName];
        const paginationEl = document.getElementById(`${tabName.replace('-', '')}Pagination`);
        const pageInfo = document.getElementById(`${tabName.replace('-', '')}PageInfo`);
        const prevBtn = document.getElementById(`${tabName.replace('-', '')}PrevBtn`);
        const nextBtn = document.getElementById(`${tabName.replace('-', '')}NextBtn`);

        if (!paginationEl) return;

        paginationEl.style.display = 'flex';

        if (pageInfo) {
            pageInfo.textContent = `第 ${state.currentPage} / ${state.totalPages} 页`;
        }

        if (prevBtn) {
            prevBtn.disabled = state.currentPage <= 1;
        }

        if (nextBtn) {
            nextBtn.disabled = state.currentPage >= state.totalPages;
        }
    }

    /**
     * Handle pagination button click
     * @param {string} tabName - Tab name
     * @param {string} direction - 'prev' or 'next'
     */
    handlePagination(tabName, direction) {
        const state = this.dataTabsState[tabName];

        if (direction === 'prev' && state.currentPage > 1) {
            state.currentPage--;
        } else if (direction === 'next' && state.currentPage < state.totalPages) {
            state.currentPage++;
        } else {
            return;
        }

        this.fetchDataForTab(tabName);
    }

    /**
     * Initialize data tabs
     */
    initDataTabs() {
        // Chat history pagination
        const prevBtn = document.getElementById('chatHistoryPrevBtn');
        const nextBtn = document.getElementById('chatHistoryNextBtn');
        if (prevBtn) prevBtn.addEventListener('click', () => this.handlePagination('chat-history', 'prev'));
        if (nextBtn) nextBtn.addEventListener('click', () => this.handlePagination('chat-history', 'next'));

        // Memory pagination
        const memPrevBtn = document.getElementById('memoryPrevBtn');
        const memNextBtn = document.getElementById('memoryNextBtn');
        if (memPrevBtn) memPrevBtn.addEventListener('click', () => this.handlePagination('memory', 'prev'));
        if (memNextBtn) memNextBtn.addEventListener('click', () => this.handlePagination('memory', 'next'));

        // Profile tab doesn't need pagination (single record)
    }

    /**
     * Format date to string
     * @param {string} dateString - ISO date string
     * @returns {string}
     */
    formatDate(dateString) {
        if (!dateString) return '';

        try {
            const date = new Date(dateString);
            const now = new Date();
            const diffMs = now - date;
            const diffMins = Math.floor(diffMs / 60000);
            const diffHours = Math.floor(diffMs / 3600000);
            const diffDays = Math.floor(diffMs / 86400000);

            if (diffMins < 1) {
                return '刚刚';
            } else if (diffMins < 60) {
                return `${diffMins}分钟前`;
            } else if (diffHours < 24) {
                return `${diffHours}小时前`;
            } else if (diffDays < 7) {
                return `${diffDays}天前`;
            } else {
                return date.toLocaleDateString('zh-CN', {
                    year: 'numeric',
                    month: '2-digit',
                    day: '2-digit',
                    hour: '2-digit',
                    minute: '2-digit'
                });
            }
        } catch (e) {
            return dateString;
        }
    }

    /**
     * Escape HTML to prevent XSS
     * @param {string} text - Text to escape
     * @returns {string}
     */
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}

// Create singleton instance
export const uiController = new UIController();

// Export class for module usage
export { UIController };
