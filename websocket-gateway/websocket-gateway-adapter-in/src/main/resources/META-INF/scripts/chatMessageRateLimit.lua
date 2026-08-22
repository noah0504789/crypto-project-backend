local rate = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local redis_time = redis.call('TIME')
local now_ms = (redis_time[1] * 1000) + math.floor(redis_time[2] / 1000)
local state = redis.call('HMGET', KEYS[1], 'tokens', 'timestamp')
local tokens = tonumber(state[1])
local timestamp = tonumber(state[2])

if tokens == nil then tokens = capacity end
if timestamp == nil then timestamp = now_ms end

local elapsed_seconds = math.max(0, now_ms - timestamp) / 1000
local filled_tokens = math.min(capacity, tokens + (elapsed_seconds * rate))
local allowed = filled_tokens >= 1

if allowed then filled_tokens = filled_tokens - 1 end

redis.call('HSET', KEYS[1], 'tokens', filled_tokens, 'timestamp', now_ms)
local ttl_ms = math.max(60000, math.ceil((capacity / rate) * 2000))
redis.call('PEXPIRE', KEYS[1], ttl_ms)

if allowed then return 1 else return 0 end
