package com.chenruichao.utils;

public interface SimpleRedisLock {

    boolean tryLock(long expireTime);

    void unlock();
}
