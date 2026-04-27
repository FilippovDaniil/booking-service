package com.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Конфигурация Redis.
 * Создаёт StringRedisTemplate — основной инструмент работы с Redis.
 * Не активируется в профиле test — там Redis исключён из автоконфигурации,
 * а TokenService использует in-memory fallback.
 */
@Configuration
@Profile("!test") // отключаем в тест-профиле, чтобы не требовать реального Redis
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
