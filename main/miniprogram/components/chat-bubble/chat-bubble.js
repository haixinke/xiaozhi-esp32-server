/**
 * components/chat-bubble/chat-bubble.js
 *
 * 聊天气泡：区分用户/AI 消息，支持流式追加文本（typing 模式下显示光效）。
 */
Component({
  properties: {
    /** 'user' | 'assistant' */
    role: {
      type: String,
      value: 'assistant',
    },
    /** 文本内容 */
    content: {
      type: String,
      value: '',
    },
    /** 是否流式输出中（显示光效） */
    typing: {
      type: Boolean,
      value: false,
    },
    /** 深色模式 */
    darkMode: {
      type: Boolean,
      value: false,
    },
  },
});
