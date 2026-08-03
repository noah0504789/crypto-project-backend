local infoKey = KEYS[1]
local id = ARGV[1]

if id == nil or id == "" then
    return false
end

redis.call("UNLINK", infoKey)

return true
