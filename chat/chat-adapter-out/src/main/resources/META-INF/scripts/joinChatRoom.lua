local roomInfoKey = KEYS[1]
local memberId = ARGV[1]

if memberId == nil or memberId == "" then
    return false
end

local current = redis.call("HGET", roomInfoKey, "member_ids")

if not current then
    return true
end

local ok, arr = pcall(cjson.decode, current)

if not ok or type(arr) ~= "table" then
    return false
end

for i = 1, #arr do
    if arr[i] == memberId then
        return true
    end
end

arr[#arr + 1] = memberId
redis.call("HSET", roomInfoKey, "member_ids", cjson.encode(arr))

return true