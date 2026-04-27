package com.booking.exception;

/**
 * Исключение «недопустимая операция» → HTTP 400 Bad Request.
 *
 * Используется для нарушений бизнес-правил, которые не являются ошибкой валидации поля:
 *  - Попытка зарегистрироваться с ролью ADMIN
 *  - Email уже занят
 *  - Бронирование своей квартиры
 *  - Подтверждение уже подтверждённой брони
 *  - Отмена завершённой/истёкшей брони
 *  - Оставить отзыв без завершённой брони
 *
 * Отличие от ошибок валидации (@Valid): те возникают до входа в сервис,
 * а InvalidOperationException — уже внутри бизнес-логики сервиса.
 *
 * Перехватывается в GlobalExceptionHandler.handleInvalidOp().
 */
public class InvalidOperationException extends RuntimeException {
    public InvalidOperationException(String message) {
        super(message);
    }
}
