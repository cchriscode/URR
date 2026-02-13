local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local req_id = ARGV[4]
local cutoff = now - window
redis.call('ZREMRANGEBYSCORE', key, '-inf', cutoff)
redis.call('ZADD', key, now, req_id)
local count = redis.call('ZCARD', key)
redis.call('EXPIRE', key, math.ceil(window / 1000) + 1)
if count > limit then
    redis.call('ZREM', key, req_id)
    return 0
end
return 1
