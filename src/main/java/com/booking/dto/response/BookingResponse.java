package com.booking.dto.response;

import com.booking.entity.Booking;
import com.booking.entity.enums.BookingStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO ответа с данными бронирования.
 *
 * Объединяет данные из трёх Entity: Booking, User (client), Apartment.
 * Вместо вложенных объектов — плоская структура с ID и именами.
 * Это избавляет клиента от необходимости делать дополнительные запросы.
 *
 * confirmedAt — null пока бронь не подтверждена, заполняется при confirm().
 */
@Data
public class BookingResponse {
    private Long id;
    private Long clientId;
    private String clientName;       // firstName + lastName клиента
    private Long apartmentId;
    private String apartmentName;
    private LocalDate startDate;
    private LocalDate endDate;
    private int guestsCount;
    private BigDecimal totalPrice;   // рассчитан при создании: ночи × pricePerNight
    private BookingStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt; // null до подтверждения

    /**
     * Маппинг Entity Booking → DTO BookingResponse.
     * Обращается к b.getClient() и b.getApartment() — убедитесь что вызывается в транзакции,
     * иначе получите LazyInitializationException на LAZY-связях.
     */
    public static BookingResponse from(Booking b) {
        BookingResponse r = new BookingResponse();
        r.setId(b.getId());
        r.setClientId(b.getClient().getId());
        r.setClientName(b.getClient().getFirstName() + " " + b.getClient().getLastName());
        r.setApartmentId(b.getApartment().getId());
        r.setApartmentName(b.getApartment().getName());
        r.setStartDate(b.getStartDate());
        r.setEndDate(b.getEndDate());
        r.setGuestsCount(b.getGuestsCount());
        r.setTotalPrice(b.getTotalPrice());
        r.setStatus(b.getStatus());
        r.setCreatedAt(b.getCreatedAt());
        r.setConfirmedAt(b.getConfirmedAt());
        return r;
    }
}
