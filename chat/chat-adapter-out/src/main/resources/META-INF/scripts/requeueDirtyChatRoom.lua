local dirtyKey = KEYS[1]
local inflightKey = KEYS[2]

local roomId = ARGV[1]
local activityMs = tonumber(ARGV[2])

if roomId == nil or roomId == "" or activityMs == nil then
    return false
end

local current = tonumber(redis.call("ZSCORE", dirtyKey, roomId))

if current == nil or activityMs > current then
    redis.call("ZADD", dirtyKey, activityMs, roomId)
end

redis.call("ZREM", inflightKey, roomId)

return true
