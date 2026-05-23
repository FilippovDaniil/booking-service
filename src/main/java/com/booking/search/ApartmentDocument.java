package com.booking.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Документ OpenSearch для квартиры.
 * Не JPA-сущность — чистый POJO, OpenSearch создаёт маппинг динамически.
 *
 * Маппинг, который создаст OpenSearch:
 *   name, description → text (анализируется, поиск по частичному совпадению + релевантность)
 *   city, amenities   → text + keyword (поиск + точный term-фильтр)
 *   pricePerNight     → float
 *   maxGuests         → integer
 *   averageRating     → float
 *   active            → boolean
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApartmentDocument {

    private String id;             // _id в OpenSearch — всегда String (в БД Long)
    private String name;
    private String description;
    private String city;
    private String street;
    private String houseNumber;
    private Double pricePerNight;  // Double, не BigDecimal — JSON не поддерживает BigDecimal напрямую
    private Integer maxGuests;
    private Boolean active;
    private Double averageRating;
    private String landlordName;
}
