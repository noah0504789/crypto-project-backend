local messageKey = KEYS[1]
local messageAccessKey = KEYS[2]
local roomInfoKey = KEYS[3]
local activityDirtyKey = KEYS[4]
local writerRecentKey = KEYS[5]

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
if not redis.call("HGET", roomInfoKey, "latest_msg_seq") then
    redis.call("HSET", roomInfoKey, "latest_msg_seq", currentMsgCnt)
end

redis.call("HINCRBY", roomInfoKey, "msg_cnt", 1)
redis.call("HINCRBY", roomInfoKey, "latest_msg_seq", 1)

redis.call("ZADD", writerRecentKey, createdAtMs, roomId)

local UNREAD_BOOST = 100000000000000
for i = 6, #KEYS do
    redis.call("ZADD", KEYS[i], UNREAD_BOOST + createdAtMs, roomId)
end

-- 방 activity 를 projector 작업 목록에 올린다. 메시지 상태 갱신과 같은 원자 단위여야
-- "메시지는 반영됐는데 dirty 만 빠진" 상태가 생기지 않는다. 같은 방이 여러 번 들어와도
-- ZSET 원소는 하나로 합쳐지고, 늦게 도착한 과거 메시지가 최신 활동 시각을 되돌리지 않는다.
local dirtyScore = tonumber(redis.call("ZSCORE", activityDirtyKey, roomId))
if dirtyScore == nil or createdAtMs > dirtyScore then
    redis.call("ZADD", activityDirtyKey, createdAtMs, roomId)
end

return true
