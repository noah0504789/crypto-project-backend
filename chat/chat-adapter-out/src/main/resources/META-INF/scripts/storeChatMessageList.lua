local messageKey = KEYS[1]
local messageCount = tonumber(ARGV[1])

local argIndex = 2
for i = 1, messageCount do
    local createdAtMs = tonumber(ARGV[argIndex]); argIndex = argIndex + 1
    local messageJson = ARGV[argIndex]; argIndex = argIndex + 1

    redis.call("ZADD", messageKey, createdAtMs, messageJson)
end

return true