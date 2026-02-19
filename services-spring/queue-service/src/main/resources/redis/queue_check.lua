-- queue_check.lua: Atomic check/status for queue service
-- Replaces 5-6 individual Redis round-trips with a single Lua call
--
-- KEYS[1] = {eventId}:queue       (ZSET: waiting queue, score=join timestamp or VWR position)
-- KEYS[2] = {eventId}:active      (ZSET: active users, score=expiry timestamp)
-- KEYS[3] = {eventId}:seen        (ZSET: queue heartbeat tracking)
-- KEYS[4] = {eventId}:active-seen (ZSET: active heartbeat tracking)
-- KEYS[5] = {eventId}:threshold   (STRING: per-event threshold, optional)
--
-- ARGV[1] = userId
-- ARGV[2] = now (ms)
-- ARGV[3] = defaultThreshold
-- ARGV[4] = activeTtlMs
--
-- Returns: [inQueue, inActive, position, queueSize, activeCount, threshold]
--   inQueue:     1 if user is in queue, 0 otherwise
--   inActive:    1 if user is active (non-expired), 0 otherwise
--   position:    1-based queue position (0 if not in queue)
--   queueSize:   total queue size
--   activeCount: count of non-expired active users
--   threshold:   per-event or default threshold

local queueKey      = KEYS[1]
local activeKey     = KEYS[2]
local queueSeenKey  = KEYS[3]
local activeSeenKey = KEYS[4]
local thresholdKey  = KEYS[5]

local userId           = ARGV[1]
local now              = tonumber(ARGV[2])
local defaultThreshold = tonumber(ARGV[3])
local activeTtlMs      = tonumber(ARGV[4])

-- 1. Check threshold
local customThreshold = redis.call('GET', thresholdKey)
local threshold = customThreshold and tonumber(customThreshold) or defaultThreshold

-- 2. Check if user is in queue
local queueScore = redis.call('ZSCORE', queueKey, userId)
local inQueue = 0
local position = 0
if queueScore then
    inQueue = 1
    -- Touch heartbeat
    redis.call('ZADD', queueSeenKey, now, userId)
    -- Get position (0-based rank → 1-based position)
    local rank = redis.call('ZRANK', queueKey, userId)
    position = rank and (rank + 1) or 0
end

-- 3. Check if user is active (non-expired)
local activeScore = redis.call('ZSCORE', activeKey, userId)
local inActive = 0
if activeScore and tonumber(activeScore) > now then
    inActive = 1
    -- Touch active heartbeat + refresh expiry
    redis.call('ZADD', activeSeenKey, now, userId)
    redis.call('ZADD', activeKey, now + activeTtlMs, userId)
end

-- 4. Queue size
local queueSize = redis.call('ZCARD', queueKey)

-- 5. Active count (non-expired only)
local activeCount = redis.call('ZCOUNT', activeKey, now, '+inf')

return {inQueue, inActive, position, queueSize, activeCount, threshold}
