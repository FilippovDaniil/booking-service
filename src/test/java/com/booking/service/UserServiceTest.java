package com.booking.service;

import com.booking.dto.request.UpdateProfileRequest;
import com.booking.dto.response.UserResponse;
import com.booking.entity.User;
import com.booking.entity.enums.Role;
import com.booking.exception.InvalidOperationException;
import com.booking.exception.ResourceNotFoundException;
import com.booking.repository.UserRepository;
import com.booking.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ===== Unit-тесты UserService =====
 *
 * Что проверяем:
 *   - getCurrentUser: возвращает данные из SecurityContext (через SecurityUtils)
 *   - updateProfile: меняет firstName/lastName и сохраняет через репозиторий
 *   - getAllUsers: возвращает список из репозитория
 *   - blockUser: устанавливает enabled = false, инвалидирует токены;
 *                нельзя заблокировать ADMIN
 *   - deleteUser: удаляет из БД; нельзя удалить ADMIN, 404 если не найден
 *
 * Зависимости, не нужные в тесте:
 *   UserRepository, SecurityUtils, TokenService — все замокированы через @Mock.
 *   UserService создаётся через @InjectMocks — получает моки через конструктор.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private SecurityUtils securityUtils;
    @Mock private TokenService tokenService;

    @InjectMocks
    private UserService userService;

    private User client;
    private User admin;

    @BeforeEach
    void setUp() {
        client = User.builder()
                .id(1L).email("client@test.com")
                .firstName("Ivan").lastName("Petrov")
                .role(Role.CLIENT).enabled(true).build();
        admin = User.builder()
                .id(99L).email("admin@test.com")
                .firstName("Admin").lastName("User")
                .role(Role.ADMIN).enabled(true).build();
    }

    // ==================== getCurrentUser ====================

    @Test
    void getCurrentUser_возвращаетТекущегоПользователя() {
        when(securityUtils.getCurrentUser()).thenReturn(client);

        UserResponse response = userService.getCurrentUser();

        assertThat(response.getEmail()).isEqualTo("client@test.com");
        assertThat(response.getFirstName()).isEqualTo("Ivan");
        assertThat(response.getRole()).isEqualTo(Role.CLIENT);
    }

    // ==================== updateProfile ====================

    @Test
    void updateProfile_успешноеОбновление() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFirstName("Новое");
        request.setLastName("Имя");

        when(securityUtils.getCurrentUser()).thenReturn(client);
        when(userRepository.save(client)).thenReturn(client);

        UserResponse response = userService.updateProfile(request);

        assertThat(client.getFirstName()).isEqualTo("Новое");
        assertThat(client.getLastName()).isEqualTo("Имя");
        verify(userRepository).save(client);
    }

    // ==================== getAllUsers ====================

    @Test
    void getAllUsers_возвращаетВсехПользователей() {
        when(userRepository.findAll()).thenReturn(List.of(client, admin));

        List<UserResponse> users = userService.getAllUsers();

        assertThat(users).hasSize(2);
        assertThat(users).extracting(UserResponse::getEmail)
                .containsExactly("client@test.com", "admin@test.com");
    }

    @Test
    void getAllUsers_пустаяБД_возвращаетПустойСписок() {
        when(userRepository.findAll()).thenReturn(List.of());

        assertThat(userService.getAllUsers()).isEmpty();
    }

    // ==================== blockUser ====================

    @Test
    void blockUser_успешнаяБлокировка() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(client));

        userService.blockUser(1L);

        assertThat(client.isEnabled()).isFalse();
        verify(userRepository).save(client);
        verify(tokenService).deleteAllRefreshTokensForUser(1L);
    }

    @Test
    void blockUser_администратора_бросаетException() {
        when(userRepository.findById(99L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.blockUser(99L))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Cannot block an admin");

        verify(userRepository, never()).save(any());
    }

    @Test
    void blockUser_несуществующийПользователь_бросаетResourceNotFoundException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.blockUser(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ==================== deleteUser ====================

    @Test
    void deleteUser_успешноеУдаление() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(client));

        userService.deleteUser(1L);

        verify(userRepository).delete(client);
    }

    @Test
    void deleteUser_администратора_бросаетException() {
        when(userRepository.findById(99L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.deleteUser(99L))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Cannot delete an admin");

        verify(userRepository, never()).delete(any());
    }

    @Test
    void deleteUser_несуществующийПользователь_бросаетResourceNotFoundException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
