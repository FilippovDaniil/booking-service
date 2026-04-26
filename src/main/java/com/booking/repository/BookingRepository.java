package com.booking.repository;

import com.booking.entity.Apartment;
import com.booking.entity.Booking;
import com.booking.entity.User;
import com.booking.entity.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByClient(User client);

    List<Booking> findByApartmentLandlord(User landlord);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM Booking b
            WHERE b.apartment = :apartment
              AND b.status IN ('CONFIRMED', 'PENDING')
              AND b.startDate < :endDate
              AND b.endDate > :startDate
            """)
    List<Booking> findConflictingBookings(
            @Param("apartment") Apartment apartment,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT b FROM Booking b WHERE b.status = 'PENDING' AND b.createdAt < :expireThreshold")
    List<Booking> findExpiredPendingBookings(@Param("expireThreshold") LocalDateTime expireThreshold);

    @Query("SELECT b FROM Booking b WHERE b.status = 'CONFIRMED' AND b.endDate < :today")
    List<Booking> findCompletedBookings(@Param("today") LocalDate today);

    boolean existsByIdAndClientId(Long bookingId, Long clientId);
}
