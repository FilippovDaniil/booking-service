package com.booking.exception;

/**
 * Бизнес-исключение «доступ запрещён» → HTTP 403 Forbidden.
 *
 * ВАЖНО: это НЕ стандартный Spring Security AccessDeniedException.
 * Полное имя класса: com.booking.exception.AccessDeniedException.
 *
 * Когда используется:
 *  - Клиент пытается подтвердить/отменить ЧУЖУЮ бронь
 *  - Арендодатель пытается изменить ЧУЖУЮ квартиру
 *  - Пользователь обращается к брони, к которой не имеет отношения
 *
 * Spring Security имеет свой org.springframework.security.access.AccessDeniedException —
 * он бросается при провале @PreAuthorize. Для него в GlobalExceptionHandler
 * есть отдельный обработчик handleSpringAccessDenied().
 *
 * Перехватывается в GlobalExceptionHandler.handleAccessDenied().
 */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
