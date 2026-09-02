#!/usr/bin/env bash
# 사용법: ./run.sh [MESSAGE_COUNT] [RATE_PER_SECOND] [라벨]
set -euo pipefail
cd "$(dirname "$0")"

[ -f benchmark.env ] || { echo "benchmark.env 없음"; exit 1; }
set -a; . ./benchmark.env; set +a
: "${ROOM_ID:?ROOM_ID가 benchmark.env에 없다}"

MESSAGE_COUNT="${1:-6000}"
RATE_PER_SECOND="${2:-100}"
LABEL="${3:-run}"
WRITER_COUNT="${WRITER_COUNT:-100}"
DRAIN_TIMEOUT_SECONDS="${DRAIN_TIMEOUT_SECONDS:-600}"

[[ "$MESSAGE_COUNT" =~ ^[1-9][0-9]*$ ]] || { echo "MESSAGE_COUNT는 양의 정수여야 한다"; exit 1; }
[[ "$RATE_PER_SECOND" =~ ^[0-9]+([.][0-9]+)?$ ]] || { echo "RATE_PER_SECOND는 양수여야 한다"; exit 1; }
awk -v rate="$RATE_PER_SECOND" 'BEGIN {exit !(rate > 0)}' || { echo "RATE_PER_SECOND는 0보다 커야 한다"; exit 1; }
[[ "$LABEL" =~ ^[a-zA-Z0-9_-]+$ ]] || { echo "라벨은 영문·숫자·_·-만 사용할 수 있다"; exit 1; }
[[ "$WRITER_COUNT" =~ ^[1-9][0-9]*$ ]] || { echo "WRITER_COUNT는 양의 정수여야 한다"; exit 1; }
[[ "$DRAIN_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]] || { echo "DRAIN_TIMEOUT_SECONDS는 양의 정수여야 한다"; exit 1; }

REPO_ROOT=$(git rev-parse --show-toplevel)
INFRA_ENV="${INFRA_ENV_FILE:-$(dirname "$REPO_ROOT")/crypto-project-infra/infra/.env}"
MONGO_CONTAINER="${MONGO_CONTAINER:-mongo-primary}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-kafka-0}"
KAFKA_BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP_SERVER:-kafka-0:9092}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:19090}"
TOPIC="${TOPIC:-chatmessage-event}"

[ -f "$INFRA_ENV" ] || { echo "인프라 환경 파일 없음: $INFRA_ENV"; exit 1; }
set -a; . "$INFRA_ENV"; set +a

CHAT=$(docker ps --filter "name=^/crypto-chat-service" --format '{{.Names}}' | head -1)
[ -n "$CHAT" ] || { echo "실행 중인 chat-service 컨테이너가 없다"; exit 1; }
docker inspect "$MONGO_CONTAINER" >/dev/null 2>&1 || { echo "Mongo 컨테이너가 없다: $MONGO_CONTAINER"; exit 1; }
docker inspect "$KAFKA_CONTAINER" >/dev/null 2>&1 || { echo "Kafka 컨테이너가 없다: $KAFKA_CONTAINER"; exit 1; }

host_port() {
  docker port "$1" "$2/tcp" 2>/dev/null | awk -F: 'NR == 1 { print $NF }'
}

CHAT_HOST_PORT=$(host_port "$CHAT" 8080)
CHAT_METRICS_URL="${CHAT_METRICS_URL:-http://localhost:${CHAT_HOST_PORT}/api/v1/actuator/prometheus}"
curl -sf --max-time 3 "$CHAT_METRICS_URL" >/dev/null || { echo "chat metrics endpoint 응답 없음: $CHAT_METRICS_URL"; exit 1; }

STAMP=$(date +%Y%m%d-%H%M%S)
RUN_ID="${LABEL}-${STAMP}"
OUT="results/${RUN_ID}"
mkdir -p results
BENCH_TMP_DIR=$(mktemp -d)
trap 'rm -rf -- "$BENCH_TMP_DIR"' EXIT

mongo_eval() {
  local database=$1 script=$2
  docker exec -i \
    -e MU="$MONGO_ROOT_USERNAME" \
    -e MP="$MONGO_ROOT_PASSWORD" \
    -e ROOM_ID="$ROOM_ID" \
    -e RUN_ID="$RUN_ID" \
    -e BENCH_SCRIPT="$script" \
    "$MONGO_CONTAINER" sh -c \
    'mongosh --quiet -u "$MU" -p "$MP" --authenticationDatabase admin "$0" --eval "$BENCH_SCRIPT"' "$database"
}

