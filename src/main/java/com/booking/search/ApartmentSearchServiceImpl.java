package com.booking.search;

import com.booking.entity.Apartment;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import jakarta.json.Json;
import org.opensearch.client.json.JsonData;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class ApartmentSearchServiceImpl implements ApartmentSearchService {

    static final String INDEX = "apartments";

    // null когда opensearch.enabled=false (тесты, dev без OpenSearch)
    @Autowired(required = false)
    private OpenSearchClient client;

    @PostConstruct
    public void ensureIndex() {
        if (client == null) return;
        try {
            boolean exists = client.indices().exists(r -> r.index(INDEX)).value();
            if (!exists) {
                client.indices().create(r -> r.index(INDEX));
                log.info("OpenSearch index '{}' created", INDEX);
            }
        } catch (Exception e) {
            log.warn("OpenSearch unavailable at startup: {}", e.getMessage());
        }
    }

    @Override
    public void indexApartment(Apartment apartment) {
        if (client == null) return;
        try {
            client.index(r -> r
                    .index(INDEX)
                    .id(String.valueOf(apartment.getId()))
                    .document(toDocument(apartment)));
        } catch (Exception e) {
            log.warn("Failed to index apartment id={}: {}", apartment.getId(), e.getMessage());
        }
    }

    @Override
    public void removeApartment(Long id) {
        if (client == null) return;
        try {
            client.delete(r -> r.index(INDEX).id(String.valueOf(id)));
        } catch (Exception e) {
            log.warn("Failed to remove apartment id={} from index: {}", id, e.getMessage());
        }
    }

    @Override
    public void reindexAll(List<Apartment> apartments) {
        if (client == null) return;
        for (Apartment apartment : apartments) {
            indexApartment(apartment);
        }
        log.info("Reindexed {} apartments", apartments.size());
    }

    @Override
    public Page<ApartmentDocument> search(String query, String city,
                                          BigDecimal minPrice, BigDecimal maxPrice,
                                          Pageable pageable) {
        if (client == null) return Page.empty(pageable);
        try {
            List<Query> clauses = new ArrayList<>();

            if (query != null && !query.isBlank()) {
                // multi_match с boost: name важнее description при ранжировании
                clauses.add(Query.of(q -> q.multiMatch(m -> m
                        .fields("name^2", "description")
                        .query(query))));
            }
            if (city != null && !city.isBlank()) {
                // ⚠️ term принимает FieldValue, не String
                clauses.add(Query.of(q -> q.term(t -> t
                        .field("city")
                        .value(FieldValue.of(city)))));
            }
            if (minPrice != null) {
                // ⚠️ range принимает JsonData, не BigDecimal
                clauses.add(Query.of(q -> q.range(r -> r
                        .field("pricePerNight")
                        .gte(JsonData.of(minPrice.doubleValue())))));
            }
            if (maxPrice != null) {
                clauses.add(Query.of(q -> q.range(r -> r
                        .field("pricePerNight")
                        .lte(JsonData.of(maxPrice.doubleValue())))));
            }
            // Только активные квартиры
            clauses.add(Query.of(q -> q.term(t -> t
                    .field("active")
                    .value(FieldValue.of(true)))));

            Query finalQuery = clauses.size() == 1
                    ? clauses.get(0)
                    : Query.of(q -> q.bool(b -> b.must(clauses)));

            SearchRequest request = new SearchRequest.Builder()
                    .index(INDEX)
                    .from((int) pageable.getOffset())
                    .size(pageable.getPageSize())
                    .query(finalQuery)
                    .build();

            SearchResponse<ApartmentDocument> response =
                    client.search(request, ApartmentDocument.class);

            List<ApartmentDocument> hits = response.hits().hits().stream()
                    .map(Hit::source)
                    .toList();
            long total = response.hits().total() != null
                    ? response.hits().total().value()
                    : hits.size();

            return new PageImpl<>(hits, pageable, total);
        } catch (Exception e) {
            log.warn("OpenSearch search failed: {}", e.getMessage());
            return Page.empty(pageable);
        }
    }

    private ApartmentDocument toDocument(Apartment a) {
        return ApartmentDocument.builder()
                .id(String.valueOf(a.getId()))
                .name(a.getName())
                .description(a.getDescription())
                .city(a.getCity())
                .street(a.getStreet())
                .houseNumber(a.getHouseNumber())
                .pricePerNight(a.getPricePerNight() != null ? a.getPricePerNight().doubleValue() : null)
                .maxGuests(a.getMaxGuests())
                .active(a.isActive())
                .averageRating(a.getAverageRating())
                .landlordName(a.getLandlord() != null
                        ? a.getLandlord().getFirstName() + " " + a.getLandlord().getLastName()
                        : null)
                .build();
    }
}
