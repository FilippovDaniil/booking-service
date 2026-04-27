package com.booking.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * DTO запроса создания/обновления квартиры.
 * Используется в POST /api/apartments и PUT /api/apartments/{id}.
 *
 * Владелец (landlord) не передаётся в запросе — он берётся из SecurityContext
 * автоматически в ApartmentService.create() через securityUtils.getCurrentUser().
 * Это защищает от подмены: клиент не может указать чужого владельца.
 */
@Data
public class ApartmentRequest {

    @NotBlank // название обязательно
    private String name;

    private String description; // описание необязательно

    @NotBlank
    private String city;

    private String street;      // улица необязательна

    private String houseNumber; // номер дома необязателен

    @NotNull
    @DecimalMin("0.01") // цена должна быть положительной (минимум 1 копейка)
    private BigDecimal pricePerNight;

    @Min(1) // минимум 1 гость
    private int maxGuests;

    // Набор удобств: "Wi-Fi", "Парковка", "Кондиционер" и т.д.
    // Set (не List) — удобства уникальны, порядок не важен
    private Set<String> amenities;

    // Ссылки на фотографии (URL)
    // List — фото идут в определённом порядке
    private List<String> photos;
}
