package com.booking.scheduler;

import com.booking.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Планировщик задач бронирований.
 * Автоматически изменяет статусы броней без участия пользователя.
 *
 * Включается через @EnableScheduling в SchedulingConfig.
 * В тестах отключён (профиль test исключает KafkaAutoConfiguration и т.д.)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingExpirationScheduler {

    private final BookingService bookingService;

    // Время ожидания подтверждения брони (по умолчанию 15 минут, из application.yml)
    @Value("${booking.pending-expiration-minutes}")
    private int pendingExpirationMinutes;

    /**
     * Запускается каждые 30 секунд (fixedDelay = задержка ПОСЛЕ завершения предыдущего выполнения).
     * Находит PENDING-брони старше {pendingExpirationMinutes} минут и переводит их в EXPIRED.
     */
    @Scheduled(fixedDelay = 30_000)
    public void expirePendingBookings() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(pendingExpirationMinutes);
        log.debug("Running expiration scheduler, threshold={}", threshold);
        bookingService.expirePendingBookings(threshold);
    }

    /**
     * Запускается в 1:00 ночи каждый день (cron = "секунда минута час день месяц деньНедели").
     * Находит CONFIRMED-брони с истёкшей датой выезда и переводит их в COMPLETED.
     */
    @Scheduled(cron = "0 0 1 * * *")
    public void completeFinishedBookings() {
        log.info("Running completion scheduler for date={}", LocalDate.now());
        bookingService.completeFinishedBookings(LocalDate.now());
    }
}
