package com.mod.loan.config.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Configuration
public class RedisConfig {

    @Value("${jedis.url}")
    String host;
    @Value("${jedis.port}")
    int port;
    @Value("${jedis.auth}")
    String auth;
    @Value("${jedis.maxTotal}")
    int maxTotal;
    @Value("${jedis.maxIdle}")
    int maxIdle;
    @Value("${jedis.maxWaitMillis}")
    int maxWaitMillis;
    @Value("${jedis.testOnBorrow}")
    String testOnBorrow;
    @Value("${jedis.blockWhenExhausted}")
    String blockWhenExhausted;
    @Value("${jedis.testWhileIdle}")
    String testWhileIdle;

    @Bean
    public JedisPoolConfig jedisPoolConfig() {
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxTotal(maxTotal);
        jedisPoolConfig.setMaxIdle(maxIdle);
        jedisPoolConfig.setMaxWaitMillis(maxWaitMillis);
        jedisPoolConfig.setTestOnBorrow(Boolean.getBoolean(testOnBorrow));
        jedisPoolConfig.setTestWhileIdle(Boolean.getBoolean(testWhileIdle));
        jedisPoolConfig.setBlockWhenExhausted(Boolean.getBoolean(blockWhenExhausted));
        return jedisPoolConfig;
    }

    @Bean
    public JedisPool jedisPool(JedisPoolConfig poolConfig) {
        JedisPool jedisPool = new JedisPool(poolConfig, host, port, 10000, auth);
        return jedisPool;
    }

//	@Bean
//    public RedisTemplate<String, ?> redisTemplate(RedisConnectionFactory redisConnectionFactory)
//    {
//        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<Object>(Object.class);
//        ObjectMapper om = new ObjectMapper();
//        jackson2JsonRedisSerializer.setObjectMapper(om);
//        RedisTemplate<String, Object> template = new RedisTemplate<String, Object>();
//        template.setConnectionFactory(redisConnectionFactory);
//        RedisSerializer<String> stringSerializer = new StringRedisSerializer();
//        template.setKeySerializer(stringSerializer);
//        template.setValueSerializer(jackson2JsonRedisSerializer);
//        template.setHashKeySerializer(stringSerializer);
//        template.setHashValueSerializer(jackson2JsonRedisSerializer);
//        template.afterPropertiesSet();
//        return template;
//    }
}
