local roomInfoKey = KEYS[1]
local roomId = ARGV[1]

if roomId == nil or roomId == "" then
    return false
end

redis.call("UNLINK", roomInfoKey)

return true