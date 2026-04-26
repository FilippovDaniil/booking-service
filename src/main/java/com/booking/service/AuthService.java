package com.booking.service;

import com.booking.dto.request.LoginRequest;
import com.booking.dto.request.RegisterRequest;
import com.booking.dto.response.TokenResponse;
import com.booking.entity.User;
import com.booking.entity.enums.Role;
import com.booking.exception.InvalidOperationException;
import com.booking.exception.ResourceNotFoundException;
import com.booking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new InvalidOperationException("Email already in use");
        }
        if (request.getRole() == Role.ADMIN) {
            throw new InvalidOperationException("Cannot register as ADMIN");
        }
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(request.getRole())
                .enabled(true)
                .build();
        userRepository.save(user);
        return createTokens(user);
    }

    public TokenResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return createTokens(user);
    }

    public TokenResponse refresh(String refreshToken) {
        Long userId = tokenService.getUserIdByRefreshToken(refreshToken);
        if (userId == null) {
            throw new InvalidOperationException("Invalid or expired refresh token");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String newAccessToken = tokenService.generateAccessToken(user);
        return new TokenResponse(newAccessToken, refreshToken);
    }

    public void logout(String refreshToken) {
        tokenService.deleteRefreshToken(refreshToken);
    }

    private TokenResponse createTokens(User user) {
        String accessToken = tokenService.generateAccessToken(user);
        String refreshToken = tokenService.generateAndSaveRefreshToken(user);
        return new TokenResponse(accessToken, refreshToken);
    }
}
