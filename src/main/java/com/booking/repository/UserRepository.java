package com.booking.repository;

import com.booking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Репозиторий пользователей.
 *
 * JpaRepository<User, Long> предоставляет стандартные CRUD-операции:
 *   save(user), findById(id), findAll(), delete(user), count() и т.д.
 *
 * Spring Data JPA автоматически создаёт реализацию по имени метода:
 *   findByEmail("user@test.com") → SELECT * FROM users WHERE email = ?
 *   existsByEmail("user@test.com") → SELECT COUNT(*) > 0 FROM users WHERE email = ?
 *
 * Никакого кода реализации писать не нужно — Spring генерирует SQL сам.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** Поиск пользователя по email — используется при логине и в JwtAuthenticationFilter. */
    Optional<User> findByEmail(String email);

    /** Проверка уникальности email при регистрации. */
    boolean existsByEmail(String email);
}
