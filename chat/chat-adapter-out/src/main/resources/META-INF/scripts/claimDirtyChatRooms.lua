local dirtyKey = KEYS[1]
local inflightKey = KEYS[2]

local batchSize = tonumber(ARGV[1])
local nowMs = tonumber(ARGV[2])

if batchSize == nil or batchSize <= 0 then
    return {}
end

-- 오래된 활동부터 처리한다. dirty 에서 빼고 inflight 로 옮기는 것이 한 원자 단위라
-- 여러 chat 인스턴스가 같은 방을 동시에 잡지 않는다.
local rooms = redis.call("ZRANGE", dirtyKey, 0, batchSize - 1, "WITHSCORES")

local claimed = {}
local index = 1

while index < #rooms do
    local roomId = rooms[index]
    local activityMs = rooms[index + 1]

    redis.call("ZREM", dirtyKey, roomId)
    redis.call("ZADD", inflightKey, nowMs, roomId)

    claimed[#claimed + 1] = roomId
    claimed[#claimed + 1] = activityMs

    index = index + 2
end

return claimed
