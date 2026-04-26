package com.booking.dto.response;

import com.booking.entity.Review;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReviewResponse {
    private Long id;
    private Long bookingId;
    private Long apartmentId;
    private String clientName;
    private int rating;
    private String comment;
    private LocalDateTime createdAt;

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
