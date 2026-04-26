package com.booking.controller;

import com.booking.dto.response.BookingResponse;
import com.booking.entity.Booking;
import com.booking.entity.enums.BookingStatus;
import com.booking.exception.ResourceNotFoundException;
import com.booking.repository.ApartmentRepository;
import com.booking.repository.BookingRepository;
import com.booking.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final UserRepository userRepository;
    private final ApartmentRepository apartmentRepository;
    private final BookingRepository bookingRepository;

    @GetMapping("/stats")
    @Operation(summary = "Get system statistics")
    public ResponseEntity<Map<String, Long>> getStats() {
        return ResponseEntity.ok(Map.of(
                "totalUsers", userRepository.count(),
                "totalApartments", apartmentRepository.count(),
                "totalBookings", bookingRepository.count()
        ));
    }

    @PutMapping("/bookings/{id}/status")
    @Operation(summary = "Force-change booking status")
    public ResponseEntity<BookingResponse> changeBookingStatus(
            @PathVariable Long id, @RequestParam BookingStatus status) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        booking.setStatus(status);
        return ResponseEntity.ok(BookingResponse.from(bookingRepository.save(booking)));
    }
}
