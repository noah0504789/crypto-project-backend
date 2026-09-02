#!/usr/bin/env bash
# 테스트 계정 ID 들을 채팅방 멤버로 등록한다.
#
# 쓰기 권한 검사는 ChatRoom.validateWritable 하나뿐이고 그것이 보는 것은
# chat_room.member_ids 다. user DB 와 무관하므로 여기만 채우면 된다.
# #288 이후 메시지 저장은 chat_room_membership 을 만들지 않는다. 이 팬아웃 테스트는
# 송신 권한에 필요한 chat_room.member_ids 만 준비하며 Mongo projection 복구는 검증하지 않는다.
#
# 사용법: tools/seed-room-members.sh [ids파일]        방 ID 는 k6.env 의 ROOM_ID
#         REMOVE=1 tools/seed-room-members.sh        등록 해제(측정 뒤 되돌리기)
set -euo pipefail
cd "$(dirname "$0")/.."

IDS_FILE="${1:-accounts/test-users-ids.txt}"
REPO_ROOT=$(git rev-parse --show-toplevel)
INFRA_ENV="${INFRA_ENV_FILE:-$(dirname "$REPO_ROOT")/crypto-project-infra/infra/.env}"
MONGO_CONTAINER="${MONGO_CONTAINER:-mongo-primary}"

[ -f "$IDS_FILE" ]  || { echo "ID 파일 없음: $IDS_FILE — 먼저 tools/mint-test-users.py 실행"; exit 1; }
[ -f k6.env ]       || { echo "k6.env 없음"; exit 1; }
[ -f "$INFRA_ENV" ] || { echo "인프라 환경 파일 없음: $INFRA_ENV"; exit 1; }

set -a; . ./k6.env; . "$INFRA_ENV"; set +a
: "${ROOM_ID:?ROOM_ID 가 k6.env 에 없다}"
: "${MONGO_ROOT_USERNAME:?}"
: "${MONGO_ROOT_PASSWORD:?}"

SCRIPT=$(python3 - "$IDS_FILE" "$ROOM_ID" "${REMOVE:-0}" <<'PY'
import json, sys

ids_file, room_id, remove = sys.argv[1], sys.argv[2], sys.argv[3] == "1"

with open(ids_file) as f:
    ids = [line.strip() for line in f if line.strip()]

update = (
    {"$pullAll": {"member_ids": ids}}
    if remove
    else {"$addToSet": {"member_ids": {"$each": ids}}}
)

print(f'''
const roomId = ObjectId("{room_id}");
const before = db.chat_room.findOne({{_id: roomId}});
if (!before) {{ print("방 없음: {room_id}"); quit(1); }}
db.chat_room.updateOne({{_id: roomId}}, {json.dumps(update)});
const after = db.chat_room.findOne({{_id: roomId}});
print("방       " + after.title);
print("멤버 수  " + (before.member_ids || []).length + " -> " + (after.member_ids || []).length);
'''.strip())
PY
)

# --eval 로 넘긴다. 표준입력으로 주면 mongosh 가 대화형 프롬프트를 줄마다 찍는다.
docker exec -i \
  -e MU="$MONGO_ROOT_USERNAME" -e MP="$MONGO_ROOT_PASSWORD" -e JS="$SCRIPT" \
  "$MONGO_CONTAINER" sh -c \
  'mongosh --quiet -u "$MU" -p "$MP" --authenticationDatabase admin chat --eval "$JS"'

echo "$([ "${REMOVE:-0}" = "1" ] && echo 해제 || echo 등록) 완료 — $(grep -c . "$IDS_FILE")개"
