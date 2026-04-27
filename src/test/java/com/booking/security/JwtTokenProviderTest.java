package com.booking.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * ===== Unit-тест JwtTokenProvider =====
 *
 * ОСОБЕННОСТЬ: этот тест НЕ использует Spring-контекст (@SpringBootTest).
 * Мы создаём объект JwtTokenProvider вручную через конструктор.
 * Это самый быстрый тип теста — нет Mockito, нет Spring, только Java.
 *
 * ЗАЧЕМ ТЕСТИРОВАТЬ JWT:
 *   JWT — критически важный компонент безопасности.
 *   Нужно убедиться:
 *     - Генерация создаёт корректный токен (3 части, разделённые точкой)
 *     - Валидация распознаёт подделанные и истёкшие токены
 *     - Извлечение данных (userId, email) работает корректно
 *     - Токен другого сервера (другой ключ) не проходит проверку
 *
 * СТРУКТУРА JWT-ТОКЕНА:
 *   header.payload.signature
 *   Пример: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.abc123
 *   Каждая часть — Base64-строка.
 *
 * ПОЧЕМУ -1L КАК ВРЕМЯ ЖИЗНИ:
 *   new JwtTokenProvider(SECRET, -1L) создаёт токен с expiry = now() - 1ms.
 *   При валидации JJWT видит что токен уже истёк → возвращает false.
 *   Это позволяет тестировать сценарий истёкшего токена без ожидания.
 */
class JwtTokenProviderTest {

    // Секрет >= 32 символов (256 бит) — требование алгоритма HS256
    private static final String SECRET = "testSecretKeyThatIsAtLeast32CharactersLong";
    private static final long EXPIRATION_MS = 900_000; // 15 минут

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        // Создаём объект напрямую (без Spring) — передаём нужные параметры конструктора
        tokenProvider = new JwtTokenProvider(SECRET, EXPIRATION_MS);
    }

    @Test
    void generateToken_создаётНепустойТокен() {
        String token = tokenProvider.generateAccessToken(1L, "user@test.com", "CLIENT");

        // Токен не пустой
        assertThat(token).isNotBlank();
        // JWT = header.payload.signature → ровно 2 точки = 3 части
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void validateToken_валидныйТокен_возвращаетTrue() {
        String token = tokenProvider.generateAccessToken(1L, "user@test.com", "CLIENT");

        // Токен, подписанный нашим ключом, должен пройти проверку
        assertThat(tokenProvider.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_поддельныйТокен_возвращаетFalse() {
        // "not.a.valid.token" — произвольная строка, не является JWT
        assertThat(tokenProvider.validateToken("not.a.valid.token")).isFalse();
    }

    @Test
    void validateToken_пустаяСтрока_возвращаетFalse() {
        assertThat(tokenProvider.validateToken("")).isFalse();
    }

    @Test
    void getUserIdFromToken_возвращаетКорректныйId() {
        // Генерируем токен с userId = 42
        String token = tokenProvider.generateAccessToken(42L, "user@test.com", "LANDLORD");

        Long userId = tokenProvider.getUserIdFromToken(token);

        assertThat(userId).isEqualTo(42L);
    }

    @Test
    void getEmailFromToken_возвращаетКорректныйEmail() {
        String token = tokenProvider.generateAccessToken(1L, "test@example.com", "CLIENT");

        String email = tokenProvider.getEmailFromToken(token);

        assertThat(email).isEqualTo("test@example.com");
    }

    @Test
    void validateToken_истёкшийТокен_возвращаетFalse() {
        // Создаём провайдер с время жизни -1ms → токен сразу просрочен
        JwtTokenProvider expiredProvider = new JwtTokenProvider(SECRET, -1L);
        String expiredToken = expiredProvider.generateAccessToken(1L, "user@test.com", "CLIENT");

        // Наш основной провайдер должен отклонить истёкший токен
        assertThat(tokenProvider.validateToken(expiredToken)).isFalse();
    }

    @Test
    void validateToken_токен_подписанный_другим_ключом_возвращаетFalse() {
        // Другой сервер / другой секрет → токен невалидный для нас
        JwtTokenProvider anotherProvider = new JwtTokenProvider(
                "anotherSecretKeyThatIsAtLeast32CharactersLong", EXPIRATION_MS);
        String foreignToken = anotherProvider.generateAccessToken(1L, "u@t.com", "CLIENT");

        // Наш провайдер должен отклонить токен с чужой подписью
        assertThat(tokenProvider.validateToken(foreignToken)).isFalse();
    }
}
