-- Fixed window rate limiting script (atomic operation)
-- KEYS[1]: rate limit key
-- ARGV[1]: limit count
-- ARGV[2]: time window (seconds)

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local current = tonumber(redis.call('GET', key) or '0')

if current == nil then
    current = 0
end

-- Reset counter if beyond time window
local ttl = redis.call('TTL', key)
if ttl == -1 then
    redis.call('EXPIRE', key, window)
    current = 0
end

if current < limit then
    redis.call('INCR', key)
    return 1  -- Allow
else
    return 0  -- Rate limited
end