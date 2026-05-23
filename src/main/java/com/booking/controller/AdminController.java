package com.booking.controller;

import com.booking.dto.request.ChangeStatusRequest;
import com.booking.dto.response.BookingResponse;
import com.booking.entity.Booking;
import com.booking.exception.ResourceNotFoundException;
import com.booking.repository.ApartmentRepository;
import com.booking.repository.BookingRepository;
import com.booking.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Контроллер административных операций.
 * Все методы требуют роль ADMIN — задано на уровне класса через @PreAuthorize.
 *
 * Особенность архитектуры: этот контроллер работает напрямую с репозиториями,
 * минуя сервисный слой. Это допустимо для простых административных операций,
 * не требующих сложной бизнес-логики (например, принудительная смена статуса).
 * В более строгой архитектуре лучше завести AdminService.
 *
 * @PreAuthorize на уровне класса = все методы требуют hasRole('ADMIN').
 * Это эквивалентно навешиванию @PreAuthorize на каждый метод отдельно.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')") // применяется ко ВСЕМ методам этого контроллера
@RequiredArgsConstructor
@Tag(name = "Admin")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    // Прямые репозитории для сбора статистики — не нужно создавать сервисы ради count()
    private final UserRepository userRepository;
    private final ApartmentRepository apartmentRepository;
    private final BookingRepository bookingRepository;

    /**
     * GET /api/admin/stats
     * Возвращает общую статистику системы: количество пользователей, квартир и броней.
     *
     * Map.of() — неизменяемый Map, создаётся на месте.
     * Jackson сериализует Map<String, Long> в JSON-объект: { "totalUsers": 42, ... }
     */
    @GetMapping("/stats")
    @Operation(summary = "Get system statistics")
    public ResponseEntity<Map<String, Long>> getStats() {
        return ResponseEntity.ok(Map.of(
                "totalUsers",      userRepository.count(),      // SELECT COUNT(*) FROM users
                "totalApartments", apartmentRepository.count(), // SELECT COUNT(*) FROM apartments
                "totalBookings",   bookingRepository.count()    // SELECT COUNT(*) FROM bookings
        ));
    }

    /**
     * PATCH /api/admin/bookings/{id}/status
     * Принудительно меняет статус брони. Только для администраторов.
     *
     * Тело запроса: { "status": "COMPLETED" }
     * PATCH — частичное обновление ресурса (только статус), тело содержит изменяемые поля.
     * Используется в экстренных ситуациях: исправить застрявшую бронь, обойти бизнес-логику.
     */
    @PatchMapping("/bookings/{id}/status")
    @Operation(summary = "Force-change booking status")
    public ResponseEntity<BookingResponse> changeBookingStatus(
            @PathVariable Long id, @Valid @RequestBody ChangeStatusRequest request) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        booking.setStatus(request.getStatus());
        return ResponseEntity.ok(BookingResponse.from(bookingRepository.save(booking)));
    }
}
