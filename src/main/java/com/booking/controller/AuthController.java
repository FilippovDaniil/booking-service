package com.booking.controller;

import com.booking.dto.request.LoginRequest;
import com.booking.dto.request.LogoutRequest;
import com.booking.dto.request.RefreshTokenRequest;
import com.booking.dto.request.RegisterRequest;
import com.booking.dto.response.TokenResponse;
import com.booking.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * Контроллер аутентификации.
 * Обрабатывает запросы регистрации, входа, обновления и выхода.
 *
 * Все эндпоинты публичные (не требуют JWT) — настроено в SecurityConfig:
 *   .requestMatchers("/api/auth/**").permitAll()
 *
 * Аннотации Spring MVC:
 *   @RestController  = @Controller + @ResponseBody. Означает: каждый метод
 *                      автоматически сериализует возвращаемый объект в JSON.
 *   @RequestMapping  — базовый путь для всех методов контроллера.
 *   @RequiredArgsConstructor — Lombok: генерирует конструктор со всеми final-полями
 *                              (Spring использует его для внедрения зависимостей).
 *
 * Аннотации Swagger:
 *   @Tag      — группирует эндпоинты в разделе "Authentication" в Swagger UI.
 *   @Operation — описание конкретного метода в документации.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/auth/register
     * Регистрирует нового пользователя (CLIENT или LANDLORD).
     *
     * @Valid — запускает валидацию полей RegisterRequest (email, пароль, имя и т.д.).
     * При ошибке — GlobalExceptionHandler.handleValidation() вернёт 400.
     * При успехе — сразу возвращает токены, повторный логин не нужен.
     */
    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        TokenResponse tokens = authService.register(request);
        // 201 Created: новый ресурс (пользователь) создан
        return ResponseEntity.created(URI.create("/api/users/me")).body(tokens);
    }

    /**
     * POST /api/auth/login
     * Выполняет вход и возвращает пару access + refresh токенов.
     */
    @PostMapping("/login")
    @Operation(summary = "Login and get tokens")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * POST /api/auth/refresh
     * Обновляет access-токен по refresh-токену.
     * Тело: { "refreshToken": "uuid" }
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    /**
     * POST /api/auth/logout
     * Инвалидирует refresh-токен (удаляет из Redis).
     * 204 No Content: действие выполнено, тела ответа нет.
     * Access-токен остаётся валидным до истечения (15 мин) — он краткосрочный и не хранится на сервере.
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout and invalidate refresh token")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}
