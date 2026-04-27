package com.booking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Фильтр аутентификации на основе JWT.
 * Выполняется ровно один раз на каждый HTTP-запрос (OncePerRequestFilter).
 *
 * Логика:
 * 1. Достаём токен из заголовка Authorization: Bearer <token>
 * 2. Если токен валиден — загружаем пользователя и кладём Authentication в SecurityContext
 * 3. Если токена нет или он невалиден — пропускаем запрос дальше без аутентификации
 *    (Spring Security сам вернёт 401, если endpoint требует авторизации)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null && tokenProvider.validateToken(token)) {
            // Получаем email из токена и загружаем полные данные пользователя из БД
            String email = tokenProvider.getEmailFromToken(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            // Создаём объект аутентификации (credentials = null, т.к. пароль уже не нужен)
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // Помещаем аутентификацию в контекст — теперь SecurityUtils.getCurrentUser() сработает
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        // Передаём запрос следующему фильтру в цепочке
        filterChain.doFilter(request, response);
    }

    /** Извлекает токен из заголовка Authorization, убирая префикс "Bearer ". */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7); // 7 = длина "Bearer "
        }
        return null;
    }
}
