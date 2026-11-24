const MAX_FRAME_LENGTH = 128;
const MAX_PACKET_LENGTH = 20;
const COMMANDS = ['INFO', 'STATUS', 'LIGHT', 'HATCH', 'STOP'];
const LIGHT_MODES = ['OFF', 'SOLID', 'BLINK', 'BREATHE', 'ALTERNATE', 'CHASE', 'RAINBOW', 'FLOW', 'MARQUEE'];

function makeRequest(line) {
  const printableLine = typeof line === 'string' && /^![\x20-\x7e]*$/.test(line) && !/[\r\n]/.test(line);
  if (!printableLine || line.length > MAX_FRAME_LENGTH || line.includes('!', 1)) {
    throw new Error('请输入以 ! 开头、最多 128 字节的单行 ASCII 指令');
  }

  const name = line.slice(1).split(' ', 1)[0];
  const frame = `${line}\n`;
  const packets = [];
  for (let offset = 0; offset < frame.length; offset += MAX_PACKET_LENGTH) {
    packets.push(frame.slice(offset, offset + MAX_PACKET_LENGTH));
  }
  return { line, command: COMMANDS.includes(name) ? name : 'UNKNOWN', frame, packets };
}

function toBytes(text) {
  return Uint8Array.from(text, (character) => character.charCodeAt(0)).buffer;
}

function fromBytes(buffer) {
  let text = '';
  for (const value of new Uint8Array(buffer)) text += String.fromCharCode(value);
  return text;
}

function createFrameReader() {
  let pending = '';

  return {
    push(chunk) {
      const frames = [];
      for (const character of chunk) {
        if (character === '!') {
          pending = '!';
        } else if (pending && character === '\n') {
          frames.push({ text: `${pending}\n`, valid: true });
          pending = '';
        } else if (pending) {
          const nextLength = pending.length + 1;
          const validCr = character === '\r' && nextLength <= MAX_FRAME_LENGTH + 1;
          const validAscii = character >= ' ' && character <= '~' && nextLength <= MAX_FRAME_LENGTH;
          if (!pending.endsWith('\r') && (validAscii || validCr)) {
            pending += character;
          } else {
            frames.push({ text: pending + character, valid: false });
            pending = '';
          }
        }
      }
      return frames;
    },
    reset() { pending = ''; }
  };
}

function parseResponse(frame) {
  const text = frame.replace(/\r?\n$/, '');
  const header = /^!(OK|ERR) ([A-Z]+)(?: (.*))?$/.exec(text);
  if (!header) return null;

  const [, kind, command, payload = ''] = header;
  if (kind === 'ERR') {
    return /^[A-Z][A-Z0-9_]*$/.test(payload)
      ? { ok: false, command, code: payload }
      : { command, error: '错误应答格式有误' };
  }
  return { ok: true, command, payload };
}

function readFields(payload) {
  const fields = Object.create(null);
  for (const token of payload.split(' ')) {
    const field = /^([A-Z][A-Z0-9_]*)=([^ =]+)$/.exec(token);
    if (!field || Object.prototype.hasOwnProperty.call(fields, field[1])) throw new Error('应答键值字段格式有误');
    fields[field[1]] = field[2];
  }
  return fields;
}

function readResult({ command, payload }) {
  if (command === 'HATCH') {
    if (payload !== 'RUNNING') throw new Error('HATCH 应答格式有误');
    return { HATCH: 'RUNNING' };
  }
  if (!COMMANDS.includes(command)) throw new Error('未定义的成功应答');

  const fields = readFields(payload);
  if (command === 'INFO') {
    const { PROTO, FW, LEDS, SN, TYPE } = fields;
    if (PROTO && PROTO !== '2') throw new Error(`协议版本不兼容：PROTO=${PROTO}，板端需要 PROTO=2`);
    if (PROTO !== '2' || !/^[a-zA-Z0-9._-]{1,16}$/.test(FW || '') || LEDS !== '20' || !/^[0-9a-fA-F]{24}$/.test(SN || '') || !['1', '2'].includes(TYPE)) {
      throw new Error('INFO 应答字段不符合 V2.0 协议');
    }
    return { PROTO, FW, LEDS, SN, TYPE };
  }

  const { HATCH, MOTOR, FAULT, LIGHT, MODE } = fields;
  if (command === 'STATUS' && HATCH && ['0', '1'].includes(MOTOR) && ['0', '1'].includes(FAULT) && LIGHT_MODES.includes(LIGHT)) {
    return { HATCH, MOTOR, FAULT, LIGHT };
  }
  if (command === 'STOP' && HATCH && MOTOR === '0') return { HATCH, MOTOR };
  if (command === 'LIGHT' && LIGHT_MODES.includes(MODE)) return { LIGHT: MODE };
  throw new Error(`${command} 应答字段不符合 V2.0 协议`);
}

module.exports = { makeRequest, toBytes, fromBytes, createFrameReader, parseResponse, readResult };
