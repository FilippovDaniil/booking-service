package com.booking.repository;

import com.booking.entity.Apartment;
import com.booking.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Репозиторий квартир.
 * Ключевой метод — findAvailable: поиск свободных квартир с фильтрацией.
 */
public interface ApartmentRepository extends JpaRepository<Apartment, Long> {

    /** Возвращает все квартиры арендодателя (включая неактивные). */
    List<Apartment> findByLandlord(User landlord);

    /**
     * Загружает все квартиры с JOIN FETCH по landlord.
     * Используется для реиндексации OpenSearch при старте.
     * JOIN FETCH необходим: после findAll() JPA-сессия закрыта, lazy landlord → LazyInitializationException.
     */
    @Query("SELECT a FROM Apartment a LEFT JOIN FETCH a.landlord")
    List<Apartment> findAllForReindex();

    /**
     * Поиск доступных квартир с фильтрацией по городу, датам, гостям и цене.
     *
     * Логика «свободная квартира»:
     *   NOT EXISTS subquery — нет активных броней (CONFIRMED/PENDING), пересекающихся с запрошенным периодом.
     *   Условие пересечения: bron.start < запрос.end AND bron.end > запрос.start
     *
     * :minPrice IS NULL / :maxPrice IS NULL — позволяет передавать null для игнорирования фильтра цены.
     * Pageable — поддерживает пагинацию и сортировку на уровне SQL (LIMIT/OFFSET).
     */
    @Query("""
            SELECT a FROM Apartment a
            WHERE a.active = true
              AND a.city = :city
              AND a.maxGuests >= :guests
              AND (:minPrice IS NULL OR a.pricePerNight >= :minPrice)
              AND (:maxPrice IS NULL OR a.pricePerNight <= :maxPrice)
              AND NOT EXISTS (
                  SELECT b FROM Booking b
                  WHERE b.apartment = a
                    AND b.status IN ('CONFIRMED', 'PENDING')
                    AND b.startDate < :endDate
                    AND b.endDate > :startDate
              )
            """)
    Page<Apartment> findAvailable(
            @Param("city") String city,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("guests") int guests,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable);
}
