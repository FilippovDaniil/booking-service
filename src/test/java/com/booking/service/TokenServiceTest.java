package com.booking.service;

import com.booking.entity.User;
import com.booking.entity.enums.Role;
import com.booking.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private TokenService tokenService;

    private User user;

    @BeforeEach
    void setUp() {
        // lenient() — стаб нужен не во всех тестах, без него Mockito бросает UnnecessaryStubbingException
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        tokenService = new TokenService(jwtTokenProvider, redisTemplate, 30);

        user = User.builder().id(1L).email("user@test.com").role(Role.CLIENT).enabled(true).build();
    }

    // ==================== generateAccessToken ====================

    @Test
    void generateAccessToken_делегируетВJwtTokenProvider() {
        when(jwtTokenProvider.generateAccessToken(1L, "user@test.com", "CLIENT"))
                .thenReturn("jwt_token");

        String token = tokenService.generateAccessToken(user);

        assertThat(token).isEqualTo("jwt_token");
    }

    // ==================== generateAndSaveRefreshToken ====================

    @Test
    void generateAndSaveRefreshToken_сохраняетВRedis() {
        String token = tokenService.generateAndSaveRefreshToken(user);

        assertThat(token).isNotNull().isNotBlank();
        // Проверяем, что токен сохранён в Redis с TTL 30 дней
        verify(valueOps).set(
                eq("refresh:" + token),
                eq("1"),
                eq(Duration.ofDays(30))
        );
    }

    @Test
    void generateAndSaveRefreshToken_redisНедоступен_сохраняетВПамять() {
        doThrow(new RuntimeException("Redis is down"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        String token = tokenService.generateAndSaveRefreshToken(user);

        // Токен всё равно сгенерирован и сохранён в in-memory fallback
        assertThat(token).isNotNull();
        // Проверяем, что он доступен через getUserIdByRefreshToken из in-memory
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis is down"));
        Long userId = tokenService.getUserIdByRefreshToken(token);
        assertThat(userId).isEqualTo(1L);
    }

    // ==================== getUserIdByRefreshToken ====================

    @Test
    void getUserIdByRefreshToken_токенВRedis_возвращаетUserId() {
        when(valueOps.get("refresh:some_token")).thenReturn("1");

        Long userId = tokenService.getUserIdByRefreshToken("some_token");

        assertThat(userId).isEqualTo(1L);
    }

    @Test
    void getUserIdByRefreshToken_токенНеНайден_возвращаетNull() {
        when(valueOps.get("refresh:unknown")).thenReturn(null);

        Long userId = tokenService.getUserIdByRefreshToken("unknown");

        assertThat(userId).isNull();
    }

    // ==================== deleteRefreshToken ====================

    @Test
    void deleteRefreshToken_удаляетИзRedis() {
        tokenService.deleteRefreshToken("some_token");

        verify(redisTemplate).delete("refresh:some_token");
    }

    @Test
    void deleteRefreshToken_удаляетИзInMemoryЕслиRedisНедоступен() {
        doThrow(new RuntimeException("Redis down")).when(redisTemplate).delete(anyString());

        // Сначала кладём токен в in-memory
        doThrow(new RuntimeException("Redis down"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));
        String token = tokenService.generateAndSaveRefreshToken(user);

        // Теперь удаляем
        tokenService.deleteRefreshToken(token);

        // Токен должен быть удалён из in-memory (не найден при поиске)
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis down"));
        assertThat(tokenService.getUserIdByRefreshToken(token)).isNull();
    }
}
