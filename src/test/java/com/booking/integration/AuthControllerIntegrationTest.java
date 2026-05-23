package com.booking.integration;

import com.booking.dto.request.LoginRequest;
import com.booking.dto.request.RegisterRequest;
import com.booking.entity.enums.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ===== Интеграционные тесты AuthController =====
 *
 * ЧЕМ ОТЛИЧАЕТСЯ ОТ UNIT-ТЕСТОВ:
 *   Unit-тест: тестируем один класс с моками зависимостей (быстро, изолированно).
 *   Интеграционный тест: поднимаем реальный Spring-контекст, реальную БД (H2),
 *   реальный HTTP-стек. Проверяем что всё работает вместе "от и до".
 *
 * КЛЮЧЕВЫЕ АННОТАЦИИ:
 *
 *   @SpringBootTest
 *     Поднимает ПОЛНЫЙ Spring Application Context — все бины, фильтры, репозитории.
 *     Использует H2 in-memory вместо PostgreSQL (настройка в application-test.yml).
 *     Это медленнее unit-тестов, но проверяет реальную интеграцию слоёв.
 *
 *   @AutoConfigureMockMvc
 *     Автоматически создаёт MockMvc — инструмент для HTTP-запросов без реального сервера.
 *     MockMvc проходит через все фильтры Spring Security, десериализацию, валидацию — как настоящий запрос.
 *
 *   @ActiveProfiles("test")
 *     Активирует профиль "test" → Spring загружает application-test.yml.
 *     В нём: H2 вместо PostgreSQL, Redis и Kafka отключены.
 *
 *   @DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
 *     Пересоздаёт Spring-контекст и базу данных после каждого теста.
 *     Это гарантирует изоляцию: данные одного теста не влияют на другой.
 *     Платим скоростью — каждый тест поднимает контекст заново.
 *
 *   @Import(TestConfig.class)
 *     Подключает тестовую конфигурацию: мок StringRedisTemplate вместо реального Redis.
 *
 * КАК РАБОТАЕТ MockMvc:
 *   mockMvc.perform(post("/api/auth/register")   // отправляем HTTP POST
 *       .contentType(MediaType.APPLICATION_JSON) // тип контента
 *       .content(json))                          // тело запроса
 *     .andExpect(status().isCreated())           // ожидаем HTTP 201 (register) или 200 (login)
 *     .andExpect(jsonPath("$.accessToken").isNotEmpty()) // ожидаем поле в JSON
 *
 * jsonPath("$...") — синтаксис JSONPath для навигации по JSON:
 *   $.accessToken          — поле accessToken в корне
 *   $.items[0].name        — имя первого элемента массива
 *   $.message              — поле message
 *   Матчеры: isNotEmpty(), isArray(), value("text")
 *
 * ObjectMapper — Jackson: преобразует Java-объекты в JSON и обратно.
 *   objectMapper.writeValueAsString(obj) → JSON-строка
 *   objectMapper.readTree(json).get("field").asText() → извлечь поле из JSON
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(TestConfig.class)
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc; // инструмент для HTTP-запросов без реального сервера

    @Autowired
    private ObjectMapper objectMapper; // Jackson: Java ↔ JSON

    // ==================== POST /api/auth/register ====================

    @Test
    void register_успешнаяРегистрация_возвращает201иТокены() throws Exception {
        RegisterRequest request = buildRegisterRequest("new@test.com", Role.CLIENT);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())                           // HTTP 201 — новый ресурс создан
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void register_дублирующийEmail_возвращает400() throws Exception {
        RegisterRequest request = buildRegisterRequest("dup@test.com", Role.CLIENT);

        // Первая регистрация — успешная
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // Вторая с тем же email — ошибка
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())  // HTTP 400
                .andExpect(jsonPath("$.message").value("Email already in use"));
    }

    @Test
    void register_роль_ADMIN_возвращает400() throws Exception {
        RegisterRequest request = buildRegisterRequest("admin@test.com", Role.ADMIN);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot register as ADMIN"));
    }

    @Test
    void register_невалидныйEmail_возвращает400() throws Exception {
        RegisterRequest request = buildRegisterRequest("not-an-email", Role.CLIENT);

        // @Valid в контроллере вызывает валидацию RegisterRequest
        // @Email аннотация выдаёт ошибку → GlobalExceptionHandler.handleValidation() → 400
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Validation failed")));
    }

    // ==================== POST /api/auth/login ====================

    @Test
    void login_верныеДанные_возвращает200иТокены() throws Exception {
        // Сначала регистрируемся — создаём аккаунт в H2
        RegisterRequest reg = buildRegisterRequest("login@test.com", Role.CLIENT);
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)));

        // Затем логинимся
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("login@test.com");
        loginRequest.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void login_неверныйПароль_возвращает401() throws Exception {
        RegisterRequest reg = buildRegisterRequest("wrongpass@test.com", Role.CLIENT);
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)));

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("wrongpass@test.com");
        loginRequest.setPassword("WRONG_PASSWORD"); // неверный пароль

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized()); // HTTP 401
    }

    // ==================== POST /api/auth/refresh ====================

    @Test
    void refresh_валидныйRefreshТокен_возвращаетНовыйAccessТокен() throws Exception {
        // Регистрируемся и сохраняем refresh-токен из ответа
        RegisterRequest reg = buildRegisterRequest("refresh@test.com", Role.CLIENT);
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andReturn();

        // Читаем JSON-ответ и достаём refresh-токен
        String body = result.getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(body).get("refreshToken").asText();

        // Используем refresh-токен для получения нового access-токена
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void refresh_невалидныйТокен_возвращает400() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", "invalid_token"))))
                .andExpect(status().isBadRequest()); // InvalidOperationException → 400
    }

    // ==================== POST /api/auth/logout ====================

    @Test
    void logout_возвращает204() throws Exception {
        // Получаем refresh-токен
        RegisterRequest reg = buildRegisterRequest("logout@test.com", Role.CLIENT);
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andReturn();

        String refreshToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("refreshToken").asText();

        // Выходим — токен должен стать недействительным
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isNoContent()); // HTTP 204

        // После logout refresh-токен больше не работает
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isBadRequest()); // токен инвалидирован
    }

    // ==================== helpers ====================

    /** Вспомогательный метод — избегаем дублирования кода в тестах. */
    private RegisterRequest buildRegisterRequest(String email, Role role) {
        RegisterRequest r = new RegisterRequest();
        r.setEmail(email);
        r.setPassword("password123");
        r.setFirstName("Test");
        r.setLastName("User");
        r.setRole(role);
        return r;
    }
}
