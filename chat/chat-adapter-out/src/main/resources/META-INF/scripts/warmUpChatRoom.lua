local roomInfoKey = KEYS[1]
local titleIndexKey = KEYS[2]
local popularKey = KEYS[3]

local roomId = ARGV[1]
local title = ARGV[2]
local popularity = tonumber(ARGV[3])
local infoPairCount = tonumber(ARGV[4])

-- title index 복원
redis.call("SADD", titleIndexKey, title)

-- room info 복원
local argIndex = 5
for i = 1, infoPairCount do
    local field = ARGV[argIndex]
    local value = ARGV[argIndex + 1]
    redis.call("HSET", roomInfoKey, field, value)
    argIndex = argIndex + 2
end

-- popularity 복원
redis.call("ZADD", popularKey, popularity, roomId)

return true