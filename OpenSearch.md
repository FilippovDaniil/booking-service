# OpenSearch — Полнотекстовый поиск

Документ описывает **все нюансы** подключения OpenSearch к Spring Boot 3.x проекту.  
Написан по опыту интеграции в `Pet_Ozon` и `Pet_Hospital`: каждая ловушка из раздела «Сводная таблица» была реально встречена.

---

## Статус в проекте Pet_Booking

Интегрировано: **2026-05-24**. Используется Вариант B (@ConditionalOnProperty + URL).

| Компонент | Путь | Назначение |
|---|---|---|
| `OpenSearchConfig` | `config/OpenSearchConfig.java` | Бин клиента (Вариант B) |
| `ApartmentDocument` | `search/ApartmentDocument.java` | Документ для индексирования |
| `ApartmentSearchService` | `search/ApartmentSearchService.java` | Интерфейс |
| `ApartmentSearchServiceImpl` | `search/ApartmentSearchServiceImpl.java` | Реализация (паттерны A+B) |
| `SearchInitializer` | `search/SearchInitializer.java` | Реиндексация при старте |
| `SearchController` | `controller/SearchController.java` | `GET /api/search/apartments` |

**Эндпоинт:** `GET /api/search/apartments?q=&city=&minPrice=&maxPrice=&page=&size=`  
**Разрешение:** `permitAll()` (без авторизации, как и GET /api/apartments)  
**Тесты:** `opensearch.enabled: false` в `application-test.yml` — все тесты без OpenSearch

---

## Мотивация

PostgreSQL `LIKE '%запрос%'` не использует B-tree индексы → полный скан таблицы на каждый запрос.  
OpenSearch решает это через инвертированный индекс + анализаторы текста:

| Возможность | PostgreSQL LIKE | OpenSearch |
|---|---|---|
| Full-text поиск | ❌ медленный LIKE | ✅ анализатор + TF-IDF |
| Поиск по нескольким полям | ❌ писать AND OR вручную | ✅ `multi_match` |
| Нечёткий поиск (опечатки) | ❌ нет | ✅ `fuzziness: AUTO` |
| Релевантность | ❌ нет | ✅ по умолчанию |
| Фильтры + полный текст | ❌ сложно | ✅ `bool.must` |

В этом проекте (~320 товаров) PostgreSQL справился бы, но OpenSearch показывает промышленный подход.

---

## Зависимости (build.gradle)

```groovy
// OpenSearch Java Client — Query DSL, индексирование, поиск
implementation 'org.opensearch.client:opensearch-java:2.15.0'

// Транспорт: Apache HttpClient 5.x
// ⚠️ КРИТИЧЕСКИ ВАЖНО: НЕ указывать версию явно!
// Spring Boot 3.4.4 BOM управляет версией (5.4.x).
// Если явно указать 5.3.x → NoClassDefFoundError: TlsSocketStrategy при старте приложения.
implementation 'org.apache.httpcomponents.client5:httpclient5'
```

## Зависимости (pom.xml — Maven)

```xml
<properties>
    <opensearch-java.version>2.15.0</opensearch-java.version>
    <httpclient5.version>5.3.1</httpclient5.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.opensearch.client</groupId>
        <artifactId>opensearch-java</artifactId>
        <version>${opensearch-java.version}</version>
    </dependency>
    <!-- ⚠️ В отличие от Gradle/Boot 3.4.x, в Spring Boot 3.2.x httpclient5
         НЕ управляется BOM — версию нужно указывать явно -->
    <dependency>
        <groupId>org.apache.httpcomponents.client5</groupId>
        <artifactId>httpclient5</artifactId>
        <version>${httpclient5.version}</version>
    </dependency>
</dependencies>
```

**Правило:** проверяй, управляет ли твоя версия Spring Boot BOM httpclient5.
```bash
# Посмотреть, что даёт BOM:
mvn dependency:list | grep httpclient5
# Если ничего — укажи версию явно
```

---

### Почему не `RestClientTransport`

Официальные гайды и AI-ассистенты часто показывают устаревший вариант:

```java
// ❌ НЕ РАБОТАЕТ в Spring Boot 3.x:
RestClient restClient = RestClient.builder(new HttpHost("localhost", 9200)).build();
OpenSearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
```

