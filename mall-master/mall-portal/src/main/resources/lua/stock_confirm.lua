-- Confirm stock deduction Lua script (atomic operation)
-- KEYS[1]: pre-lock stock key
-- KEYS[2]: pre-lock token key
-- ARGV[1]: pre-lock token
-- ARGV[2]: confirm quantity

local lockToken = redis.call('GET', KEYS[2])
if not lockToken then
    return {-1, 'Pre-lock token not exists or expired'}
end

if lockToken ~= ARGV[1] then
    return {-2, 'Pre-lock token mismatch'}
end

local lockCount = tonumber(redis.call('GET', KEYS[1]) or '0')
if lockCount < tonumber(ARGV[2]) then
    return {-3, 'Pre-lock stock insufficient'}
end

-- Deduct pre-lock stock
redis.call('DECRBY', KEYS[1], ARGV[2])

-- Delete pre-lock token
redis.call('DEL', KEYS[2])

return {1, 'Confirm success'}