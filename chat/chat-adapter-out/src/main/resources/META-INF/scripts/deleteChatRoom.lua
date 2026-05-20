local messageKey = KEYS[1]
local messageAccessKey = KEYS[2]
local roomInfoKey = KEYS[3]
local readStateKey = KEYS[4]
local popularKey = KEYS[5]
local titleIndexKey = KEYS[6]

local roomId = ARGV[1]
local title = ARGV[2]

redis.call("UNLINK", messageKey, messageAccessKey, roomInfoKey, readStateKey)
redis.call("ZREM", popularKey, roomId)
redis.call("SREM", titleIndexKey, title)

for i = 7, #KEYS do
    redis.call("ZREM", KEYS[i], roomId)
end

return true