package com.booking.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JPA-сущность «Квартира/Апартамент».
 * Хранится в таблице apartments.
 * Каждая квартира принадлежит одному арендодателю (landlord).
 * Удаление реализовано через мягкое отключение: active = false.
 */
@Entity
@Table(name = "apartments", indexes = {
        // Индекс по городу ускоряет поиск (самый частый фильтр)
        @Index(name = "idx_apartment_city", columnList = "city")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Apartment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Владелец квартиры — ленивая загрузка, чтобы не тянуть User при каждом запросе квартиры
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "landlord_id", nullable = false)
    private User landlord;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT") // TEXT позволяет хранить длинные описания без ограничения 255 символов
    private String description;

    @Column(nullable = false)
    private String city;

    private String street;

    private String houseNumber;

    // BigDecimal для денежных значений — не теряет точность в отличие от double
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerNight;

    @Column(nullable = false)
    private int maxGuests;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true; // false означает «снято с публикации»

    // Удобства (Wi-Fi, парковка и т.д.) — хранятся в отдельной таблице apartment_amenities
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "apartment_amenities", joinColumns = @JoinColumn(name = "apartment_id"))
    @Column(name = "amenity")
    @Builder.Default
    private Set<String> amenities = new HashSet<>();

    // Ссылки на фото — хранятся в отдельной таблице apartment_photos
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "apartment_photos", joinColumns = @JoinColumn(name = "apartment_id"))
    @Column(name = "photo_url")
    @Builder.Default
    private List<String> photos = new ArrayList<>();

    // Вычисляемый рейтинг — обновляется при добавлении/удалении отзыва через ReviewService
    @Column(nullable = false)
    @Builder.Default
    private double averageRating = 0.0;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
