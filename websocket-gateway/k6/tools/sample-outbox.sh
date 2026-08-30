#!/usr/bin/env bash
# 측정 중 outbox 의 상태별 적체를 1초 간격으로 기록한다.
#
# 게이트웨이가 굶는 이유를 가르는 용도다.
#   PENDING 이 쌓인다     폴러가 집어가지 못한다
#   PENDING 이 0 근처다   폴러는 집어가는데 그 뒤(Kafka 발행)가 느리다
#
# 사용법: tools/sample-outbox.sh <출력파일> [주기초]
set -euo pipefail
cd "$(dirname "$0")/.."

OUT="${1:?사용법: tools/sample-outbox.sh <출력파일> [주기초]}"
INTERVAL="${2:-1}"

INFRA_ENV="${INFRA_ENV_FILE:-$HOME/crypto-project/crypto-project-infra/infra/.env}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql-master}"

set -a; . "$INFRA_ENV"; set +a

SQL="SELECT dispatch_type, status, COUNT(*) FROM outbox GROUP BY dispatch_type, status;"

echo "# epoch<TAB>dispatch_type<TAB>status<TAB>count" > "$OUT"

while true; do
  stamp=$(date +%s)
  docker exec -i -e P="$MYSQL_ROOT_PASSWORD" -e Q="$SQL" "$MYSQL_CONTAINER" \
    sh -c 'mysql -uroot -p"$P" event -N -B -e "$Q" 2>/dev/null' \
    | sed "s/^/${stamp}\t/" >> "$OUT" || true
  sleep "$INTERVAL"
done
