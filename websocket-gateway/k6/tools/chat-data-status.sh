#!/usr/bin/env bash
# 지우기 전에 무엇이 있는지 본다. 읽기만 한다.
set -euo pipefail
cd "$(dirname "$0")/.."

REPO_ROOT=$(git rev-parse --show-toplevel)
INFRA_ENV="${INFRA_ENV_FILE:-$(dirname "$REPO_ROOT")/crypto-project-infra/infra/.env}"
MONGO_CONTAINER="${MONGO_CONTAINER:-mongo-primary}"

set -a; . "$INFRA_ENV"; set +a

read -r -d '' JS <<'EOF' || true
print("chat_message            " + db.chat_message.countDocuments({}));
print("chat_room_membership    " + db.chat_room_membership.countDocuments({}));
print("chat_room               " + db.chat_room.countDocuments({}));
print("");
db.chat_room.find({}, {
  title: 1,
  host_id: 1,
  member_ids: 1,
  msg_cnt: 1,
  latest_msg_seq: 1,
  last_msg_created_at: 1
}).forEach(r => {
  print(r._id + "  " + r.title + "  host " + (r.host_id ? "있음" : "없음")
      + "  멤버 " + ((r.member_ids || []).length)
      + "  msg_cnt " + (r.msg_cnt || 0)
      + "  latest_msg_seq " + (r.latest_msg_seq || 0)
      + "  last_msg_created_at " + (r.last_msg_created_at || "없음"));
});
EOF

docker exec -i -e MU="$MONGO_ROOT_USERNAME" -e MP="$MONGO_ROOT_PASSWORD" -e JS="$JS" \
  "$MONGO_CONTAINER" sh -c \
  'mongosh --quiet -u "$MU" -p "$MP" --authenticationDatabase admin chat --eval "$JS"'
