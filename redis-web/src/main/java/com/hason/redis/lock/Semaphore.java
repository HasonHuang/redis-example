package com.hason.redis.lock;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

import java.util.List;
import java.util.UUID;

/**
 * 计数信号量：限制一项资源能同时被多少个进程访问
 *
 * @author Huanghs
 * @since 2.0
 * @date 2017/4/14
 */
public class Semaphore {

    private static final String PREFIX = "semaphore:";
    private static final String OWNER = ":owner";

    /**
     * 简单的计数信号量（不公平的）
     *
     * 优点：简单，运行速度快
     * 缺点：每个进程的系统时间必须相同，信号量的数量偶尔超过限制
     *
     * 步骤：
     * 1.清除过期的信号量
     * 2.生成token放进信号量集合，当前时间戳作为分值
     * 3.获取token在集合中的排名：若排名低于可获取的信号量总数（排名由0开始），表示申请成功，否则失败
     *
     * @param jedis Redis 客户端
     * @param name 被锁对象
     * @param limit （可申请的）信号量总数
     * @param lockTimeout 信号量的有效时长，毫秒
     * @return 成功时返回 UUID，失败时返回 null
     */
    public String acquireSimple(Jedis jedis, String name, int limit, long lockTimeout) {
        String semaphoreName = PREFIX + name;
        String token = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        Transaction transaction = jedis.multi();  // 使用事务保证原子性
        // 清除过期的信号量
        transaction.zremrangeByScore(semaphoreName, "-inf", String.valueOf(now - lockTimeout));
        // 把 token 放进信号量集合，获取排名
        transaction.zadd(semaphoreName, now, token);
        transaction.zrank(semaphoreName, token);
        List<Object> execList = transaction.exec();

        // 获取排名
        int rank = ((Long) execList.get(execList.size() - 1)).intValue();
        if (rank < limit) {
            return token;
        }

        // 获取信号量失败，清除无用数据
        jedis.zrem(semaphoreName, token);
        return null;
    }

    public boolean releaseSimple(Jedis jedis, String name, String token) {
        jedis.zrem(PREFIX + name, token);
    }

    /**
     * 公平信号量
     *
     * 优点：主机间无需拥有相同时间（误差可在一两秒内）
     * 缺点：主机间的时间误差会导致信号量过早释放或太晚释放（需控制在一两秒内），信号量的数量偶尔超过限制
     *
     * 超时集合：记录信号量与获取时间
     * 计数器：  记录获取信号量的编号，每次获取时执行自增
     * 信号量拥有者集合： 记录信号量与计数（ID编号）
     *
     * 步骤：
     * 1. 在超时集合中的删除过期的信号量
     * 2. 对超时集合和信号量拥有者集合执行交集，结果覆盖到信号量拥有者集合中
     * 3. 计数器自增；把结果和新的信号量放到信号量拥有者集合中，同时把当前时间戳和新的信号量放到超时集合中
     * 4. 检查新的信号量在 信号量拥有者集合 中的排名，若排名足够低（排名由0开始），表示获取成功；否则失败
     *
     * @param jedis Redis客户端
     * @param name 被锁对象
     * @param limit （可申请的）信号量总数
     * @param lockTimeout 信号量的有效时长，毫秒
     * @return
     */
    public String acquireFair(Jedis jedis, String name, int limit, long lockTimeout) {

    }

    /**
     * 取消竞争条件的信号量。任何时候都应该使用这种信号量
     *
     * 优点：保证信号量的数量不会超过限制
     *
     * @param jedis
     * @param name
     * @param limit
     * @param lockTimeout
     * @return
     */

    public String acquireFairWithLock(
            Jedis jedis, String name, int limit, long lockTimeout) {
        // 获取信号量前，执行锁定操作，每个时刻只能有一个客户端获取信号量
        String token = DistributedLock.acquire(jedis, name, 1);
        if (token != null) {
            try {
                // 获取信号量
                return acquireFair(jedis, name, limit, lockTimeout);
            } finally {
                // 解除锁定
                DistributedLock.release(jedis, name, token);
            }
        }
        // 获取信号量失败
        return null;
    }

    public boolean releaseFair(Jedis jedis, String name, String token) {
        Transaction transaction = jedis.multi();
        transaction.zrem(PREFIX + name, token);  // 超时集合
        transaction.zrem(PREFIX + name + OWNER, token);  // 信号量拥有者
        List<Object> execList = transaction.exec();
        return (Long) execList.get(execList.size() - 1) == 1;
    }

    /**
     * 刷新信号量。续期
     *
     * @param jedis Redis客户端
     * @param name 被锁对象
     * @param token 信号量对应的令牌，获取信号量时的返回值
     * @return true 成功，false 失败
     */
    public boolean refreshFair(Jedis jedis, String name, String token) {
        long now = System.currentTimeMillis();
        // 新增时返回 1； 更新时返回 0
        Long result = jedis.zadd(PREFIX + name, now, token);
        // 如果是新增，表示该信号量已经过期被清除掉，告知客户端已失去信号量
        if (result == 1) {
            releaseFair(jedis, name, token);
            return false;
        }
        // 刷新成功
        return true;
    }
}
