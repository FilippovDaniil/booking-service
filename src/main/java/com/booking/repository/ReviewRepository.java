package com.booking.repository;

import com.booking.entity.Booking;
import com.booking.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByBooking(Booking booking);

    boolean existsByBooking(Booking booking);

    @Query("SELECT r FROM Review r WHERE r.booking.apartment.id = :apartmentId")
    List<Review> findByApartmentId(@Param("apartmentId") Long apartmentId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.booking.apartment.id = :apartmentId")
    Double calculateAverageRating(@Param("apartmentId") Long apartmentId);
}
