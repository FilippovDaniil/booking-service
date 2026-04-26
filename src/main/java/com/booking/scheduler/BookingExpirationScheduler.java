package com.booking.scheduler;

import com.booking.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingExpirationScheduler {

    private final BookingService bookingService;

    @Value("${booking.pending-expiration-minutes}")
    private int pendingExpirationMinutes;

    @Scheduled(fixedDelay = 30_000)
    public void expirePendingBookings() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(pendingExpirationMinutes);
        log.debug("Running expiration scheduler, threshold={}", threshold);
        bookingService.expirePendingBookings(threshold);
    }

    @Scheduled(cron = "0 0 1 * * *")
    public void completeFinishedBookings() {
        log.info("Running completion scheduler for date={}", LocalDate.now());
        bookingService.completeFinishedBookings(LocalDate.now());
    }
}
