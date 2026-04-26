package com.booking.dto.response;

import com.booking.entity.Apartment;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Data
public class ApartmentResponse {
    private Long id;
    private Long landlordId;
    private String landlordName;
    private String name;
    private String description;
    private String city;
    private String street;
    private String houseNumber;
    private BigDecimal pricePerNight;
    private int maxGuests;
    private boolean active;
    private Set<String> amenities;
    private List<String> photos;
    private double averageRating;

    public static ApartmentResponse from(Apartment a) {
        ApartmentResponse r = new ApartmentResponse();
        r.setId(a.getId());
        r.setLandlordId(a.getLandlord().getId());
        r.setLandlordName(a.getLandlord().getFirstName() + " " + a.getLandlord().getLastName());
        r.setName(a.getName());
        r.setDescription(a.getDescription());
        r.setCity(a.getCity());
        r.setStreet(a.getStreet());
        r.setHouseNumber(a.getHouseNumber());
        r.setPricePerNight(a.getPricePerNight());
        r.setMaxGuests(a.getMaxGuests());
        r.setActive(a.isActive());
        r.setAmenities(a.getAmenities());
        r.setPhotos(a.getPhotos());
        r.setAverageRating(a.getAverageRating());
        return r;
    }
}
