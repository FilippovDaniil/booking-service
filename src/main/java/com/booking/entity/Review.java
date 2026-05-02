package com.booking.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * JPA-сущность «Отзыв».
 * Отзыв привязан к конкретному бронированию (OneToOne, уникальный).
 * Это гарантирует: один клиент — одна бронь — один отзыв.
 * Через бронирование доступны: квартира, клиент.
 */
@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // unique = true на уровне БД страхует от двойных отзывов даже при гонке потоков
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    // Значение от 1 до 5, проверяется в ReviewRequest через @Min/@Max
    @Column(nullable = false)
    private int rating;

    @Column(columnDefinition = "TEXT") // TEXT: неограниченная длина, в отличие от VARCHAR(255)
    private String comment;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
