package com.booking.security;

import com.booking.entity.User;
import com.booking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Реализация UserDetailsService для Spring Security.
 * Spring Security вызывает loadUserByUsername при аутентификации
 * (в AuthenticationManager.authenticate) и в JwtAuthenticationFilter.
 *
 * «Username» в контексте Spring Security — это email пользователя.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        // Возвращаем стандартный UserDetails от Spring Security.
        // enabled = user.isEnabled() — заблокированные пользователи получат DisabledException при логине.
        // Роль оборачивается в "ROLE_CLIENT" / "ROLE_LANDLORD" / "ROLE_ADMIN" —
        // именно такой формат требует @PreAuthorize("hasRole('CLIENT')")
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                user.isEnabled(),          // если false — аккаунт заблокирован
                true,                      // accountNonExpired
                true,                      // credentialsNonExpired
                true,                      // accountNonLocked
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }
}
