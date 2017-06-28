package com.hason.redis.lock;

import redis.clients.jedis.Jedis;

import java.util.Arrays;
import java.util.UUID;

/**
 * 计数信号量：限制一项资源能同时被多少个进程访问
 *
 * Lua 版本的信号量获取操作简化了在没有信号量可用的情况下的代码；
 * 另外，因为所有操作都是在 Redis 内部完成的，所以 Lua 版本
 * 的信号量实现不再需要【计数器】以及【信号量拥有者】集合
 * （因为在信号量仍然可用的情况下，第一个执行 Lua 脚本的客户端
 * 就是获取信号量的客户端）
 *
 *
 * @author Huanghs
 * @since 2.0
 * @date 2017/6/28
 */
public class LuaSemaphore {

    private static final String PREFIX = "semaphore:";

    public static void main(String[] args) {
        Jedis jedis = new Jedis("localhost", 6379);
        String name = "test";
        String token1 = acquire(jedis, name, 2, 60 * 1000);
        System.out.println("获取1：" + token1);
        boolean refresh = refresh(jedis, name, token1);
        System.out.println("刷新1：" + refresh);
        String token2 = acquire(jedis, name, 2, 60 * 1000);
        System.out.println("获取2：" + token2);
        boolean refresh2 = refresh(jedis, name, token1);
        System.out.println("刷新2：" + refresh2);
        String token3 = acquire(jedis, name, 2, 60 * 1000);
        System.out.println("获取2：" + token3);

        System.out.println("释放1：" + release(jedis, name, token1));
        System.out.println("释放2：" + release(jedis, name, token2));
    }
    public static String acquire(Jedis jedis,
             String name, int limit, long lockTimeout) {
        String lockList = PREFIX + name;
        String token = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        String script =
                // 清除所有已过期的信号量
                "redis.call('zremrangebyscore', KEYS[1], '-inf', ARGV[1])" +
                // 如果有剩余的信号量可用，那么获取信号量
                " if redis.call('zcard', KEYS[1]) < tonumber(ARGV[2]) then" +
                // 把时间戳添加到信号量有序集合里
                "    redis.call('zadd', KEYS[1], ARGV[3], ARGV[4])" +
                // 返回token
                "    return ARGV[4]" +
                " end";

        // 有token返回表示成功，null 表示失败
        return (String) jedis.eval(script,
                Arrays.asList(lockList),
                Arrays.asList(String.valueOf(now - lockTimeout), String.valueOf(limit), String.valueOf(now), token));
    }

    public static boolean release(Jedis jedis, String name, String token) {
        return jedis.zrem(PREFIX + name, token) == 1;
    }

    public static boolean refresh(Jedis jedis, String name, String token) {
        String lockList = PREFIX + name;
        long now = System.currentTimeMillis();

        // 如果信号量没有被刷新，Lua脚本将返回空值，而Java会将这个空值转换成 null 并返回给调用者
        String script =
                " if redis.call('zscore', KEYS[1], ARGV[1]) then" +
                // 如果信号量仍然存在，那么对它的时间戳进行更新
                "   return redis.call('zadd', KEYS[1], ARGV[2], ARGV[1]) or true" +
                " end";
        return null != jedis.eval(script,
                Arrays.asList(lockList),
                Arrays.asList(token, String.valueOf(now)));
    }
}
