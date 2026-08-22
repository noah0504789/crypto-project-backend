import ws from 'k6/ws';
import { check, sleep } from 'k6';

// 실행 대상과 인증정보는 CLI 환경변수로 전달한다.
const WS_BASE_URL = (__ENV.WS_BASE_URL || 'wss://localhost:8000').replace(/\/$/, '');
const ROOM_ID = __ENV.ROOM_ID || '';

const TOKEN_USER1 = __ENV.TOKEN_USER_1 || '';
const TOKEN_USER2 = __ENV.TOKEN_USER_2 || '';

const WRITER_ID_1 = __ENV.WRITER_ID_1 || '';
const WRITER_ID_2 = __ENV.WRITER_ID_2 || '';

// ===== 테스트 조건 =====
const NUM_VUS = Number(__ENV.VUS || 2000);
const RAMP_UP = __ENV.RAMP_UP || '10s';
const HOLD_DURATION = __ENV.HOLD_DURATION || '20s';
const RAMP_DOWN = __ENV.RAMP_DOWN || '5s';
const HOLD_SECONDS = Number(__ENV.HOLD_SECONDS || 20);
const WAIT_CONNECTED_MS = Number(__ENV.WAIT_CONNECTED_MS || 15000);

// ===== 디버그 =====
const DEBUG_VUS = new Set([1, 2, 3, 4]);
const DEBUG_LIMIT = 300;

// SockJS endpoint path
const WS_BASE_PATH = '/ws';

// ramping-vus에서는 VU가 반복 실행될 수 있으므로 VU당 1회만 연결하도록 막는다.
const connectedVus = new Set();

