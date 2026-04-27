package com.booking.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO запроса входа (логина).
 * Передаётся в POST /api/auth/login.
 *
 * Содержит минимальный набор полей для аутентификации.
 * Пароль здесь в открытом виде — это нормально, так как:
 *  1. HTTPS шифрует транспортный уровень
 *  2. AuthService сразу передаёт пароль в AuthenticationManager для проверки BCrypt-хэша
 *  3. Пароль нигде не сохраняется и не логируется
 */
@Data
public class LoginRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;
}
