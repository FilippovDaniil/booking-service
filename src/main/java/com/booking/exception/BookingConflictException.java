package com.booking.exception;

/**
 * Исключение «конфликт бронирования» → HTTP 409 Conflict.
 * Бросается из BookingService.create() когда квартира уже занята на запрошенные даты.
 *
 * HTTP 409 (Conflict) выбран намеренно — это не ошибка пользователя (400),
 * а конфликт состояния ресурса: кто-то другой уже забронировал эти даты.
 *
 * Перехватывается в GlobalExceptionHandler.handleConflict().
 */
public class BookingConflictException extends RuntimeException {
    public BookingConflictException(String message) {
        super(message);
    }
}
