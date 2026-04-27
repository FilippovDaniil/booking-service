package com.booking.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * DTO запроса создания отзыва.
 * Используется в POST /api/reviews.
 *
 * Клиент (author) не передаётся — берётся из SecurityContext.
 * Квартира определяется через booking.apartment — отзыв привязан к брони, не к квартире напрямую.
 * Это гарантирует: один отзыв = одна завершённая бронь = один клиент.
 */
@Data
public class ReviewRequest {

    @NotNull
    private Long bookingId; // бронь, к которой оставляется отзыв

    @Min(1) // минимальная оценка 1
    @Max(5) // максимальная оценка 5
    private int rating;

    @NotBlank
    private String comment; // текст отзыва обязателен
}
