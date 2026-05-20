import ws from 'k6/ws';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

// ===== fixed values =====
const server_host = 'biol-longest-queue-geographic.trycloudflare.com';
const server_port = '443';
const room_id = '69e509b7f611e464a27a6267';

// 기존 토큰 그대로 넣기
const token_user_1 = 'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6Im15LWF1dGhvcml6YXRpb24tc2VydmVyLWp3dDoxIn0.eyJzdWIiOiJub2FoMDk2OUBnbWFpbC5jb20iLCJhdWQiOlsibXktY2xpZW50LWlkIl0sIm5iZiI6MTc3ODIyMjE0Miwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlzcyI6Imh0dHA6Ly9jcnlwdG8tb2F1dGgyLWF1dGhvcml6YXRpb24tc2VydmVyOjkwMDAiLCJpZCI6IjI0YjI1OWRiLTQ1NWQtNDhiOS04ZWVhLTllODQzNzQ5Mzg1OSIsImV4cCI6MTc3ODgyNjk0MiwiaWF0IjoxNzc4MjIyMTQyLCJqdGkiOiJiZjZlOWEzMS1jMTcyLTQ0MzMtYmM5Yy1jM2FmNjNjOGQ2YWUifQ.VHQWmx3oy30-yuetnlMRJsF0imOo49GFXDjvlQnMNop3RXLa0t6v2QdhH-PkkqHFH9PYvlzPW_iT8w_WJkPiyDT7yCNU5f1XYnyBnEhrNmS8wjYRop4eHz-GlNfzTNzBTyaL1PoDd69zzOCFS6NMlgXR5NRsLZ9nTjAl0qfJsSL-GRj9BPalSuuYwbjL6v8yo9JX-0FshJ7tXroZMFm2xAzeUQuWpmV9t1wOBn1MWYvs4eD7Xp2T77UzX19CL95WkpEcpHAS95DNO4oFsKmM13AP6rLoiaS4iWw2Ct-KjbGlotRThIz0ZnJ4NGkTV6AI7rWpGCIUMsPf09kkL4Zu0A';
const token_user_2 = 'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6Im15LWF1dGhvcml6YXRpb24tc2VydmVyLWp3dDoxIn0.eyJzdWIiOiJub2FoMDUwNEBrYWthby5jb20iLCJhdWQiOlsibXktY2xpZW50LWlkIl0sIm5iZiI6MTc3ODIyMjA1Miwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlzcyI6Imh0dHA6Ly9jcnlwdG8tb2F1dGgyLWF1dGhvcml6YXRpb24tc2VydmVyOjkwMDAiLCJpZCI6ImI0MjE4OTU5LTQ3ZGMtNDIxMC1hZWUzLWQ3NTIxMTVmNDhhYyIsImV4cCI6MTc3ODgyNjg1MiwiaWF0IjoxNzc4MjIyMDUyLCJqdGkiOiJjZWJkN2Q2Ni1hNmZhLTRkNmItYThiMi1jNGFkYTMyMDUzYzUifQ.T57dLfq1LVJtTHDcYUkm4LmoUjP_JJyNoWwv9UuKWPuKWIcEmtZaTMDSssbGw9UwI6n-No_6k4Dx-obtp6NXcqgFishfF8K9qMxngK8rzQjmg9z_bDAjLZ7HbcTojPCwsEhGBFIILa4UXCaUfa4pxYfBSkQVfT7M_Lml7gwos2CZw1Rg4xOxrvNTcQLzGTNJnLsMsNSZ_N_ilWdlr2jiSy1-Bh22Ng4jQ9l12wxjtqXds2iMGIFPSyL3CQvd_UuESODxj0_CDTcq8_lsSBWlByzIKgrQyACBQWz6v6mg478TfUZVKOv5e5cd2Vn_EMOXHtdddt-56roykOVoEOZGcA';

const writer_id_1 = '24b259db-455d-48b9-8eea-9e8437493859';
const writer_id_2 = 'b4218959-47dc-4210-aee3-d752115f48ac';

// ===== stomp destinations =====
const ws_base_path = '/ws';
const send_destination = '/msg/chat.send';
const ack_destination = '/user/queue/chat/ack';
const broadcast_destination = `/topic/chat/${room_id}`;

