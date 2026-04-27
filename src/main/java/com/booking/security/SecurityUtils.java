package com.booking.security;

import com.booking.entity.User;
import com.booking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Вспомогательный компонент для получения текущего аутентифицированного пользователя.
 * Используется в сервисах, которым нужно знать «кто вызвал метод».
 *
 * SecurityContextHolder хранит Authentication в ThreadLocal —
 * данные актуальны только внутри одного потока HTTP-запроса.
 */
@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final UserRepository userRepository;

    /**
     * Возвращает сущность User текущего пользователя из БД.
     * Principal в SecurityContext — это UserDetails (email = username).
     * Делаем дополнительный запрос в БД, чтобы получить полную сущность с id, role и т.д.
     */
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("No authenticated user");
        }
        // Principal — объект UserDetails, у которого getUsername() возвращает email
        String email = ((UserDetails) auth.getPrincipal()).getUsername();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
