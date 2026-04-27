package com.booking.entity;

import com.booking.entity.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA-сущность «Бронирование».
 * Связывает клиента (client) с квартирой (apartment) на указанный период.
 * Составной индекс по (apartment_id, start_date, end_date) критически важен
 * для быстрой проверки конфликтов дат при создании новой брони.
 */
@Entity
@Table(name = "bookings", indexes = {
        // Ускоряет запросы на пересечение дат для конкретной квартиры
        @Index(name = "idx_booking_apartment_dates", columnList = "apartment_id,start_date,end_date"),
        // Ускоряет запросы «мои бронирования» по клиенту
        @Index(name = "idx_booking_client", columnList = "client_id"),
        // Ускоряет запросы планировщика (фильтр по статусу)
        @Index(name = "idx_booking_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apartment_id", nullable = false)
    private Apartment apartment;

    // LocalDate (без времени) — только дата заезда
    @Column(nullable = false)
    private LocalDate startDate;

    // LocalDate (без времени) — только дата выезда
    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private int guestsCount;

    // Итоговая стоимость = количество ночей × pricePerNight, вычисляется при создании
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Заполняется только при переходе в статус CONFIRMED
    private LocalDateTime confirmedAt;
}
