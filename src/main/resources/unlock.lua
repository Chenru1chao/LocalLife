-- 判断当前锁的值与当前线程的编号是否一致
if (ARGV[1] == redis.call('get', KEYS[1]))
then
    -- 一致则释放锁
    return redis.call('del', KEYS[1]);
end
-- 反之返回失败 当前锁不属于当前线程则不释放
return 0;