mongo_server_snapshot() {
  mongo_eval admin '
const status = db.serverStatus();
const number = value => value == null ? 0 : (typeof value === "number" ? value : value.toNumber());
print(JSON.stringify({
  opcounters: {
    insert: number(status.opcounters.insert),
    query: number(status.opcounters.query),
    update: number(status.opcounters.update),
    delete: number(status.opcounters.delete),
    getmore: number(status.opcounters.getmore),
    command: number(status.opcounters.command)
  },
  document: {
    inserted: number(status.metrics.document.inserted),
    updated: number(status.metrics.document.updated),
    deleted: number(status.metrics.document.deleted),
    returned: number(status.metrics.document.returned)
  },
  transactions: {
    committed: number(status.transactions.totalCommitted),
    aborted: number(status.transactions.totalAborted)
  }
}));
'
}

run_message_count() {
  mongo_eval chat '
const roomId = ObjectId(process.env.ROOM_ID);
const escaped = process.env.RUN_ID.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
print(db.chat_message.countDocuments({
  room_id: roomId,
  content: new RegExp("^chatmessage-write-benchmark:" + escaped + ":")
}));
'
}

room_state() {
  mongo_eval chat '
const roomId = ObjectId(process.env.ROOM_ID);
const room = db.chat_room.findOne({_id: roomId});
if (!room) { throw new Error("테스트 방이 없다"); }
print(JSON.stringify({
  members: (room.member_ids || []).length,
  msgCnt: Number(room.msg_cnt || 0),
  latestMsgSeq: Number(room.latest_msg_seq || 0),
  memberships: db.chat_room_membership.countDocuments({room_id: roomId}),
  messages: db.chat_message.countDocuments({room_id: roomId})
}));
'
}

kafka_lag() {
  docker exec "$KAFKA_CONTAINER" /opt/kafka/bin/kafka-consumer-groups.sh \
    --bootstrap-server "$KAFKA_BOOTSTRAP_SERVER" \
    --describe --group chat 2>/dev/null \
    | awk -v topic="$TOPIC" '$2 == topic {sum += $6; found=1} END {print found ? sum : -1}'
}

swapins() {
  vm_stat | awk '/Swapins/{gsub(/\./,"",$2); print $2}'
}

STATE_BEFORE=$(room_state)
MEMBER_COUNT=$(jq -r '.members' <<<"$STATE_BEFORE")
MEMBERSHIP_COUNT=$(jq -r '.memberships' <<<"$STATE_BEFORE")
EXISTING_MESSAGE_COUNT=$(jq -r '.messages' <<<"$STATE_BEFORE")
ROOM_MESSAGE_COUNT=$(jq -r '.msgCnt' <<<"$STATE_BEFORE")
ROOM_LATEST_SEQUENCE=$(jq -r '.latestMsgSeq' <<<"$STATE_BEFORE")
INITIAL_LAG=$(kafka_lag)

[ "$MEMBER_COUNT" -ge "$WRITER_COUNT" ] || { echo "방 멤버가 writer 수보다 적다: $MEMBER_COUNT < $WRITER_COUNT"; exit 1; }
[ "$MEMBERSHIP_COUNT" -eq 0 ] || { echo "room membership이 남아 있다: $MEMBERSHIP_COUNT (tools/reset-data.sh 필요)"; exit 1; }
[ "$EXISTING_MESSAGE_COUNT" -eq 0 ] || { echo "테스트 방 메시지가 남아 있다: $EXISTING_MESSAGE_COUNT (tools/reset-data.sh 필요)"; exit 1; }
[ "$ROOM_MESSAGE_COUNT" -eq 0 ] || { echo "room msgCnt가 0이 아니다: $ROOM_MESSAGE_COUNT (tools/reset-data.sh 필요)"; exit 1; }
[ "$ROOM_LATEST_SEQUENCE" -eq 0 ] || { echo "room latestMsgSeq가 0이 아니다: $ROOM_LATEST_SEQUENCE (tools/reset-data.sh 필요)"; exit 1; }
[ "$INITIAL_LAG" -eq 0 ] || { echo "시작 전 Kafka lag이 0이 아니다: $INITIAL_LAG"; exit 1; }

mongo_eval chat '
const room = db.chat_room.findOne({_id: ObjectId(process.env.ROOM_ID)});
print(JSON.stringify(room.member_ids || []));
' > "$BENCH_TMP_DIR/members.json"

METRICS_START=$(date +%s)
SWAP_BEFORE=$(swapins)
mongo_server_snapshot > "$OUT.mongo-before.json"
curl -sf --max-time 5 "$CHAT_METRICS_URL" > "$OUT.metrics-before-chat.prom"