**Причина:** `RestClientTransport` требует `opensearch-rest-client`, который тянет `httpclient` 4.x  
(`org.apache.http`). В Spring Boot 3.x зависимость `httpclient` (4.x) **удалена из BOM**.

Правильный подход — `ApacheHttpClient5TransportBuilder` (httpclient5):

```java
// ✅ ПРАВИЛЬНО для Spring Boot 3.x:
HttpHost httpHost = new HttpHost(scheme, host, port);
OpenSearchTransport transport = ApacheHttpClient5TransportBuilder
        .builder(httpHost)
        .setMapper(new JacksonJsonpMapper())
        .build();
```

---

## Конфигурация — два варианта

### Вариант A: всегда включён (Pet_Ozon — simple)

```java
package com.example.marketplace.config;

import org.apache.hc.core5.http.HttpHost;          // ← httpclient5, не org.apache.http!
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenSearchConfig {

    @Value("${opensearch.host:localhost}")
    private String host;

    @Value("${opensearch.port:9200}")
    private int port;

    @Value("${opensearch.scheme:http}")
    private String scheme;

    @Bean
    public OpenSearchClient openSearchClient() {
        // ⚠️ ВАЖНО: в httpclient5 порядок параметров HttpHost — (scheme, host, port)
        // В httpclient4 было: (host, port, scheme) — легко перепутать, результат: NPE
        HttpHost httpHost = new HttpHost(scheme, host, port);

        OpenSearchTransport transport = ApacheHttpClient5TransportBuilder
                .builder(httpHost)
                .setMapper(new JacksonJsonpMapper())  // Jackson для сериализации JSON
                .build();
        return new OpenSearchClient(transport);
    }
}
```

**Ключевые свойства** (`application.properties`):
```properties
# По умолчанию localhost:9200 — для локальной разработки без Docker
opensearch.host=localhost
opensearch.port=9200
opensearch.scheme=http
```

Spring Boot автоматически конвертирует переменные окружения:
```
OPENSEARCH_HOST → opensearch.host
OPENSEARCH_PORT → opensearch.port
```

---

### Вариант B: @ConditionalOnProperty + URL (Pet_Hospital — отключаемый)

Используется когда OpenSearch нужно отключать в тестах (или по feature-flag).

```java
package com.hospital.config;

import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.net.URI;

@Configuration
@ConditionalOnProperty(name = "opensearch.enabled", havingValue = "true", matchIfMissing = true)
// matchIfMissing=true → включён по умолчанию; выключается только явным enabled=false
public class OpenSearchConfig {

    @Value("${opensearch.url:http://localhost:9200}")
    private String opensearchUrl;  // один параметр вместо трёх (host+port+scheme)

    @Bean
    public OpenSearchClient openSearchClient() {
        URI uri = URI.create(opensearchUrl);
        String scheme = uri.getScheme() == null ? "http"      : uri.getScheme();
        String host   = uri.getHost()   == null ? "localhost" : uri.getHost();
        int    port   = uri.getPort()   == -1   ? 9200        : uri.getPort();

        HttpHost httpHost = new HttpHost(scheme, host, port);
        OpenSearchTransport transport = ApacheHttpClient5TransportBuilder
                .builder(httpHost)
                .setMapper(new JacksonJsonpMapper())
                .build();
        return new OpenSearchClient(transport);
    }
}
```

**application.yml:**
```yaml
opensearch:
  enabled: true
  url: ${OPENSEARCH_URL:http://localhost:9200}
```

**application-test.yml:**
```yaml
opensearch:
  enabled: false  # бин OpenSearchClient не создаётся → все 200+ тестов без OpenSearch
```

| | Вариант A (host/port/scheme) | Вариант B (URL) |
|---|---|---|
| Параметров | 3 | 1 |
| ENV в K8s/Docker | 3 переменных | 1 переменная `OPENSEARCH_URL` |
| Отключение в тестах | нет | `enabled: false` |
| Сложность | простая | немного сложнее (парсинг URI) |

---

## Модель документа — ProductDocument.java

```java
package com.example.marketplace.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDocument {
    private String id;           // _id в OpenSearch — ВСЕГДА String (даже если в БД Long)
    private String name;
    private String description;
    private Double price;        // Double, не BigDecimal — JSON не поддерживает BigDecimal напрямую
    private String category;     // keyword-поле: анализатор не применяется → точный match через term
    private String shopName;
    private Integer stockQuantity;
}
```

