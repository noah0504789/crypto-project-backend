local roomInfoKey = KEYS[1]
local titleIndexKey = KEYS[2]

local oldTitle = ARGV[1]
local newTitle = ARGV[2]
local pairCount = tonumber(ARGV[3])

local currentTitle = redis.call("HGET", roomInfoKey, "title")

if oldTitle ~= newTitle then
    if redis.call("SISMEMBER", titleIndexKey, newTitle) == 1 then
        -- 이미 새 제목이 인덱스에 있는데,
        -- 현재 방 제목도 새 제목이면 같은 요청 재처리로 보고 성공 처리
        if currentTitle == newTitle then
            local argIndex = 4
            for i = 1, pairCount do
                local field = ARGV[argIndex]
                local value = ARGV[argIndex + 1]
                redis.call("HSET", roomInfoKey, field, value)
                argIndex = argIndex + 2
            end
            return true
        end

        return false
    end

    redis.call("SREM", titleIndexKey, oldTitle)
    redis.call("SADD", titleIndexKey, newTitle)
end

local argIndex = 4
for i = 1, pairCount do
    local field = ARGV[argIndex]
    local value = ARGV[argIndex + 1]
    redis.call("HSET", roomInfoKey, field, value)
    argIndex = argIndex + 2
end

return true