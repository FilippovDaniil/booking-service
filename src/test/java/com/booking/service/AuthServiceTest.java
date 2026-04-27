package com.booking.service;

import com.booking.dto.request.LoginRequest;
import com.booking.dto.request.RegisterRequest;
import com.booking.dto.response.TokenResponse;
import com.booking.entity.User;
import com.booking.entity.enums.Role;
import com.booking.exception.InvalidOperationException;
import com.booking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ===== КАК ЧИТАТЬ ЭТОТ ФАЙЛ =====
 *
 * Unit-тест — тест одного класса (AuthService) в изоляции.
 * Все зависимости заменяются «муляжами» (моками) — реальные БД и Redis не нужны.
 *
 * ИСПОЛЬЗУЕМЫЕ ИНСТРУМЕНТЫ:
 *
 * JUnit 5 (@Test, @BeforeEach)
 *   Фреймворк для написания и запуска тестов.
 *   @Test     — помечает метод как тест-кейс
 *   @BeforeEach — метод запускается ПЕРЕД каждым @Test (подготовка данных)
 *
 * Mockito (@Mock, @InjectMocks, when/verify)
 *   Фреймворк для создания моков зависимостей.
 *   @Mock        — создаёт «пустой» объект нужного типа (все методы возвращают null/0)
 *   @InjectMocks — создаёт РЕАЛЬНЫЙ объект тестируемого класса и внедряет @Mock-поля
 *   when(...).thenReturn(...) — задаём поведение мока: «когда вызван X — вернуть Y»
 *   verify(mock).method(...)  — проверяем, что метод мока был вызван
 *   doThrow(...).when(mock).method(...) — мок бросает исключение при вызове
 *
 * AssertJ (assertThat)
 *   Библиотека проверок (assertions). Удобнее стандартного JUnit assertEquals.
 *   assertThat(value).isEqualTo(expected)
 *   assertThat(value).isNotNull()
 *   assertThatThrownBy(() -> ...).isInstanceOf(SomeException.class).hasMessageContaining("...")
 *
 * @ExtendWith(MockitoExtension.class)
 *   Подключает Mockito к JUnit 5: обрабатывает @Mock и @InjectMocks аннотации.
 *
 * СТРУКТУРА ТЕСТА (паттерн AAA — Arrange, Act, Assert):
 *   Arrange — подготовка: настройка моков через when(...)
 *   Act     — действие: вызов тестируемого метода
 *   Assert  — проверка: assertThat(...) или assertThatThrownBy(...)
 *
 * НАИМЕНОВАНИЕ ТЕСТОВ:
 *   Формат: метод_условие_ожидаемыйРезультат
 *   Пример: register_emailУжеЗанят_бросаетException
 */
@ExtendWith(MockitoExtension.class) // подключаем Mockito к JUnit 5
class AuthServiceTest {

    // @Mock — создаёт мок-объект (не реальную реализацию)
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private TokenService tokenService;

    // @InjectMocks — создаёт РЕАЛЬНЫЙ AuthService и внедряет все @Mock через конструктор
    @InjectMocks
    private AuthService authService;

    // Общие тестовые данные, переиспользуемые в нескольких тестах
    private RegisterRequest registerRequest;
    private User savedUser;

    @BeforeEach // запускается перед каждым тестом
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setEmail("user@test.com");
        registerRequest.setPassword("password123");
        registerRequest.setFirstName("Ivan");
        registerRequest.setLastName("Petrov");
        registerRequest.setRole(Role.CLIENT);

        // Симулируем объект, который вернул бы userRepository.save()
        savedUser = User.builder()
                .id(1L)
                .email("user@test.com")
                .password("hashed_password")
                .firstName("Ivan")
                .lastName("Petrov")
                .role(Role.CLIENT)
                .enabled(true)
                .build();
    }

    // ==================== register ====================

    @Test
    void register_успешнаяРегистрация_возвращаетТокены() {
        // Arrange: настраиваем поведение моков
        when(userRepository.existsByEmail("user@test.com")).thenReturn(false); // email свободен
        when(passwordEncoder.encode("password123")).thenReturn("hashed_password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(tokenService.generateAccessToken(any())).thenReturn("access_token");
        when(tokenService.generateAndSaveRefreshToken(any())).thenReturn("refresh_token");

        // Act: вызываем тестируемый метод
        TokenResponse response = authService.register(registerRequest);

        // Assert: проверяем результат
        assertThat(response.getAccessToken()).isEqualTo("access_token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh_token");
        verify(userRepository).save(any(User.class));       // сохранение должно произойти
        verify(passwordEncoder).encode("password123");      // пароль должен быть захэширован
    }

    @Test
    void register_emailУжеЗанят_бросаетException() {
        // Arrange: email уже занят
        when(userRepository.existsByEmail("user@test.com")).thenReturn(true);

        // Assert + Act: проверяем, что метод бросает исключение с нужным сообщением
        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Email already in use");

        // Дополнительно проверяем что save() не был вызван (пользователь не создан)
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_роль_ADMIN_запрещена() {
        registerRequest.setRole(Role.ADMIN); // попытка зарегистрироваться как ADMIN
        when(userRepository.existsByEmail(any())).thenReturn(false);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Cannot register as ADMIN");
    }

    // ==================== login ====================

    @Test
    void login_верныеДанные_возвращаетТокены() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("user@test.com");
        loginRequest.setPassword("password123");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(savedUser));
        when(tokenService.generateAccessToken(savedUser)).thenReturn("access_token");
        when(tokenService.generateAndSaveRefreshToken(savedUser)).thenReturn("refresh_token");
        // authenticationManager.authenticate() — ничего не делаем, просто не бросаем исключение

        TokenResponse response = authService.login(loginRequest);

        assertThat(response.getAccessToken()).isEqualTo("access_token");
        // Проверяем, что AuthenticationManager был вызван для проверки пароля
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void login_неверныйПароль_бросаетBadCredentialsException() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("user@test.com");
        loginRequest.setPassword("wrong");

        // Настраиваем мок: authenticationManager бросит исключение при неверном пароле
        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any());

        // AuthService не должен перехватывать это исключение — оно прокидывается наверх
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);
    }

    // ==================== refresh ====================

    @Test
    void refresh_валидныйТокен_возвращаетНовыйAccessToken() {
        // tokenService находит userId по refresh-токену
        when(tokenService.getUserIdByRefreshToken("valid_refresh")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(savedUser));
        when(tokenService.generateAccessToken(savedUser)).thenReturn("new_access_token");

        TokenResponse response = authService.refresh("valid_refresh");

        assertThat(response.getAccessToken()).isEqualTo("new_access_token");
        // Refresh-токен остаётся тем же — клиент продолжает его использовать
        assertThat(response.getRefreshToken()).isEqualTo("valid_refresh");
    }

    @Test
    void refresh_невалидныйТокен_бросаетException() {
        // tokenService не находит токен → возвращает null
        when(tokenService.getUserIdByRefreshToken("bad_token")).thenReturn(null);

        assertThatThrownBy(() -> authService.refresh("bad_token"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Invalid or expired refresh token");
    }

    // ==================== logout ====================

    @Test
    void logout_удаляетRefreshТокен() {
        // Act: вызываем logout
        authService.logout("some_refresh_token");

        // Assert: проверяем что TokenService.deleteRefreshToken был вызван с нужным аргументом
        verify(tokenService).deleteRefreshToken("some_refresh_token");
    }
}
