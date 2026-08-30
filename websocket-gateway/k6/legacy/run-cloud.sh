#!/usr/bin/env bash
# 클라우드(k6 전용) 실행 스크립트.
# 로컬판과 달리 JFR·docker 제어가 없다 — 서버는 맥에 있으므로 그쪽에서 따로 뜬다.
# 사용법: ./run.sh <VUS> [MESSAGE_COUNT] [라벨]
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
mkdir -p results

# k6 자체 자원 사용량을 함께 남긴다. 로컬 측정에서 k6 가 CPU 484% 를 먹어
# 서버와 경합한 것이 확인됐으므로, 분리 후에도 k6 가 포화하지 않는지 봐야 한다.
( while true; do
    ps -Ao rss,%cpu,comm 2>/dev/null | awk '/ k6$/{printf "%.0f %.0f\n",$1/1024,$2}'
    sleep 3
  done > "$OUT.k6usage" ) &
MON=$!

{
  echo "=== ${LABEL} VU=${VUS} MESSAGE_COUNT=${MESSAGE_COUNT} ==="
  echo "시작: $(date '+%Y-%m-%d %H:%M:%S %Z')"
  echo "대상: ${WS_BASE_URL}"
} | tee "$OUT.meta"

export VUS MESSAGE_COUNT
export MESSAGE_INTERVAL_MS=1000 ACK_TIMEOUT_MS=11000 COLLECT_WINDOW_MS=60000

k6 run --insecure-skip-tls-verify chat-message-fanout-native.js 2>&1 | tee "$OUT.txt"

kill $MON 2>/dev/null || true
{
  echo "종료: $(date '+%Y-%m-%d %H:%M:%S %Z')"
  awk 'NF==2{if($1>m)m=$1; if($2>c)c=$2} END{printf "k6 자원 최대: 메모리 %.0f MB · CPU %.0f%%\n", m, c}' "$OUT.k6usage"
} | tee -a "$OUT.meta"
