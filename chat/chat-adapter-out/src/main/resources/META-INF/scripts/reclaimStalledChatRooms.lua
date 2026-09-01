local inflightKey = KEYS[1]

local staleBeforeMs = tonumber(ARGV[1])
local batchSize = tonumber(ARGV[2])
local nowMs = tonumber(ARGV[3])

if batchSize == nil or batchSize <= 0 then
    return {}
end

local rooms = redis.call("ZRANGEBYSCORE", inflightKey, "-inf", staleBeforeMs, "LIMIT", 0, batchSize)

-- claim 시각을 현재로 갱신해 lease 를 연장한다. 재생성이 오래 걸려도 다른 인스턴스가
-- 같은 방을 중복으로 집어가지 않는다.
for i = 1, #rooms do
    redis.call("ZADD", inflightKey, nowMs, rooms[i])
end

return rooms
