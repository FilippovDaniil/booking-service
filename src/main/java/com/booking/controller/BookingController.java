package com.booking.controller;

import com.booking.dto.request.BookingRequest;
import com.booking.dto.response.BookingResponse;
import com.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Контроллер бронирований.
 * Все эндпоинты требуют аутентификации (JWT).
 *
 * Жизненный цикл брони через API:
 *   POST /api/bookings                   → создать (CLIENT) → PENDING
 *   POST /api/bookings/{id}/confirm      → подтвердить (CLIENT) → CONFIRMED
 *   POST /api/bookings/{id}/cancel       → отменить клиентом → CANCELLED_BY_CLIENT
 *   POST /api/bookings/{id}/cancel-by-landlord → отменить арендодателем → CANCELLED_BY_LANDLORD
 *
 * Переходы PENDING→EXPIRED и CONFIRMED→COMPLETED происходят автоматически
 * через BookingExpirationScheduler (без HTTP-запроса).
 *
 * @SecurityRequirement(name = "bearerAuth") — показывает замочек в Swagger UI для всего контроллера.
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {

    private final BookingService bookingService;

    /**
     * POST /api/bookings
     * Создаёт бронь со статусом PENDING.
     * Клиент должен подтвердить в течение 15 минут, иначе бронь истечёт.
     *
     * Доступно всем аутентифицированным пользователям — сервис сам проверяет,
     * что клиент не является владельцем квартиры.
     */
    @PostMapping
    @Operation(summary = "Create booking (CLIENT)")
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody BookingRequest request) {
        return ResponseEntity.ok(bookingService.create(request));
    }

    /**
     * GET /api/bookings
     * Возвращает список броней с учётом роли:
     *   CLIENT   → только свои брони
     *   LANDLORD → брони на его квартиры
     *   ADMIN    → все брони системы
     *
     * Один эндпоинт для всех ролей — логика определения набора данных в сервисе.
     */
    @GetMapping
    @Operation(summary = "Get bookings (role-based: CLIENT=own, LANDLORD=on his apartments, ADMIN=all)")
    public ResponseEntity<List<BookingResponse>> getMyBookings() {
        return ResponseEntity.ok(bookingService.getMyBookings());
    }

    /**
     * GET /api/bookings/{id}
     * Детали конкретной брони.
     * Доступна клиенту, арендодателю квартиры и администратору.
     * Остальные получат 403.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get booking details")
    public ResponseEntity<BookingResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getById(id));
    }

    /**
     * POST /api/bookings/{id}/confirm
     * Клиент подтверждает бронь (PENDING → CONFIRMED).
     * Только владелец брони, только статус PENDING.
     */
    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm booking (CLIENT, PENDING → CONFIRMED)")
    public ResponseEntity<BookingResponse> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.confirm(id));
    }

    /**
     * POST /api/bookings/{id}/cancel
     * Клиент отменяет бронь (PENDING/CONFIRMED → CANCELLED_BY_CLIENT).
     * Нельзя отменить начавшуюся бронь.
     */
    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel booking by client")
    public ResponseEntity<BookingResponse> cancelByClient(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.cancelByClient(id));
    }

    /**
     * POST /api/bookings/{id}/cancel-by-landlord
     * Арендодатель отменяет бронь (PENDING/CONFIRMED → CANCELLED_BY_LANDLORD).
     *
     * @PreAuthorize("hasRole('LANDLORD')") — только пользователи с ролью LANDLORD.
     * Сервис дополнительно проверяет, что это именно владелец данной квартиры.
     */
    @PostMapping("/{id}/cancel-by-landlord")
    @PreAuthorize("hasRole('LANDLORD')")
    @Operation(summary = "Cancel booking by landlord")
    public ResponseEntity<BookingResponse> cancelByLandlord(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.cancelByLandlord(id));
    }
}
