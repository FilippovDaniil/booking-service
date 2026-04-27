package com.booking.controller;

import com.booking.dto.request.UpdateProfileRequest;
import com.booking.dto.response.UserResponse;
import com.booking.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Контроллер управления пользователями.
 * Все эндпоинты требуют аутентификации (JWT) — указано на уровне класса
 * через @SecurityRequirement и через SecurityConfig (.anyRequest().authenticated()).
 *
 * Матрица доступа:
 *   GET  /api/users/me       — любой аутентифицированный пользователь (свой профиль)
 *   PUT  /api/users/me       — любой аутентифицированный (обновить своё имя/фамилию)
 *   GET  /api/users          — только ADMIN (список всех пользователей)
 *   PUT  /api/users/{id}/block  — только ADMIN
 *   DELETE /api/users/{id}   — только ADMIN
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users")
@SecurityRequirement(name = "bearerAuth") // все эндпоинты требуют JWT (для Swagger UI)
public class UserController {

    private final UserService userService;

    /**
     * GET /api/users/me
     * Возвращает профиль текущего аутентифицированного пользователя.
     * Текущий пользователь определяется из JWT (через SecurityUtils).
     */
    @GetMapping("/me")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<UserResponse> getMe() {
        return ResponseEntity.ok(userService.getCurrentUser());
    }

    /**
     * PUT /api/users/me
     * Обновляет имя и фамилию текущего пользователя.
     * Email и роль изменить через этот эндпоинт нельзя.
     */
    @PutMapping("/me")
    @Operation(summary = "Update current user profile")
    public ResponseEntity<UserResponse> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(request));
    }

    /**
     * GET /api/users
     * Список всех пользователей системы. Только ADMIN.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all users (ADMIN)")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /**
     * PUT /api/users/{id}/block
     * Блокирует пользователя (enabled = false) и инвалидирует все его refresh-токены.
     * Заблокированный пользователь не сможет войти — CustomUserDetailsService
     * вернёт DisabledException при попытке логина.
     * Нельзя заблокировать другого администратора.
     */
    @PutMapping("/{id}/block")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Block user (ADMIN)")
    public ResponseEntity<Void> blockUser(@PathVariable Long id) {
        userService.blockUser(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/users/{id}
     * Полностью удаляет пользователя из БД. Только ADMIN.
     * Нельзя удалить администратора.
     * Каскадное удаление зависит от настроек JPA — связанные данные могут остаться.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete user (ADMIN)")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
