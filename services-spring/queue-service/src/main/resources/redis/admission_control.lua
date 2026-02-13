-- KEYS[1] = queue:{eventId}        (ZSET: waiting queue, score=timestamp)
-- KEYS[2] = active:{eventId}       (ZSET: active users, score=expiry timestamp)
-- KEYS[3] = queue:seen:{eventId}   (ZSET: heartbeat tracking)
-- ARGV[1] = admitCount             (max to admit this batch)
-- ARGV[2] = now                    (current timestamp ms)
-- ARGV[3] = activeTtlMs            (active user TTL in ms)
-- ARGV[4] = maxActive              (threshold)

local queueKey = KEYS[1]
local activeKey = KEYS[2]
local queueSeenKey = KEYS[3]
local admitCount = tonumber(ARGV[1])
local now = tonumber(ARGV[2])
local activeTtlMs = tonumber(ARGV[3])
local maxActive = tonumber(ARGV[4])

-- 1. Remove expired active users
redis.call('ZREMRANGEBYSCORE', activeKey, '-inf', now)

-- 2. Current active count (non-expired only)
local activeCount = redis.call('ZCARD', activeKey)

-- 3. Available slots
local available = maxActive - activeCount
if available <= 0 then
    return {0, activeCount}
end

local toAdmit = math.min(available, admitCount)

-- 4. ZPOPMIN - atomic pop from queue (no duplicates)
local popped = redis.call('ZPOPMIN', queueKey, toAdmit)
if #popped == 0 then
    return {0, activeCount}
end

-- 5. Add to active with expiry score, remove from seen
local admitted = 0
for i = 1, #popped, 2 do
    local userId = popped[i]
    redis.call('ZADD', activeKey, now + activeTtlMs, userId)
    redis.call('ZREM', queueSeenKey, userId)
    admitted = admitted + 1
end

return {admitted, activeCount + admitted}