// ===== test config =====
const num_vus = 130;
const num_msgs_per_user = 10;
const send_interval_ms = 1000;

const subscribe_settling_ms = 15000;
const ack_timeout_ms = 10000;
const broadcast_timeout_ms = 10000;
const broadcast_collect_window_ms = 30000;
const wait_connected_ms = 15000;

const force_close_ms =
  subscribe_settling_ms +
  num_msgs_per_user * send_interval_ms +
  broadcast_collect_window_ms +
  10000;

const expected_broadcast_per_user = num_vus * num_msgs_per_user;
const expected_broadcast_total = num_vus * expected_broadcast_per_user;

// ===== debug =====
const debug_vus = new Set([1]);
const debug_limit = 500;

// ===== custom metrics =====
const connect_frame_count = new Counter('connect_frame_count');
const subscribe_frame_count = new Counter('subscribe_frame_count');
const send_count = new Counter('send_count');

const ack_ok_count = new Counter('ack_ok_count');
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

// ===== k6 options =====
export const options = {
  scenarios: {
    burst_message_130: {
      executor: 'per-vu-iterations',
      vus: num_vus,
      iterations: 1,
      maxDuration: '10m',
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// ===== utils =====
function random_int(min, max_inclusive) {
  return Math.floor(Math.random() * (max_inclusive - min + 1)) + min;
}

function random_string(length) {
  const chars = 'abcdefghijklmnopqrstuvwxyz';
  let out = '';

  for (let i = 0; i < length; i++) {
    out += chars.charAt(Math.floor(Math.random() * chars.length));
  }

  return out;
}

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

function sock_js_wrap(stomp_frame) {
  return JSON.stringify([stomp_frame]);
}

function parse_sock_js_data(raw) {
  const text = String(raw);

  if (text === 'o') return { type: 'open' };
  if (text === 'h') return { type: 'heartbeat' };

  if (text.startsWith('c[') || text.startsWith('c')) {
    return { type: 'close', raw: text };
  }

  if (text.startsWith('a[')) {
    try {
      const arr = JSON.parse(text.slice(1));
      return { type: 'array', messages: Array.isArray(arr) ? arr : [] };
    } catch (error) {
      return { type: 'invalid_array', raw: text, error: String(error) };
    }
  }

  if (
    text.startsWith('CONNECTED') ||
    text.startsWith('MESSAGE') ||
    text.startsWith('ERROR') ||
    text.startsWith('RECEIPT')
  ) {
    return { type: 'array', messages: [text] };
  }

  return { type: 'unknown', raw: text };
}

function parse_stomp_frame(frame_text) {
  const text = String(frame_text);
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

  const sock_js_server_id = random_int(0, 999);
  const sock_js_session_id = random_string(8);

  const url =
    `wss://${server_host}:${server_port}` +
    `${ws_base_path}/${sock_js_server_id}/${sock_js_session_id}/websocket` +
    `?access_token=${access_token}`;

  let sock_js_opened = false;
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
      'sockjs open received': () => sock_js_opened,
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

  function mark_ack(msg_id) {
    if (!pending_ack.has(msg_id)) return;

    const started_at = pending_ack.get(msg_id);
    pending_ack.delete(msg_id);

    ack_ok_count.add(1);
    ack_ok_rate.add(true);
    ack_latency_ms.add(Date.now() - started_at);
  }

  function mark_broadcast_delivery(msg_id) {
    if (!msg_id) return;

    if (received_broadcast_ids.has(msg_id)) return;

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

  const res = ws.connect(url, {}, function (socket) {
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

        const msg_id = `k6-${vu}-${sent}-${Date.now()}`;
        const content = `CID:${msg_id}|burst-message-${vu}-${sent}`;
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

        socket.send(sock_js_wrap(send_frame));
        debug_log(vu, `[VU ${vu}] sent_message ${sent}/${num_msgs_per_user}, msg_id=${msg_id}`);
      }, send_interval_ms);
    }

    socket.on('open', function () {
      debug_log(vu, `[VU ${vu}] websocket_open`);
    });

    socket.on('message', function (raw) {
      const parsed = parse_sock_js_data(raw);

      if (parsed.type === 'open') {
        sock_js_opened = true;

        if (!connect_sent) {
          const connect_frame = build_stomp_frame('CONNECT', {
            'accept-version': '1.2',
            'heart-beat': '10000,10000',
            writerId: writer_id,
          });

          socket.send(sock_js_wrap(connect_frame));
          connect_sent = true;

          debug_log(vu, `[VU ${vu}] sent_connect`);
        }

        return;
      }

      if (parsed.type === 'heartbeat') {
        return;
      }

      if (parsed.type === 'close') {
        debug_log(vu, `[VU ${vu}] sockjs_close_frame=${parsed.raw}`);
        finalize(socket, 'sockjs_close_frame');
        return;
      }

      if (parsed.type === 'invalid_array') {
        debug_log(vu, `[VU ${vu}] invalid_sockjs_array error=${parsed.error}, raw=${parsed.raw}`);
        return;
      }

      if (parsed.type === 'unknown') {
        debug_log(vu, `[VU ${vu}] unknown_sockjs_frame=${parsed.raw}`);
        return;
      }

      if (parsed.type !== 'array') {
        return;
      }

      for (const msg of parsed.messages) {
        if (typeof msg !== 'string') continue;

        if (msg.includes('CONNECTED')) {
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

            socket.send(sock_js_wrap(subscribe_ack_frame));
            socket.send(sock_js_wrap(subscribe_broadcast_frame));

            subscribe_frame_count.add(2);

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

        if (msg.includes('ERROR')) {
          console.log(`[VU ${vu}] STOMP ERROR=${msg}`);
          finalize(socket, 'stomp_error');
          return;
        }

        const frame = parse_stomp_frame(msg);
        if (frame.command !== 'MESSAGE') continue;

        let body_obj = null;

        try {
          body_obj = JSON.parse(frame.body);
        } catch (_) {
          debug_log(vu, `[VU ${vu}] failed_to_parse_message_body body=${frame.body}`);
          continue;
        }

        const msg_id = resolve_message_id(body_obj);

        if (!msg_id) {
          debug_log(vu, `[VU ${vu}] message_has_no_client_message_id_or_cid body=${frame.body}`);
          continue;
        }

        const destination =
          frame.headers.destination ||
          frame.headers['simpDestination'] ||
          '';

        if (destination === ack_destination) {
          mark_ack(msg_id);
        } else if (destination === broadcast_destination) {
          mark_broadcast_delivery(msg_id);
        } else {
          if (pending_ack.has(msg_id)) {
            mark_ack(msg_id);
          }

          if (body_obj?.content?.includes?.('CID:')) {
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
          `[VU ${vu}] timeout_waiting_connected sock_js_opened=${sock_js_opened}, connect_sent=${connect_sent}, session=${sock_js_session_id}`
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
      `[VU ${vu}] upgrade_failed status=${res && res.status}, error=${res && JSON.stringify(res.error)}`
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

  const timeout_total_count = late_total_count + missing_total_count;

  const expected_per_user = expected_total_count / num_vus;
  const ok_per_user = timeout_ok_count / num_vus;
  const timeout_per_user = timeout_total_count / num_vus;

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
========== clean_broadcast_result ==========

send_count: ${format_number(total_send_count)}
subscribe_frame_count: ${format_number(total_subscribe_frame_count)}

broadcast:
- total_count: ${format_number(total_count)} / ${format_number(expected_total_count)} (${percent(total_count, expected_total_count)})
- late_total_count: ${format_number(late_total_count)} / ${format_number(expected_total_count)} (${percent(late_total_count, expected_total_count)})
- missing_total_count: ${format_number(missing_total_count)} / ${format_number(expected_total_count)} (${percent(missing_total_count, expected_total_count)})

- ok_rate_per_user: ${format_number(ok_per_user)} / ${format_number(expected_per_user)} (${percent(ok_per_user, expected_per_user)})
- timeout_rate_per_user: ${format_number(timeout_per_user)} / ${format_number(expected_per_user)} (${percent(timeout_per_user, expected_per_user)})

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
