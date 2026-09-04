local messageKey = KEYS[1]
local messageAccessKey = KEYS[2]
local roomInfoKey = KEYS[3]
local lastReadKey = KEYS[4]
local activityDirtyKey = KEYS[5]
local writerRecentKey = KEYS[6]

local messageId = ARGV[1]
local roomId = ARGV[2]
local createdAt = ARGV[3]
local createdAtMs = tonumber(ARGV[4])
local content = ARGV[5]
local writerId = ARGV[6]
local messageJson = ARGV[7]

-- 이미 처리된 메시지면 멱등적으로 성공 처리
if redis.call("ZSCORE", messageAccessKey, messageId) then
    return true
end

redis.call("ZADD", messageKey, createdAtMs, messageJson)
redis.call("ZADD", messageAccessKey, createdAtMs, messageId)

local currentMsgCnt = tonumber(redis.call("HGET", roomInfoKey, "msg_cnt") or "0")
if not redis.call("HGET", roomInfoKey, "last_msg_seq") then
    redis.call("HSET", roomInfoKey, "last_msg_seq", currentMsgCnt)
end

redis.call("HINCRBY", roomInfoKey, "msg_cnt", 1)
local lastMsgSeq = tonumber(redis.call("HINCRBY", roomInfoKey, "last_msg_seq", 1))

-- 보낸 사람은 자기 메시지를 읽은 것으로 본다. 읽음 위치를 방 watermark 까지 올려 두면
-- projector 가 계산해도 같은 결론이 나오고, 목록에서 자기 방이 즉시 최신으로 올라온다.
local writerLastRead = tonumber(redis.call("HGET", lastReadKey, writerId))
if writerLastRead == nil or lastMsgSeq > writerLastRead then
    redis.call("HSET", lastReadKey, writerId, lastMsgSeq)
end

redis.call("ZADD", writerRecentKey, createdAtMs, roomId)

-- 나머지 멤버의 정렬 점수는 여기서 갱신하지 않는다. 메시지당 O(members) 쓰기를 없애려고
-- 방을 dirty 로만 표시하고, 반영은 projector 가 방 단위로 한 번에 한다(→ docs/modules/CHAT.md §5).
local dirtyScore = tonumber(redis.call("ZSCORE", activityDirtyKey, roomId))
if dirtyScore == nil or createdAtMs > dirtyScore then
    redis.call("ZADD", activityDirtyKey, createdAtMs, roomId)
end

return true
