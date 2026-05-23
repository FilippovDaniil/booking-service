package com.booking.controller;

import com.booking.dto.request.ReviewRequest;
import com.booking.dto.response.ReviewResponse;
import com.booking.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

import java.util.List;

/**
 * Контроллер отзывов.
 *
 * Особенность: эндпоинты расположены на ДВУХ базовых путях:
 *   /api/reviews            — создание и удаление отзывов
 *   /api/apartments/{id}/reviews — получение отзывов квартиры
 *
 * Поэтому @RequestMapping на уровне класса не используется,
 * а полные пути прописаны в каждом @PostMapping / @GetMapping / @DeleteMapping.
 *
 * Матрица доступа:
 *   POST /api/reviews                          — только CLIENT (только после COMPLETED брони)
 *   GET /api/apartments/{apartmentId}/reviews  — публичный (без токена)
 *   DELETE /api/reviews/{id}                   — только ADMIN
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Reviews")
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * POST /api/reviews
     * Создаёт отзыв к завершённой брони.
     * Условия (проверяются в сервисе): бронь COMPLETED, отзыва ещё нет, текущий = клиент.
     */
    @PostMapping("/api/reviews")
    @PreAuthorize("hasRole('CLIENT')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create review (CLIENT, only after COMPLETED booking)")
    public ResponseEntity<ReviewResponse> create(@Valid @RequestBody ReviewRequest request) {
        ReviewResponse created = reviewService.create(request);
        // 201 Created + Location: /api/reviews/{id}
        return ResponseEntity.created(URI.create("/api/reviews/" + created.getId())).body(created);
    }

    /**
     * GET /api/apartments/{apartmentId}/reviews
     * Список всех отзывов к квартире. Публичный — без токена.
     * URL намеренно вложен в /api/apartments — семантически отзывы принадлежат квартире.
     */
    @GetMapping("/api/apartments/{apartmentId}/reviews")
    @Operation(summary = "Get reviews for apartment")
    public ResponseEntity<List<ReviewResponse>> getByApartment(@PathVariable Long apartmentId) {
        return ResponseEntity.ok(reviewService.getByApartment(apartmentId));
    }

    /**
     * DELETE /api/reviews/{id}
     * Удаляет отзыв. Только ADMIN может удалять чужие отзывы (модерация).
     * После удаления автоматически пересчитывается рейтинг квартиры.
     */
    @DeleteMapping("/api/reviews/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete review (ADMIN)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        reviewService.deleteReview(id);
        return ResponseEntity.noContent().build();
    }
}
