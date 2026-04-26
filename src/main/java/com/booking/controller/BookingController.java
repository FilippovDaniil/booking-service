package com.booking.controller;

import com.booking.dto.request.BookingRequest;
import com.booking.dto.response.BookingResponse;
import com.booking.service.BookingService;
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
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @Operation(summary = "Create booking (CLIENT)")
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody BookingRequest request) {
        return ResponseEntity.ok(bookingService.create(request));
    }

    @GetMapping
    @Operation(summary = "Get bookings (role-based: CLIENT=own, LANDLORD=on his apartments, ADMIN=all)")
    public ResponseEntity<List<BookingResponse>> getMyBookings() {
        return ResponseEntity.ok(bookingService.getMyBookings());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get booking details")
    public ResponseEntity<BookingResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getById(id));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm booking (CLIENT, PENDING → CONFIRMED)")
    public ResponseEntity<BookingResponse> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.confirm(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel booking by client")
    public ResponseEntity<BookingResponse> cancelByClient(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.cancelByClient(id));
    }

    @PostMapping("/{id}/cancel-by-landlord")
    @PreAuthorize("hasRole('LANDLORD')")
    @Operation(summary = "Cancel booking by landlord")
    public ResponseEntity<BookingResponse> cancelByLandlord(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.cancelByLandlord(id));
    }
}
