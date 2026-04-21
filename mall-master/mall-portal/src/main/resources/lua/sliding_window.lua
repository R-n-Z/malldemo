-- Sliding window rate limiting script (atomic operation)
-- KEYS[1]: rate limit key (sorted set)
-- ARGV[1]: limit count
-- ARGV[2]: time window (milliseconds)
-- ARGV[3]: current timestamp

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

-- Remove requests outside time window
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

-- Count requests in current window
local count = redis.call('ZCARD', key)

if count < limit then
    redis.call('ZADD', key, now, now .. ':' .. math.random())
    redis.call('EXPIRE', key, window / 1000 + 1)
    return 1
else
    return 0
end