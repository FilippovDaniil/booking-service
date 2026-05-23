package com.booking.search;

import com.booking.repository.ApartmentRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Реиндексация квартир при старте приложения.
 * Запускается после инициализации всех бинов.
 *
 * Использует findAllForReindex() с JOIN FETCH landlord — защита от LazyInitializationException:
 * после возврата из findAll() JPA-сессия закрыта, и обращение к lazy-полю landlord вызовет исключение.
 * JOIN FETCH загружает landlord в одном запросе — прокси не нужны.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchInitializer {

    private final ApartmentRepository apartmentRepository;

    // null когда opensearch.enabled=false (тесты, dev без OpenSearch)
    @Autowired(required = false)
    private ApartmentSearchService searchService;

    @PostConstruct
    public void reindexOnStartup() {
        if (searchService == null) return;
        try {
            var apartments = apartmentRepository.findAllForReindex();
            searchService.reindexAll(apartments);
            log.info("OpenSearch: reindexed {} apartments on startup", apartments.size());
        } catch (Exception e) {
            log.warn("OpenSearch: startup reindex failed: {}", e.getMessage());
        }
    }
}
