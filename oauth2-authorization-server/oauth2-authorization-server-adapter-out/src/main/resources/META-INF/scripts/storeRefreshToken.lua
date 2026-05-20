local refreshTTL = tonumber(ARGV[1])
local refreshTokenValue = ARGV[2]
local email = ARGV[3]

if refreshTTL == nil or refreshTTL <= 0 then
    error('refreshTTL must be positive')
end

if refreshTokenValue == nil or refreshTokenValue == '' then
    error('refreshTokenValue is missing')
end

if email == nil or email == '' then
    error('email is missing')
end

local tokenKey = KEYS[1]
local emailKeyPrefix = KEYS[2]
local idxTokenKey = KEYS[3]

local oldRefreshToken = redis.call('GET', tokenKey)

if oldRefreshToken then
    local oldEmailKey = emailKeyPrefix .. oldRefreshToken

    redis.call('DEL', oldEmailKey)
    redis.call('SREM', idxTokenKey, oldEmailKey)
end

local newEmailKey = emailKeyPrefix .. refreshTokenValue

redis.call('SETEX', tokenKey, refreshTTL, refreshTokenValue)
redis.call('SETEX', newEmailKey, refreshTTL, email)

redis.call('SADD', idxTokenKey, tokenKey, newEmailKey)
redis.call('EXPIRE', idxTokenKey, refreshTTL)

return true