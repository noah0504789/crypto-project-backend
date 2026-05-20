local messageKey = KEYS[1]
local messageAccessKey = KEYS[2]
local roomInfoKey = KEYS[3]

local messageId = ARGV[1]
local roomId = ARGV[2]
local scoreCount = tonumber(ARGV[3])

if messageId == nil or messageId == "" then
    return 0
end

if roomId == nil or roomId == "" then
    return 0
end

if scoreCount == nil or scoreCount ~= (#KEYS - 3) then
    return 0
end

local scores = {}
local argIndex = 4

for i = 1, scoreCount do
    local score = tonumber(ARGV[argIndex])

    if score == nil then
        return 0
    end

    scores[i] = score
    argIndex = argIndex + 1
end

local messages = redis.call("ZREVRANGE", messageKey, 0, 199)

local found = false
local targetJson = nil

for _, messageJson in ipairs(messages) do
    local ok, decoded = pcall(cjson.decode, messageJson)
    if ok and decoded and decoded["id"] == messageId then
        found = true
        targetJson = messageJson
        break
    end
end

if found then
    redis.call("ZREM", messageKey, targetJson)
    redis.call("ZREM", messageAccessKey, messageId)

    local currentCnt = tonumber(redis.call("HGET", roomInfoKey, "msg_cnt") or "0")
    if currentCnt > 0 then
        redis.call("HINCRBY", roomInfoKey, "msg_cnt", -1)
    end
end

for i = 1, scoreCount do
    local score = scores[i]
    local recentKey = KEYS[i + 3]

    if score <= 0 then
        redis.call("ZREM", recentKey, roomId)
    else
        redis.call("ZADD", recentKey, score, roomId)
    end
end

if found then
    return 1
end

return 2