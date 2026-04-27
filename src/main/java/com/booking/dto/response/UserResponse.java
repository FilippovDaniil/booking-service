package com.booking.dto.response;

import com.booking.entity.User;
import com.booking.entity.enums.Role;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO ответа с данными пользователя.
 *
 * Намеренно НЕ содержит поле password — никогда не отдаём хэш пароля клиенту.
 * Это пример принципа «минимального раскрытия данных» (principle of least privilege).
 *
 * Статический фабричный метод from(User) — стандартный паттерн маппинга Entity → DTO.
 * Альтернативы: MapStruct, ModelMapper — но для учебного проекта ручной маппинг нагляднее.
 */
@Data
public class UserResponse {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private Role role;
    private boolean enabled;       // false = аккаунт заблокирован
    private LocalDateTime createdAt;

    /**
     * Преобразует Entity User в DTO UserResponse.
     * Вызывается из сервисов: UserResponse.from(user).
     */
    public static UserResponse from(User user) {
        UserResponse r = new UserResponse();
        r.setId(user.getId());
        r.setEmail(user.getEmail());
        r.setFirstName(user.getFirstName());
        r.setLastName(user.getLastName());
        r.setRole(user.getRole());
        r.setEnabled(user.isEnabled());
        r.setCreatedAt(user.getCreatedAt());
        return r;
    }
}
