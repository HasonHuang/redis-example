/*
 * Copyright 2014 - 2016 珠宝易 All Rights Reserved
 */
package com.hason;

import redis.clients.jedis.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by Hason on 2017/3/22.
 */
public class App {
    public static void main(String[] args) {
        Jedis jedis = new Jedis("localhost", 6379);
        String set = jedis.set("key", "val", "xx");
        System.out.println(set);
    }
}
