local infoKey = KEYS[1]

local pairCount = tonumber(ARGV[1])
local argIndex = 2

-- 알림 정보 필드 적재(불변 데이터 → 덮어써도 동일 값)
for i = 1, pairCount do
    redis.call("HSET", infoKey, ARGV[argIndex], ARGV[argIndex + 1])
    argIndex = argIndex + 2
end

local ttlSeconds = tonumber(ARGV[argIndex])

if ttlSeconds ~= nil and ttlSeconds > 0 then
    redis.call("EXPIRE", infoKey, ttlSeconds)
end

return true
