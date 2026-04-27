package com.booking.service;

import com.booking.entity.User;
import com.booking.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис управления JWT-токенами.
 *
 * Access-токены — короткоживущие JWT (15 мин), не хранятся на сервере.
 * Refresh-токены — долгоживущие случайные UUID (30 дней), хранятся в Redis.
 *
 * Схема хранения в Redis: ключ = "refresh:{UUID}", значение = userId (строка).
 *
 * Резервное хранилище: если Redis недоступен, токены хранятся в памяти (ConcurrentHashMap).
 * Недостаток — при перезапуске приложения все in-memory токены теряются.
 */
@Service
@Slf4j
public class TokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final int refreshExpirationDays;

    // Резервное хранилище на случай недоступности Redis
    private final Map<String, String> inMemoryTokenStore = new ConcurrentHashMap<>();

    // Префикс ключа в Redis — изолирует refresh-токены от других данных
    private static final String REFRESH_PREFIX = "refresh:";

    public TokenService(JwtTokenProvider jwtTokenProvider,
                        StringRedisTemplate redisTemplate,
                        @Value("${jwt.refresh-expiration-days}") int refreshExpirationDays) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.redisTemplate = redisTemplate;
        this.refreshExpirationDays = refreshExpirationDays;
    }

    /** Генерирует короткоживущий access-токен для пользователя (не сохраняется на сервере). */
    public String generateAccessToken(User user) {
        return jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());
    }

    /**
     * Генерирует refresh-токен (UUID) и сохраняет его в Redis с TTL.
     * Возвращает сам токен — он будет отправлен клиенту.
     */
    public String generateAndSaveRefreshToken(User user) {
        String refreshToken = UUID.randomUUID().toString();
        String key = REFRESH_PREFIX + refreshToken;
        try {
            redisTemplate.opsForValue().set(key, String.valueOf(user.getId()),
                    Duration.ofDays(refreshExpirationDays));
        } catch (Exception e) {
            log.warn("Redis unavailable, using in-memory token store: {}", e.getMessage());
            inMemoryTokenStore.put(key, String.valueOf(user.getId()));
        }
        return refreshToken;
    }

    /**
     * По refresh-токену возвращает ID пользователя.
     * Сначала ищет в Redis, при ошибке — в in-memory fallback.
     * Возвращает null, если токен не найден (истёк или невалидный).
     */
    public Long getUserIdByRefreshToken(String refreshToken) {
        String key = REFRESH_PREFIX + refreshToken;
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) return Long.parseLong(value);
        } catch (Exception e) {
            log.warn("Redis unavailable, checking in-memory store");
        }
        String value = inMemoryTokenStore.get(key);
        return value != null ? Long.parseLong(value) : null;
    }

    /** Инвалидирует конкретный refresh-токен (logout). */
    public void deleteRefreshToken(String refreshToken) {
        String key = REFRESH_PREFIX + refreshToken;
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis unavailable, deleting from in-memory store");
        }
        inMemoryTokenStore.remove(key);
    }

    /**
     * Инвалидирует все refresh-токены пользователя — используется при блокировке.
     * Redis: сканируем все ключи с префиксом "refresh:*" и удаляем совпадающие.
     * Внимание: keys() неэффективен на больших объёмах — в продакшне лучше хранить
     * маппинг userId → [token1, token2, ...] отдельно.
     */
    public void deleteAllRefreshTokensForUser(Long userId) {
        String userIdStr = String.valueOf(userId);
        inMemoryTokenStore.entrySet().removeIf(e -> userIdStr.equals(e.getValue()));
        try {
            var keys = redisTemplate.keys(REFRESH_PREFIX + "*");
            if (keys != null) {
                keys.forEach(key -> {
                    String value = redisTemplate.opsForValue().get(key);
                    if (userIdStr.equals(value)) redisTemplate.delete(key);
                });
            }
        } catch (Exception e) {
            log.warn("Redis unavailable during token cleanup: {}", e.getMessage());
        }
    }

    /** Делегирует валидацию подписи и срока токена в JwtTokenProvider. */
    public boolean validateToken(String token) {
        return jwtTokenProvider.validateToken(token);
    }
}
