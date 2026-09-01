local lastReadKey = KEYS[1]
local inflightKey = KEYS[2]

local roomId = ARGV[1]
local activeKeyPrefix = ARGV[2]
local activeKeySuffix = ARGV[3]
local ttlSeconds = tonumber(ARGV[4])
local memberCount = tonumber(ARGV[5])

if memberCount == nil or memberCount < 0 then
    return -1
end

local argIndex = 6

for i = 1, memberCount do
    local memberId = ARGV[argIndex]
    local lastReadSeq = tonumber(ARGV[argIndex + 1])
    local score = tonumber(ARGV[argIndex + 2])

    -- 읽음 위치는 되돌리지 않는다. Mongo 로 재생성하는 동안 더 최신 읽음이 캐시에 들어와 있을 수 있다.
    local current = tonumber(redis.call("HGET", lastReadKey, memberId))

    if current == nil or lastReadSeq > current then
        redis.call("HSET", lastReadKey, memberId, lastReadSeq)
    end

    redis.call("ZADD", activeKeyPrefix .. memberId .. activeKeySuffix, score, roomId)

    argIndex = argIndex + 3
end

if ttlSeconds ~= nil and ttlSeconds > 0 then
    redis.call("EXPIRE", lastReadKey, ttlSeconds)
end

redis.call("ZREM", inflightKey, roomId)

return memberCount
