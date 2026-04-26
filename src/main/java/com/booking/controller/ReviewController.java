package com.booking.controller;

import com.booking.dto.request.ReviewRequest;
import com.booking.dto.response.ReviewResponse;
import com.booking.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Reviews")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/api/reviews")
    @PreAuthorize("hasRole('CLIENT')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create review (CLIENT, only after COMPLETED booking)")
    public ResponseEntity<ReviewResponse> create(@Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(reviewService.create(request));
    }

    @GetMapping("/api/apartments/{apartmentId}/reviews")
    @Operation(summary = "Get reviews for apartment")
    public ResponseEntity<List<ReviewResponse>> getByApartment(@PathVariable Long apartmentId) {
        return ResponseEntity.ok(reviewService.getByApartment(apartmentId));
    }

    @DeleteMapping("/api/reviews/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete review (ADMIN)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        reviewService.deleteReview(id);
        return ResponseEntity.noContent().build();
    }
}
