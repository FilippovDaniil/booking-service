package com.booking.service;

import com.booking.dto.request.LoginRequest;
import com.booking.dto.request.RegisterRequest;
import com.booking.dto.response.TokenResponse;
import com.booking.entity.User;
import com.booking.entity.enums.Role;
import com.booking.exception.InvalidOperationException;
import com.booking.exception.ResourceNotFoundException;
import com.booking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис аутентификации: регистрация, вход, обновление токена, выход.
 *
 * Токены:
 *  - access-токен  — короткоживущий JWT (15 мин), передаётся в заголовке Authorization.
 *  - refresh-токен — долгоживущий UUID (30 дней), хранится в Redis, используется
 *    только для получения нового access-токена.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    // AuthenticationManager проверяет email+пароль через CustomUserDetailsService
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    /**
     * Регистрация нового пользователя.
     * Ограничения: нельзя зарегистрироваться с ролью ADMIN, email должен быть уникальным.
     * Пароль сохраняется в виде BCrypt-хэша.
     */
    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new InvalidOperationException("Email already in use");
        }
        if (request.getRole() == Role.ADMIN) {
            throw new InvalidOperationException("Cannot register as ADMIN");
        }
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword())) // хэшируем пароль
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(request.getRole())
                .enabled(true)
                .build();
        userRepository.save(user);
        return createTokens(user); // сразу выдаём токены — логинить повторно не нужно
    }

    /**
     * Вход по email и паролю.
     * AuthenticationManager бросит BadCredentialsException при неверных данных
     * или DisabledException если аккаунт заблокирован — их перехватит GlobalExceptionHandler.
     */
    public TokenResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return createTokens(user);
    }

    /**
     * Обновление access-токена по refresh-токену.
     * Refresh-токен не меняется — клиент продолжает использовать старый.
     */
    public TokenResponse refresh(String refreshToken) {
        Long userId = tokenService.getUserIdByRefreshToken(refreshToken);
        if (userId == null) {
            throw new InvalidOperationException("Invalid or expired refresh token");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String newAccessToken = tokenService.generateAccessToken(user);
        return new TokenResponse(newAccessToken, refreshToken); // тот же refresh-токен
    }

    /** Выход: удаляем refresh-токен из Redis, чтобы им нельзя было воспользоваться повторно. */
    public void logout(String refreshToken) {
        tokenService.deleteRefreshToken(refreshToken);
    }

    /** Вспомогательный метод: генерирует оба токена и возвращает их клиенту. */
    private TokenResponse createTokens(User user) {
        String accessToken = tokenService.generateAccessToken(user);
        String refreshToken = tokenService.generateAndSaveRefreshToken(user);
        return new TokenResponse(accessToken, refreshToken);
    }
}
