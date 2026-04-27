package com.booking.repository;

import com.booking.entity.Booking;
import com.booking.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий отзывов.
 * Содержит кастомные JPQL-запросы для работы с рейтингами.
 *
 * JPQL (Java Persistence Query Language) — язык запросов Hibernate,
 * оперирует именами Java-классов и полей, а не таблицами и столбцами БД.
 * Hibernate сам транслирует JPQL → SQL для конкретной СУБД (PostgreSQL / H2).
 */
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * Поиск отзыва по брони — используется внутри сервиса.
     * Spring Data автоматически генерирует запрос по имени метода.
     */
    Optional<Review> findByBooking(Booking booking);

    /**
     * Проверяет наличие отзыва для данной брони.
     * Используется перед созданием нового отзыва, чтобы не допустить дублирования.
     */
    boolean existsByBooking(Booking booking);

    /**
     * Возвращает все отзывы для квартиры через JOIN:
     *   Review → Booking → Apartment (по apartment.id)
     *
     * JPQL-запрос: r.booking.apartment.id — навигация по полям Entity,
     * Hibernate сам выполнит JOIN между таблицами reviews, bookings и apartments.
     */
    @Query("SELECT r FROM Review r WHERE r.booking.apartment.id = :apartmentId")
    List<Review> findByApartmentId(@Param("apartmentId") Long apartmentId);

    /**
     * Вычисляет средний рейтинг квартиры из всех её отзывов.
     *
     * AVG() — агрегатная функция SQL, возвращает среднее.
     * Возвращает null если у квартиры нет отзывов (не Double 0.0!).
     * В сервисе: avg != null ? avg : 0.0
     */
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.booking.apartment.id = :apartmentId")
    Double calculateAverageRating(@Param("apartmentId") Long apartmentId);
}
