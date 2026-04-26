package com.booking.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class BookingRequest {

    @NotNull
    private Long apartmentId;

    @NotNull
    @Future
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    @Min(1)
    private int guestsCount;
}
