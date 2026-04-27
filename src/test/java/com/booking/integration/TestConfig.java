package com.booking.integration;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

/**
 * Тестовая конфигурация: предоставляет мок StringRedisTemplate.
 * Используется в интеграционных тестах вместо реального Redis.
 * TokenService перехватывает ошибки и переключается на in-memory хранилище.
 */
@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate() {
        StringRedisTemplate mock = Mockito.mock(StringRedisTemplate.class);

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        Mockito.when(mock.opsForValue()).thenReturn(valueOps);

        // Все операции Redis бросают исключение → TokenService использует in-memory fallback
        Mockito.doThrow(new RuntimeException("Redis not available in tests"))
                .when(valueOps).set(Mockito.anyString(), Mockito.anyString(), Mockito.any(Duration.class));
        Mockito.when(valueOps.get(Mockito.anyString()))
                .thenThrow(new RuntimeException("Redis not available in tests"));
        Mockito.when(mock.delete(Mockito.anyString()))
                .thenThrow(new RuntimeException("Redis not available in tests"));

        return mock;
    }
}
