local accessTTL = tonumber(ARGV[1])
local refreshTTL = tonumber(ARGV[2])
local accessClaimCount = tonumber(ARGV[3])

if accessTTL == nil or accessTTL <= 0 then
    error('accessTTL must be positive')
end

if refreshTTL == nil or refreshTTL <= 0 then
    error('refreshTTL must be positive')
end

if accessClaimCount == nil or accessClaimCount <= 0 then
    error('accessClaimCount must be positive')
end

local accessStart = 4
local accessEnd = accessStart + (accessClaimCount * 2) - 1

local accessTokenValue = ARGV[accessEnd + 1]
local refreshTokenValue = ARGV[accessEnd + 2]
local email = ARGV[accessEnd + 3]

if accessTokenValue == nil or refreshTokenValue == nil or email == nil then
    error('token values or email are missing')
end

local atClaimKey = KEYS[1]
local atTokenKey = KEYS[2]
local rtEmailKey = KEYS[3]
local rtTokenKey = KEYS[4]
local idxTokenKey = KEYS[5]

-- Java 쪽 pre-check만으로는 원자성이 부족하므로 Lua 안에서도 중복 저장 방지
if redis.call('EXISTS', atTokenKey) == 1 then
    return false
end

-- access token claims
redis.call('HSET', atClaimKey, unpack(ARGV, accessStart, accessEnd))
redis.call('EXPIRE', atClaimKey, accessTTL)

-- access token value
redis.call('SETEX', atTokenKey, accessTTL, accessTokenValue)

-- refresh token reverse lookup: refreshToken -> email
redis.call('SETEX', rtEmailKey, refreshTTL, email)

-- refresh token lookup: email -> refreshToken
redis.call('SETEX', rtTokenKey, refreshTTL, refreshTokenValue)

-- index set for bulk delete
redis.call('SADD', idxTokenKey, atClaimKey, atTokenKey, rtEmailKey, rtTokenKey)
redis.call('EXPIRE', idxTokenKey, refreshTTL)

return true