-- Pre-lock stock Lua script (atomic operation)
-- KEYS[1]: available stock key
-- KEYS[2]: pre-lock stock key
-- KEYS[3]: pre-lock token key (skuId)
-- KEYS[4]: pre-lock token info key
-- ARGV[1]: pre-lock quantity
-- ARGV[2]: pre-lock expiration (seconds)
-- ARGV[3]: pre-lock token
-- ARGV[4]: order ID

local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
local lockStock = tonumber(redis.call('GET', KEYS[2]) or '0')
local totalAvailable = stock - lockStock

if totalAvailable < tonumber(ARGV[1]) then
    return {-1, 'Insufficient stock', 0}
end

-- Pre-lock stock
redis.call('INCRBY', KEYS[2], ARGV[1])
redis.call('EXPIRE', KEYS[2], ARGV[2])

-- Record pre-lock token info (token -> skuId:count:orderId)
local tokenInfo = ARGV[3] .. ':' .. KEYS[3] .. ':' .. ARGV[1] .. ':' .. ARGV[4]
redis.call('SET', KEYS[4], tokenInfo, 'EX', ARGV[2])

-- Calculate expiration time
local expireTime = redis.call('TTL', KEYS[4])

return {1, 'Pre-lock success', expireTime}