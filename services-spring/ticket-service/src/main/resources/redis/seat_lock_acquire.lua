-- KEYS[1] = seat:{eventId}:{seatId}  (HASH: status, userId, token, heldAt)
-- ARGV[1] = userId
-- ARGV[2] = ttl (seconds)

local seatKey = KEYS[1]
local userId = ARGV[1]
local ttl = tonumber(ARGV[2])

-- 1. Check current status
local status = redis.call('HGET', seatKey, 'status')
if status == 'HELD' or status == 'CONFIRMED' then
    local currentUser = redis.call('HGET', seatKey, 'userId')
    if currentUser == userId then
        -- Same user re-selecting: extend TTL and return existing token
        redis.call('EXPIRE', seatKey, ttl)
        local existingToken = redis.call('HGET', seatKey, 'token')
        return {1, existingToken}
    end
    return {0, '-1'}  -- Failure: seat taken by another user
end

-- 2. Generate monotonically increasing fencing token
local token = redis.call('INCR', seatKey .. ':token_seq')

-- 3. Atomic state transition: AVAILABLE -> HELD
redis.call('HMSET', seatKey,
    'status', 'HELD',
    'userId', userId,
    'token', token,
    'heldAt', redis.call('TIME')[1]
)
redis.call('EXPIRE', seatKey, ttl)

return {1, token}
