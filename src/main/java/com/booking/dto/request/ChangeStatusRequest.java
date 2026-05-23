package com.booking.dto.request;

import com.booking.entity.enums.BookingStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChangeStatusRequest {

    @NotNull
    private BookingStatus status;
}
