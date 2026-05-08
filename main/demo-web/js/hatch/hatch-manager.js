/**
 * 孵化状态机
 * 管理5个阶段的切换：egg -> hatching -> cracking -> birth -> chat
 */

/** @typedef {'egg'|'hatching'|'cracking'|'birth'|'chat'} HatchStage */

export class HatchManager {
    constructor() {
        /** @type {HatchStage} */
        this.currentStage = 'egg';
        /** @type {Map<HatchStage, Function>} */
        this.stageHandlers = new Map();
        this.onStageChange = null;
    }

    /**
     * 注册阶段处理函数
     * @param {HatchStage} stage
     * @param {{ enter: () => void | Promise<void>, exit?: () => void }} handler
     */
    registerStage(stage, handler) {
        this.stageHandlers.set(stage, handler);
    }

    /**
     * 切换到指定阶段
     * @param {HatchStage} stage
     */
    async goTo(stage) {
        const prev = this.currentStage;
        if (prev === stage) return;

        // 退出当前阶段
        const prevHandler = this.stageHandlers.get(prev);
        if (prevHandler && prevHandler.exit) {
            prevHandler.exit();
        }

        this.currentStage = stage;

        // 进入新阶段
        const nextHandler = this.stageHandlers.get(stage);
        if (nextHandler && nextHandler.enter) {
            await nextHandler.enter();
        }

        if (this.onStageChange) {
            this.onStageChange(stage, prev);
        }
    }

    /**
     * 重置到初始状态
     */
    async reset() {
        await this.goTo('egg');
    }

    /**
     * 获取当前阶段
     * @returns {HatchStage}
     */
    getStage() {
        return this.currentStage;
    }
}
