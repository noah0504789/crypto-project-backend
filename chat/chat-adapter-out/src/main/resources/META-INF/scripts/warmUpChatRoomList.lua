local roomCount = tonumber(ARGV[1])
local argIndex = 2

local seenTitles = {}

-- 같은 배치 안에서 title 중복만 체크
for i = 1, roomCount do
    local roomId = ARGV[argIndex]
    argIndex = argIndex + 1

    local title = ARGV[argIndex]
    argIndex = argIndex + 1

    local popularity = tonumber(ARGV[argIndex])
    argIndex = argIndex + 1

    if seenTitles[title] then
        return false
    end

    seenTitles[title] = true

    local infoPairCount = tonumber(ARGV[argIndex])
    argIndex = argIndex + 1

    argIndex = argIndex + (infoPairCount * 2)

    -- ttlSeconds skip
    argIndex = argIndex + 1
end

-- 실제 복원
argIndex = 2

for i = 1, roomCount do
    local base = (i - 1) * 3

    local roomInfoKey = KEYS[base + 1]
    local titleIndexKey = KEYS[base + 2]
    local popularKey = KEYS[base + 3]

    local roomId = ARGV[argIndex]
    argIndex = argIndex + 1

    local title = ARGV[argIndex]
    argIndex = argIndex + 1

    local popularity = tonumber(ARGV[argIndex])
    argIndex = argIndex + 1

    redis.call("SADD", titleIndexKey, title)

    local infoPairCount = tonumber(ARGV[argIndex])
    argIndex = argIndex + 1

    for j = 1, infoPairCount do
        local field = ARGV[argIndex]
        local value = ARGV[argIndex + 1]

        redis.call("HSET", roomInfoKey, field, value)

        argIndex = argIndex + 2
    end

    local ttlSeconds = tonumber(ARGV[argIndex])
    argIndex = argIndex + 1

    if ttlSeconds ~= nil and ttlSeconds > 0 then
        redis.call("EXPIRE", roomInfoKey, ttlSeconds)
    end

    redis.call("ZADD", popularKey, popularity, roomId)
end

return true