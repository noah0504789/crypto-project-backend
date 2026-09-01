local messageKey = KEYS[1]
local messageAccessKey = KEYS[2]
local roomInfoKey = KEYS[3]
local writerRecentKey = KEYS[4]

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
for i = 5, #KEYS do
    redis.call("ZADD", KEYS[i], UNREAD_BOOST + createdAtMs, roomId)
end

return true