**Это не JPA-сущность.** Обычный POJO — OpenSearch сам создаёт динамический маппинг.

Маппинг, который создаст OpenSearch автоматически:
- `name`, `description` → `text` (анализируется, разбивается на токены)
- `category` → `text` + `keyword` (dual mapping: поиск + точный match)
- `price` → `float`

---

## ProductSearchService — ключевые паттерны

## Два паттерна graceful degradation

### Паттерн A: try-catch в каждом методе (Pet_Ozon)

```java
public void indexProduct(Product product) {
    try {
        openSearchClient.index(r -> r
                .index(INDEX)
                .id(String.valueOf(product.getId()))
                .document(toDocument(product)));
    } catch (Exception e) {
        // НЕ бросаем исключение — каталог PostgreSQL продолжает работать
        log.warn("Failed to index product id={}: {}", product.getId(), e.getMessage());
    }
}
```

Если OpenSearch недоступен:
- `GET /api/products` — работает (PostgreSQL + JPA)
- `GET /api/search/products` — возвращает пустую страницу, не 500

---

### Паттерн B: @Autowired(required=false) + null-check (Pet_Hospital)

Работает в паре с `@ConditionalOnProperty`. Когда бин не создаётся — Spring инжектирует `null`.

```java
@Slf4j
@Service
public class SearchServiceImpl implements SearchService {

    @Autowired(required = false)   // null когда opensearch.enabled=false
    private OpenSearchClient client;

    @PostConstruct
    public void ensureIndexes() {
        if (client == null) return;  // ← guard в начале каждого метода
        createIndexIfAbsent("patients");
        createIndexIfAbsent("doctors");
    }

    @Override
    public void indexPatient(PatientDocument doc) {
        if (client == null) return;
        try {
            client.index(r -> r.index("patients").id(doc.getId()).document(doc));
        } catch (Exception e) {
            log.warn("OpenSearch index failed: {}", e.getMessage());
        }
    }

    @Override
    public List<PatientDocument> searchPatients(String query) {
        if (client == null) return Collections.emptyList();
        // ...
    }
}
```

Когда вызывается `indexPatient` из `PatientServiceImpl`, тоже нужна защита:

```java
@Service
public class PatientServiceImpl {

    @Autowired(required = false)  // ← не ломает тесты без OpenSearch
    private SearchService searchService;

    public PatientResponse create(CreatePatientRequest req) {
        Patient saved = patientRepository.save(patient);
        if (searchService != null) indexPatient(saved);  // или через null-check внутри
        return mapper.toResponse(saved);
    }
}
```

| | Паттерн A (try-catch) | Паттерн B (null-check) |
|---|---|---|
| Бин всегда создаётся | да | нет (conditional) |
| OpenSearch упал в рантайме | перехватывает | не перехватывает (бин есть, но сервер упал) |
| Тесты без OpenSearch | нужен мок | просто enabled=false |
| Когда выбрать | OpenSearch всегда запущен | OpenSearch опционален (тесты, dev) |

Паттерны не взаимоисключают — B часто используется с A внутри методов.

---

### @PostConstruct ensureIndex()

```java
@PostConstruct
public void ensureIndex() {
    try {
        boolean exists = openSearchClient.indices().exists(r -> r.index("products")).value();
        if (!exists) {
            openSearchClient.indices().create(r -> r.index("products"));
            log.info("OpenSearch index 'products' created");
        }
    } catch (Exception e) {
        log.warn("OpenSearch unavailable at startup: {}", e.getMessage());
    }
}
```

### multi_match с boost по полям

```java
// Простой multi_match — одинаковый вес
Query q = Query.of(q -> q.multiMatch(m -> m
        .fields(List.of("name", "description"))
        .query(query)));

// multi_match с boost — fullName важнее department
// ⚠️ boost указывается через суффикс ^N в имени поля
Query q = Query.of(q -> q.multiMatch(m -> m
        .query(query)
        .fields("fullName^3", "specialization", "department")));
// fullName в 3 раза важнее остальных полей при ранжировании
```

---

### Построение поискового запроса

