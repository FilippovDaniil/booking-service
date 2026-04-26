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

@Service
@Slf4j
public class TokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final int refreshExpirationDays;

    // Fallback in-memory store when Redis is unavailable
    private final Map<String, String> inMemoryTokenStore = new ConcurrentHashMap<>();

    private static final String REFRESH_PREFIX = "refresh:";

    public TokenService(JwtTokenProvider jwtTokenProvider,
                        StringRedisTemplate redisTemplate,
                        @Value("${jwt.refresh-expiration-days}") int refreshExpirationDays) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.redisTemplate = redisTemplate;
        this.refreshExpirationDays = refreshExpirationDays;
    }

    public String generateAccessToken(User user) {
        return jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());
    }

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

    public void deleteRefreshToken(String refreshToken) {
        String key = REFRESH_PREFIX + refreshToken;
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis unavailable, deleting from in-memory store");
        }
        inMemoryTokenStore.remove(key);
    }

    public void deleteAllRefreshTokensForUser(Long userId) {
        String userIdStr = String.valueOf(userId);
        // In-memory fallback cleanup
        inMemoryTokenStore.entrySet().removeIf(e -> userIdStr.equals(e.getValue()));
        // Redis cleanup
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

    public boolean validateToken(String token) {
        return jwtTokenProvider.validateToken(token);
    }
}
