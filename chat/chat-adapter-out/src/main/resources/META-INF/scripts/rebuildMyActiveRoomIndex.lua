-- 한 사용자의 내 방 정렬 인덱스를 통째 재구축한다(Redis projection 유실 복구).
-- KEYS[1] = activeKey (CHAT_ROOM_ACTIVE_BY_MEMBER_INDEX)
-- ARGV[1] = roomCount, 이후 roomCount개의 (score, roomId) 쌍
local activeKey = KEYS[1]
local count = tonumber(ARGV[1])

if count == nil or count < 0 then
    return false
end

redis.call("DEL", activeKey)

local argIndex = 2
for i = 1, count do
    local score = tonumber(ARGV[argIndex])
    local roomId = ARGV[argIndex + 1]

    redis.call("ZADD", activeKey, score, roomId)

    argIndex = argIndex + 2
end

return true
