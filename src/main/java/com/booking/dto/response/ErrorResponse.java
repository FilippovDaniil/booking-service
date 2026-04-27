package com.booking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO ответа при ошибке.
 * Возвращается GlobalExceptionHandler при любом исключении.
 *
 * Стандартная структура ошибки позволяет клиенту (фронтенд, мобильное приложение)
 * единообразно обрабатывать все ошибки:
 *   status    — HTTP-код ошибки (400, 403, 404, 409, 500 и т.д.)
 *   message   — читаемое описание проблемы
 *   timestamp — время возникновения ошибки (полезно для отладки и логов)
 *
 * Пример JSON-ответа:
 * {
 *   "status": 404,
 *   "message": "Apartment not found: 42",
 *   "timestamp": "2026-04-27T19:30:00"
 * }
 */
@Data
@AllArgsConstructor
public class ErrorResponse {
    private int status;
    private String message;
    private LocalDateTime timestamp;

    // Удобный конструктор: timestamp проставляется автоматически
    public ErrorResponse(int status, String message) {
        this.status = status;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
}
