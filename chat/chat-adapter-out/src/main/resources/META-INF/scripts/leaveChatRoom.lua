local roomInfoKey = KEYS[1]
local lastReadStateKey = KEYS[2]
local recentRoomKey = KEYS[3]

local roomId = ARGV[1]
local memberId = ARGV[2]

local current = redis.call("HGET", roomInfoKey, "member_ids")

if current then
    local ok, arr = pcall(cjson.decode, current)

    if ok and type(arr) == "table" then
        local filtered = {}

        for i = 1, #arr do
            if arr[i] ~= memberId then
                filtered[#filtered + 1] = arr[i]
            end
        end

        local encoded
        if #filtered == 0 then
            encoded = "[]"
        else
            encoded = cjson.encode(filtered)
        end

        redis.call("HSET", roomInfoKey, "member_ids", encoded)
    end
end

redis.call("HDEL", lastReadStateKey, memberId)
redis.call("ZREM", recentRoomKey, roomId)

return true