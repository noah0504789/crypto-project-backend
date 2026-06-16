local roomInfoKey = KEYS[1]
local titleIndexKey = KEYS[2]
local popularKey = KEYS[3]

local roomId = ARGV[1]
local oldTitle = ARGV[2]
local newTitle = ARGV[3]
local popularity = tonumber(ARGV[4])
local pairCount = tonumber(ARGV[5])

if oldTitle ~= "" and oldTitle ~= newTitle then
    redis.call("SREM", titleIndexKey, oldTitle)
end

if newTitle ~= "" then
    redis.call("SADD", titleIndexKey, newTitle)
end

local argIndex = 6

for i = 1, pairCount do
    local field = ARGV[argIndex]
    local value = ARGV[argIndex + 1]

    redis.call("HSET", roomInfoKey, field, value)

    argIndex = argIndex + 2
end

local ttlSeconds = tonumber(ARGV[argIndex])

if ttlSeconds ~= nil and ttlSeconds > 0 then
    redis.call("EXPIRE", roomInfoKey, ttlSeconds)
end

redis.call("ZADD", popularKey, popularity, roomId)

return true