// ===== k6 시나리오 =====
export const options = {
  scenarios: {
    connection_capacity_sockjs: {
      executor: 'ramping-vus',
      stages: [
        { duration: RAMP_UP, target: NUM_VUS },
        { duration: HOLD_DURATION, target: NUM_VUS },
        { duration: RAMP_DOWN, target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
};

// ===== 유틸 =====
function randomInt(min, maxInclusive) {
  return Math.floor(Math.random() * (maxInclusive - min + 1)) + min;
}

function randomString(length) {
  const chars = 'abcdefghijklmnopqrstuvwxyz';
  let out = '';
  for (let i = 0; i < length; i++) {
    out += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return out;
}

function debugLog(vu, message) {
  if (DEBUG_VUS.has(vu)) {
    const text = String(message);
    console.log(text.length > DEBUG_LIMIT ? text.slice(0, DEBUG_LIMIT) + '...' : text);
  }
}

function buildStompFrame(command, headers = {}, body = '') {
  let frame = `${command}\n`;
  for (const [k, v] of Object.entries(headers)) {
    frame += `${k}:${v}\n`;
  }
  frame += `\n${body}\u0000`;
  return frame;
}

function sockJsWrap(stompFrame) {
  return JSON.stringify([stompFrame]);
}

function parseSockJsData(raw) {
  if (raw === 'o') {
    return { type: 'open' };
  }

  if (raw === 'h') {
    return { type: 'heartbeat' };
  }

  if (typeof raw === 'string' && raw.startsWith('a')) {
    try {
      const arr = JSON.parse(raw.slice(1));
      return { type: 'array', messages: arr };
    } catch (e) {
      return { type: 'invalid_array', raw };
    }
  }

  if (typeof raw === 'string' && raw.startsWith('c')) {
    return { type: 'close', raw };
  }

  return { type: 'unknown', raw };
}

export default function () {
  const vu = __VU;

  // ramping-vus는 같은 VU가 default function을 여러 번 실행할 수 있음.
  // 우리는 VU당 연결 1회만 필요하므로 재실행은 대기만 한다.
  if (connectedVus.has(vu)) {
    sleep(1);
    return;
  }

  connectedVus.add(vu);

  const isUser1 = vu % 2 === 0;
  const accessToken = isUser1 ? TOKEN_USER1 : TOKEN_USER2;
  const writerId = isUser1 ? WRITER_ID_1 : WRITER_ID_2;

  if (!ROOM_ID || !accessToken || !writerId) {
    throw new Error('ROOM_ID, TOKEN_USER_1/2, WRITER_ID_1/2를 실행 환경에 설정해야 함');
  }

  const sockJsServerId = randomInt(0, 999);
  const sockJsSessionId = randomString(8);

  const url =
    `${WS_BASE_URL}${WS_BASE_PATH}/${sockJsServerId}/${sockJsSessionId}/websocket` +
    `?access_token=${encodeURIComponent(accessToken)}`;

  let sockJsOpened = false;
  let connectSent = false;
  let stompConnected = false;
  let subscribeSent = false;
  let disconnectSent = false;
  let finalized = false;
  let closeReason = 'unknown';

  function finalize(socket, reason) {
    if (finalized) {
      return;
    }

    finalized = true;
    closeReason = reason;

    check(null, {
      'sockjs open received': () => sockJsOpened,
      'stomp connected': () => stompConnected,
    });

    debugLog(
      vu,
      `[VU ${vu}] finalize reason=${reason}, sockJsOpened=${sockJsOpened}, connectSent=${connectSent}, stompConnected=${stompConnected}, subscribeSent=${subscribeSent}, disconnectSent=${disconnectSent}`
    );

    try {
      socket.close();
    } catch (e) {
      debugLog(vu, `[VU ${vu}] close error=${JSON.stringify(e)}`);
    }
  }

  const res = ws.connect(url, {}, function (socket) {
    socket.on('open', function () {
      debugLog(vu, `[VU ${vu}] websocket upgraded/open`);
    });

    socket.on('message', function (raw) {
      debugLog(vu, `[VU ${vu}] recv=${raw}`);

      const parsed = parseSockJsData(raw);

      if (parsed.type === 'open') {
        sockJsOpened = true;

        if (!connectSent) {
          const connectFrame = buildStompFrame('CONNECT', {
            'accept-version': '1.2',
            'heart-beat': '10000,10000',
            writerId: writerId,
          });

          socket.send(sockJsWrap(connectFrame));
          connectSent = true;

          debugLog(vu, `[VU ${vu}] sent CONNECT`);
        }

        return;
      }

      if (parsed.type === 'heartbeat') {
        return;
      }

      if (parsed.type === 'close') {
        debugLog(vu, `[VU ${vu}] sockjs close frame=${raw}`);
        finalize(socket, 'sockjs-close-frame');
        return;
      }

      if (parsed.type === 'array') {
        for (const msg of parsed.messages) {
          if (typeof msg !== 'string') {
            continue;
          }

          const command = String(msg).split('\n')[0].trim();

          if (command === 'CONNECTED') {
            stompConnected = true;

            debugLog(vu, `[VU ${vu}] STOMP CONNECTED`);

            const subscribeFrame = buildStompFrame('SUBSCRIBE', {
              id: `sub-${vu}`,
              destination: `/topic/chat/${ROOM_ID}`,
              ack: 'auto',
            });

            socket.send(sockJsWrap(subscribeFrame));
            subscribeSent = true;

            debugLog(vu, `[VU ${vu}] sent SUBSCRIBE`);

            socket.setTimeout(function () {
              if (!disconnectSent) {
                const disconnectFrame = buildStompFrame('DISCONNECT', {
                  receipt: `bye-${vu}`,
                });

                socket.send(sockJsWrap(disconnectFrame));
                disconnectSent = true;

                debugLog(vu, `[VU ${vu}] sent DISCONNECT`);
              }

              finalize(socket, 'normal-hold-complete');
            }, HOLD_SECONDS * 1000);

            return;
          }

          if (command === 'ERROR') {
            console.log(`[VU ${vu}] STOMP ERROR=${msg}`);
            finalize(socket, 'stomp-error');
            return;
          }
        }
      }
    });

    socket.on('close', function () {
      debugLog(vu, `[VU ${vu}] socket closed. reason=${closeReason}`);
    });

    socket.on('error', function (e) {
      console.log(`[VU ${vu}] websocket error=${JSON.stringify(e)}`);
      finalize(socket, 'websocket-error');
    });

    socket.setTimeout(function () {
      if (!stompConnected) {
        console.log(
          `[VU ${vu}] timeout waiting CONNECTED. sockJsOpened=${sockJsOpened}, connectSent=${connectSent}, urlSession=${sockJsSessionId}`
        );

        finalize(socket, 'timeout-wait-connected');
      }
    }, WAIT_CONNECTED_MS);
  });

  if (!res || res.status !== 101) {
    console.log(`[VU ${vu}] upgrade failed status=${res && res.status}, error=${res && JSON.stringify(res.error)}`);
  }

  check(res, {
    'ws upgrade status is 101': (r) => r && r.status === 101,
  });

  if (DEBUG_VUS.has(vu)) {
    console.log(`[VU ${vu}] ws result status=${res && res.status}, error=${res && JSON.stringify(res.error)}`);
  }

  sleep(0.1);
}
