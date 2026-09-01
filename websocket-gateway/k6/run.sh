#!/usr/bin/env bash
# 사용법: ./run.sh <VUS> [MESSAGE_COUNT] [라벨]
#   ./run.sh 2 3 smoke     스모크
#   ./run.sh 20 30 warmup  워밍업(버림)
#   ./run.sh 60            측정
set -euo pipefail
cd "$(dirname "$0")"

[ -f k6.env ] || { echo "k6.env 없음"; exit 1; }
set -a; . ./k6.env; set +a
: "${ROOM_ID:?ROOM_ID 가 k6.env 에 없다}"

VUS="${1:?사용법: ./run.sh <VUS> [MESSAGE_COUNT] [라벨]}"
MESSAGE_COUNT="${2:-60}"
LABEL="${3:-run}"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="results/${LABEL}-vu${VUS}-msg${MESSAGE_COUNT}-${STAMP}"
mkdir -p results/jfr

WS=$(docker ps --filter "name=^/crypto-websocket-gateway" --format '{{.Names}}' | head -1)
CHAT=$(docker ps --filter "name=^/crypto-chat-service" --format '{{.Names}}' | head -1)

host_port() {
  local container=$1 container_port=$2
  docker port "$container" "${container_port}/tcp" 2>/dev/null | awk -F: 'NR == 1 { print $NF }'
}

WS_HOST_PORT=$([ -n "$WS" ] && host_port "$WS" 8100 || true)
CHAT_HOST_PORT=$([ -n "$CHAT" ] && host_port "$CHAT" 8080 || true)

GATEWAY_METRICS_URL="${GATEWAY_METRICS_URL:-http://localhost:${WS_HOST_PORT}/actuator/prometheus}"
CHAT_METRICS_URL="${CHAT_METRICS_URL:-http://localhost:${CHAT_HOST_PORT}/api/v1/actuator/prometheus}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:19090}"
METRIC_KEEP='^(executor_|stomp_|chat_|ws_|jvm_memory_used_bytes|jvm_gc_pause_seconds|hikaricp_connections)'
PROMETHEUS_QUERY='{job=~"crypto-websocket-gateway|crypto-chat-service",__name__=~"executor_.*|stomp_.*|chat_.*|ws_.*|jvm_memory_used_bytes|jvm_gc_pause_seconds.*|hikaricp_connections.*"}'

snapshot_metrics() {
  local mode=$1 stamp=$2

  {
    curl -sf --max-time 3 "$GATEWAY_METRICS_URL" 2>/dev/null \
      | grep -E "$METRIC_KEEP" | grep -v '^#' | sed "s|^|${stamp} gateway |" || true
    curl -sf --max-time 3 "$CHAT_METRICS_URL" 2>/dev/null \
      | grep -E "$METRIC_KEEP" | grep -v '^#' | sed "s|^|${stamp} chat |" || true
  } >> "$OUT.metrics"

  curl -sf --max-time 3 "$GATEWAY_METRICS_URL" > "$OUT.metrics-${mode}-gateway.prom" || true
  curl -sf --max-time 3 "$CHAT_METRICS_URL" > "$OUT.metrics-${mode}-chat.prom" || true
}

swapins() { vm_stat | awk '/Swapins/{gsub(/\./,"",$2);print $2}'; }

{
  echo "=== ${LABEL} VU=${VUS} MESSAGE_COUNT=${MESSAGE_COUNT} ==="
  echo "시작: $(date '+%Y-%m-%d %H:%M:%S')"
  echo "gateway=${WS}  chat=${CHAT}"
} | tee "$OUT.meta"

SWAP0=$(swapins)
METRICS_START=$(date +%s)
snapshot_metrics before "$METRICS_START"

# JFR — 측정 회차에만
if [ "$LABEL" = "run" ]; then
  [ -n "$WS" ]   && docker exec "$WS"   jcmd 1 JFR.start name=load settings=profile \
                      duration=10m dumponexit=true filename=/tmp/gateway-vu${VUS}.jfr >/dev/null 2>&1 || true
  [ -n "$CHAT" ] && docker exec "$CHAT" jcmd 1 JFR.start name=load settings=profile \
                      duration=10m dumponexit=true filename=/tmp/chat-vu${VUS}.jfr >/dev/null 2>&1 || true
fi

# k6 v2 는 시스템 환경변수를 __ENV 로 그대로 넘긴다(--include-system-env-vars 기본 true).
# `-e NAME` 처럼 값 없이 쓰면 오히려 undefined 로 덮어써서 쓰지 않는다.
# export 만으로 넘기면 토큰이 명령줄(ps)에도 노출되지 않는다.
export VUS MESSAGE_COUNT
export MESSAGE_INTERVAL_MS=1000 ACK_TIMEOUT_MS=11000 COLLECT_WINDOW_MS=60000

k6 run --insecure-skip-tls-verify "${SCENARIO:-scenarios/chat-message-fanout-native.js}" 2>&1 | tee "$OUT.txt"

sleep 30   # 드레인 구간까지 녹화

if [ "$LABEL" = "run" ]; then
  [ -n "$WS" ]   && { docker exec "$WS"   jcmd 1 JFR.stop name=load >/dev/null 2>&1 || true
                      docker cp "$WS:/tmp/gateway-vu${VUS}.jfr" "results/jfr/" 2>/dev/null || true; }
  [ -n "$CHAT" ] && { docker exec "$CHAT" jcmd 1 JFR.stop name=load >/dev/null 2>&1 || true
                      docker cp "$CHAT:/tmp/chat-vu${VUS}.jfr" "results/jfr/" 2>/dev/null || true; }
fi

METRICS_END=$(date +%s)
snapshot_metrics after "$METRICS_END"

curl -sf --get --max-time 15 "$PROMETHEUS_URL/api/v1/query_range" \
  --data-urlencode "query=$PROMETHEUS_QUERY" \
  --data-urlencode "start=$METRICS_START" \
  --data-urlencode "end=$METRICS_END" \
  --data-urlencode "step=5" \
  > "$OUT.prometheus.json" || true

tools/summarize-metrics.py "$OUT.metrics" | tee "$OUT.metrics-summary.txt"

SWAP1=$(swapins)
{
  echo "종료: $(date '+%Y-%m-%d %H:%M:%S')"
  echo "측정 중 swapin: $(( (SWAP1-SWAP0)*4096/1024/1024 )) MB"
  echo "metrics_start_epoch=${METRICS_START}"
  echo "metrics_end_epoch=${METRICS_END}"
  echo "gateway_image=$(docker inspect -f '{{.Image}}' "$WS" 2>/dev/null || true)"
  echo "chat_image=$(docker inspect -f '{{.Image}}' "$CHAT" 2>/dev/null || true)"
} | tee -a "$OUT.meta"
