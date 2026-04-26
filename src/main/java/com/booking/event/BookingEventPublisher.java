package com.booking.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishBookingEvent(BookingEvent event) {
        log.info("Publishing booking event: type={}, bookingId={}", event.getEventType(), event.getBookingId());
        try {
            kafkaTemplate.send("booking-lifecycle", String.valueOf(event.getBookingId()), event);
        } catch (Exception e) {
            log.warn("Failed to publish booking event to Kafka: {}", e.getMessage());
        }
    }

    public void publishNotification(NotificationEvent event) {
        log.info("Publishing notification event for userId={}", event.getUserId());
        try {
            kafkaTemplate.send("notification-request", String.valueOf(event.getUserId()), event);
        } catch (Exception e) {
            log.warn("Failed to publish notification to Kafka: {}", e.getMessage());
        }
    }
}
