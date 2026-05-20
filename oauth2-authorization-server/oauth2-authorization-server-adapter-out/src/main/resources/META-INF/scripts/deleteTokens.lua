local idxTokenKey = KEYS[1]

local keysToDel = redis.call('SMEMBERS', idxTokenKey)

if #keysToDel > 0 then
    redis.call('DEL', unpack(keysToDel))
end

redis.call('DEL', idxTokenKey)

return true