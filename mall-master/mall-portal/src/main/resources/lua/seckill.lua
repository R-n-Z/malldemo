-- KEYS[1]: stock key
-- KEYS[2]: user purchase record key
-- KEYS[3]: token key
-- ARGV[1]: user ID
-- ARGV[2]: purchase quantity
-- ARGV[3]: limit per user
-- ARGV[4]: seckill token

local stockKey = KEYS[1]
local userKey = KEYS[2]
local tokenKey = KEYS[3]
local userId = ARGV[1]
local quantity = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local token = ARGV[4]

-- 1. Validate token
local savedToken = redis.call('GET', tokenKey)
if not savedToken or savedToken ~= token then
    return {-1, 'Invalid seckill token'}
end

-- 2. Check user purchase count
local userCount = tonumber(redis.call('HGET', userKey, userId) or 0)
if userCount + quantity > limit then
    return {-2, 'Exceeds purchase limit'}
end

-- 3. Check stock
local stock = tonumber(redis.call('GET', stockKey) or 0)
if stock < quantity then
    return {-3, 'Insufficient stock'}
end

-- 4. Deduct stock
redis.call('DECRBY', stockKey, quantity)

-- 5. Record user purchase count
redis.call('HINCRBY', userKey, userId, quantity)
redis.call('EXPIRE', userKey, 86400)  -- 24 hours expiration

-- 6. Delete token (prevent reuse)
redis.call('DEL', tokenKey)

return {1, 'Seckill success'}