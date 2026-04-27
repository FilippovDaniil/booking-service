package com.booking.service;

import com.booking.dto.request.ReviewRequest;
import com.booking.dto.response.ReviewResponse;
import com.booking.entity.Apartment;
import com.booking.entity.Booking;
import com.booking.entity.Review;
import com.booking.entity.User;
import com.booking.entity.enums.BookingStatus;
import com.booking.entity.enums.Role;
import com.booking.exception.AccessDeniedException;
import com.booking.exception.InvalidOperationException;
import com.booking.exception.ResourceNotFoundException;
import com.booking.repository.ApartmentRepository;
import com.booking.repository.BookingRepository;
import com.booking.repository.ReviewRepository;
import com.booking.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private ApartmentRepository apartmentRepository;
    @Mock private SecurityUtils securityUtils;

    @InjectMocks
    private ReviewService reviewService;

    private User client;
    private User otherClient;
    private Apartment apartment;
    private Booking completedBooking;
    private ReviewRequest reviewRequest;
    private Review review;

    @BeforeEach
    void setUp() {
        client = User.builder().id(1L).firstName("Ivan").lastName("Petrov")
                .role(Role.CLIENT).enabled(true).build();
        otherClient = User.builder().id(2L).role(Role.CLIENT).enabled(true).build();

        apartment = Apartment.builder()
                .id(10L)
                .landlord(User.builder().id(99L).build())
                .name("Test Apt")
                .pricePerNight(new BigDecimal("1000.00"))
                .maxGuests(4)
                .active(true)
                .build();

        completedBooking = Booking.builder()
                .id(100L)
                .client(client)
                .apartment(apartment)
                .status(BookingStatus.COMPLETED)
                .build();

        reviewRequest = new ReviewRequest();
        reviewRequest.setBookingId(100L);
        reviewRequest.setRating(5);
        reviewRequest.setComment("Отличная квартира!");

        review = Review.builder()
                .id(50L)
                .booking(completedBooking)
                .rating(5)
                .comment("Отличная квартира!")
                .build();
    }

    // ==================== create ====================

    @Test
    void create_успешноеСозданиеОтзыва() {
        when(securityUtils.getCurrentUser()).thenReturn(client);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(completedBooking));
        when(reviewRepository.existsByBooking(completedBooking)).thenReturn(false);
        when(reviewRepository.save(any())).thenReturn(review);
        when(apartmentRepository.findById(10L)).thenReturn(Optional.of(apartment));
        when(reviewRepository.calculateAverageRating(10L)).thenReturn(5.0);

        ReviewResponse response = reviewService.create(reviewRequest);

        assertThat(response.getRating()).isEqualTo(5);
        assertThat(response.getComment()).isEqualTo("Отличная квартира!");
        verify(apartmentRepository).save(any()); // рейтинг должен обновиться
    }

    @Test
    void create_бронь_не_завершена_бросаетException() {
        completedBooking.setStatus(BookingStatus.CONFIRMED); // не COMPLETED
        when(securityUtils.getCurrentUser()).thenReturn(client);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(completedBooking));

        assertThatThrownBy(() -> reviewService.create(reviewRequest))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("only review completed bookings");
    }

    @Test
    void create_чужаяБронь_бросаетAccessDeniedException() {
        when(securityUtils.getCurrentUser()).thenReturn(otherClient);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(completedBooking));

        assertThatThrownBy(() -> reviewService.create(reviewRequest))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void create_отзывУжеЕсть_бросаетException() {
        when(securityUtils.getCurrentUser()).thenReturn(client);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(completedBooking));
        when(reviewRepository.existsByBooking(completedBooking)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.create(reviewRequest))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("already exists");
    }

    // ==================== getByApartment ====================

    @Test
    void getByApartment_возвращаетСписокОтзывов() {
        when(reviewRepository.findByApartmentId(10L)).thenReturn(List.of(review));

        List<ReviewResponse> result = reviewService.getByApartment(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRating()).isEqualTo(5);
    }

    // ==================== deleteReview ====================

    @Test
    void deleteReview_успешноеУдаление_рейтингПересчитывается() {
        when(reviewRepository.findById(50L)).thenReturn(Optional.of(review));
        when(apartmentRepository.findById(10L)).thenReturn(Optional.of(apartment));
        when(reviewRepository.calculateAverageRating(10L)).thenReturn(null); // отзывов больше нет

        reviewService.deleteReview(50L);

        verify(reviewRepository).delete(review);
        assertThat(apartment.getAverageRating()).isEqualTo(0.0); // рейтинг сброшен до 0
    }

    @Test
    void deleteReview_несуществующийОтзыв_бросаетException() {
        when(reviewRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.deleteReview(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
