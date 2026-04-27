package com.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO запроса обновления профиля пользователя.
 * Используется в PUT /api/users/me.
 *
 * Намеренно содержит только имя и фамилию — email и роль менять через профиль нельзя.
 * Это защита от случайного или злонамеренного изменения критичных данных.
 * Для смены email нужен отдельный endpoint с подтверждением (в данном проекте не реализован).
 */
@Data
public class UpdateProfileRequest {

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;
}
