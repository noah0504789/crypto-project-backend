import ws from 'k6/ws';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ===== 고정값 =====
const SERVER_HOST = 'hook-meeting-foundations-abstract.trycloudflare.com';
const SERVER_PORT = '443';
const ROOM_ID = '69e509b7f611e464a27a6267';

const TOKEN_USER1 =
  'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6Im15LWF1dGhvcml6YXRpb24tc2VydmVyLWp3dDoxIn0.eyJzdWIiOiJub2FoMDk2OUBnbWFpbC5jb20iLCJhdWQiOlsibXktY2xpZW50LWlkIl0sIm5iZiI6MTc3ODIyMjE0Miwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlzcyI6Imh0dHA6Ly9jcnlwdG8tb2F1dGgyLWF1dGhvcml6YXRpb24tc2VydmVyOjkwMDAiLCJpZCI6IjI0YjI1OWRiLTQ1NWQtNDhiOS04ZWVhLTllODQzNzQ5Mzg1OSIsImV4cCI6MTc3ODgyNjk0MiwiaWF0IjoxNzc4MjIyMTQyLCJqdGkiOiJiZjZlOWEzMS1jMTcyLTQ0MzMtYmM5Yy1jM2FmNjNjOGQ2YWUifQ.VHQWmx3oy30-yuetnlMRJsF0imOo49GFXDjvlQnMNop3RXLa0t6v2QdhH-PkkqHFH9PYvlzPW_iT8w_WJkPiyDT7yCNU5f1XYnyBnEhrNmS8wjYRop4eHz-GlNfzTNzBTyaL1PoDd69zzOCFS6NMlgXR5NRsLZ9nTjAl0qfJsSL-GRj9BPalSuuYwbjL6v8yo9JX-0FshJ7tXroZMFm2xAzeUQuWpmV9t1wOBn1MWYvs4eD7Xp2T77UzX19CL95WkpEcpHAS95DNO4oFsKmM13AP6rLoiaS4iWw2Ct-KjbGlotRThIz0ZnJ4NGkTV6AI7rWpGCIUMsPf09kkL4Zu0A';

const TOKEN_USER2 =
  'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6Im15LWF1dGhvcml6YXRpb24tc2VydmVyLWp3dDoxIn0.eyJzdWIiOiJub2FoMDUwNEBrYWthby5jb20iLCJhdWQiOlsibXktY2xpZW50LWlkIl0sIm5iZiI6MTc3ODIyMjA1Miwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlzcyI6Imh0dHA6Ly9jcnlwdG8tb2F1dGgyLWF1dGhvcml6YXRpb24tc2VydmVyOjkwMDAiLCJpZCI6ImI0MjE4OTU5LTQ3ZGMtNDIxMC1hZWUzLWQ3NTIxMTVmNDhhYyIsImV4cCI6MTc3ODgyNjg1MiwiaWF0IjoxNzc4MjIyMDUyLCJqdGkiOiJjZWJkN2Q2Ni1hNmZhLTRkNmItYThiMi1jNGFkYTMyMDUzYzUifQ.T57dLfq1LVJtTHDcYUkm4LmoUjP_JJyNoWwv9UuKWPuKWIcEmtZaTMDSssbGw9UwI6n-No_6k4Dx-obtp6NXcqgFishfF8K9qMxngK8rzQjmg9z_bDAjLZ7HbcTojPCwsEhGBFIILa4UXCaUfa4pxYfBSkQVfT7M_Lml7gwos2CZw1Rg4xOxrvNTcQLzGTNJnLsMsNSZ_N_ilWdlr2jiSy1-Bh22Ng4jQ9l12wxjtqXds2iMGIFPSyL3CQvd_UuESODxj0_CDTcq8_lsSBWlByzIKgrQyACBQWz6v6mg478TfUZVKOv5e5cd2Vn_EMOXHtdddt-56roykOVoEOZGcA';

const WRITER_ID_1 = '24b259db-455d-48b9-8eea-9e8437493859';
const WRITER_ID_2 = 'b4218959-47dc-4210-aee3-d752115f48ac';

// ===== 테스트 조건 =====
const NUM_VUS = 200;
const HOLD_SECONDS = 60;
const SEND_INTERVAL_MS = 10000;     // 10초당 1개
const SUBSCRIBE_SETTLING_MS = 500;
const ACK_TIMEOUT_MS = 10000;
const BROADCAST_TIMEOUT_MS = 10000;
const WAIT_CONNECTED_MS = 15000;
const FORCE_CLOSE_MS = 70000;

// hold 60초 동안 10초마다 1개면 대략 6개
const NUM_MSGS = Math.floor(HOLD_SECONDS * 1000 / SEND_INTERVAL_MS);

