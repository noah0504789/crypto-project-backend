#!/usr/bin/env bash
# 측정 중 앱 지표를 주기적으로 긁어 파일에 쌓는다.
#
# Prometheus/Grafana 를 띄우지 않는 이유: 그 스택이 1.5GB 를 먹는데 이 호스트는
# 이미 스왑을 심하게 쓴다. 모니터링이 측정 대상을 밀어내면 재는 값이 바뀐다.
# 필요한 건 앱 두 개의 지표뿐이고 actuator 가 이미 그걸 준다.
#
# 파일에 남기는 편이 Grafana 보다 낫기도 하다 — 회차 결과가 results/ 에 남는다.
#
# 사용법: tools/sample-metrics.sh <출력파일> [주기초]
set -euo pipefail

OUT="${1:?사용법: tools/sample-metrics.sh <출력파일> [주기초]}"
INTERVAL="${2:-2}"

GATEWAY_URL="${GATEWAY_METRICS_URL:-http://localhost:8100/actuator/prometheus}"
CHAT_URL="${CHAT_METRICS_URL:-http://localhost:8080/actuator/prometheus}"

# 채팅 팬아웃 경로에 해당하는 것만 남긴다. 전부 받으면 파일이 수백 MB 가 된다.
KEEP='^(executor_|stomp_|chat_|ws_active_sessions|jvm_memory_used_bytes|jvm_gc_pause)'

scrape() {
  local source=$1 url=$2 stamp=$3
  curl -sf --max-time 3 "$url" 2>/dev/null \
    | grep -E "$KEEP" \
    | grep -v '^#' \
    | sed "s|^|${stamp} ${source} |" || true
}

echo "샘플링 시작 (${INTERVAL}초 간격) -> $OUT"
: > "$OUT"

while true; do
  stamp=$(date +%s)
  { scrape gateway "$GATEWAY_URL" "$stamp"
    scrape chat "$CHAT_URL" "$stamp"; } >> "$OUT"
  sleep "$INTERVAL"
done
