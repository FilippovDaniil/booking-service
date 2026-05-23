package com.booking.integration;

import com.booking.dto.request.ApartmentRequest;
import com.booking.dto.request.BookingRequest;
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
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ===== Интеграционные тесты BookingController =====
 *
 * Проверяем ПОЛНЫЙ цикл бронирования через реальные HTTP-запросы к реальному контексту:
 *   1. Регистрация клиента и арендодателя
 *   2. Создание квартиры арендодателем
 *   3. Создание бронирования клиентом → статус PENDING
 *   4. Подтверждение → CONFIRMED
 *   5. Отмена → CANCELLED_BY_CLIENT / CANCELLED_BY_LANDLORD
 *
 * ПАТТЕРН СЦЕНАРНОГО ТЕСТА:
 *   Интеграционные тесты часто описывают сценарии использования (user stories).
 *   В @BeforeEach подготавливаем "сцену": пользователи и квартира уже существуют.
 *   В каждом @Test разыгрываем конкретный сценарий.
 *
 * КАК ПОЛУЧИТЬ ТОКЕН В ТЕСТЕ:
 *   1. POST /api/auth/register → получаем TokenResponse
 *   2. Парсим JSON: objectMapper.readTree(result.getResponse().getContentAsString())
 *   3. Достаём: .get("accessToken").asText()
 *   4. Используем в запросах: .header("Authorization", "Bearer " + token)
 *
 * КАК ПОЛУЧИТЬ ID СОЗДАННОГО ОБЪЕКТА:
 *   1. POST /api/apartments → тело ответа содержит { "id": 42, ... }
 *   2. Парсим: objectMapper.readTree(...).get("id").asLong()
 *   3. Используем в следующих запросах: /api/bookings или /api/apartments/42
 *
 * ЗАЧЕМ @BeforeEach А НЕ @BeforeAll:
 *   @BeforeAll — один раз перед всеми тестами класса (нужен static).
 *   @BeforeEach — перед каждым тестом.
 *   С @DirtiesContext(AFTER_EACH_TEST_METHOD) контекст пересоздаётся после каждого теста,
 *   поэтому @BeforeEach правильно повторяет создание пользователей и квартиры.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(TestConfig.class)
class BookingControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // Токены и ID подготавливаются в @BeforeEach и переиспользуются в тестах
    private String clientToken;
    private String landlordToken;
    private Long apartmentId;

    @BeforeEach
    void setUp() throws Exception {
        // Регистрируем клиента и арендодателя, сохраняем их токены
        clientToken   = registerAndGetToken("client@test.com",   Role.CLIENT);
        landlordToken = registerAndGetToken("landlord@test.com", Role.LANDLORD);
        // Арендодатель создаёт квартиру — сохраняем её ID
        apartmentId   = createApartment(landlordToken);
    }

    // ==================== POST /api/bookings ====================

    @Test
    void createBooking_клиент_успешноеСоздание_возвращает201_статус_PENDING() throws Exception {
        BookingRequest req = buildBookingRequest(apartmentId,
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(13)); // 3 ночи × 2000 = 6000

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())                    // HTTP 201 — новый ресурс создан
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalPrice").value(6000.0));
    }

    @Test
    void createBooking_безТокена_возвращает403() throws Exception {
        // Без Authorization заголовка → SecurityConfig блокирует запрос
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildBookingRequest(apartmentId,
                                        LocalDate.now().plusDays(1), LocalDate.now().plusDays(3)))))
                .andExpect(status().isForbidden()); // HTTP 403
    }

    @Test
    void createBooking_конфликтДат_возвращает409() throws Exception {
        // Первый клиент бронирует на 5-10 число
        mockMvc.perform(post("/api/bookings")
                .header("Authorization", "Bearer " + clientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        buildBookingRequest(apartmentId,
                                LocalDate.now().plusDays(5), LocalDate.now().plusDays(10)))));

        // Второй клиент пытается забронировать пересекающиеся даты (7-12)
        String client2Token = registerAndGetToken("client2@test.com", Role.CLIENT);
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + client2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildBookingRequest(apartmentId,
                                        LocalDate.now().plusDays(7), LocalDate.now().plusDays(12)))))
                .andExpect(status().isConflict()); // HTTP 409 — BookingConflictException
    }

    // ==================== POST /api/bookings/{id}/confirm ====================

    @Test
    void confirmBooking_клиент_успешноеПодтверждение_статус_CONFIRMED() throws Exception {
        Long bookingId = createBooking(clientToken, apartmentId);

        mockMvc.perform(post("/api/bookings/" + bookingId + "/confirm")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void confirmBooking_дваРаза_возвращает400() throws Exception {
        Long bookingId = createBooking(clientToken, apartmentId);

        // Первое подтверждение — успешно
        mockMvc.perform(post("/api/bookings/" + bookingId + "/confirm")
                .header("Authorization", "Bearer " + clientToken));

        // Второе подтверждение — бронь уже не PENDING
        mockMvc.perform(post("/api/bookings/" + bookingId + "/confirm")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isBadRequest()); // InvalidOperationException → 400
    }

    // ==================== POST /api/bookings/{id}/cancel ====================

    @Test
    void cancelByClient_успешнаяОтмена_статус_CANCELLED_BY_CLIENT() throws Exception {
        Long bookingId = createBooking(clientToken, apartmentId);

        mockMvc.perform(post("/api/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED_BY_CLIENT"));
    }

    // ==================== POST /api/bookings/{id}/cancel-by-landlord ====================

    @Test
    void cancelByLandlord_арендодатель_успешнаяОтмена() throws Exception {
        Long bookingId = createBooking(clientToken, apartmentId);

        mockMvc.perform(post("/api/bookings/" + bookingId + "/cancel-by-landlord")
                        .header("Authorization", "Bearer " + landlordToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED_BY_LANDLORD"));
    }

    @Test
    void cancelByLandlord_клиент_возвращает403() throws Exception {
        Long bookingId = createBooking(clientToken, apartmentId);

        // @PreAuthorize("hasRole('LANDLORD')") — CLIENT не пройдёт
        mockMvc.perform(post("/api/bookings/" + bookingId + "/cancel-by-landlord")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isForbidden());
    }

    // ==================== GET /api/bookings ====================

    @Test
    void getMyBookings_клиент_видитТолькоСвоиБрони() throws Exception {
        createBooking(clientToken, apartmentId);

        mockMvc.perform(get("/api/bookings")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1)); // ровно одна бронь
    }

    // ==================== helpers ====================

    /**
     * Вспомогательный метод: регистрирует пользователя и возвращает его access-токен.
     * Используется в @BeforeEach и в тестах где нужен второй клиент.
     */
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

    /** Создаёт квартиру от имени арендодателя, возвращает её ID. */
    private Long createApartment(String token) throws Exception {
        ApartmentRequest req = new ApartmentRequest();
        req.setName("Test Apartment");
        req.setCity("Moscow");
        req.setPricePerNight(new BigDecimal("2000.00"));
        req.setMaxGuests(4);

        MvcResult result = mockMvc.perform(post("/api/apartments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }

    /** Создаёт бронирование от имени клиента, возвращает ID брони. */
    private Long createBooking(String token, Long aptId) throws Exception {
        BookingRequest req = buildBookingRequest(aptId,
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(13));

        MvcResult result = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }

    private BookingRequest buildBookingRequest(Long aptId, LocalDate start, LocalDate end) {
        BookingRequest req = new BookingRequest();
        req.setApartmentId(aptId);
        req.setStartDate(start);
        req.setEndDate(end);
        req.setGuestsCount(2);
        return req;
    }
}