{
  echo "label=$LABEL"
  echo "run_id=$RUN_ID"
  echo "message_count=$MESSAGE_COUNT"
  echo "rate_per_second=$RATE_PER_SECOND"
  echo "writer_count=$WRITER_COUNT"
  echo "member_count=$MEMBER_COUNT"
  echo "room_id=$ROOM_ID"
  echo "started_at=$(date '+%Y-%m-%d %H:%M:%S')"
  echo "chat_container=$CHAT"
  echo "chat_image=$(docker inspect -f '{{.Config.Image}}' "$CHAT")"
  echo "chat_image_digest=$(docker inspect -f '{{.Image}}' "$CHAT")"
} > "$OUT.meta"

PUBLISH_STARTED=$(date +%s)
python3 tools/produce-events.py \
  --count "$MESSAGE_COUNT" \
  --rate "$RATE_PER_SECOND" \
  --room-id "$ROOM_ID" \
  --run-id "$RUN_ID" \
  --members-file "$BENCH_TMP_DIR/members.json" \
  --writer-count "$WRITER_COUNT" \
  2> >(tee "$OUT.producer.txt" >&2) \
| docker exec -i "$KAFKA_CONTAINER" /opt/kafka/bin/kafka-console-producer.sh \
    --bootstrap-server "$KAFKA_BOOTSTRAP_SERVER" \
    --topic "$TOPIC" \
    --property parse.headers=true \
    --property parse.key=true \
    --producer-property acks=all \
    --producer-property enable.idempotence=true \
    2>> "$OUT.producer.txt"
PUBLISH_ENDED=$(date +%s)

DEADLINE=$((SECONDS + DRAIN_TIMEOUT_SECONDS))
STABLE=0
PERSISTED=0
LAG=-1
while [ "$SECONDS" -lt "$DEADLINE" ]; do
  PERSISTED=$(run_message_count)
  LAG=$(kafka_lag)
  printf '\r저장 대기: %s/%s, lag=%s' "$PERSISTED" "$MESSAGE_COUNT" "$LAG"
  if [ "$PERSISTED" -eq "$MESSAGE_COUNT" ] && [ "$LAG" -eq 0 ]; then
    STABLE=$((STABLE + 1))
    [ "$STABLE" -ge 2 ] && break
  else
    STABLE=0
  fi
  sleep 2
done
echo

METRICS_END=$(date +%s)
SWAP_AFTER=$(swapins)
mongo_server_snapshot > "$OUT.mongo-after.json"
curl -sf --max-time 5 "$CHAT_METRICS_URL" > "$OUT.metrics-after-chat.prom"

PROMETHEUS_QUERY='{job=~"crypto-chat-service|mongo|kafka",__name__=~"chat_message_persistence_.*|kafka_consumergroup_lag|mongodb_op_counters_total"}'
curl -sf --get --max-time 15 "$PROMETHEUS_URL/api/v1/query_range" \
  --data-urlencode "query=$PROMETHEUS_QUERY" \
  --data-urlencode "start=$METRICS_START" \
  --data-urlencode "end=$METRICS_END" \
  --data-urlencode "step=5" \
  > "$OUT.prometheus.json" || true

PUBLISH_SECONDS=$((PUBLISH_ENDED - PUBLISH_STARTED))
DRAIN_SECONDS=$((METRICS_END - PUBLISH_STARTED))
SWAP_MEGABYTES=$(( (SWAP_AFTER - SWAP_BEFORE) * 4096 / 1024 / 1024 ))
{
  echo "finished_at=$(date '+%Y-%m-%d %H:%M:%S')"
  echo "persisted=$PERSISTED"
  echo "final_lag=$LAG"
  echo "publish_seconds=$PUBLISH_SECONDS"
  echo "drain_seconds=$DRAIN_SECONDS"
  echo "swapin_megabytes=$SWAP_MEGABYTES"
  echo "metrics_start_epoch=$METRICS_START"
  echo "metrics_end_epoch=$METRICS_END"
} >> "$OUT.meta"

python3 tools/summarize.py \
  --before-mongo "$OUT.mongo-before.json" \
  --after-mongo "$OUT.mongo-after.json" \
  --before-metrics "$OUT.metrics-before-chat.prom" \
  --after-metrics "$OUT.metrics-after-chat.prom" \
  --expected "$MESSAGE_COUNT" \
  --persisted "$PERSISTED" \
  --members "$MEMBER_COUNT" \
  --publish-seconds "$PUBLISH_SECONDS" \
  --drain-seconds "$DRAIN_SECONDS" \
  | tee "$OUT.summary.md"

if [ "$PERSISTED" -ne "$MESSAGE_COUNT" ] || [ "$LAG" -ne 0 ]; then
  echo "drain timeout: persisted=$PERSISTED/$MESSAGE_COUNT lag=$LAG" >&2
  exit 1
fi
