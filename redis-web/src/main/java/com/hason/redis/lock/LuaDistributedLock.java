package com.hason.redis.lock;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Lua脚本实现分布式锁
 *
 * @author Huanghs
 * @since 2.0
 * @date 2017/4/14
 */
public class LuaDistributedLock {

    private static final String PREFIX = "lock:";

    /**
     * 获取锁
     *
     * @param jedis Redis 客户端
     * @param name 被锁对象
     * @param timeout 最长等待时间，毫秒
     * @return 成功时返回 UUID, 失败时返回 null
     */
    public static String acquire(Jedis jedis, String name, long timeout) {
        String token = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis() + timeout;
        // 失败时重新获取锁，直到超时为止
        while (timestamp >= System.currentTimeMillis()) {
            Long result = jedis.setnx(PREFIX + name, token);
            if (result == 1) {
                return token;
            }
            // 获取失败时短暂休息继续申请
            sleep(1);
        }
        return null;
    }

    /**
     * 获取带有超时的锁
     *
     * @param jedis Redis 客户端
     * @param name 被锁对象
     * @param lockTimeout 锁的超时时间
     * @param getTimeout 获取操作的超时时间，超时后返回失败
     * @return 成功时返回 UUID，失败时返回 null
     */
    public static String getWithTimeout(
            Jedis jedis, String name, long lockTimeout, long getTimeout) {
        String lockName = PREFIX + name;
        String token = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis() + getTimeout;

        // 失败时重新获取锁，直到超时为止
        while (timestamp > System.currentTimeMillis()) {
            // 仅当锁不存在时，保存锁
            // 锁已经存在时，检查是否未设为超时，如果是则为其设置超时时间
            Object result = jedis.eval("if redis.call('exists', KEYS[1]) == 0 then " +
                            "return redis.call('setex', KEYS[1], unpack(ARGV)) " +
                            "elseif redis.call('ttl', KEYS[1]) == -1 then " +
                            "redis.call('pexpire', KEYS[1], ARGV[1]) end",
                    Arrays.asList(lockName),
                    Arrays.asList(String.valueOf(lockTimeout), token));
            if ("OK".equals(result)) {
                return token;
            }
            // 休眠一会再申请锁
            sleep(1);
        }

        return null;
    }

    /**
     * 释放锁
     *
     * @param jedis Redis 客户端
     * @param name 被锁对象
     * @param token 与锁对应的令牌，获取锁时返回的内容
     * @return true 成功，false 失败
     */
    public static boolean release(Jedis jedis, String name, String token) {
        String lockName = PREFIX + name;
        // 仅当锁存在且token正确时，才执行释放操作
        // 用户提供的锁token不正确或锁不存在，无法释放锁，返回错误
        Object result = jedis.eval("if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "return redis.call('del', KEYS[1]) or true end",
                Arrays.asList(lockName), Arrays.asList(token)
        );
        return result != null;
    }

    private static void sleep(long mills) {
        try {
            TimeUnit.MILLISECONDS.sleep(mills);
        } catch (InterruptedException ignored) {
        }
    }
}
