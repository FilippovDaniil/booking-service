package com.booking.service;

import com.booking.dto.request.ApartmentRequest;
import com.booking.dto.response.ApartmentResponse;
import com.booking.entity.Apartment;
import com.booking.entity.User;
import com.booking.entity.enums.Role;
import com.booking.exception.AccessDeniedException;
import com.booking.exception.ResourceNotFoundException;
import com.booking.repository.ApartmentRepository;
import com.booking.search.ApartmentSearchService;
import com.booking.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис управления квартирами.
 *
 * Создание/редактирование — только LANDLORD, только своих квартир.
 * Удаление (мягкое: active = false) — LANDLORD (своей) или ADMIN (любой).
 * Поиск доступных квартир — открытый, без авторизации (вызывается напрямую из контроллера).
 */
@Service
@RequiredArgsConstructor
public class ApartmentService {

    private final ApartmentRepository apartmentRepository;
    private final SecurityUtils securityUtils;

    // null когда opensearch.enabled=false (тесты и dev без OpenSearch)
    @Autowired(required = false)
    private ApartmentSearchService searchService;

    /** Создаёт квартиру; текущий пользователь автоматически становится арендодателем. */
    @Transactional
    public ApartmentResponse create(ApartmentRequest request) {
        User landlord = securityUtils.getCurrentUser();
        Apartment apartment = Apartment.builder()
                .landlord(landlord)
                .name(request.getName())
                .description(request.getDescription())
                .city(request.getCity())
                .street(request.getStreet())
                .houseNumber(request.getHouseNumber())
                .pricePerNight(request.getPricePerNight())
                .maxGuests(request.getMaxGuests())
                .active(true)
                .build();
        if (request.getAmenities() != null) apartment.setAmenities(request.getAmenities());
        if (request.getPhotos() != null) apartment.setPhotos(request.getPhotos());
        Apartment saved = apartmentRepository.save(apartment);
        if (searchService != null) searchService.indexApartment(saved);
        return ApartmentResponse.from(saved);
    }

    /** Обновляет квартиру; проверяет, что текущий пользователь — её владелец. */
    @Transactional
    public ApartmentResponse update(Long id, ApartmentRequest request) {
        Apartment apartment = getOwnedApartment(id); // бросит 403, если не владелец
        apartment.setName(request.getName());
        apartment.setDescription(request.getDescription());
        apartment.setCity(request.getCity());
        apartment.setStreet(request.getStreet());
        apartment.setHouseNumber(request.getHouseNumber());
        apartment.setPricePerNight(request.getPricePerNight());
        apartment.setMaxGuests(request.getMaxGuests());
        if (request.getAmenities() != null) apartment.setAmenities(request.getAmenities());
        if (request.getPhotos() != null) apartment.setPhotos(request.getPhotos());
        Apartment saved = apartmentRepository.save(apartment);
        if (searchService != null) searchService.indexApartment(saved);
        return ApartmentResponse.from(saved);
    }

    /**
     * Мягкое удаление: active = false.
     * Квартира остаётся в БД, исторические брони сохраняются.
     * ADMIN может деактивировать любую квартиру, LANDLORD — только свою.
     */
    @Transactional
    public void delete(Long id) {
        Apartment apartment = findById(id);
        User current = securityUtils.getCurrentUser();
        if (current.getRole() != Role.ADMIN && !apartment.getLandlord().getId().equals(current.getId())) {
            throw new AccessDeniedException("You don't own this apartment");
        }
        apartment.setActive(false);
        apartmentRepository.save(apartment);
        // Мягкое удаление — переиндексируем с active=false, чтобы пропал из поиска
        if (searchService != null) searchService.indexApartment(apartment);
    }

    public ApartmentResponse getById(Long id) {
        return ApartmentResponse.from(findById(id));
    }

    /** Возвращает все квартиры текущего арендодателя (включая неактивные). */
    public List<ApartmentResponse> getMyApartments() {
        User landlord = securityUtils.getCurrentUser();
        return apartmentRepository.findByLandlord(landlord).stream()
                .map(ApartmentResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Поиск доступных квартир с пагинацией.
     * Делегирует в JPQL-запрос репозитория, который исключает квартиры
     * с пересекающимися активными бронями (CONFIRMED/PENDING).
     */
    public Page<ApartmentResponse> search(String city, LocalDate startDate, LocalDate endDate,
                                          int guests, BigDecimal minPrice, BigDecimal maxPrice,
                                          Pageable pageable) {
        return apartmentRepository
                .findAvailable(city, startDate, endDate, guests, minPrice, maxPrice, pageable)
                .map(ApartmentResponse::from);
    }

    /** Загружает квартиру по ID или бросает ResourceNotFoundException (404). */
    public Apartment findById(Long id) {
        return apartmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Apartment not found: " + id));
    }

    /** Загружает квартиру и проверяет, что текущий пользователь — её владелец. */
    private Apartment getOwnedApartment(Long id) {
        Apartment apartment = findById(id);
        User current = securityUtils.getCurrentUser();
        if (!apartment.getLandlord().getId().equals(current.getId())) {
            throw new AccessDeniedException("You don't own this apartment");
        }
        return apartment;
    }
}
