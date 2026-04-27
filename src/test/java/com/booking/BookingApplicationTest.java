package com.booking;

import com.booking.integration.TestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke-тест: проверяет, что Spring-контекст поднимается без ошибок.
 * Использует H2 вместо PostgreSQL и мок вместо Redis (через TestConfig).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
class BookingApplicationTest {

    @Test
    void contextLoads() {
        // Если контекст поднялся — тест прошёл
    }
}