```java
public Page<ProductDocument> search(String query, String category,
                                     BigDecimal minPrice, BigDecimal maxPrice,
                                     Pageable pageable) {
    try {
        List<Query> clauses = new ArrayList<>();

        if (query != null && !query.isBlank()) {
            // multi_match: ищет по двум полям одновременно, объединяет релевантность
            clauses.add(Query.of(q -> q.multiMatch(m -> m
                    .fields(List.of("name", "description"))
                    .query(query))));
        }
        if (category != null && !category.isBlank()) {
            // ⚠️ ЛОВУШКА: .value() принимает FieldValue, не String!
            clauses.add(Query.of(q -> q.term(t -> t
                    .field("category")
                    .value(FieldValue.of(category)))));   // ← обязательно FieldValue.of()
        }
        if (minPrice != null) {
            // ⚠️ ЛОВУШКА: диапазон принимает JsonData, не BigDecimal!
            clauses.add(Query.of(q -> q.range(r -> r
                    .field("price")
                    .gte(JsonData.of(minPrice.doubleValue())))));
        }
        if (maxPrice != null) {
            clauses.add(Query.of(q -> q.range(r -> r
                    .field("price")
                    .lte(JsonData.of(maxPrice.doubleValue())))));
        }

        // Нет фильтров → match_all. Есть → bool.must (AND всех условий)
        Query finalQuery = clauses.isEmpty()
                ? Query.of(q -> q.matchAll(m -> m))
                : Query.of(q -> q.bool(b -> b.must(clauses)));

        SearchRequest request = new SearchRequest.Builder()
                .index("products")
                .from((int) pageable.getOffset())   // OFFSET для пагинации
                .size(pageable.getPageSize())         // LIMIT
                .query(finalQuery)
                .build();

        SearchResponse<ProductDocument> response =
                openSearchClient.search(request, ProductDocument.class);

        List<ProductDocument> hits = response.hits().hits().stream()
                .map(h -> h.source())
                .toList();
        long total = response.hits().total() != null
                ? response.hits().total().value()
                : hits.size();

        return new PageImpl<>(hits, pageable, total);
    } catch (Exception e) {
        log.warn("OpenSearch search failed: {}", e.getMessage());
        return Page.empty(pageable);   // graceful degradation
    }
}
```

---

## Стратегии тестирования

### Стратегия 1: null-клиент (юнит-тесты, без Docker)

Используется с паттерном B (`@Autowired(required=false)`). OpenSearch выключен через `opensearch.enabled=false` в `application-test.yml` → бин не создаётся → `SearchServiceImpl.client == null` → все методы — no-op.

```java
// SearchServiceTest.java — юнит-тест без моков и без Docker
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @InjectMocks
    private SearchServiceImpl searchService;
    // client не инжектируется → остаётся null

    @Test
    void searchPatients_whenClientNull_returnsEmptyList() {
        List<PatientDocument> result = searchService.searchPatients("test");
        assertThat(result).isEmpty();  // graceful no-op
    }

    @Test
    void indexPatient_whenClientNull_doesNotThrow() {
        assertThatCode(() -> searchService.indexPatient(
                PatientDocument.builder().id("1").fullName("Test").build()))
                .doesNotThrowAnyException();
    }
}
```

### Стратегия 2: Testcontainers (интеграционный тест с реальным OpenSearch)

Запускает настоящий OpenSearch в Docker-контейнере на случайном порту.

```java
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "opensearch.enabled=true",
                // Отключаем тяжёлые зависимости, не нужные для теста поиска:
                "spring.cache.type=none",
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
                "spring.kafka.bootstrap-servers=localhost:19092",
                "spring.kafka.producer.transaction-id-prefix=",
                // Testcontainers PostgreSQL — отдельная БД для этого теста
                "spring.datasource.url=jdbc:tc:postgresql:15:///hospital_search_test",
                "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
                "spring.main.allow-bean-definition-overriding=true"
                // ↑ нужно если в проекте конфликтуют два @Primary бина
        })
class SearchIntegrationTest {

    @Container
    static GenericContainer<?> opensearch = new GenericContainer<>(
            DockerImageName.parse("opensearchproject/opensearch:2.17.0"))
            .withEnv("discovery.type", "single-node")
            .withEnv("DISABLE_SECURITY_PLUGIN", "true")
            .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withExposedPorts(9200)
            .withStartupTimeout(java.time.Duration.ofMinutes(3));

    @BeforeAll
    static void checkDocker() {
        // Пропустить тест если Docker недоступен (CI без Docker, Windows без TLS)
        Assumptions.assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "Skipping: Docker not available");
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        // Сообщаем Spring Boot URL реального контейнера (порт случайный)
        registry.add("opensearch.url",
                () -> "http://" + opensearch.getHost() + ":" + opensearch.getMappedPort(9200));
    }

    @Autowired
    private SearchService searchService;

    @Test
    void indexAndSearch() throws InterruptedException {
        searchService.indexPatient(PatientDocument.builder()
                .id("test-1").fullName("Иванов Сергей").active(true).build());

        // OpenSearch индексирует асинхронно — ждём
        Thread.sleep(1500);

        List<PatientDocument> results = searchService.searchPatients("Иванов");
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getFullName()).contains("Иванов");
    }
}
```

