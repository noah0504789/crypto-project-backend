#!/usr/bin/env bash
# 측정 전 채팅 데이터를 비운다.
#
# 방 자체(host_id · member_ids · title)는 남긴다. 지우는 것은 메시지와 그 파생물이다.
# Mongo 만 지우면 Redis 에 남은 목록 캐시가 유령 메시지를 돌려주므로 같이 지운다.
#
# 사용법: tools/reset-chat-data.sh          지울 것을 보여주고 확인을 받는다
#         YES=1 tools/reset-chat-data.sh    확인 없이 실행
set -euo pipefail
cd "$(dirname "$0")/.."

REPO_ROOT=$(git rev-parse --show-toplevel)
INFRA_ENV="${INFRA_ENV_FILE:-$(dirname "$REPO_ROOT")/crypto-project-infra/infra/.env}"
MONGO_CONTAINER="${MONGO_CONTAINER:-mongo-primary}"
REDIS_CONTAINER="${REDIS_CONTAINER:-redis-0}"
REDIS_PORT="${REDIS_PORT:-7100}"

[ -f k6.env ]       || { echo "k6.env 없음"; exit 1; }
[ -f "$INFRA_ENV" ] || { echo "인프라 환경 파일 없음: $INFRA_ENV"; exit 1; }

set -a; . ./k6.env; . "$INFRA_ENV"; set +a
: "${ROOM_ID:?ROOM_ID 가 k6.env 에 없다}"

mongo_eval() {
  docker exec -i -e MU="$MONGO_ROOT_USERNAME" -e MP="$MONGO_ROOT_PASSWORD" -e JS="$1" \
    "$MONGO_CONTAINER" sh -c \
    'mongosh --quiet -u "$MU" -p "$MP" --authenticationDatabase admin chat --eval "$JS"'
}

echo "지울 것:"
mongo_eval '
const roomId = ObjectId("'"$ROOM_ID"'");
print("  chat_message          " + db.chat_message.countDocuments({room_id: roomId}));
print("  chat_room_membership  " + db.chat_room_membership.countDocuments({room_id: roomId}));
const room = db.chat_room.findOne({_id: roomId});
print("");
print("남길 것:");
print("  chat_room             " + (room ? room.title : "(없음)")
    + "  host " + (room && room.host_id ? "있음" : "없음")
    + "  멤버 " + (room ? (room.member_ids || []).length : 0));
'

if [ "${YES:-0}" != "1" ]; then
  printf '\n진행할까? (yes 입력): '
  read -r answer
  [ "$answer" = "yes" ] || { echo "취소"; exit 0; }
fi

echo
echo "Mongo..."
mongo_eval '
const roomId = ObjectId("'"$ROOM_ID"'");
print("  chat_message 삭제         " + db.chat_message.deleteMany({room_id: roomId}).deletedCount);
print("  chat_room_membership 삭제 " + db.chat_room_membership.deleteMany({room_id: roomId}).deletedCount);
db.chat_room.updateOne(
  {_id: roomId},
  {
    $set: {msg_cnt: NumberLong("0"), last_msg_seq: NumberLong("0"), popularity: NumberLong("0")},
    $unset: {last_msg_created_at: "", memberIds: "", msgCnt: "", lastMsgSeq: ""}
  }
);
print("  chat_room 카운터·watermark 초기화");
'

# 채팅 키는 전부 {chat} 해시태그를 써 한 슬롯에 모여 있다. 그 슬롯이 어느 노드에
# 있는지는 고정이 아니므로 스캔은 노드를 돌며 하고, 삭제는 -c 로 리다이렉트를 따른다.
# 방 제목 유니크 인덱스와 인기방 인덱스는 메시지 파생물이 아니므로 남긴다.
echo "Redis..."

redis_del() {
  docker exec -i "$REDIS_CONTAINER" redis-cli -c -p "$REDIS_PORT" DEL "$1"
}

redis_zrem() {
  docker exec -i "$REDIS_CONTAINER" redis-cli -c -p "$REDIS_PORT" ZREM "$1" "$2"
}

deleted=0
for key in "{chat}:message:$ROOM_ID" "{chat}:room:$ROOM_ID" \
           "{chat}:room:$ROOM_ID:message-access" "{chat}:room:$ROOM_ID:last_read"; do
  deleted=$((deleted + $(redis_del "$key")))
done

# projector 작업 목록은 여러 방이 공유하므로 키 전체가 아니라 테스트 방 entry만 지운다.
deleted=$((deleted + $(redis_zrem "{chat}:room:activity:recent" "$ROOM_ID")))
deleted=$((deleted + $(redis_zrem "{chat}:room:activity:inflight" "$ROOM_ID")))

for node_port in 7100 7101 7102 7103 7104 7105; do
  container="redis-$((node_port - 7100))"
  for key in $(docker exec -i "$container" redis-cli -p "$node_port" \
                 --scan --pattern '{chat}:active-room:*' 2>/dev/null); do
    deleted=$((deleted + $(redis_zrem "$key" "$ROOM_ID")))
  done
done

echo "  삭제한 키·projection entry $deleted"

echo
echo "완료. 남은 상태:"
tools/chat-data-status.sh
