package com.booking.event;

import com.booking.entity.enums.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingEvent {

    public enum EventType {
        CREATED, CONFIRMED, CANCELLED, EXPIRED, COMPLETED
    }

    private Long bookingId;
    private Long clientId;
    private Long apartmentId;
    private EventType eventType;
    private BookingStatus newStatus;
    private LocalDateTime occurredAt;

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