**Требования для запуска:**
- Docker Desktop запущен
- `OPENSEARCH_URL` не задан (DynamicPropertySource перезапишет)
- `pom.xml` содержит `testcontainers-bom` или явные зависимости testcontainers

### Сравнение стратегий

| Стратегия | Скорость | Docker нужен | Реальный OpenSearch | Когда |
|---|---|---|---|---|
| null-клиент (юнит) | мгновенно | нет | нет | Покрыть graceful no-op |
| Testcontainers (интеграционный) | ~2-3 мин | да | да | Проверить реальный index/search |
| EmbeddedOpenSearch | не существует | нет | нет | — |

---

## Критическая ловушка: LazyInitializationException в reindexAll

`AppConfig.initData()` — это `CommandLineRunner`. JPA-сессия создаётся и закрывается внутри  
каждого вызова репозитория. После `productRepository.findAll()` сессия закрыта.  
При обращении к lazy-ассоциациям (`product.getCategory().getName()`) Hibernate пытается  
инициализировать прокси — но сессии нет → `LazyInitializationException`.

```java
// ❌ ОШИБКА: после возврата из findAll() сессия закрыта
productSearchService.reindexAll(productRepository.findAll());
// → "Could not initialize proxy [Category#1] - no Session"
```

**Решение:** JOIN FETCH загружает ассоциации в рамках одного запроса — прокси не нужны:

```java
// В ProductRepository:
@Query("SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.seller")
List<Product> findAllForReindex();

// В AppConfig:
productSearchService.reindexAll(productRepository.findAllForReindex());
```

SQL, который генерирует Hibernate:
```sql
SELECT p.*, c.*, s.*
FROM products p
LEFT JOIN categories c ON c.id = p.category_id
LEFT JOIN users s ON s.id = p.seller_id
```

Категории и продавцы уже в памяти — никаких прокси.

---

## Стратегии индексирования

### Реиндексация при старте (Pet_Ozon)

```java
// AppConfig.java — CommandLineRunner
productSearchService.reindexAll(productRepository.findAllForReindex());
```

Плюсы: индекс всегда актуален после рестарта.  
Минусы: при большой БД замедляет старт приложения.

### Инкрементальное индексирование (Pet_Hospital)

Seed-данные из Flyway-миграций **не индексируются**. Только новые/обновлённые сущности:

```java
public PatientResponse create(CreatePatientRequest req) {
    Patient saved = patientRepository.save(patient);
    indexPatient(saved);   // только что созданный
    return mapper.toResponse(saved);
}

public PatientResponse update(Long id, UpdatePatientRequest req) {
    Patient saved = patientRepository.save(existing);
    indexPatient(saved);   // переиндексируем при обновлении
    return mapper.toResponse(saved);
}

public void softDelete(Long id) {
    patient.setActive(false);
    patientRepository.save(patient);
    if (searchService != null) searchService.deletePatient(String.valueOf(id));
}
```

Плюсы: быстрый старт, нет нагрузки на OpenSearch при деплое.  
Минусы: исторические данные не ищутся до явной реиндексации (или первого обновления).

---

## Интеграция в сервисы

При каждом изменении товара через `ProductService` или `SellerService`:

```java
// createProduct():
Product saved = productRepository.save(product);
productSearchService.indexProduct(saved);   // индексируем после сохранения

// updateProduct():
Product saved = productRepository.save(product);
productSearchService.indexProduct(saved);   // переиндексируем

// deleteProduct():
productRepository.deleteById(id);
productSearchService.removeProduct(id);     // удаляем из индекса
```

