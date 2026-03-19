package com.dinorun.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis leader/follower 분리 설정
 *
 * leader  → 쓰기 전용 (ZADD, INCR 등)
 * follower → 읽기 전용 (ZREVRANGE 등) — 복제 지연 허용
 *
 * application.yml:
 *   redis:
 *     leader:
 *       host: redis-leader-svc
 *       port: 6379
 *     follower:
 *       host: redis-follower-svc
 *       port: 6379
 */
@Configuration
public class RedisConfig {

    @Value("${redis.leader.host}")
    private String leaderHost;

    @Value("${redis.leader.port:6379}")
    private int leaderPort;

    @Value("${redis.follower.host}")
    private String followerHost;

    @Value("${redis.follower.port:6379}")
    private int followerPort;

    @Bean
    @Primary
    public LettuceConnectionFactory leaderConnectionFactory() {
        return new LettuceConnectionFactory(
            new RedisStandaloneConfiguration(leaderHost, leaderPort)
        );
    }

    @Bean("followerConnectionFactory")
    public LettuceConnectionFactory followerConnectionFactory() {
        return new LettuceConnectionFactory(
            new RedisStandaloneConfiguration(followerHost, followerPort)
        );
    }

    @Bean
    @Primary
    public StringRedisTemplate leaderRedis(
            LettuceConnectionFactory leaderConnectionFactory) {
        return new StringRedisTemplate(leaderConnectionFactory);
    }

    @Bean("followerRedis")
    public StringRedisTemplate followerRedis(
            LettuceConnectionFactory followerConnectionFactory) {
        return new StringRedisTemplate(followerConnectionFactory);
    }
}