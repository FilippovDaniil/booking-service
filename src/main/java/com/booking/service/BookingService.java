package com.booking.service;

import com.booking.dto.request.BookingRequest;
import com.booking.dto.response.BookingResponse;
import com.booking.entity.Apartment;
import com.booking.entity.Booking;
import com.booking.entity.User;
import com.booking.entity.enums.BookingStatus;
import com.booking.event.BookingEvent;
import com.booking.event.BookingEventPublisher;
import com.booking.event.NotificationEvent;
import com.booking.exception.AccessDeniedException;
import com.booking.exception.BookingConflictException;
import com.booking.exception.InvalidOperationException;
import com.booking.exception.ResourceNotFoundException;
import com.booking.repository.BookingRepository;
import com.booking.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ApartmentService apartmentService;
    private final SecurityUtils securityUtils;
    private final BookingEventPublisher eventPublisher;

    @Transactional
    public BookingResponse create(BookingRequest request) {
        User client = securityUtils.getCurrentUser();
        Apartment apartment = apartmentService.findById(request.getApartmentId());

        if (!apartment.isActive()) {
            throw new InvalidOperationException("Apartment is not available");
        }
        if (apartment.getLandlord().getId().equals(client.getId())) {
            throw new InvalidOperationException("Cannot book your own apartment");
        }
        if (request.getGuestsCount() > apartment.getMaxGuests()) {
            throw new InvalidOperationException("Guests count exceeds apartment limit");
        }
        if (!request.getEndDate().isAfter(request.getStartDate())) {
            throw new InvalidOperationException("End date must be after start date");
        }

        // Pessimistic lock check for date conflicts
        List<Booking> conflicts = bookingRepository.findConflictingBookings(
                apartment, request.getStartDate(), request.getEndDate());
        if (!conflicts.isEmpty()) {
            throw new BookingConflictException("Apartment is already booked for these dates");
        }

        long nights = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate());
        BigDecimal totalPrice = apartment.getPricePerNight().multiply(BigDecimal.valueOf(nights));

        Booking booking = Booking.builder()
                .client(client)
                .apartment(apartment)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .guestsCount(request.getGuestsCount())
                .totalPrice(totalPrice)
                .status(BookingStatus.PENDING)
                .build();

        booking = bookingRepository.save(booking);

        eventPublisher.publishBookingEvent(new BookingEvent(
                booking.getId(), client.getId(), apartment.getId(),
                BookingEvent.EventType.CREATED, BookingStatus.PENDING));

        eventPublisher.publishNotification(new NotificationEvent(
                client.getId(),
                "Booking created",
                "Please confirm your booking #" + booking.getId() + " within 15 minutes."));

        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse confirm(Long id) {
        Booking booking = getBooking(id);
        User current = securityUtils.getCurrentUser();

        if (!booking.getClient().getId().equals(current.getId())) {
            throw new AccessDeniedException("Not your booking");
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidOperationException("Booking is not in PENDING status");
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(LocalDateTime.now());
        booking = bookingRepository.save(booking);

        eventPublisher.publishBookingEvent(new BookingEvent(
                booking.getId(), booking.getClient().getId(), booking.getApartment().getId(),
                BookingEvent.EventType.CONFIRMED, BookingStatus.CONFIRMED));

        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse cancelByClient(Long id) {
        Booking booking = getBooking(id);
        User current = securityUtils.getCurrentUser();

        if (!booking.getClient().getId().equals(current.getId())) {
            throw new AccessDeniedException("Not your booking");
        }
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new InvalidOperationException("Cannot cancel booking with status: " + booking.getStatus());
        }
        if (booking.getStartDate().isBefore(LocalDate.now())) {
            throw new InvalidOperationException("Cannot cancel a booking that has already started");
        }

        booking.setStatus(BookingStatus.CANCELLED_BY_CLIENT);
        booking = bookingRepository.save(booking);

        eventPublisher.publishBookingEvent(new BookingEvent(
                booking.getId(), booking.getClient().getId(), booking.getApartment().getId(),
                BookingEvent.EventType.CANCELLED, BookingStatus.CANCELLED_BY_CLIENT));

        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse cancelByLandlord(Long id) {
        Booking booking = getBooking(id);
        User current = securityUtils.getCurrentUser();

        if (!booking.getApartment().getLandlord().getId().equals(current.getId())) {
            throw new AccessDeniedException("Not your apartment's booking");
        }
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new InvalidOperationException("Cannot cancel booking with status: " + booking.getStatus());
        }
        if (booking.getStartDate().isBefore(LocalDate.now())) {
            throw new InvalidOperationException("Cannot cancel a booking that has already started");
        }

        booking.setStatus(BookingStatus.CANCELLED_BY_LANDLORD);
        booking = bookingRepository.save(booking);

        eventPublisher.publishBookingEvent(new BookingEvent(
                booking.getId(), booking.getClient().getId(), booking.getApartment().getId(),
                BookingEvent.EventType.CANCELLED, BookingStatus.CANCELLED_BY_LANDLORD));

        eventPublisher.publishNotification(new NotificationEvent(
                booking.getClient().getId(),
                "Booking cancelled",
                "Your booking #" + booking.getId() + " was cancelled by the landlord."));

        return BookingResponse.from(booking);
    }

    public BookingResponse getById(Long id) {
        Booking booking = getBooking(id);
        User current = securityUtils.getCurrentUser();

        boolean isClient = booking.getClient().getId().equals(current.getId());
        boolean isLandlord = booking.getApartment().getLandlord().getId().equals(current.getId());
        boolean isAdmin = current.getRole().name().equals("ADMIN");

        if (!isClient && !isLandlord && !isAdmin) {
            throw new AccessDeniedException("Access denied");
        }
        return BookingResponse.from(booking);
    }

    public List<BookingResponse> getMyBookings() {
        User current = securityUtils.getCurrentUser();
        return switch (current.getRole()) {
            case CLIENT -> bookingRepository.findByClient(current).stream()
                    .map(BookingResponse::from).collect(Collectors.toList());
            case LANDLORD -> bookingRepository.findByApartmentLandlord(current).stream()
                    .map(BookingResponse::from).collect(Collectors.toList());
            case ADMIN -> bookingRepository.findAll().stream()
                    .map(BookingResponse::from).collect(Collectors.toList());
        };
    }

    // Called by scheduler — no auth context
    @Transactional
    public void expirePendingBookings(LocalDateTime threshold) {
        List<Booking> expired = bookingRepository.findExpiredPendingBookings(threshold);
        for (Booking booking : expired) {
            booking.setStatus(BookingStatus.EXPIRED);
            bookingRepository.save(booking);
            log.info("Booking {} expired", booking.getId());

            eventPublisher.publishBookingEvent(new BookingEvent(
                    booking.getId(), booking.getClient().getId(), booking.getApartment().getId(),
                    BookingEvent.EventType.EXPIRED, BookingStatus.EXPIRED));

            eventPublisher.publishNotification(new NotificationEvent(
                    booking.getClient().getId(),
                    "Booking expired",
                    "Your booking #" + booking.getId() + " was not confirmed in time and has expired."));
        }
    }

    @Transactional
    public void completeFinishedBookings(LocalDate today) {
        List<Booking> toComplete = bookingRepository.findCompletedBookings(today);
        for (Booking booking : toComplete) {
            booking.setStatus(BookingStatus.COMPLETED);
            bookingRepository.save(booking);
            log.info("Booking {} completed", booking.getId());

            eventPublisher.publishBookingEvent(new BookingEvent(
                    booking.getId(), booking.getClient().getId(), booking.getApartment().getId(),
                    BookingEvent.EventType.COMPLETED, BookingStatus.COMPLETED));
        }
    }

    private Booking getBooking(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));
    }
}
