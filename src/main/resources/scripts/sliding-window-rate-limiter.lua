local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])

local clearBefore = now - window

-- 1. Remove timestamps older than the sliding window
redis.call('ZREMRANGEBYSCORE', key, '-inf', clearBefore)

-- 2. Count requests currently in the window
local currentRequests = redis.call('ZCARD', key)

-- 3. Check quota
if currentRequests < limit then
    -- Add unique member: timestamp + random suffix to prevent collisions on same ms
    local member = tostring(now) .. '-' .. tostring(redis.call('INCR', key .. ':seq'))
    redis.call('ZADD', key, now, member)

    -- Set TTL to slightly more than the window so empty keys expire automatically
    redis.call('PEXPIRE', key, window)
    redis.call('PEXPIRE', key .. ':seq', window)

    local remaining = limit - currentRequests - 1
    return {1, remaining}
else
    return {0, 0}
end