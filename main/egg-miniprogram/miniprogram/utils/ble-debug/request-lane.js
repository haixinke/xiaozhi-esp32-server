const { toBytes, fromBytes, createFrameReader, parseResponse, readResult } = require('./protocol');

const PACKET_GAP_MS = 30;
const ACK_TIMEOUT_MS = 2000;
const WRITE_TIMEOUT_MS = 5000;

function escapeInvalidFrame(text) {
  return text.replace(/[\\\x00-\x1f\x7f-\xff]/g, (character) =>
    `\\x${character.charCodeAt(0).toString(16).padStart(2, '0')}`);
}

function createRequestLane({ writePacket, onLog, onActivity, onResponse, onFailure, onCancelledReply = () => {} }) {
  const reader = createFrameReader();
  const retiredCommands = new Set();
  let active = null;
  let writing = null;
  let nextPacketTimer = null;
  let ackTimer = null;

  function clearRequestTimers() {
    clearTimeout(nextPacketTimer);
    clearTimeout(ackTimer);
    nextPacketTimer = null;
    ackTimer = null;
  }

  function finish(reply, error) {
    const request = active;
    active = null;
    clearRequestTimers();
    if (!request) return;

    if (error) request.reject(error);
    else request.resolve(reply);
    onActivity();
  }

  function accept(reply) {
    if (reply.error) {
      onFailure(new Error(reply.error));
      return;
    }
    let result;
    try {
      result = reply.ok ? { ...reply, fields: readResult(reply) } : reply;
    } catch (error) {
      onFailure(error);
      return;
    }
    onResponse(result);
    finish(result);
  }

  function sendNextPacket() {
    if (!active || writing) return;
    const request = active;
    const packet = request.packets[request.nextIndex];
    const flight = { request, timer: null };
    writing = flight;
    request.nextIndex += 1;
    if (request.nextIndex === 1) onLog('tx', request.frame);
    onLog('tx-part', packet);
    flight.timer = setTimeout(() => {
      if (writing === flight) onFailure(new Error('蓝牙写入回调超时'));
    }, WRITE_TIMEOUT_MS);

    writePacket(toBytes(packet)).then(() => {
      if (writing !== flight) return;
      clearTimeout(flight.timer);
      writing = null;
      if (active !== request) {
        sendNextPacket();
        return;
      }
      if (request.nextIndex < request.packets.length) {
        nextPacketTimer = setTimeout(sendNextPacket, PACKET_GAP_MS);
        return;
      }
      request.writeComplete = true;
      onLog('event', `${request.command} 写入完成，等待板端应答`);
      if (request.earlyReply) {
        accept(request.earlyReply);
        return;
      }
      ackTimer = setTimeout(() => onFailure(new Error(`${request.command} 应答超时`)), ACK_TIMEOUT_MS);
    }, (error) => {
      if (writing === flight) onFailure(error);
    });
  }

  function request(message) {
    if (retiredCommands.has(message.command)) {
      const error = new Error(`上一条 ${message.command} 的应答尚未收到，请等待迟到应答后重试，或重新连接`);
      onLog('error', error.message);
      return Promise.reject(error);
    }
    if (active && (message.command !== 'STOP' || active.command === 'STOP')) {
      return Promise.reject(new Error('有指令正在执行，请等待板端应答'));
    }
    if (active) {
      const previous = active;
      clearRequestTimers();
      // 协议按命令名匹配应答。已发完或正在写最后一包的指令可能仍有应答。
      if (previous.nextIndex === previous.packets.length && !previous.earlyReply) {
        retiredCommands.add(previous.command);
      }
      onLog('event', `STOP 优先，取消等待 ${previous.command} 应答及剩余分包`);
      previous.reject(new Error(`${previous.command} 已被 STOP 抢占`));
    }

    return new Promise((resolve, reject) => {
      active = { ...message, resolve, reject, nextIndex: 0, writeComplete: false, earlyReply: null };
      onActivity();
      sendNextPacket();
    });
  }

  function receive(value) {
    const request = active;
    const text = fromBytes(value);
    onLog('rx-part', text);
    for (const frame of reader.push(text)) {
      if (!frame.valid) {
        onLog('error', `丢弃无效或超长的通知报文 ${escapeInvalidFrame(frame.text)}`);
        if (request && active === request && request.command !== 'STOP') onFailure(new Error('通知报文格式有误'));
        continue;
      }

      onLog('rx', frame.text);
      const reply = parseResponse(frame.text);
      if (reply && retiredCommands.delete(reply.command)) {
        onLog('event', `忽略已取消的 ${reply.command} 应答`);
        onCancelledReply(reply.command);
        continue;
      }
      // 同一通知缓冲中的后续报文不能匹配处理前半段时才触发的新请求。
      if (!request || active !== request) continue;
      if (!reply) {
        if (request.command !== 'STOP') onFailure(new Error('板端应答格式有误'));
        continue;
      }
      if (request.command !== 'STOP' && reply.command === 'UNKNOWN' && reply.code === 'FRAME') {
        onFailure(new Error('板端拒绝报文：FRAME'));
        continue;
      }
      if (reply.command !== request.command) continue;
      if (request.writeComplete) {
        accept(reply);
      } else if (request.nextIndex === request.packets.length && !request.earlyReply) {
        // 通知可能早于最后一包的写入回调到达。
        request.earlyReply = reply;
      }
    }
  }

  function reset(error) {
    if (writing) clearTimeout(writing.timer);
    writing = null;
    reader.reset();
    retiredCommands.clear();
    finish(null, error);
  }

  return {
    request,
    receive,
    reset,
    waitingForCancelledReply: (command) => retiredCommands.has(command),
    get busy() { return Boolean(active); },
    get stopping() { return Boolean(active && active.command === 'STOP'); }
  };
}

module.exports = { createRequestLane };