Синхронизация при старте (AppConfig):
```java
productSearchService.reindexAll(productRepository.findAllForReindex());
```

---

## Docker Compose

```yaml
opensearch:
  image: opensearchproject/opensearch:2.17.0
  environment:
    - discovery.type=single-node          # без кластеризации (dev режим)
    - DISABLE_SECURITY_PLUGIN=true         # HTTP без TLS и Basic Auth — ТОЛЬКО для dev!
    - bootstrap.memory_lock=false          # управляется через limits
    - OPENSEARCH_JAVA_OPTS=-Xms512m -Xmx512m   # фиксируем heap
  ports:
    - "9200:9200"
  volumes:
    - opensearch_data:/usr/share/opensearch/data
  healthcheck:
    # Вариант A: базовый — curl -f падает при status=red, но и при yellow тоже (нежелательно)
    # test: ["CMD-SHELL", "curl -f http://localhost:9200/_cluster/health || exit 1"]
    # Вариант B: надёжный — принимает green И yellow (yellow = норма для single-node)
    test: ["CMD-SHELL", "curl -s http://localhost:9200/_cluster/health | grep -qE '\"status\":\"(green|yellow)\"'"]
    interval: 20s
    timeout: 10s
    retries: 10
    start_period: 60s   # OpenSearch стартует медленно — даём 60с перед первой проверкой

app:
  environment:
    OPENSEARCH_HOST: opensearch    # ← имя сервиса в docker-compose сети, не localhost!
    OPENSEARCH_PORT: "9200"
    OPENSEARCH_SCHEME: http
  depends_on:
    opensearch:
      condition: service_healthy   # ждём healthcheck перед стартом приложения
```

---

## Kubernetes — 08-opensearch.yaml

### vm.max_map_count — обязательное требование

OpenSearch падает без этой настройки:
```
max virtual memory areas vm.max_map_count [65530] is too low, increase to at least [262144]
```

В Rancher Desktop (k3s в Linux VM) устанавливается через privileged initContainer:

```yaml
initContainers:
  - name: sysctl-fix
    image: busybox:1.36
    command: ["sysctl", "-w", "vm.max_map_count=262144"]
    securityContext:
      privileged: true   # требуется для изменения параметра ядра
```

### imagePullPolicy для OpenSearch

```yaml
containers:
  - name: opensearch
    image: opensearchproject/opensearch:2.17.0
    imagePullPolicy: IfNotPresent   # ← НЕ Never!
```

`imagePullPolicy: Never` — только для кастомных образов (marketplace-app), которые мы загружаем вручную.  
Публичные образы (opensearch, postgres, grafana) используют `IfNotPresent`.

### initContainer wait-for-opensearch в 06-app.yaml

```yaml
initContainers:
  - name: wait-for-opensearch
    image: busybox:1.36
    command:
      - sh
      - -c
      - |
        until nc -z opensearch-service 9200; do
          echo "Waiting for opensearch-service:9200..."
          sleep 3
        done
        echo "OpenSearch is ready!"
```

Без этого: Spring Boot стартует до готовности OpenSearch, `AppConfig.reindexAll()` вызывается  
немедленно, первый документ не индексируется → цикл прерывается → индекс пуст.

### Имена сервисов

| Среда | Переменная | Значение |
|---|---|---|
| Docker Compose | `OPENSEARCH_HOST` | `opensearch` (имя сервиса) |
| Kubernetes | `OPENSEARCH_HOST` | `opensearch-service` (имя K8s Service) |

Не `localhost` — приложение и OpenSearch в разных контейнерах.

### Ресурсы

```yaml
resources:
  requests:
    memory: "600Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "1000m"
```

Без limits OpenSearch может занять всю память ноды и вытолкнуть другие поды.  
Фиксация heap (`-Xms512m -Xmx512m`) предотвращает динамическое расширение выше лимита.

---

## Поисковый контроллер

```
GET /api/search/products
```

| Параметр | Тип | Обязательный | Описание |
|---|---|---|---|
| `q` | String | нет | Полнотекстовый запрос по `name` и `description` |
| `category` | String | нет | Точная фильтрация по категории |
| `minPrice` | BigDecimal | нет | Цена от |
| `maxPrice` | BigDecimal | нет | Цена до |
| `page` | int | нет | Номер страницы (от 0) |
| `size` | int | нет | Размер страницы (по умолчанию 20) |

