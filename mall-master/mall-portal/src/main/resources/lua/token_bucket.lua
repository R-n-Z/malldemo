-- Token bucket Lua script (atomic operation)
-- KEYS[1]: token bucket key
-- KEYS[2]: last refresh time key
-- ARGV[1]: token generation rate (tokens/second)
-- ARGV[2]: token bucket capacity
-- ARGV[3]: current timestamp (milliseconds)
-- ARGV[4]: tokens needed

local bucketKey = KEYS[1]
local timeKey = KEYS[2]
local rate = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local tokensNeeded = tonumber(ARGV[4])

-- Get last refresh time and current token count
local lastTime = tonumber(redis.call('GET', timeKey) or now)
local tokens = tonumber(redis.call('GET', bucketKey) or capacity)

-- Calculate time difference (seconds)
local elapsed = (now - lastTime) / 1000.0

-- Calculate new tokens, cannot exceed capacity
local newTokens = elapsed * rate
tokens = math.min(capacity, tokens + newTokens)

-- Try to acquire tokens
if tokens >= tokensNeeded then
    tokens = tokens - tokensNeeded
    -- Update token bucket and last refresh time
    redis.call('SET', bucketKey, tokens)
    redis.call('SET', timeKey, now)
    -- Set expiration (1 hour without request)
    redis.call('EXPIRE', bucketKey, 3600)
    redis.call('EXPIRE', timeKey, 3600)
    return 1  -- Acquire success
else
    -- Calculate wait time
    local waitTokens = tokensNeeded - tokens
    local waitTime = math.ceil(waitTokens / rate * 1000)
    return waitTime  -- Return wait time in milliseconds
end