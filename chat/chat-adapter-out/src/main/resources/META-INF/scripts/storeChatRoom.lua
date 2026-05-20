local infoKey = KEYS[1]
local titleIndexKey = KEYS[2]
local popularKey = KEYS[3]
local lastReadKey = KEYS[4]

local roomId = ARGV[1]
local title = ARGV[2]
local score = tonumber(ARGV[3])
local infoPairCount = tonumber(ARGV[4])

local existingRoomId = redis.call("HGET", infoKey, "id")
local existingTitle = redis.call("HGET", infoKey, "title")

local function storeRoomInfo(startIndex)
    local argIndex = startIndex

    for i = 1, infoPairCount do
        local field = ARGV[argIndex]
        local value = ARGV[argIndex + 1]

        redis.call("HSET", infoKey, field, value)

        argIndex = argIndex + 2
    end

    return argIndex
end

local function initLastReads(startIndex)
    local argIndex = startIndex

    local initialLastReadSeq = ARGV[argIndex]
    argIndex = argIndex + 1

    local memberCount = tonumber(ARGV[argIndex])
    argIndex = argIndex + 1

    for i = 1, memberCount do
        local memberId = ARGV[argIndex]

        -- 중요: 기존 lastRead가 있으면 덮어쓰지 않음
        redis.call("HSETNX", lastReadKey, memberId, initialLastReadSeq)

        argIndex = argIndex + 1
    end
end

local added = redis.call("SADD", titleIndexKey, title)

if added == 0 then
    -- 이미 같은 방이 같은 제목으로 저장된 상태면 재처리로 보고 성공
    if existingRoomId == roomId and existingTitle == title then
        local nextIndex = storeRoomInfo(5)

        redis.call("ZADD", popularKey, score, roomId)

        initLastReads(nextIndex)

        return true
    end

    return false
end

local nextIndex = storeRoomInfo(5)

redis.call("ZADD", popularKey, score, roomId)

initLastReads(nextIndex)

return true