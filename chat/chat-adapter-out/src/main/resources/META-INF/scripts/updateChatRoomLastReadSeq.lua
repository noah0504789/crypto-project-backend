local lastReadKey = KEYS[1]

local memberId = ARGV[1]
local lastReadSeq = tonumber(ARGV[2])

if memberId == nil or memberId == "" or lastReadSeq == nil then
    return false
end

-- 읽음 위치는 단조 증가만 허용한다. projector flush 와 읽음 처리가 경합할 때
-- 뒤늦게 도착한 과거 값이 최신 읽음을 되돌리면 이미 읽은 방이 다시 unread 로 뜬다.
local current = tonumber(redis.call("HGET", lastReadKey, memberId))

if current == nil or lastReadSeq > current then
    redis.call("HSET", lastReadKey, memberId, lastReadSeq)
end

return true
