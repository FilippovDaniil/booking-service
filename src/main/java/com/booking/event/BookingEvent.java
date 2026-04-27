package com.booking.event;

import com.booking.entity.enums.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Событие изменения статуса бронирования.
 * Публикуется в Kafka-топик "booking-lifecycle" при каждом изменении статуса.
 *
 * Другие микросервисы (аналитика, нотификации и т.д.) могут подписаться
 * на этот топик для реагирования на события без прямой связи с этим сервисом.
 */
@Data
@NoArgsConstructor   // нужен Jackson для десериализации из Kafka
@AllArgsConstructor
public class BookingEvent {

    /** Тип события соответствует переходу статуса бронирования. */
    public enum EventType {
        CREATED, CONFIRMED, CANCELLED, EXPIRED, COMPLETED
    }

    private Long bookingId;
    private Long clientId;
    private Long apartmentId;
    private EventType eventType;
    private BookingStatus newStatus; // статус ПОСЛЕ изменения
    private LocalDateTime occurredAt; // время наступления события

    /** Основной конструктор: время события выставляется автоматически. */
    public BookingEvent(Long bookingId, Long clientId, Long apartmentId,
                        EventType eventType, BookingStatus newStatus) {
        this.bookingId = bookingId;
        this.clientId = clientId;
        this.apartmentId = apartmentId;
        this.eventType = eventType;
        this.newStatus = newStatus;
        this.occurredAt = LocalDateTime.now();
    }
}
