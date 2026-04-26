package com.booking.service;

import com.booking.dto.request.UpdateProfileRequest;
import com.booking.dto.response.UserResponse;
import com.booking.entity.User;
import com.booking.entity.enums.Role;
import com.booking.exception.InvalidOperationException;
import com.booking.exception.ResourceNotFoundException;
import com.booking.repository.UserRepository;
import com.booking.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;
    private final TokenService tokenService;

    public UserResponse getCurrentUser() {
        return UserResponse.from(securityUtils.getCurrentUser());
    }

    @Transactional
    public UserResponse updateProfile(UpdateProfileRequest request) {
        User user = securityUtils.getCurrentUser();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        return UserResponse.from(userRepository.save(user));
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void blockUser(Long id) {
        User user = getUser(id);
        if (user.getRole() == Role.ADMIN) {
            throw new InvalidOperationException("Cannot block an admin");
        }
        user.setEnabled(false);
        userRepository.save(user);
        tokenService.deleteAllRefreshTokensForUser(id);
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = getUser(id);
        if (user.getRole() == Role.ADMIN) {
            throw new InvalidOperationException("Cannot delete an admin");
        }
        userRepository.delete(user);
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }
}
