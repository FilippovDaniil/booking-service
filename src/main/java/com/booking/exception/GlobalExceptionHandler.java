package com.booking.exception;

import com.booking.dto.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Импорт Spring Security AccessDeniedException (не путать с com.booking.exception.AccessDeniedException)
// Когда @PreAuthorize("hasRole(...)") падает — Spring Security бросает этот класс.
// Без явного обработчика он проваливается в handleGeneral() и возвращает 500.

import java.util.stream.Collectors;

/**
 * Централизованная обработка исключений для всех REST-контроллеров.
 * @RestControllerAdvice перехватывает исключения, брошенные из любого @RestController,
 * и преобразует их в структурированный JSON-ответ с нужным HTTP-кодом.
 *
 * Без этого класса Spring вернул бы стандартную HTML-страницу ошибки.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** 404 Not Found — ресурс не найден в БД. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        log.error("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, ex.getMessage()));
    }

    /** 409 Conflict — квартира уже занята на запрошенные даты. */
    @ExceptionHandler(BookingConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(BookingConflictException ex) {
        log.error("Booking conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(409, ex.getMessage()));
    }

    /** 403 Forbidden — пользователь пытается выполнить операцию с чужим ресурсом. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.error("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(403, ex.getMessage()));
    }

    /**
     * 403 Forbidden — @PreAuthorize провалился (роль не подходит).
     * Spring Security бросает свой AccessDeniedException, который без этого обработчика
     * проваливается в handleGeneral() и возвращает 500.
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleSpringAccessDenied(
            org.springframework.security.access.AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(403, "Access denied"));
    }

    /** 400 Bad Request — нарушение бизнес-правил (нельзя забронировать свою квартиру и т.п.). */
    @ExceptionHandler(InvalidOperationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOp(InvalidOperationException ex) {
        log.error("Invalid operation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(400, ex.getMessage()));
    }

    /** 401 Unauthorized — неверный email или пароль при логине. */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(401, "Invalid email or password"));
    }

    /** 403 Forbidden — попытка войти с заблокированным аккаунтом. */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(DisabledException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(403, "User account is blocked"));
    }

    /**
     * 400 Bad Request — ошибки валидации @Valid на DTO.
     * Собираем все сообщения о полях в одну строку через "; ".
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(400, "Validation failed: " + message));
    }

    /** 500 Internal Server Error — любое непредвиденное исключение. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "Internal server error"));
    }
}
