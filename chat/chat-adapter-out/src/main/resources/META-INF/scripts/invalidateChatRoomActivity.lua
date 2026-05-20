local lastReadKey = KEYS[1]
local recentRoomKey = KEYS[2]

local roomId = ARGV[1]
local memberId = ARGV[2]

if roomId == nil or roomId == "" then
    return false
end

if memberId == nil or memberId == "" then
    return false
end

redis.call("HDEL", lastReadKey, memberId)
redis.call("ZREM", recentRoomKey, roomId)

return true