// ===== 디버그 =====
const DEBUG_VUS = new Set([1, 2]);
const DEBUG_LIMIT = 200;

// ===== 메트릭 =====
const connectFrameCount = new Counter('connect_frame_count');
const subscribeFrameCount = new Counter('subscribe_frame_count');
const sendCount = new Counter('send_count');

const ackOkCount = new Counter('ack_ok_count');
const ackTimeoutCount = new Counter('ack_timeout_count');
const broadcastOkCount = new Counter('broadcast_ok_count');
const broadcastTimeoutCount = new Counter('broadcast_timeout_count');

const ackOkRate = new Rate('ack_ok_rate');
const broadcastOkRate = new Rate('broadcast_ok_rate');

const ackLatency = new Trend('ack_latency_ms', true);
const broadcastLatency = new Trend('broadcast_latency_ms', true);

// ===== k6 옵션 =====
export const options = {
  scenarios: {
    light_message_200: {
      executor: 'per-vu-iterations',
      vus: NUM_VUS,
      iterations: 1,
      maxDuration: '10m',
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
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
  if (raw === 'o') return { type: 'open' };
  if (raw === 'h') return { type: 'heartbeat' };

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

function parseStompFrame(frameText) {
  const text = String(frameText);
  const lines = text.split('\n');
  const command = (lines[0] || '').trim();

  const headers = {};
  let i = 1;

  for (; i < lines.length; i++) {
    const line = lines[i];
    if (line === '') {
      i++;
      break;
    }
    const idx = line.indexOf(':');
    if (idx > -1) {
      headers[line.slice(0, idx)] = line.slice(idx + 1);
    }
  }

  let body = lines.slice(i).join('\n');
  if (body.endsWith('\u0000')) {
    body = body.slice(0, -1);
  }

  return { command, headers, body, raw: text };
}

export default function () {
  const vu = __VU;

  const isUser1 = vu % 2 === 0;
  const accessToken = isUser1 ? TOKEN_USER1 : TOKEN_USER2;
  const writerId = isUser1 ? WRITER_ID_1 : WRITER_ID_2;

  const sockJsServerId = randomInt(0, 999);
  const sockJsSessionId = randomString(8);

  const url =
    `wss://${SERVER_HOST}:${SERVER_PORT}` +
    `/ws/${sockJsServerId}/${sockJsSessionId}/websocket` +
    `?access_token=${accessToken}`;

  let sockJsOpened = false;
  let connectSent = false;
  let stompConnected = false;
  let subscribeSent = false;
  let finalized = false;
  let closeReason = 'unknown';
  let sendStarted = false;
  let sent = 0;

  const pendingAck = new Map();
  const pendingBroadcast = new Map();

  function finalize(socket, reason) {
    if (finalized) return;

    finalized = true;
    closeReason = reason;

    check(null, {
      'sockjs open received': () => sockJsOpened,
      'stomp connected': () => stompConnected,
    });

    debugLog(
      vu,
      `[VU ${vu}] finalize reason=${reason}, sockJsOpened=${sockJsOpened}, stompConnected=${stompConnected}, subscribeSent=${subscribeSent}, sent=${sent}, pendingAck=${pendingAck.size}, pendingBroadcast=${pendingBroadcast.size}`
    );

    try {
      socket.close();
    } catch (e) {
      debugLog(vu, `[VU ${vu}] close error=${JSON.stringify(e)}`);
    }
  }

  function maybeFinish(socket) {
    if (
      sendStarted &&
      sent >= NUM_MSGS &&
      pendingAck.size === 0 &&
      pendingBroadcast.size === 0
    ) {
      finalize(socket, 'all-messages-complete');
    }
  }

  function markAck(msgId) {
    if (!pendingAck.has(msgId)) return;
    const startedAt = pendingAck.get(msgId);
    pendingAck.delete(msgId);

    ackOkCount.add(1);
    ackOkRate.add(true);
    ackLatency.add(Date.now() - startedAt);
  }

  function markBroadcast(msgId) {
    if (!pendingBroadcast.has(msgId)) return;
    const startedAt = pendingBroadcast.get(msgId);
    pendingBroadcast.delete(msgId);

    broadcastOkCount.add(1);
    broadcastOkRate.add(true);
    broadcastLatency.add(Date.now() - startedAt);
  }

  function scanTimeouts() {
    const now = Date.now();

    for (const [msgId, startedAt] of Array.from(pendingAck.entries())) {
      if (now - startedAt >= ACK_TIMEOUT_MS) {
        pendingAck.delete(msgId);
        ackTimeoutCount.add(1);
        ackOkRate.add(false);
      }
    }

    for (const [msgId, startedAt] of Array.from(pendingBroadcast.entries())) {
      if (now - startedAt >= BROADCAST_TIMEOUT_MS) {
        pendingBroadcast.delete(msgId);
        broadcastTimeoutCount.add(1);
        broadcastOkRate.add(false);
      }
    }
  }

  const res = ws.connect(url, {}, function (socket) {
    function startSending() {
      if (sendStarted) return;
      sendStarted = true;

      debugLog(vu, `[VU ${vu}] startSending NUM_MSGS=${NUM_MSGS}`);

      socket.setInterval(function () {
        if (finalized) return;
        if (sent >= NUM_MSGS) return;

        sent += 1;

        const msgId = `k6-${vu}-${sent}-${Date.now()}`;
        const content = `CID:${msgId}|light-message-${vu}-${sent}`;
        const now = Date.now();

        pendingAck.set(msgId, now);
        pendingBroadcast.set(msgId, now);
        sendCount.add(1);

        const bodyObj = {
          roomId: ROOM_ID,
          writerId,
          content,
          clientMessageId: msgId,
        };

        const sendFrame = buildStompFrame(
          'SEND',
          {
            destination: '/msg/chat.send',
            'content-type': 'application/json',
          },
          JSON.stringify(bodyObj)
        );

        socket.send(sockJsWrap(sendFrame));
        debugLog(vu, `[VU ${vu}] sent MESSAGE ${sent}/${NUM_MSGS}, msgId=${msgId}`);
      }, SEND_INTERVAL_MS);
    }

    socket.on('open', function () {
      debugLog(vu, `[VU ${vu}] websocket upgraded/open`);
    });

    socket.on('message', function (raw) {
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
          connectFrameCount.add(1);

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

      if (parsed.type !== 'array') {
        return;
      }

      for (const msg of parsed.messages) {
        if (typeof msg !== 'string') continue;

        if (msg.includes('CONNECTED')) {
          if (!stompConnected) {
            stompConnected = true;

            const subscribeAckFrame = buildStompFrame('SUBSCRIBE', {
              id: `ack-${vu}`,
              destination: '/user/queue/chat/ack',
              ack: 'auto',
            });

            const subscribeBroadcastFrame = buildStompFrame('SUBSCRIBE', {
              id: `broadcast-${vu}`,
              destination: `/topic/chat/${ROOM_ID}`,
              ack: 'auto',
            });

            socket.send(sockJsWrap(subscribeAckFrame));
            socket.send(sockJsWrap(subscribeBroadcastFrame));
            subscribeFrameCount.add(2);
            subscribeSent = true;

            debugLog(vu, `[VU ${vu}] STOMP CONNECTED + SUBSCRIBED`);

            socket.setTimeout(function () {
              startSending();
            }, SUBSCRIBE_SETTLING_MS);

            socket.setTimeout(function () {
              if (!finalized) {
                debugLog(vu, `[VU ${vu}] hold complete`);
                scanTimeouts();
                maybeFinish(socket);
                //finalize(socket, 'hold-complete');
              }
            }, HOLD_SECONDS * 1000);
          }
          continue;
        }

        if (msg.includes('ERROR')) {
          console.log(`[VU ${vu}] STOMP ERROR=${msg}`);
          finalize(socket, 'stomp-error');
          return;
        }

        const frame = parseStompFrame(msg);
        if (frame.command !== 'MESSAGE') continue;

        let bodyObj = null;
        try {
          bodyObj = JSON.parse(frame.body);
        } catch (_) {
          continue;
        }

        const msgId = bodyObj?.clientMessageId;
        if (!msgId) continue;

        const destination = frame.headers.destination || '';

        if (destination === '/user/queue/chat/ack') {
          markAck(msgId);
        } else if (destination === `/topic/chat/${ROOM_ID}`) {
          markBroadcast(msgId);
        } else {
          if (pendingAck.has(msgId)) markAck(msgId);
          if (pendingBroadcast.has(msgId)) markBroadcast(msgId);
        }

        maybeFinish(socket);
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

    socket.setInterval(function () {
      if (finalized) return;
      scanTimeouts();
      maybeFinish(socket);
    }, 200);

    socket.setTimeout(function () {
      if (!finalized) {
        console.log(`[VU ${vu}] force close timeout`);
        scanTimeouts();
        finalize(socket, 'force-close-timeout');
      }
    }, FORCE_CLOSE_MS);
  });

  check(res, {
    'ws upgrade status is 101': (r) => r && r.status === 101,
  });

  if (!res || res.status !== 101) {
    console.log(`[VU ${vu}] upgrade failed status=${res && res.status}, error=${res && JSON.stringify(res.error)}`);
  }

  if (DEBUG_VUS.has(vu)) {
    console.log(`[VU ${vu}] ws result status=${res && res.status}, error=${res && JSON.stringify(res.error)}`);
  }
}
