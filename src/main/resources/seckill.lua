local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId
local beginKey = 'seckill:begin:' .. voucherId
local endKey = 'seckill:end:' .. voucherId

-- 活动时间校验：以 Redis 服务器时间为准，避免多实例时钟不一致
-- 时间 key 不存在时跳过校验（tonumber 对不存在的 key 返回 nil）
local now = tonumber(redis.call('time')[1])
local beginTs = tonumber(redis.call('get', beginKey))
local endTs = tonumber(redis.call('get', endKey))

if beginTs and now < beginTs then
    return 3
end

if endTs and now > endTs then
    return 4
end

if (tonumber(redis.call('get', stockKey)) <= 0) then
    return 1
end

if (redis.call('sismember', orderKey, userId) == 1) then
    return 2
end

redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'id', orderId, 'voucherId', voucherId)

return 0
