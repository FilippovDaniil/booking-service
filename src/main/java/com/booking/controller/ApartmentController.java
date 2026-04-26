package com.booking.controller;

import com.booking.dto.request.ApartmentRequest;
import com.booking.dto.response.ApartmentResponse;
import com.booking.service.ApartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/apartments")
@RequiredArgsConstructor
@Tag(name = "Apartments")
public class ApartmentController {

    private final ApartmentService apartmentService;

    @GetMapping
    @Operation(summary = "Search available apartments")
    public ResponseEntity<Page<ApartmentResponse>> search(
            @RequestParam String city,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam int guests,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            Pageable pageable) {
        return ResponseEntity.ok(
                apartmentService.search(city, startDate, endDate, guests, minPrice, maxPrice, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get apartment details")
    public ResponseEntity<ApartmentResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(apartmentService.getById(id));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('LANDLORD')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get my apartments (LANDLORD)")
    public ResponseEntity<List<ApartmentResponse>> getMyApartments() {
        return ResponseEntity.ok(apartmentService.getMyApartments());
    }

    @PostMapping
    @PreAuthorize("hasRole('LANDLORD')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create apartment (LANDLORD)")
    public ResponseEntity<ApartmentResponse> create(@Valid @RequestBody ApartmentRequest request) {
        return ResponseEntity.ok(apartmentService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('LANDLORD')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update apartment (LANDLORD)")
    public ResponseEntity<ApartmentResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody ApartmentRequest request) {
        return ResponseEntity.ok(apartmentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('LANDLORD') or hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Deactivate apartment (LANDLORD/ADMIN)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        apartmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
