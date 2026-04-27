package com.booking.dto.request;

import com.booking.entity.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO (Data Transfer Object) запроса регистрации.
 *
 * DTO — объект для передачи данных между слоями приложения.
 * Принцип: контроллер получает DTO из HTTP-запроса, сервис работает с Entity.
 * Это разделение позволяет не выставлять внутренние поля Entity наружу
 * и добавлять валидацию на входные данные.
 *
 * @Data (Lombok) = @Getter + @Setter + @ToString + @EqualsAndHashCode + @RequiredArgsConstructor
 *
 * Аннотации валидации (Bean Validation / Jakarta):
 *   @NotBlank  — строка не null, не пустая, не только пробелы
 *   @Email     — строка соответствует формату email
 *   @Size      — ограничение длины строки
 *   @NotNull   — значение не null (для объектов, не строк)
 *
 * Срабатывают при наличии @Valid в контроллере (@Valid @RequestBody RegisterRequest request).
 * При нарушении — GlobalExceptionHandler.handleValidation() возвращает 400.
 */
@Data
public class RegisterRequest {

    @NotBlank
    @Email  // проверяет формат: должна быть @, домен, точка
    private String email;

    @NotBlank
    @Size(min = 6) // минимальная длина пароля 6 символов
    private String password;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotNull  // роль обязательна, но не @NotBlank (это не строка, а enum)
    private Role role; // CLIENT или LANDLORD (ADMIN регистрироваться не может)
}
