package com.booking.scheduler;

import com.booking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты планировщика.
 * Проверяем, что планировщик:
 *   1. Вызывает bookingService с правильным threshold (now - 15 min)
 *   2. Вызывает completeFinishedBookings с сегодняшней датой
 *
 * @Value-поля не инжектируются Mockito — используем ReflectionTestUtils.setField.
 */
@ExtendWith(MockitoExtension.class)
class BookingExpirationSchedulerTest {

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private BookingExpirationScheduler scheduler;

    @BeforeEach
    void setUp() {
        // Инжектируем значение @Value вручную — Spring не поднимается в unit-тестах
        ReflectionTestUtils.setField(scheduler, "pendingExpirationMinutes", 15);
    }

    // ==================== expirePendingBookings ====================

    @Test
    void expirePendingBookings_делегируетВServiceСПравильнымThreshold() {
        scheduler.expirePendingBookings();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(bookingService).expirePendingBookings(captor.capture());

        LocalDateTime threshold = captor.getValue();
        LocalDateTime expected = LocalDateTime.now().minusMinutes(15);

        // Допускаем погрешность ±2 секунды на выполнение теста
        assertThat(threshold).isCloseTo(expected, within(2, ChronoUnit.SECONDS));
    }

    @Test
    void expirePendingBookings_вызываетServiceРовноОдинРаз() {
        scheduler.expirePendingBookings();

        verify(bookingService, times(1)).expirePendingBookings(any());
        verifyNoMoreInteractions(bookingService);
    }

    // ==================== completeFinishedBookings ====================

    @Test
    void completeFinishedBookings_делегируетВServiceССегодняшнейДатой() {
        scheduler.completeFinishedBookings();

        ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
        verify(bookingService).completeFinishedBookings(captor.capture());

        assertThat(captor.getValue()).isEqualTo(LocalDate.now());
    }

    @Test
    void completeFinishedBookings_вызываетServiceРовноОдинРаз() {
        scheduler.completeFinishedBookings();

        verify(bookingService, times(1)).completeFinishedBookings(any());
        verifyNoMoreInteractions(bookingService);
    }
}
