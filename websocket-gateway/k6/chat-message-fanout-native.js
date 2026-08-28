import ws from 'k6/ws';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

// Runtime target and credentials are supplied by the CLI. Do not commit real tokens.
const ws_base_url = (__ENV.WS_BASE_URL || 'wss://localhost:8000').replace(/\/$/, '');
const stomp_host = __ENV.STOMP_HOST || ws_base_url.replace(/^wss?:\/\//, '');
const origin = __ENV.ORIGIN || 'http://localhost:5173';
const room_id = __ENV.ROOM_ID || '';

// ===== tokens from env =====
const token_user_1 = __ENV.TOKEN_USER_1 || '';
const token_user_2 = __ENV.TOKEN_USER_2 || '';

const writer_id_1 = __ENV.WRITER_ID_1 || '';
const writer_id_2 = __ENV.WRITER_ID_2 || '';

// ===== native websocket endpoint =====
const ws_base_path = '/ws-native';

// ===== stomp destinations =====
const send_destination = '/msg/chat.send';
const ack_destination = '/user/queue/chat/ack';
const broadcast_destination = `/topic/chat/${room_id}`;
// 뱃지는 서버가 멤버마다 convertAndSendToUser 로 개별 발행한다(StompMyChatRoomBadgeAdapter).
// 채팅 브로드캐스트와 달리 brokerChannel 태스크가 멤버 수만큼 생기므로 서버 부하에서 차지하는
// 비중이 크다. 구독하지 않으면 그 부하가 측정에서 통째로 빠진다.
const badge_destination = '/user/queue/chat/badge';

// ===== test config =====
const num_vus = Number(__ENV.VUS || 130);
const num_msgs_per_user = Number(__ENV.MESSAGE_COUNT || 10);
const send_interval_ms = Number(__ENV.MESSAGE_INTERVAL_MS || 1000);

const subscribe_settling_ms = Number(__ENV.START_DELAY_MS || 15000);
const ack_timeout_ms = Number(__ENV.ACK_TIMEOUT_MS || 10000);
const broadcast_timeout_ms = Number(__ENV.BROADCAST_TIMEOUT_MS || 10000);
const broadcast_collect_window_ms = Number(__ENV.COLLECT_WINDOW_MS || 30000);
const wait_connected_ms = Number(__ENV.WAIT_CONNECTED_MS || 15000);

const force_close_ms =
  subscribe_settling_ms +
  num_msgs_per_user * send_interval_ms +
  broadcast_collect_window_ms +
  10000;

const expected_broadcast_per_user = num_vus * num_msgs_per_user;
const expected_broadcast_total = num_vus * expected_broadcast_per_user;

// ===== debug =====
const debug_vus = new Set([1]);
const debug_limit = 800;

// ===== custom metrics =====
const connect_frame_count = new Counter('connect_frame_count');
const subscribe_frame_count = new Counter('subscribe_frame_count');
const send_count = new Counter('send_count');

const ack_ok_count = new Counter('ack_ok_count');
const ack_failed_count = new Counter('ack_failed_count');
const ack_timeout_count = new Counter('ack_timeout_count');
const ack_ok_rate = new Rate('ack_ok_rate');
const ack_latency_ms = new Trend('ack_latency_ms', true);

const broadcast_expected_total_count = new Counter('broadcast_expected_total_count');
const broadcast_delivery_ok_count = new Counter('broadcast_delivery_ok_count');
const broadcast_delivery_timeout_ok_count = new Counter('broadcast_delivery_timeout_ok_count');
const broadcast_delivery_late_count = new Counter('broadcast_delivery_late_count');
const broadcast_delivery_missing_count = new Counter('broadcast_delivery_missing_count');
const broadcast_delivery_timeout_ok_rate = new Rate('broadcast_delivery_timeout_ok_rate');
const broadcast_delivery_latency_ms = new Trend('broadcast_delivery_latency_ms', true);
const broadcast_duplicate_count = new Counter('broadcast_duplicate_count');

// 뱃지는 채팅 브로드캐스트 지표와 섞지 않는다 — 발행 단위(멤버별)와 전달 대상(그 사용자의 모든 세션)이
// 달라 같은 분모로 볼 수 없다. 서버가 실제로 내보내는 양을 확인하는 용도로만 센다.
const badge_received_count = new Counter('badge_received_count');
// 배칭 효과를 지연과 무관하게 판정하는 지표다. 프레임 수는 스왑에 흔들리지 않는다.
const batch_frame_count = new Counter('batch_frame_count');
const batch_size = new Trend('batch_size');

// ===== k6 options =====
export const options = {
  scenarios: {
    chat_message_fanout_native: {
      executor: 'per-vu-iterations',
      vus: num_vus,
      iterations: 1,
      maxDuration: '10m',
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// ===== utils =====
function debug_log(vu, message) {
  if (!debug_vus.has(vu)) return;

  const text = String(message);
  console.log(text.length > debug_limit ? text.slice(0, debug_limit) + '...' : text);
}

function build_stomp_frame(command, headers = {}, body = '') {
  let frame = `${command}\n`;

  for (const [key, value] of Object.entries(headers)) {
    frame += `${key}:${value}\n`;
  }

  frame += `\n${body}\u0000`;
  return frame;
}

function split_stomp_frames(raw) {
  const text = String(raw);

  if (text === '\n' || text.trim() === '') {
    return [];
  }

  return text
    .split('\u0000')
    .map((part) => part.trimStart())
    .filter((part) => part.length > 0)
    .map((part) => `${part}\u0000`);
}

function parse_stomp_frame(frame_text) {
  let text = String(frame_text);

  if (text.endsWith('\u0000')) {
    text = text.slice(0, -1);
  }

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

  const body = lines.slice(i).join('\n');

  return { command, headers, body, raw: text };
}

function resolve_message_id(body_obj) {
  if (body_obj?.clientMessageId) {
    return body_obj.clientMessageId;
  }

  const content = body_obj?.content;

  if (typeof content === 'string') {
    const match = content.match(/CID:([^|]+)/);

    if (match && match[1]) {
      return match[1];
    }
  }

  return null;
}

function resolve_sent_at_from_msg_id(msg_id) {
  if (!msg_id || typeof msg_id !== 'string') return null;

  const parts = msg_id.split('-');
  const last = parts[parts.length - 1];
  const timestamp = Number(last);

  return Number.isFinite(timestamp) ? timestamp : null;
}

export default function () {
  const vu = __VU;

  const is_user_1 = vu % 2 === 0;
  const access_token = is_user_1 ? token_user_1 : token_user_2;
  const writer_id = is_user_1 ? writer_id_1 : writer_id_2;

  if (!room_id || !access_token || !writer_id) {
    throw new Error('ROOM_ID, TOKEN_USER_1/2, WRITER_ID_1/2를 실행 환경에 설정해야 함');
  }

  // 브라우저 Native WebSocket 요청과 맞춤.
  // access_token은 query string으로만 전달.
  const url = `${ws_base_url}${ws_base_path}?access_token=${encodeURIComponent(access_token)}`;

  let websocket_opened = false;
  let connect_sent = false;
  let stomp_connected = false;
  let send_started = false;
  let all_messages_queued = false;
  let finalized = false;
  let sent = 0;

  const pending_ack = new Map();
  const received_broadcast_ids = new Set();

  function finalize(socket, reason) {
    if (finalized) return;
    finalized = true;

    const remaining_ack = pending_ack.size;

    if (remaining_ack > 0) {
      ack_timeout_count.add(remaining_ack);

      for (let i = 0; i < remaining_ack; i++) {
        ack_ok_rate.add(false);
      }

      pending_ack.clear();
    }

    broadcast_expected_total_count.add(expected_broadcast_per_user);

    const received = received_broadcast_ids.size;
    const missing = Math.max(expected_broadcast_per_user - received, 0);

    if (missing > 0) {
      broadcast_delivery_missing_count.add(missing);

      for (let i = 0; i < missing; i++) {
        broadcast_delivery_timeout_ok_rate.add(false);
      }
    }

    check(null, {
      'websocket open received': () => websocket_opened,
      'stomp connected': () => stomp_connected,
    });

    debug_log(
      vu,
      `[VU ${vu}] finalize reason=${reason}, sent=${sent}, broadcast_received=${received}, broadcast_missing=${missing}, expected=${expected_broadcast_per_user}`
    );

    try {
      socket.close();
    } catch (error) {
      debug_log(vu, `[VU ${vu}] close error=${JSON.stringify(error)}`);
    }
  }

  // 서버는 실패도 ACK로 응답한다(success=false, errorCode). 성공 ACK만 성공률에 넣는다.
  function mark_ack(msg_id, body_obj) {
    if (!pending_ack.has(msg_id)) return;

    const started_at = pending_ack.get(msg_id);
    pending_ack.delete(msg_id);

    if (body_obj?.success !== true) {
      ack_failed_count.add(1);
      ack_ok_rate.add(false);
      return;
    }

    ack_ok_count.add(1);
    ack_ok_rate.add(true);
    ack_latency_ms.add(Date.now() - started_at);
  }

  function mark_broadcast_delivery(msg_id) {
    if (!msg_id) return;

    if (received_broadcast_ids.has(msg_id)) {
      broadcast_duplicate_count.add(1);
      return;
    }

    received_broadcast_ids.add(msg_id);
    broadcast_delivery_ok_count.add(1);

    const sent_at = resolve_sent_at_from_msg_id(msg_id);

    if (sent_at) {
      const latency = Date.now() - sent_at;
      broadcast_delivery_latency_ms.add(latency);

      if (latency <= broadcast_timeout_ms) {
        broadcast_delivery_timeout_ok_count.add(1);
        broadcast_delivery_timeout_ok_rate.add(true);
      } else {
        broadcast_delivery_late_count.add(1);
        broadcast_delivery_timeout_ok_rate.add(false);
      }
    } else {
      broadcast_delivery_late_count.add(1);
      broadcast_delivery_timeout_ok_rate.add(false);
    }
  }

  // 브라우저 요청과 최대한 유사하게 맞춤.
  // Authorization, X-User-Id, Sec-WebSocket-Protocol은 넣지 않음.
  const params = {
    headers: {
      Origin: origin,
      Pragma: 'no-cache',
      'Cache-Control': 'no-cache',
      'Accept-Language': 'ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7',
    },
  };

  const res = ws.connect(url, params, function (socket) {
    function start_sending() {
      if (send_started) return;
      send_started = true;

      debug_log(
        vu,
        `[VU ${vu}] start_sending messages_per_user=${num_msgs_per_user}, expected_broadcast_per_user=${expected_broadcast_per_user}, expected_broadcast_total=${expected_broadcast_total}`
      );

      socket.setInterval(function () {
        if (finalized) return;

        if (sent >= num_msgs_per_user) {
          if (!all_messages_queued) {
            all_messages_queued = true;
            debug_log(vu, `[VU ${vu}] all_send_frames_queued`);

            socket.setTimeout(function () {
              finalize(socket, 'broadcast_collect_window_complete');
            }, broadcast_collect_window_ms);
          }

          return;
        }

        sent += 1;

        const msg_id = `chat-message-fanout-native-${vu}-${sent}-${Date.now()}`;
        const content = `CID:${msg_id}|chat-message-fanout-native-${vu}-${sent}`;
        const now = Date.now();

        pending_ack.set(msg_id, now);
        send_count.add(1);

        const body_obj = {
          roomId: room_id,
          writerId: writer_id,
          content,
          clientMessageId: msg_id,
        };

        const send_frame = build_stomp_frame(
          'SEND',
          {
            destination: send_destination,
            'content-type': 'application/json',
          },
          JSON.stringify(body_obj)
        );

        socket.send(send_frame);
        debug_log(vu, `[VU ${vu}] sent_message ${sent}/${num_msgs_per_user}, msg_id=${msg_id}`);
      }, send_interval_ms);
    }

    socket.on('open', function () {
      websocket_opened = true;
      debug_log(vu, `[VU ${vu}] websocket_open`);

      if (!connect_sent) {
        const connect_frame = build_stomp_frame('CONNECT', {
          'accept-version': '1.2',
          'heart-beat': '10000,10000',
          host: stomp_host,
        });

        socket.send(connect_frame);
        connect_sent = true;
        connect_frame_count.add(1);

        debug_log(vu, `[VU ${vu}] sent_connect`);
      }
    });

    socket.on('message', function (raw) {
      const frames = split_stomp_frames(raw);

      for (const frame_text of frames) {
        if (typeof frame_text !== 'string') continue;

        const frame = parse_stomp_frame(frame_text);

        if (frame.command === 'ERROR') {
          console.log(`[VU ${vu}] STOMP ERROR=${frame_text}`);
          finalize(socket, 'stomp_error');
          return;
        }

        if (frame.command === 'CONNECTED') {
          if (!stomp_connected) {
            stomp_connected = true;

            const subscribe_ack_frame = build_stomp_frame('SUBSCRIBE', {
              id: `ack-${vu}`,
              destination: ack_destination,
              ack: 'auto',
            });

            const subscribe_broadcast_frame = build_stomp_frame('SUBSCRIBE', {
              id: `broadcast-${vu}`,
              destination: broadcast_destination,
              ack: 'auto',
            });

            const subscribe_badge_frame = build_stomp_frame('SUBSCRIBE', {
              id: `badge-${vu}`,
              destination: badge_destination,
              ack: 'auto',
            });

            socket.send(subscribe_ack_frame);
            socket.send(subscribe_broadcast_frame);
            socket.send(subscribe_badge_frame);

            subscribe_frame_count.add(3);

            debug_log(
              vu,
              `[VU ${vu}] stomp_connected_and_subscribed ack=${ack_destination}, broadcast=${broadcast_destination}`
            );

            socket.setTimeout(function () {
              start_sending();
            }, subscribe_settling_ms);
          }

          continue;
        }

        if (frame.command !== 'MESSAGE') {
          continue;
        }

        let body_obj = null;

        try {
          body_obj = JSON.parse(frame.body);
        } catch (_) {
          debug_log(vu, `[VU ${vu}] failed_to_parse_message_body body=${frame.body}`);
          continue;
        }

        const destination =
          frame.headers.destination ||
          frame.headers['simpDestination'] ||
          '';

        // 뱃지는 clientMessageId 를 담지 않으므로 msg_id 검사보다 먼저 가른다.
        // 아래 검사에 걸리면 debug 로그만 남기고 버려져 측정에서 빠진다.
        if (destination === badge_destination) {
          badge_received_count.add(1);
          continue;
        }

        // 게이트웨이가 같은 방의 메시지를 100ms 창으로 묶어 봉투로 보낸다
        // (StompChatMessageBatchPayload: { roomId, messages: [...] }).
        // 프레임 1개가 메시지 N건이므로 펼쳐서 각각 집계한다 —
        // 봉투째로 세면 수신률이 프레임 수로 계산되어 대부분 미도달로 잡힌다.
        // 배칭이 꺼진 회차나 옛 게이트웨이를 대비해 단건 모양도 함께 받는다.
        const payloads = Array.isArray(body_obj?.messages) ? body_obj.messages : [body_obj];

        if (destination === broadcast_destination) {
          batch_frame_count.add(1);
          batch_size.add(payloads.length);
        }

        for (const payload of payloads) {
          const msg_id = resolve_message_id(payload);

          if (!msg_id) {
            debug_log(vu, `[VU ${vu}] message_has_no_client_message_id_or_cid body=${frame.body}`);
            continue;
          }

          const is_ack_payload = payload?.success !== undefined || payload?.errorCode !== undefined;

          if (destination === ack_destination) {
            mark_ack(msg_id, payload);
          } else if (destination === broadcast_destination) {
            mark_broadcast_delivery(msg_id);
          } else if (is_ack_payload) {
            mark_ack(msg_id, payload);
          } else if (payload?.content?.includes?.('CID:')) {
            mark_broadcast_delivery(msg_id);
          }
        }
      }
    });

    socket.on('close', function () {
      debug_log(vu, `[VU ${vu}] socket_closed`);
    });

    socket.on('error', function (error) {
      console.log(`[VU ${vu}] websocket_error=${JSON.stringify(error)}`);
      finalize(socket, 'websocket_error');
    });

    socket.setInterval(function () {
      if (finalized) return;

      const now = Date.now();

      for (const [msg_id, started_at] of Array.from(pending_ack.entries())) {
        if (now - started_at >= ack_timeout_ms) {
          pending_ack.delete(msg_id);
          ack_timeout_count.add(1);
          ack_ok_rate.add(false);
        }
      }
    }, 200);

    socket.setTimeout(function () {
      if (!stomp_connected) {
        console.log(
          `[VU ${vu}] timeout_waiting_connected websocket_opened=${websocket_opened}, connect_sent=${connect_sent}`
        );

        finalize(socket, 'timeout_waiting_connected');
      }
    }, wait_connected_ms);

    socket.setTimeout(function () {
      if (!finalized) {
        console.log(`[VU ${vu}] force_close_timeout`);
        finalize(socket, 'force_close_timeout');
      }
    }, force_close_ms);
  });

  check(res, {
    'ws upgrade status is 101': (r) => r && r.status === 101,
  });

  if (!res || res.status !== 101) {
    console.log(
      `[VU ${vu}] upgrade_failed path=${ws_base_path}, status=${res && res.status}, error=${res && JSON.stringify(res.error)}`
    );
  }
}

function format_number(value, digits = 2) {
  const n = Number(value);

  if (!Number.isFinite(n)) {
    return '0';
  }

  const fixed = Number.isInteger(n) ? String(n) : n.toFixed(digits);
  const parts = fixed.split('.');
  const integer_part = parts[0];
  const decimal_part = parts[1];
  const with_commas = integer_part.replace(/\B(?=(\d{3})+(?!\d))/g, ',');

  if (!decimal_part || Number(decimal_part) === 0) {
    return with_commas;
  }

  return `${with_commas}.${decimal_part}`;
}

export function handleSummary(data) {
  const get_count = (name) => data.metrics[name]?.values?.count || 0;
  const get_trend = (name, key) => data.metrics[name]?.values?.[key] || 0;

  const total_send_count = get_count('send_count');
  const total_subscribe_frame_count = get_count('subscribe_frame_count');

  const expected_total_count = get_count('broadcast_expected_total_count');
  const total_count = get_count('broadcast_delivery_ok_count');
  const timeout_ok_count = get_count('broadcast_delivery_timeout_ok_count');
  const late_total_count = get_count('broadcast_delivery_late_count');
  const missing_total_count = get_count('broadcast_delivery_missing_count');
  const duplicate_total_count = get_count('broadcast_duplicate_count');

  const timeout_total_count = late_total_count + missing_total_count;

  const expected_per_user = expected_total_count / num_vus;
  const ok_per_user = timeout_ok_count / num_vus;
  const timeout_per_user = timeout_total_count / num_vus;
  const duplicate_per_user = duplicate_total_count / num_vus;
  const badge_received = get_count('badge_received_count');
  const batch_frames = get_count('batch_frame_count');
  const batch_avg = get_trend('batch_size', 'avg');
  const batch_max = get_trend('batch_size', 'max');

  const percent = (value, total) => {
    if (!total) return '0.00%';
    return `${((value / total) * 100).toFixed(2)}%`;
  };

  const latency_avg = get_trend('broadcast_delivery_latency_ms', 'avg');
  const latency_p90 = get_trend('broadcast_delivery_latency_ms', 'p(90)');
  const latency_p95 = get_trend('broadcast_delivery_latency_ms', 'p(95)');
  const latency_p99 = get_trend('broadcast_delivery_latency_ms', 'p(99)');
  const latency_max = get_trend('broadcast_delivery_latency_ms', 'max');

  const default_summary = textSummary(data, {
    indent: ' ',
    enableColors: true,
  });

  const clean_result = `
========== chat_message_fanout_native_result ==========

send_count: ${format_number(total_send_count)}
subscribe_frame_count: ${format_number(total_subscribe_frame_count)}

broadcast:
- total_count: ${format_number(total_count)} / ${format_number(expected_total_count)} (${percent(total_count, expected_total_count)})
- late_total_count: ${format_number(late_total_count)} / ${format_number(expected_total_count)} (${percent(late_total_count, expected_total_count)})
- missing_total_count: ${format_number(missing_total_count)} / ${format_number(expected_total_count)} (${percent(missing_total_count, expected_total_count)})
- duplicate_total_count: ${format_number(duplicate_total_count)} / ${format_number(expected_total_count)} (${percent(duplicate_total_count, expected_total_count)})

- ok_rate_per_user: ${format_number(ok_per_user)} / ${format_number(expected_per_user)} (${percent(ok_per_user, expected_per_user)})
- timeout_rate_per_user: ${format_number(timeout_per_user)} / ${format_number(expected_per_user)} (${percent(timeout_per_user, expected_per_user)})
- duplicate_per_user: ${format_number(duplicate_per_user)}

배칭(지연과 무관하게 판정 가능):
- 브로드캐스트 프레임 수: ${format_number(batch_frames)}
- 프레임당 메시지: avg=${format_number(batch_avg)} max=${format_number(batch_max)}
- 배칭 없을 때 프레임 수: ${format_number(total_count)} (수신 메시지 총량)

badge(참고, 채팅 지표와 분리):
- received_count: ${format_number(badge_received)}

- latency_ms:
  avg=${format_number(latency_avg)}ms
  p90=${format_number(latency_p90)}ms
  p95=${format_number(latency_p95)}ms
  p99=${format_number(latency_p99)}ms
  max=${format_number(latency_max)}ms

============================================

`;

  return {
    stdout: `${default_summary}\n${clean_result}`,
  };
}
