-- KEYS[1] = seat:{eventId}:{seatId}
-- ARGV[1] = userId
-- ARGV[2] = token

local seatKey = KEYS[1]
local userId = ARGV[1]
local token = ARGV[2]

local currentUserId = redis.call('HGET', seatKey, 'userId')
local currentToken = redis.call('HGET', seatKey, 'token')

-- Verify both user and fencing token
if currentUserId ~= userId or currentToken ~= token then
    return 0  -- Failed: lock expired or stolen
end

-- Mark as CONFIRMED (prevents release while payment processes)
redis.call('HSET', seatKey, 'status', 'CONFIRMED')
return 1
