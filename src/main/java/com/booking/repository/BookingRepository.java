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

/**
 * Репозиторий бронирований.
 * Содержит несколько кастомных JPQL-запросов для бизнес-логики.
 */
public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** Возвращает все брони конкретного клиента. */
    List<Booking> findByClient(User client);

    /** Возвращает все брони на квартиры конкретного арендодателя (через JOIN). */
    List<Booking> findByApartmentLandlord(User landlord);

    /**
     * Поиск конфликтующих броней с пессимистической блокировкой записей.
     *
     * PESSIMISTIC_WRITE блокирует строки в БД до конца транзакции.
     * Это предотвращает ситуацию «двойного бронирования»:
     * если две транзакции одновременно проверяют доступность — вторая будет ждать первую.
     *
     * Условие пересечения дат: A.start < B.end AND A.end > B.start
     * (стандартный алгоритм проверки перекрытия двух отрезков)
     */
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

    /**
     * Находит PENDING-брони, созданные раньше указанного порога.
     * Используется планировщиком для автоматического перевода в EXPIRED.
     * expireThreshold = now() - 15 минут
     */
    @Query("SELECT b FROM Booking b WHERE b.status = 'PENDING' AND b.createdAt < :expireThreshold")
    List<Booking> findExpiredPendingBookings(@Param("expireThreshold") LocalDateTime expireThreshold);

    /**
     * Находит CONFIRMED-брони, дата выезда которых уже прошла.
     * Используется планировщиком для перевода в COMPLETED (запускается в 1:00 ночи).
     */
    @Query("SELECT b FROM Booking b WHERE b.status = 'CONFIRMED' AND b.endDate < :today")
    List<Booking> findCompletedBookings(@Param("today") LocalDate today);

    boolean existsByIdAndClientId(Long bookingId, Long clientId);
}
