-- Release stock Lua script (atomic operation)
-- KEYS[1]: available stock key
-- KEYS[2]: pre-lock stock key
-- KEYS[3]: pre-lock token key
-- ARGV[1]: pre-lock token
-- ARGV[2]: release quantity

local lockToken = redis.call('GET', KEYS[2])
if not lockToken then
    return {-1, 'Pre-lock record not exists'}
end

-- Release pre-lock stock
local currentLock = tonumber(redis.call('GET', KEYS[2]) or '0')
if currentLock >= tonumber(ARGV[2]) then
    redis.call('DECRBY', KEYS[2], ARGV[2])
else
    redis.call('SET', KEYS[2], '0')
end

-- Restore available stock
redis.call('INCRBY', KEYS[1], ARGV[2])

-- Delete pre-lock token
redis.call('DEL', KEYS[3])

return {1, 'Release success'}