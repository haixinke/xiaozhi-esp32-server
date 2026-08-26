// 诉求类型本地兜底：仅在字典接口拉取失败时使用，正式数据以智控台字典管理（EGG_FEEDBACK_TYPE）为准
const FALLBACK_FEEDBACK_TYPES = Object.freeze([
  Object.freeze({ value: 'AI_CONTENT_VIOLATION', label: 'AI 虚拟宠物对话违规（涉政、色情、暴力、轻生、暧昧诱导）' }),
  Object.freeze({ value: 'AI_MISLEADING_ADVICE', label: 'AI 误导或给出不实医疗、理财建议' }),
  Object.freeze({ value: 'MINOR_USE', label: '未成年人使用相关问题（时长、模式、内容限制）' }),
  Object.freeze({ value: 'SENIOR_SUPPORT', label: '老年人操作、防诈骗或使用指引咨询' }),
  Object.freeze({ value: 'PRIVACY', label: '账号或聊天记录隐私保护问题' }),
  Object.freeze({ value: 'CRISIS_INTERVENTION', label: '极端情绪干预失效（自杀、抑郁、家暴未安抚）' }),
  Object.freeze({ value: 'FUNCTION_FAILURE', label: '功能故障或无法打开宠物对话' }),
  Object.freeze({ value: 'REMINDER_OR_AI_LABEL', label: '时长提醒或 AI 标识缺失' }),
  Object.freeze({ value: 'OTHER', label: '其他申诉、意见建议' })
]);

module.exports = { FALLBACK_FEEDBACK_TYPES };
