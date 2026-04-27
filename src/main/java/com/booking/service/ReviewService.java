package com.booking.service;

import com.booking.dto.request.ReviewRequest;
import com.booking.dto.response.ReviewResponse;
import com.booking.entity.Booking;
import com.booking.entity.Review;
import com.booking.entity.User;
import com.booking.entity.enums.BookingStatus;
import com.booking.exception.AccessDeniedException;
import com.booking.exception.InvalidOperationException;
import com.booking.exception.ResourceNotFoundException;
import com.booking.repository.ApartmentRepository;
import com.booking.repository.BookingRepository;
import com.booking.repository.ReviewRepository;
import com.booking.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис отзывов.
 *
 * Правила:
 *  - Отзыв может оставить только CLIENT, только после завершённой брони (COMPLETED).
 *  - Один отзыв на одно бронирование (уникальность на уровне БД и кода).
 *  - После добавления/удаления отзыва пересчитывается averageRating квартиры.
 *  - Удалять отзывы может только ADMIN.
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final ApartmentRepository apartmentRepository;
    private final SecurityUtils securityUtils;

    /**
     * Создаёт отзыв.
     * Проверяет: бронь принадлежит текущему пользователю, статус COMPLETED, отзыв ещё не был оставлен.
     */
    @Transactional
    public ReviewResponse create(ReviewRequest request) {
        User client = securityUtils.getCurrentUser();
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (!booking.getClient().getId().equals(client.getId())) {
            throw new AccessDeniedException("Not your booking");
        }
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new InvalidOperationException("Can only review completed bookings");
        }
        if (reviewRepository.existsByBooking(booking)) {
            throw new InvalidOperationException("Review already exists for this booking");
        }

        Review review = Review.builder()
                .booking(booking)
                .rating(request.getRating())
                .comment(request.getComment())
                .build();
        review = reviewRepository.save(review);

        // Пересчитываем средний рейтинг квартиры после добавления нового отзыва
        updateApartmentRating(booking.getApartment().getId());

        return ReviewResponse.from(review);
    }

    /** Возвращает все отзывы для квартиры (через JOIN на booking → apartment). */
    public List<ReviewResponse> getByApartment(Long apartmentId) {
        return reviewRepository.findByApartmentId(apartmentId).stream()
                .map(ReviewResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Удаляет отзыв (только ADMIN).
     * После удаления пересчитывает средний рейтинг квартиры.
     */
    @Transactional
    public void deleteReview(Long id) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        Long apartmentId = review.getBooking().getApartment().getId();
        reviewRepository.delete(review);
        updateApartmentRating(apartmentId); // рейтинг может упасть или стать 0
    }

    /**
     * Пересчитывает и сохраняет средний рейтинг квартиры.
     * AVG возвращает null, если отзывов нет — в этом случае ставим 0.
     */
    private void updateApartmentRating(Long apartmentId) {
        Double avg = reviewRepository.calculateAverageRating(apartmentId);
        apartmentRepository.findById(apartmentId).ifPresent(a -> {
            a.setAverageRating(avg != null ? avg : 0.0);
            apartmentRepository.save(a);
        });
    }
}
