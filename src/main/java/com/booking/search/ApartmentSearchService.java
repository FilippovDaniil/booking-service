package com.booking.search;

import com.booking.entity.Apartment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface ApartmentSearchService {

    void indexApartment(Apartment apartment);

    void removeApartment(Long id);

    void reindexAll(List<Apartment> apartments);

    Page<ApartmentDocument> search(String query, String city,
                                   BigDecimal minPrice, BigDecimal maxPrice,
                                   Pageable pageable);
}
