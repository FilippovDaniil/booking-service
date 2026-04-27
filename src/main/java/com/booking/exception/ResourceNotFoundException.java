package com.booking.exception;

/**
 * Исключение «ресурс не найден» → HTTP 404 Not Found.
 * Бросается из сервисов при обращении к несуществующей записи в БД:
 *   userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found: " + id))
 *
 * Перехватывается в GlobalExceptionHandler.handleNotFound().
 *
 * Extends RuntimeException (unchecked) — не нужно объявлять throws в сигнатуре метода.
 * В Spring это стандартный подход для бизнес-исключений.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message); // передаём сообщение родительскому RuntimeException
    }
}
