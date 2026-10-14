package com.chenruichao.Exception;

public class RedisException extends RuntimeException {
    public RedisException(String message) {
        super(message);
    }

    public RedisException() {
    }
}
