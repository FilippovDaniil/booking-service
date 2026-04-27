package com.booking.controller;

import com.booking.dto.request.ApartmentRequest;
import com.booking.dto.response.ApartmentResponse;
import com.booking.service.ApartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Контроллер управления квартирами.
 *
 * Матрица доступа:
 *   GET /api/apartments        — публичный (поиск без токена)
 *   GET /api/apartments/{id}   — публичный
 *   GET /api/apartments/my     — только LANDLORD
 *   POST /api/apartments       — только LANDLORD
 *   PUT /api/apartments/{id}   — только LANDLORD (своей квартиры)
 *   DELETE /api/apartments/{id} — LANDLORD (своей) или ADMIN (любой)
 *
 * Публичные GET-запросы настроены в SecurityConfig:
 *   .requestMatchers(HttpMethod.GET, "/api/apartments/**").permitAll()
 *
 * @PreAuthorize — проверка роли на уровне метода (метод-level security).
 * Включается через @EnableMethodSecurity в SecurityConfig.
 * @SecurityRequirement — показывает замочек в Swagger UI (только визуально).
 */
@RestController
@RequestMapping("/api/apartments")
@RequiredArgsConstructor
@Tag(name = "Apartments")
public class ApartmentController {

    private final ApartmentService apartmentService;

    /**
     * GET /api/apartments?city=...&startDate=...&endDate=...&guests=...&minPrice=...&maxPrice=...
     * Поиск доступных квартир с пагинацией.
     *
     * Pageable — Spring автоматически читает из параметров запроса:
     *   ?page=0&size=10&sort=pricePerNight,asc
     *
     * @DateTimeFormat(iso = DATE) — указывает Spring как парсить дату из строки "2026-06-01".
     *
     * Page<T> — объект с данными страницы: content, totalElements, totalPages, number.
     */
    @GetMapping
    @Operation(summary = "Search available apartments")
    public ResponseEntity<Page<ApartmentResponse>> search(
            @RequestParam String city,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam int guests,
            @RequestParam(required = false) BigDecimal minPrice, // необязательный фильтр
            @RequestParam(required = false) BigDecimal maxPrice, // необязательный фильтр
            Pageable pageable) {
        return ResponseEntity.ok(
                apartmentService.search(city, startDate, endDate, guests, minPrice, maxPrice, pageable));
    }

    /**
     * GET /api/apartments/{id}
     * Получить детали квартиры по ID. Публичный.
     *
     * @PathVariable — извлекает {id} из URL-пути.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get apartment details")
    public ResponseEntity<ApartmentResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(apartmentService.getById(id));
    }

    /**
     * GET /api/apartments/my
     * Возвращает все квартиры текущего арендодателя (включая неактивные).
     * Требует роль LANDLORD.
     */
    @GetMapping("/my")
    @PreAuthorize("hasRole('LANDLORD')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get my apartments (LANDLORD)")
    public ResponseEntity<List<ApartmentResponse>> getMyApartments() {
        return ResponseEntity.ok(apartmentService.getMyApartments());
    }

    /**
     * POST /api/apartments
     * Создаёт новую квартиру. Владелец = текущий пользователь (из SecurityContext).
     */
    @PostMapping
    @PreAuthorize("hasRole('LANDLORD')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create apartment (LANDLORD)")
    public ResponseEntity<ApartmentResponse> create(@Valid @RequestBody ApartmentRequest request) {
        return ResponseEntity.ok(apartmentService.create(request));
    }

    /**
     * PUT /api/apartments/{id}
     * Обновляет квартиру. Сервис проверяет, что текущий пользователь — её владелец.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('LANDLORD')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update apartment (LANDLORD)")
    public ResponseEntity<ApartmentResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody ApartmentRequest request) {
        return ResponseEntity.ok(apartmentService.update(id, request));
    }

    /**
     * DELETE /api/apartments/{id}
     * Мягкое удаление (active = false). LANDLORD может удалить свою, ADMIN — любую.
     * Возвращает 204 No Content при успехе.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('LANDLORD') or hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Deactivate apartment (LANDLORD/ADMIN)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        apartmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
