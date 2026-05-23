package com.booking.integration;

import com.booking.dto.request.ApartmentRequest;
import com.booking.dto.request.RegisterRequest;
import com.booking.entity.enums.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ===== Интеграционные тесты ApartmentController =====
 *
 * Проверяем управление квартирами через HTTP:
 *   - Публичный поиск без токена
 *   - CRUD операции только для LANDLORD
 *   - Права: только владелец может редактировать свою квартиру
 *   - Мягкое удаление (active = false вместо DELETE из БД)
 *
 * ПОЧЕМУ ВАЖНО ТЕСТИРОВАТЬ ПРАВА ДОСТУПА:
 *   @PreAuthorize("hasRole('LANDLORD')") работает через Spring AOP.
 *   В unit-тестах мы мокаем сервис и не проверяем фильтры.
 *   Только интеграционный тест через MockMvc проверяет РЕАЛЬНУЮ цепочку:
 *     HTTP запрос → JwtAuthenticationFilter → @PreAuthorize → Controller → Service
 *
 * ТЕСТ "НЕГАТИВНОГО СЦЕНАРИЯ":
 *   Тесты create_клиент_возвращает403 и create_безТокена_возвращает403
 *   проверяют что система ПРАВИЛЬНО ОТКАЗЫВАЕТ — это так же важно,
 *   как тесты успешного пути.
 *
 * ПОРЯДОК ОПЕРАЦИЙ В @BeforeEach:
 *   Для тестов обновления и удаления нужна существующая квартира.
 *   Создаём её один раз перед тестами через createApartment().
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(TestConfig.class)
class ApartmentControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String landlordToken;  // токен арендодателя
    private String clientToken;    // токен клиента (для проверки запрета)

    @BeforeEach
    void setUp() throws Exception {
        landlordToken = registerAndGetToken("landlord@test.com", Role.LANDLORD);
        clientToken   = registerAndGetToken("client@test.com",   Role.CLIENT);
    }

    // ==================== GET /api/apartments (публичный поиск) ====================

    @Test
    void search_безТокена_возвращает200() throws Exception {
        // Поиск квартир публичный — не требует JWT (настройка в SecurityConfig)
        mockMvc.perform(get("/api/apartments")
                        .param("city", "Moscow")
                        .param("startDate", "2027-06-01")
                        .param("endDate", "2027-06-05")
                        .param("guests", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray()); // Page<T>.content — массив элементов
    }

    // ==================== POST /api/apartments (только LANDLORD) ====================

    @Test
    void create_арендодатель_успешноеСоздание_возвращает201() throws Exception {
        ApartmentRequest req = buildApartmentRequest();

        mockMvc.perform(post("/api/apartments")
                        .header("Authorization", "Bearer " + landlordToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())              // HTTP 201 — новый ресурс создан
                .andExpect(jsonPath("$.name").value("Test Apartment"))
                .andExpect(jsonPath("$.city").value("Moscow"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void create_клиент_возвращает403() throws Exception {
        // @PreAuthorize("hasRole('LANDLORD')") блокирует клиента
        // → GlobalExceptionHandler.handleSpringAccessDenied() → 403
        mockMvc.perform(post("/api/apartments")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildApartmentRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_безТокена_возвращает403() throws Exception {
        // Без JWT → SecurityConfig отклоняет запрос
        mockMvc.perform(post("/api/apartments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildApartmentRequest())))
                .andExpect(status().isForbidden());
    }

    // ==================== GET /api/apartments/my ====================

    @Test
    void getMyApartments_арендодатель_видитСвоиКвартиры() throws Exception {
        // Создаём квартиру — она должна появиться в /my
        mockMvc.perform(post("/api/apartments")
                .header("Authorization", "Bearer " + landlordToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildApartmentRequest())));

        mockMvc.perform(get("/api/apartments/my")
                        .header("Authorization", "Bearer " + landlordToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())         // результат — массив
                .andExpect(jsonPath("$.length()").value(1)); // именно 1 квартира
    }

    // ==================== PUT /api/apartments/{id} ====================

    @Test
    void update_владелецОбновляетКвартиру() throws Exception {
        Long apartmentId = createApartment(landlordToken);

        ApartmentRequest updateReq = buildApartmentRequest();
        updateReq.setName("Updated Name"); // меняем название

        mockMvc.perform(put("/api/apartments/" + apartmentId)
                        .header("Authorization", "Bearer " + landlordToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name")); // название изменилось
    }

    @Test
    void update_чужойАрендодатель_возвращает403() throws Exception {
        Long apartmentId = createApartment(landlordToken);

        // Другой арендодатель не должен редактировать чужую квартиру
        String anotherLandlordToken = registerAndGetToken("another@test.com", Role.LANDLORD);

        mockMvc.perform(put("/api/apartments/" + apartmentId)
                        .header("Authorization", "Bearer " + anotherLandlordToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildApartmentRequest())))
                .andExpect(status().isForbidden()); // AccessDeniedException → 403
    }

    // ==================== DELETE /api/apartments/{id} ====================

    @Test
    void delete_владелецДеактивируетКвартиру() throws Exception {
        Long apartmentId = createApartment(landlordToken);

        // Деактивируем квартиру
        mockMvc.perform(delete("/api/apartments/" + apartmentId)
                        .header("Authorization", "Bearer " + landlordToken))
                .andExpect(status().isNoContent()); // HTTP 204

        // Квартира деактивирована — getById возвращает её с active=false
        mockMvc.perform(get("/api/apartments/" + apartmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false)); // мягкое удаление
    }

    // ==================== GET /api/apartments/{id} ====================

    @Test
    void getById_несуществующаяКвартира_возвращает404() throws Exception {
        mockMvc.perform(get("/api/apartments/99999"))
                .andExpect(status().isNotFound()); // ResourceNotFoundException → 404
    }

    // ==================== helpers ====================

    private String registerAndGetToken(String email, Role role) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail(email);
        req.setPassword("password123");
        req.setFirstName("Test");
        req.setLastName("User");
        req.setRole(role);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    /** Создаёт квартиру и возвращает её ID из ответа. */
    private Long createApartment(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/apartments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildApartmentRequest())))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }

    private ApartmentRequest buildApartmentRequest() {
        ApartmentRequest req = new ApartmentRequest();
        req.setName("Test Apartment");
        req.setCity("Moscow");
        req.setStreet("Tverskaya");
        req.setHouseNumber("1");
        req.setPricePerNight(new BigDecimal("2000.00"));
        req.setMaxGuests(4);
        return req;
    }
}
