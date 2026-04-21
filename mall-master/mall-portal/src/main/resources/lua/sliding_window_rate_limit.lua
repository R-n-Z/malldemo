-- Sliding window rate limiting Lua script
-- KEYS[1]: window key
-- ARGV[1]: window size (seconds)
-- ARGV[2]: max requests
-- ARGV[3]: current timestamp
-- ARGV[4]: request ID

local windowKey = KEYS[1]
local windowSize = tonumber(ARGV[1])
local maxRequests = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requestId = ARGV[4]

-- Remove data outside window
redis.call('ZREMRANGEBYSCORE', windowKey, 0, now - windowSize * 1000)

-- Count requests in current window
local count = redis.call('ZCARD', windowKey)

if count < maxRequests then
    -- Add current request
    redis.call('ZADD', windowKey, now, requestId)
    -- Set expiration
    redis.call('EXPIRE', windowKey, windowSize)
    return 1
end

return 0