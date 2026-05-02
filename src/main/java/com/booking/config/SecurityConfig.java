package com.booking.config;

import com.booking.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

/**
 * Конфигурация Spring Security.
 *
 * Ключевые решения:
 * - CSRF отключён: приложение stateless (JWT), CSRF-атаки неприменимы.
 * - Сессии отключены: каждый запрос аутентифицируется по JWT, без серверных сессий.
 * - @EnableMethodSecurity включает @PreAuthorize на методах контроллеров/сервисов.
 * - JwtAuthenticationFilter добавляется ПЕРЕД UsernamePasswordAuthenticationFilter.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // включает поддержку @PreAuthorize, @PostAuthorize и т.д.
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)  // stateless API — CSRF не нужен
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()           // регистрация и логин — без токена
                .requestMatchers(HttpMethod.GET, "/api/apartments/**").permitAll() // поиск квартир — публично
                .requestMatchers("/swagger-ui/**", "/api-docs/**", "/swagger-ui.html").permitAll() // документация
                .requestMatchers("/frontend/**").permitAll()           // статические HTML-страницы
                .anyRequest().authenticated()                          // всё остальное требует JWT
            )
            // Наш фильтр должен запустится ДО стандартного фильтра логина,
            // чтобы заполнить SecurityContext из JWT до проверки авторизации
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Настройка CORS: разрешаем запросы с любого origin.
     * В продакшне лучше указать конкретные домены фронтенда.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Collections.singletonList("*"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(Collections.singletonList("*"));
        config.setExposedHeaders(Collections.singletonList("*"));
        config.setMaxAge(3600L); // браузер кэширует preflight-запрос на 1 час
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** BCrypt с default strength (10 раундов) — хороший баланс безопасности и скорости. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** AuthenticationManager нужен AuthService для проверки email+пароля при логине. */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
