package com.booking.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * DTO запроса создания бронирования.
 * Используется в POST /api/bookings.
 *
 * Клиент (client) не передаётся — берётся из SecurityContext в BookingService.create().
 * totalPrice не передаётся — рассчитывается автоматически: ночи × pricePerNight.
 * status не передаётся — всегда PENDING при создании.
 */
@Data
public class BookingRequest {

    @NotNull
    private Long apartmentId; // ID квартиры для бронирования

    @NotNull
    @Future // дата заезда должна быть в будущем (нельзя забронировать на вчера)
    private LocalDate startDate;

    @NotNull
    // endDate не @Future — достаточно что startDate в будущем и endDate > startDate
    private LocalDate endDate;

    @Min(1) // минимум 1 гость
    private int guestsCount;
}
