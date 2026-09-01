local roomInfoKey = KEYS[1]
local lastReadKey = KEYS[2]
local messageKey = KEYS[3]
local inflightKey = KEYS[4]

local roomId = ARGV[1]
local activeKeyPrefix = ARGV[2]
local activeKeySuffix = ARGV[3]
local unreadBoost = tonumber(ARGV[4])
local claimedActivityMs = tonumber(ARGV[5])

-- 방 캐시가 비었으면 projection 근거가 없다. inflight 에 남겨 두고 호출자가
-- Mongo(durable source) 기준 재생성으로 넘긴다.
local memberJson = redis.call("HGET", roomInfoKey, "member_ids")

if not memberJson then
    return {-1, 0}
end

local decoded, members = pcall(cjson.decode, memberJson)

if not decoded or type(members) ~= "table" then
    return {-1, 0}
end

local latestMsgSeq = tonumber(
        redis.call("HGET", roomInfoKey, "latest_msg_seq")
                or redis.call("HGET", roomInfoKey, "msg_cnt")
                or "0"
)

-- 활동 시각은 캐시의 최신 메시지가 정한다. claim score 는 처리 순서와 합치기용이지 활동 시각이
-- 아니다 — 둘의 max 를 쓰면 hard delete 로 사라진 메시지의 시각이 계속 이긴다.
-- 캐시가 비었을 때만(보존 기간 초과로 축출) claim score 로 물러선다.
local lastMsgCreatedAtMs = claimedActivityMs
local latest = redis.call("ZRANGE", messageKey, -1, -1, "WITHSCORES")

if #latest == 2 then
    local latestMs = tonumber(latest[2])

    if latestMs ~= nil then
        lastMsgCreatedAtMs = latestMs
    end
end

local updated = 0
local mismatched = 0

for i = 1, #members do
    local memberId = members[i]
    local activeKey = activeKeyPrefix .. memberId .. activeKeySuffix
    local lastReadSeq = tonumber(redis.call("HGET", lastReadKey, memberId) or "0")

    local score = lastMsgCreatedAtMs

    if lastReadSeq < latestMsgSeq then
        score = score + unreadBoost
    end

    local current = tonumber(redis.call("ZSCORE", activeKey, roomId))

    if current == nil or current ~= score then
        mismatched = mismatched + 1
    end

    redis.call("ZADD", activeKey, score, roomId)

    updated = updated + 1
end

redis.call("ZREM", inflightKey, roomId)

return {updated, mismatched}
