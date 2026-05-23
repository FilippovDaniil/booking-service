package com.booking.controller;

import com.booking.search.ApartmentDocument;
import com.booking.search.ApartmentSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Полнотекстовый поиск квартир через OpenSearch.
 *
 * GET /api/search/apartments?q=уютная&city=Москва&minPrice=1000&maxPrice=5000&page=0&size=10
 *
 * Доступ: permitAll — без авторизации (как и GET /api/apartments).
 * При недоступном OpenSearch — возвращает пустую страницу (graceful degradation).
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "Search")
public class SearchController {

    private final ApartmentSearchService searchService;

    /**
     * Полнотекстовый поиск по полям name + description с опциональными фильтрами.
     * Без параметров — возвращает все активные квартиры (match_all + filter active=true).
     */
    @GetMapping("/apartments")
    @Operation(summary = "Full-text search apartments (OpenSearch)")
    public ResponseEntity<Page<ApartmentDocument>> searchApartments(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            Pageable pageable) {
        return ResponseEntity.ok(searchService.search(q, city, minPrice, maxPrice, pageable));
    }
}
