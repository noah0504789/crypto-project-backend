-- 인기방 zset을 category 단위로 통째 재구축한다(스케줄러 주기 재계산).
-- KEYS[1] = popularKey (CHAT_ROOM_POPULAR_BY_CATEGORY_INDEX)
-- ARGV[1] = ttlSeconds
-- ARGV[2] = roomCount
-- 이후 roomCount개의 (score, roomId) 쌍
local popularKey = KEYS[1]
local ttl = tonumber(ARGV[1])
local count = tonumber(ARGV[2])

redis.call("DEL", popularKey)

local idx = 3
for i = 1, count do
    local score = tonumber(ARGV[idx])
    local roomId = ARGV[idx + 1]
    redis.call("ZADD", popularKey, score, roomId)
    idx = idx + 2
end

if count > 0 then
    redis.call("EXPIRE", popularKey, ttl)
end

return true
