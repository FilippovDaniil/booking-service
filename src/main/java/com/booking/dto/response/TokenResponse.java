package com.booking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * DTO ответа с JWT-токенами.
 * Возвращается при регистрации, логине и обновлении токена.
 *
 * accessToken  — короткоживущий JWT (15 мин).
 *   Клиент передаёт его в каждом запросе: Authorization: Bearer <accessToken>
 *   Хранить в памяти приложения (не в localStorage — уязвим к XSS).
 *
 * refreshToken — долгоживущий UUID (30 дней), хранится в Redis.
 *   Используется ТОЛЬКО для обновления accessToken (POST /api/auth/refresh).
 *   Хранить в httpOnly cookie (недоступна для JavaScript — защита от XSS).
 *
 * tokenType = "Bearer" — стандартное значение, информирует клиента как использовать токен.
 */
@Data
@AllArgsConstructor
public class TokenResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";

    // Основной конструктор без tokenType — он всегда "Bearer"
    public TokenResponse(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }
}
