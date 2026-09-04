#!/usr/bin/env bash
# 테스트 방의 ChatMessage 쓰기 벤치마크 데이터만 초기화한다.
set -euo pipefail
cd "$(dirname "$0")/.."

[ -f benchmark.env ] || { echo "benchmark.env 없음"; exit 1; }
set -a; . ./benchmark.env; set +a
: "${ROOM_ID:?ROOM_ID가 benchmark.env에 없다}"

REPO_ROOT=$(git rev-parse --show-toplevel)
INFRA_ENV="${INFRA_ENV_FILE:-$(dirname "$REPO_ROOT")/crypto-project-infra/infra/.env}"
MONGO_CONTAINER="${MONGO_CONTAINER:-mongo-primary}"
REDIS_CONTAINER="${REDIS_CONTAINER:-redis-0}"
REDIS_PORT="${REDIS_PORT:-7100}"

[ -f "$INFRA_ENV" ] || { echo "인프라 환경 파일 없음: $INFRA_ENV"; exit 1; }
set -a; . "$INFRA_ENV"; set +a

mongo_eval() {
  local script=$1
  docker exec -i \
    -e MU="$MONGO_ROOT_USERNAME" \
    -e MP="$MONGO_ROOT_PASSWORD" \
    -e ROOM_ID="$ROOM_ID" \
    -e BENCH_SCRIPT="$script" \
    "$MONGO_CONTAINER" sh -c \
    'mongosh --quiet -u "$MU" -p "$MP" --authenticationDatabase admin chat --eval "$BENCH_SCRIPT"'
}

mongo_eval '
const roomId = ObjectId(process.env.ROOM_ID);
print("삭제 대상 테스트 방 메시지: " + db.chat_message.countDocuments({room_id: roomId}));
print("삭제 대상 room membership: " + db.chat_room_membership.countDocuments({room_id: roomId}));
const room = db.chat_room.findOne({_id: roomId});
print("남길 방: " + (room ? room.title : "없음") + ", 멤버 " + (room ? (room.member_ids || []).length : 0));
'

if [ "${YES:-0}" != "1" ]; then
  printf '\n초기화할까? (yes 입력): '
  read -r answer
  [ "$answer" = "yes" ] || { echo "취소"; exit 0; }
fi

mongo_eval '
const roomId = ObjectId(process.env.ROOM_ID);
const messages = db.chat_message.deleteMany({room_id: roomId}).deletedCount;
const memberships = db.chat_room_membership.deleteMany({room_id: roomId}).deletedCount;
db.chat_room.updateOne(
  {_id: roomId},
  {$set: {msg_cnt: NumberLong("0"), last_msg_seq: NumberLong("0")}, $unset: {last_msg_created_at: ""}}
);
print("테스트 방 메시지 삭제: " + messages);
print("room membership 삭제: " + memberships);
print("방 msgCnt/lastMsgSeq 초기화 완료");
'

redis_del() {
  docker exec -i "$REDIS_CONTAINER" redis-cli -c -p "$REDIS_PORT" DEL "$1"
}

redis_zrem() {
  docker exec -i "$REDIS_CONTAINER" redis-cli -c -p "$REDIS_PORT" ZREM "$1" "$2"
}

removed=0
for key in "{chat}:message:$ROOM_ID" "{chat}:room:$ROOM_ID" \
           "{chat}:room:$ROOM_ID:message-access" "{chat}:room:$ROOM_ID:last_read"; do
  removed=$((removed + $(redis_del "$key")))
done

removed=$((removed + $(redis_zrem "{chat}:room:activity:recent" "$ROOM_ID")))
removed=$((removed + $(redis_zrem "{chat}:room:activity:inflight" "$ROOM_ID")))

for node_port in 7100 7101 7102 7103 7104 7105; do
  container="redis-$((node_port - 7100))"
  for key in $(docker exec -i "$container" redis-cli -p "$node_port" \
                 --scan --pattern '{chat}:active-room:*' 2>/dev/null); do
    removed=$((removed + $(redis_zrem "$key" "$ROOM_ID")))
  done
done

echo "Redis 키·projection entry 삭제: $removed"
echo "초기화 완료"
