package com.booking.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Публикует события в Kafka.
 * Все ошибки отправки логируются, но не пробрасываются —
 * недоступность Kafka не должна ломать основной бизнес-поток.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventPublisher {

    // KafkaTemplate<ключ, значение> — ключ String (bookingId или userId), значение — JSON-объект
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Отправляет событие жизненного цикла брони в топик "booking-lifecycle".
     * Ключ партиционирования = bookingId (все события одной брони попадут в одну партицию).
     */
    public void publishBookingEvent(BookingEvent event) {
        log.info("Publishing booking event: type={}, bookingId={}", event.getEventType(), event.getBookingId());
        try {
            kafkaTemplate.send("booking-lifecycle", String.valueOf(event.getBookingId()), event);
        } catch (Exception e) {
            log.warn("Failed to publish booking event to Kafka: {}", e.getMessage());
        }
    }

    /**
     * Отправляет уведомление для пользователя в топик "notification-request".
     * Ключ = userId (все уведомления одного пользователя идут в одну партицию).
     */
    public void publishNotification(NotificationEvent event) {
        log.info("Publishing notification event for userId={}", event.getUserId());
        try {
            kafkaTemplate.send("notification-request", String.valueOf(event.getUserId()), event);
        } catch (Exception e) {
            log.warn("Failed to publish notification to Kafka: {}", e.getMessage());
        }
    }
}
