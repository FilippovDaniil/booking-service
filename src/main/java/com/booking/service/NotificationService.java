package com.booking.service;

import com.booking.event.BookingEvent;
import com.booking.event.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationService {

    @KafkaListener(topics = "booking-lifecycle", groupId = "booking-service-lifecycle")
    public void handleBookingEvent(BookingEvent event) {
        log.info("[BOOKING EVENT] type={}, bookingId={}, newStatus={}",
                event.getEventType(), event.getBookingId(), event.getNewStatus());
    }

    @KafkaListener(topics = "notification-request", groupId = "booking-service-notifications")
    public void handleNotification(NotificationEvent event) {
        log.info("[NOTIFICATION] userId={}, subject={}, body={}",
                event.getUserId(), event.getSubject(), event.getBody());
        // In production: send email via SMTP / SendGrid / etc.
    }
}
