-- KEYS[1] = seat:{eventId}:{seatId}
-- ARGV[1] = userId
-- ARGV[2] = token

local seatKey = KEYS[1]
local userId = ARGV[1]
local token = ARGV[2]

local currentUserId = redis.call('HGET', seatKey, 'userId')
local currentToken = redis.call('HGET', seatKey, 'token')

-- Only release if same user and same token
if currentUserId ~= userId or currentToken ~= token then
    return 0
end

redis.call('DEL', seatKey)
return 1
