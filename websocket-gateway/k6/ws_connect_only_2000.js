import ws from 'k6/ws';
import { check, sleep } from 'k6';

// ===== 고정값 =====
const SERVER_HOST = 'wiring-showtimes-growth-queen.trycloudflare.com';
const SERVER_PORT = '443';
const ROOM_ID = '69e509b7f611e464a27a6267';

const TOKEN_USER1 = 'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6Im15LWF1dGhvcml6YXRpb24tc2VydmVyLWp3dDoxIn0.eyJzdWIiOiJub2FoMDk2OUBnbWFpbC5jb20iLCJhdWQiOlsibXktY2xpZW50LWlkIl0sIm5iZiI6MTc3NzYxNjc5MSwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlzcyI6Imh0dHA6Ly9jcnlwdG8tb2F1dGgyLWF1dGhvcml6YXRpb24tc2VydmVyOjkwMDAiLCJpZCI6IjI0YjI1OWRiLTQ1NWQtNDhiOS04ZWVhLTllODQzNzQ5Mzg1OSIsImV4cCI6MTc3ODIyMTU5MSwiaWF0IjoxNzc3NjE2NzkxLCJqdGkiOiI4ODJkYjc1YS02ZmY1LTQwMjMtODVmNC1lZThhYzdhNDVjNjYifQ.eVELi052LvHBtB1ZGCcWm5be-sFTy1eGgXWtoDe84FdPItx-KrmYDypqSNksOAWEtSbVZVc5q91jO2SxNQr485xTAlGk8FPcEsF-a0lioXCDF6ZXCiYlJxlLiirYrD0IFyU8eZOsijzmwOmk12COd2IqbA2kA9CtJt0X1_gNA8JDk4HZfvoCu4EuWzm7n_bQYDKgbDT-mSHavcqtRPIywf-MMkn3IvvtbjDBWiV7z7wEMTqyNzRZzrVxrkTsH88xknyry7m-3KZ6-8O0vJ4tFifeC5IsfjowmiZdsIP41OF6DsUmuMJSvSbf44djN_iDJPoflPJR_M6IITNAX-qvEw';
const TOKEN_USER2 = 'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6Im15LWF1dGhvcml6YXRpb24tc2VydmVyLWp3dDoxIn0.eyJzdWIiOiJub2FoMDUwNEBrYWthby5jb20iLCJhdWQiOlsibXktY2xpZW50LWlkIl0sIm5iZiI6MTc3NzYxNjcyNiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlzcyI6Imh0dHA6Ly9jcnlwdG8tb2F1dGgyLWF1dGhvcml6YXRpb24tc2VydmVyOjkwMDAiLCJpZCI6ImI0MjE4OTU5LTQ3ZGMtNDIxMC1hZWUzLWQ3NTIxMTVmNDhhYyIsImV4cCI6MTc3ODIyMTUyNiwiaWF0IjoxNzc3NjE2NzI2LCJqdGkiOiI5NTVhODBlZC1mNjE2LTQ0ZDMtODNiNi1iYjc4OTNkYzFjMGIifQ.RnNMF8IkUOBqa8fsf5Kh82p11pQGfZUVIVG6CCHEtazGC-UONNloGyXurrpUnU3Yh2zv0Ke9xEb7-Ae7ukR6eCVGTL3gRUy3xBmgRgu0hlGeF4MwFI9YY2R0Uf9oghmkHck84DkQiqopOK9bYwPc-frtMjzw1Hu4mbxH7ZzANl2z5vqDf_LVFF9S1hl3GoVwqi9-SNnT0NNfFTwqsX218izYY59QaMF__qXEVdPMENZSk2rCiM4JaMEIE3xNBsxNEDa7v7YzXVwpvCLRaxdQanA0dDwQW1fp0mQvINR-eVrZXLSdK42hGaiHOX4vCFhehCG3PHJ2AOryVqPa-nQLdw';

const WRITER_ID_1 = '24b259db-455d-48b9-8eea-9e8437493859';
const WRITER_ID_2 = 'b4218959-47dc-4210-aee3-d752115f48ac';

// ===== 테스트 조건 =====
const NUM_VUS = 2000;
const HOLD_SECONDS = 20;
const WAIT_CONNECTED_MS = 15000;

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
    connect_only_2000: {
      executor: 'ramping-vus',
      stages: [
        { duration: '10s', target: NUM_VUS },
        { duration: '20s', target: NUM_VUS },
        { duration: '5s', target: 0 },
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

  const sockJsServerId = randomInt(0, 999);
  const sockJsSessionId = randomString(8);

  const url =
    `wss://${SERVER_HOST}:${SERVER_PORT}` +
    `${WS_BASE_PATH}/${sockJsServerId}/${sockJsSessionId}/websocket` +
    `?access_token=${accessToken}`;

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

          if (msg.includes('CONNECTED')) {
            stompConnected = true;

            debugLog(vu, `[VU ${vu}] STOMP CONNECTED`);

            const subscribeFrame = buildStompFrame('SUBSCRIBE', {
              id: `sub-${vu}`,
              destination: `/topic/chat/room/${ROOM_ID}`,
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

          if (msg.includes('ERROR')) {
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