Примеры:
```
GET /api/search/products?q=ноутбук
GET /api/search/products?category=Наушники&maxPrice=10000
GET /api/search/products?q=sony&minPrice=5000&maxPrice=30000&page=0&size=10
GET /api/search/products                   # match_all — все товары
```

Доступ: `permitAll()` — без авторизации (как и `GET /api/products`).

---

## Сводная таблица нюансов

| # | Проблема | Симптом | Решение |
|---|----------|---------|---------|
| 1 | `httpclient5:5.3.x` с явной версией | `NoClassDefFoundError: TlsSocketStrategy` | Убрать версию — BOM даёт 5.4.x |
| 2 | `RestClientTransport` (httpclient4) | `ClassNotFoundException: org.apache.http.HttpHost` | Использовать `ApacheHttpClient5TransportBuilder` |
| 3 | Неверный порядок параметров `HttpHost` | NPE или неверный хост | httpclient5: `(scheme, host, port)` — отличается от 4.x |
| 4 | `TermQuery.value(String)` | Ошибка компиляции в opensearch-java 2.x | `.value(FieldValue.of(category))` |
| 5 | `RangeQuery` с `BigDecimal` | Ошибка компиляции/runtime | `JsonData.of(price.doubleValue())` |
| 6 | `findAll()` в `reindexAll` | `LazyInitializationException: Category` | `findAllForReindex()` с `JOIN FETCH` |
| 7 | `vm.max_map_count` в K8s | OpenSearch выходит с ошибкой (Exit 78) | Privileged initContainer `sysctl` |
| 8 | `imagePullPolicy: Never` для OpenSearch | `ErrImageNeverPull` | `IfNotPresent` для публичных образов |
| 9 | Нет `wait-for-opensearch` initContainer | `reindexAll` вызван до готовности — индекс пуст | initContainer в `06-app.yaml` |
| 10 | `OPENSEARCH_HOST=localhost` в Docker/K8s | `Connection refused` — приложение ищет себя | Указать имя сервиса: `opensearch` / `opensearch-service` |
| 11 | `httpclient5` без версии в Spring Boot 3.2.x | `ClassNotFoundException` или несовместимая версия | В Boot 3.2.x BOM не управляет httpclient5 — указать явно (напр. `5.3.1`) |
| 12 | `healthcheck: curl -f` для OpenSearch | Контейнер не healthy — yellow воспринимается как сбой | Использовать `grep -qE '"status":"(green\|yellow)"'` |
| 13 | `@Autowired(required=false)` без null-check внутри сервисов | NPE в runtime при enabled=false | Добавить `if (searchService != null)` в каждое место вызова |
| 14 | Seed-данные Flyway не попадают в индекс | Исторические записи не находятся | Добавить `reindexAll` при старте, или смириться с инкрементальной моделью |
| 15 | `spring.main.allow-bean-definition-overriding` в интеграционных тестах | `BeanDefinitionOverrideException` при двух `@Primary` бинах | Добавить `allow-bean-definition-overriding: true` в `application-test.yml` |

---

## Проверка работоспособности

```powershell
# Прямой доступ к OpenSearch через port-forward (K8s)
kubectl port-forward -n marketplace svc/opensearch-service 9200:9200

# Статус кластера — должен быть green или yellow
curl http://localhost:9200/_cluster/health

# Количество документов в индексе products
curl http://localhost:9200/products/_count
# Ожидаем: {"count":320,...}

# Поиск через port-forward (K8s)
curl "http://localhost:9200/products/_search?q=ноутбук&size=3&pretty"

# Поиск через API приложения
curl "http://localhost:30667/api/search/products?q=ноутбук&size=5"
```

Docker Compose — OpenSearch доступен напрямую:
```bash
curl http://localhost:9200/_cluster/health
curl http://localhost:9200/products/_count
```

---

## Известные проблемы в Pet_Booking

| # | Проблема | Симптом | Решение |
|---|----------|---------|---------|
| 16 | `httpclient5` без версии, Boot 3.2.x | `ClassNotFoundException` | В Boot 3.4.5 BOM управляет — версию не указывать |
| 17 | `build-and-load.ps1` encoding в PowerShell 5.1 | ParseException (кириллица) | Запускать шаги вручную: docker build → docker save → rdctl shell docker load → kubectl apply |
