local messageKey = KEYS[1]
local messageAccessKey = KEYS[2]
local roomInfoKey = KEYS[3]
local activityDirtyKey = KEYS[4]

local messageId = ARGV[1]
local roomId = ARGV[2]
local activityMs = tonumber(ARGV[3])

if messageId == nil or messageId == "" then
    return 0
end

if roomId == nil or roomId == "" then
    return 0
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

-- 멤버별 정렬 점수를 여기서 되돌리지 않는다. 방을 dirty 로 올려 두면 projector 가
-- 남은 최신 메시지를 기준으로 다시 계산한다. hard delete 는 watermark 를 줄이지 않는다.
local dirtyScore = tonumber(redis.call("ZSCORE", activityDirtyKey, roomId))
if activityMs ~= nil and (dirtyScore == nil or activityMs > dirtyScore) then
    redis.call("ZADD", activityDirtyKey, activityMs, roomId)
end

if found then
    return 1
end

return 2
