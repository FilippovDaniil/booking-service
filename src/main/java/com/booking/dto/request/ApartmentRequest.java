package com.booking.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Data
public class ApartmentRequest {

    @NotBlank
    private String name;

    private String description;

    @NotBlank
    private String city;

    private String street;

    private String houseNumber;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal pricePerNight;

    @Min(1)
    private int maxGuests;

    private Set<String> amenities;

    private List<String> photos;
}
