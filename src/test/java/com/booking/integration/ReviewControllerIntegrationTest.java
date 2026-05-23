package com.booking.integration;

import com.booking.dto.request.ApartmentRequest;
import com.booking.dto.request.BookingRequest;
import com.booking.dto.request.LoginRequest;
import com.booking.dto.request.RegisterRequest;
import com.booking.dto.request.ReviewRequest;
import com.booking.entity.Booking;
import com.booking.entity.User;
import com.booking.entity.enums.BookingStatus;
import com.booking.entity.enums.Role;
import com.booking.repository.BookingRepository;
import com.booking.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционные тесты ReviewController.
 *
 * Особенность: для создания отзыва нужна бронь со статусом COMPLETED.
 * Завершённую бронь создаём в три шага:
 *   1. Создаём бронь через API (статус PENDING)
 *   2. Принудительно меняем статус на COMPLETED через BookingRepository
 *      (т.к. реальный scheduler работает по расписанию, не в тестах)
 *
 * Для тестирования DELETE /api/reviews/{id} нужен ADMIN.
 * ADMIN нельзя зарегистрировать через /api/auth/register (бизнес-правило).
 * Создаём его напрямую через UserRepository + PasswordEncoder.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(TestConfig.class)
class ReviewControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private BookingRepository bookingRepository;

    private String clientToken;
    private String adminToken;
    private Long apartmentId;
    private Long bookingId;

    @BeforeEach
    void setUp() throws Exception {
        clientToken   = registerAndGetToken("client@test.com", Role.CLIENT);
        String landlordToken = registerAndGetToken("landlord@test.com", Role.LANDLORD);
        apartmentId   = createApartment(landlordToken);
        bookingId     = createAndCompleteBooking(clientToken, apartmentId);
        adminToken    = createAdminAndGetToken();
    }

    // ==================== GET /api/apartments/{id}/reviews (public) ====================

    @Test
    void getReviewsByApartment_публичный_возвращает200() throws Exception {
        mockMvc.perform(get("/api/apartments/" + apartmentId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getReviewsByApartment_послеСозданияОтзыва_содержитОтзыв() throws Exception {
        createReview(clientToken, bookingId, 5, "Excellent!");

        mockMvc.perform(get("/api/apartments/" + apartmentId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].rating").value(5));
    }

    // ==================== POST /api/reviews ====================

    @Test
    void createReview_успешноеСоздание_возвращает201() throws Exception {
        ReviewRequest req = buildReviewRequest(bookingId, 5, "Great place!");

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())              // HTTP 201 — новый ресурс создан
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("Great place!"));
    }

    @Test
    void createReview_безТокена_возвращает403() throws Exception {
        ReviewRequest req = buildReviewRequest(bookingId, 4, "Good");

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createReview_дваРазаНаОднуБронь_возвращает400() throws Exception {
        // Первый отзыв — успешный
        createReview(clientToken, bookingId, 5, "First review");

        // Второй отзыв на ту же бронь — ошибка дублирования
        ReviewRequest req = buildReviewRequest(bookingId, 3, "Second attempt");
        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("already exists")));
    }

    @Test
    void createReview_невалидныйРейтинг_возвращает400() throws Exception {
        ReviewRequest req = buildReviewRequest(bookingId, 10, "Bad rating"); // max is 5

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ==================== DELETE /api/reviews/{id} ====================

    @Test
    void deleteReview_admin_успешноеУдаление_возвращает204() throws Exception {
        Long reviewId = createReview(clientToken, bookingId, 4, "Nice place");

        mockMvc.perform(delete("/api/reviews/" + reviewId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // После удаления список отзывов пуст
        mockMvc.perform(get("/api/apartments/" + apartmentId + "/reviews"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deleteReview_клиент_возвращает403() throws Exception {
        Long reviewId = createReview(clientToken, bookingId, 5, "My review");

        // @PreAuthorize("hasRole('ADMIN')") — CLIENT не пройдёт
        mockMvc.perform(delete("/api/reviews/" + reviewId)
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isForbidden());
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

    /** ADMIN нельзя создать через API — создаём напрямую в БД и получаем токен через /login. */
    private String createAdminAndGetToken() throws Exception {
        User admin = User.builder()
                .email("admin@test.com")
                .password(passwordEncoder.encode("password123"))
                .firstName("Admin")
                .lastName("System")
                .role(Role.ADMIN)
                .enabled(true)
                .build();
        userRepository.save(admin);

        LoginRequest req = new LoginRequest();
        req.setEmail("admin@test.com");
        req.setPassword("password123");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private Long createApartment(String token) throws Exception {
        ApartmentRequest req = new ApartmentRequest();
        req.setName("Review Test Apartment");
        req.setCity("Moscow");
        req.setStreet("Arbat");
        req.setHouseNumber("5");
        req.setPricePerNight(new BigDecimal("1500.00"));
        req.setMaxGuests(3);

        MvcResult result = mockMvc.perform(post("/api/apartments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }

    /**
     * Создаёт бронирование с будущими датами, затем напрямую меняет статус на COMPLETED.
     * Это обходит ограничение @Future валидации в BookingRequest
     * и имитирует завершение брони (что в продакшне делает планировщик).
     */
    private Long createAndCompleteBooking(String token, Long aptId) throws Exception {
        BookingRequest req = new BookingRequest();
        req.setApartmentId(aptId);
        req.setStartDate(LocalDate.now().plusDays(1));
        req.setEndDate(LocalDate.now().plusDays(4));
        req.setGuestsCount(2);

        MvcResult result = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        Long id = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();

        // Принудительно завершаем бронь в обход планировщика
        Booking booking = bookingRepository.findById(id).orElseThrow();
        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(booking);

        return id;
    }

    private Long createReview(String token, Long bookingId, int rating, String comment) throws Exception {
        ReviewRequest req = buildReviewRequest(bookingId, rating, comment);

        MvcResult result = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }

    private ReviewRequest buildReviewRequest(Long bookingId, int rating, String comment) {
        ReviewRequest req = new ReviewRequest();
        req.setBookingId(bookingId);
        req.setRating(rating);
        req.setComment(comment);
        return req;
    }
}
