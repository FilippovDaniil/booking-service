package com.booking.dto.response;

import com.booking.entity.Review;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO ответа с данными отзыва.
 *
 * Содержит apartmentId и clientName для удобства клиента —
 * чтобы не делать дополнительный запрос за деталями брони.
 * Данные извлекаются через цепочку: review → booking → apartment / client.
 */
@Data
public class ReviewResponse {
    private Long id;
    private Long bookingId;
    private Long apartmentId;    // удобное поле: к какой квартире относится отзыв
    private String clientName;   // имя автора отзыва (firstName + lastName)
    private int rating;          // оценка 1-5
    private String comment;
    private LocalDateTime createdAt;

    /**
     * Маппинг Review → ReviewResponse.
     * Цепочка r.getBooking().getApartment() и r.getBooking().getClient()
     * требует активной транзакции из-за LAZY fetch на связях Booking.
     */
    public static ReviewResponse from(Review r) {
        ReviewResponse resp = new ReviewResponse();
        resp.setId(r.getId());
        resp.setBookingId(r.getBooking().getId());
        resp.setApartmentId(r.getBooking().getApartment().getId());
        resp.setClientName(r.getBooking().getClient().getFirstName() + " " + r.getBooking().getClient().getLastName());
        resp.setRating(r.getRating());
        resp.setComment(r.getComment());
        resp.setCreatedAt(r.getCreatedAt());
        return resp;
    }
}
