package com.booking.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Утилита для работы с JWT-токенами.
 * Отвечает за три операции: генерацию, валидацию, извлечение данных из токена.
 *
 * Алгоритм подписи: HS256 (HMAC-SHA256).
 * Секрет берётся из конфига (jwt.secret), либо из переменной окружения JWT_SECRET.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    // SecretKey — объект-ключ JJWT, построенный из строки секрета
    private final SecretKey key;

    // Время жизни access-токена в миллисекундах (по умолчанию 15 минут)
    private final long accessExpirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration-ms}") long accessExpirationMs) {
        // hmacShaKeyFor требует ключ >= 256 бит; наш секрет должен быть >= 32 символов
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = accessExpirationMs;
    }

    /**
     * Создаёт подписанный JWT.
     * subject = userId (строка), плюс кастомные claims: email и role.
     */
    public String generateAccessToken(Long userId, String email, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessExpirationMs);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)        // алгоритм определяется автоматически по типу ключа (HS256)
                .compact();
    }

    /**
     * Проверяет подпись и срок действия токена.
     * Возвращает false при любой ошибке (истёк, поддельный, пустой).
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Invalid JWT token: {}", ex.getMessage());
            return false;
        }
    }

    /** Извлекает ID пользователя из subject поля токена. */
    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        return Long.parseLong(claims.getSubject());
    }

    /** Извлекает email из кастомного claim'а токена. */
    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        return claims.get("email", String.class);
    }
}
