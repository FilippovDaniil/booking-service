package com.booking.dto.response;

import com.booking.entity.Apartment;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * DTO ответа с данными квартиры.
 *
 * Содержит landlordId и landlordName — это «плоское» представление связи ManyToOne.
 * Вместо вложенного объекта User возвращаем только нужные поля арендодателя.
 * Это снижает объём передаваемых данных и скрывает лишние детали (email, пароль и т.д.).
 *
 * averageRating — денормализованное поле: хранится в таблице apartments
 * и пересчитывается при каждом добавлении/удалении отзыва (ReviewService.updateApartmentRating).
 */
@Data
public class ApartmentResponse {
    private Long id;
    private Long landlordId;
    private String landlordName;     // firstName + lastName арендодателя
    private String name;
    private String description;
    private String city;
    private String street;
    private String houseNumber;
    private BigDecimal pricePerNight;
    private int maxGuests;
    private boolean active;          // false = квартира деактивирована
    private Set<String> amenities;
    private List<String> photos;
    private double averageRating;    // среднее по всем отзывам, 0.0 если отзывов нет

    /**
     * Маппинг Entity Apartment → DTO ApartmentResponse.
     * Вызов a.getLandlord() триггерит ленивую загрузку — убедитесь, что
     * вызов происходит внутри транзакции или с EAGER fetch.
     */
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
