-- KEYS[1] = {eventId}:seen          (heartbeat ZSET)
-- KEYS[2] = {eventId}:queue         (queue ZSET)
-- ARGV[1] = cutoffTimestamp         (stale threshold ms)
-- ARGV[2] = batchSize               (max to clean per batch)

local heartbeatKey = KEYS[1]
local queueKey = KEYS[2]
local cutoff = tonumber(ARGV[1])
local batchSize = tonumber(ARGV[2])

local staleUsers = redis.call('ZRANGEBYSCORE', heartbeatKey,
    '-inf', cutoff, 'LIMIT', 0, batchSize)

if #staleUsers == 0 then
    return {0}
end

redis.call('ZREM', heartbeatKey, unpack(staleUsers))
redis.call('ZREM', queueKey, unpack(staleUsers))

return {#staleUsers}
