# Pet Booking Service

Учебный backend-проект — REST API для платформы бронирования жилья (аналог Airbnb).  
Написан на **Java 17 + Spring Boot 3.4.5**.

> Цель проекта: изучить построение production-ready REST API со всем стандартным стеком:
> аутентификация, роли, работа с БД, кэш, очереди сообщений, полнотекстовый поиск, тесты, мониторинг, CI/CD.

---

## Содержание

1. [Что умеет приложение](#1-что-умеет-приложение)
2. [Технологический стек](#2-технологический-стек)
3. [Архитектура](#3-архитектура)
4. [Слоистая архитектура Spring Boot](#4-слоистая-архитектура-spring-boot)
5. [Схема базы данных](#5-схема-базы-данных)
6. [Как работает безопасность (JWT)](#6-как-работает-безопасность-jwt)
7. [Жизненный цикл бронирования](#7-жизненный-цикл-бронирования)
8. [Структура проекта](#8-структура-проекта)
9. [Быстрый старт](#9-быстрый-старт)
10. [API — все эндпоинты](#10-api--все-эндпоинты)
11. [Примеры запросов (curl)](#11-примеры-запросов-curl)
12. [Тесты](#12-тесты)
13. [Мониторинг: Loki + Grafana](#13-мониторинг-loki--grafana)
14. [CI/CD через GitLab](#14-cicd-через-gitlab)
15. [Переменные окружения](#15-переменные-окружения)
16. [Запуск в Kubernetes (Rancher Desktop)](#16-запуск-в-kubernetes-rancher-desktop)

---

## 1. Что умеет приложение

| Функция | Детали |
|---------|--------|
| **Аутентификация** | Регистрация, вход, обновление и отзыв JWT-токенов |
| **Роли пользователей** | `CLIENT` — арендует; `LANDLORD` — сдаёт; `ADMIN` — администрирует |
| **Квартиры** | CRUD для арендодателей; публичный поиск с фильтрами по городу, датам, гостям, цене |
| **Полнотекстовый поиск** | OpenSearch 2.17.0: поиск по названию и описанию с фильтрами по городу и цене |
| **Бронирования** | Полный жизненный цикл: создание → подтверждение → отмена / истечение / завершение |
| **Защита от двойного бронирования** | Пессимистическая блокировка на уровне БД при создании брони |
| **Отзывы** | Клиент оставляет отзыв только после завершённой брони; рейтинг квартиры обновляется автоматически |
| **События** | Kafka-события при каждом изменении статуса брони |
| **Уведомления** | Kafka-очередь для уведомлений пользователям (заглушка → в продакшне email) |
| **Планировщик** | Автоматически истекают PENDING-брони (каждые 30 сек) и завершаются CONFIRMED (в 1:00 ночи) |
| **Мониторинг** | Логи в Loki, дашборды в Grafana |
| **Документация** | Swagger UI с возможностью протестировать прямо в браузере |

---

## 2. Технологический стек

| Компонент | Технология | Зачем используется |
|-----------|------------|-------------------|
| Язык | **Java 17** | LTS-версия с современными фичами (records, sealed classes, text blocks) |
| Фреймворк | **Spring Boot 3.4.5** | Автоконфигурация, встроенный Tomcat, огромная экосистема |
| Безопасность | **Spring Security + JJWT 0.12.6** | Фильтры, роли, JWT-токены без хранения сессий на сервере |
| База данных | **PostgreSQL 16** | Надёжная СУБД с поддержкой ACID, индексов, блокировок |
| ORM | **Spring Data JPA / Hibernate** | Маппинг Java-классов на таблицы, JPQL-запросы, ленивая загрузка |
| Полнотекстовый поиск | **OpenSearch 2.17.0** | Инвертированный индекс, multi_match, фильтры по цене и городу |
| Кэш токенов | **Redis 7** | Хранение refresh-токенов с TTL; при недоступности — in-memory fallback |
| Очередь сообщений | **Apache Kafka (Confluent 7.6.0)** | Асинхронная публикация событий бронирования и уведомлений |
| Сборка | **Gradle 8** | Управление зависимостями, сборка JAR, запуск тестов |
| Документация API | **SpringDoc OpenAPI / Swagger UI** | Автогенерация документации из аннотаций контроллеров |
| Тесты | **JUnit 5 + Mockito + AssertJ + H2** | Юнит-тесты с моками; интеграционные — с H2 вместо PostgreSQL |
| Логирование | **Logback + Loki4j** | Структурированные логи с отправкой в Loki |
| Мониторинг | **Grafana + Loki** | Визуализация и поиск по логам |
| Контейнеризация | **Docker + Docker Compose** | Одна команда поднимает всё: приложение + БД + Kafka + OpenSearch + мониторинг |
| Оркестрация | **Kubernetes (Rancher Desktop / k3s)** | Локальный K8s-кластер для тестирования production-деплоя |
| CI/CD | **GitLab CI** | Автоматические сборка, тесты, деплой |

---

## 3. Архитектура

### Общая схема

```
┌──────────────────────────────────────────────────────────────────┐
│                        HTTP-клиенты                              │
│              (браузер, Postman, мобильное приложение)            │
└─────────────────────────────┬────────────────────────────────────┘
                              │  HTTP / JSON  (порт 8555)
┌─────────────────────────────▼────────────────────────────────────┐
│                    Spring Boot Application                        │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │                 Spring Security Filter Chain              │    │
│  │  JwtAuthenticationFilter → читает JWT → заполняет        │    │
│  │  SecurityContext (кто делает запрос и какая у него роль)  │    │
│  └──────────────────────────────────────────────────────────┘    │
│                                                                   │
│  ┌───────────────┐    ┌──────────────┐    ┌─────────────────┐    │
│  │  Controllers  │───▶│   Services   │───▶│  Repositories   │    │
│  │  (HTTP слой)  │    │  (бизнес-    │    │  (доступ к БД)  │    │
│  │               │    │   логика)    │    │                 │    │
│  └───────────────┘    └──────┬───────┘    └────────┬────────┘    │
│                              │                     │             │
│                    ┌─────────┴──────┐              │             │
│                    │  Kafka + Search │              │             │
│                    │  (OpenSearch)   │              │             │
│                    └─────────┬──────┘              │             │
└──────────────────────────────┼─────────────────────┼─────────────┘
                               │                     │
          ┌────────────────────┼─────────────────────┼────────────┐
          │                    │                     │            │
    ┌─────▼──────┐  ┌──────────▼──┐  ┌──────▼──────┐  ┌─────────┐│
    │   Kafka    │  │  OpenSearch │  │    Redis    │  │Postgres ││
    │ (события   │  │  (поиск     │  │  (refresh   │  │(основные││
    │  брони и   │  │  квартир)   │  │   токены)   │  │  данные)││
    │  нотиф.)   │  │             │  │             │  │         ││
    └─────┬──────┘  └─────────────┘  └─────────────┘  └─────────┘│
          │                                                        │
    ┌─────▼──────────────────────────────────────────────────┐    │
    │  NotificationService (Kafka Consumer)                   │    │
    │  читает события и отправляет уведомления пользователям  │    │
    └─────────────────────────────────────────────────────────┘    │
                                                                    │
    ┌─────────────────────────────────────────────────────────┐    │
    │  Мониторинг: Loki (логи) → Grafana (дашборды)           │    │
    └─────────────────────────────────────────────────────────┘    │
          └─────────────────────────────────────────────────────────┘
```

### Паттерн «Producer-Consumer» через Kafka

```
BookingService ──publish──▶ Kafka Topic "booking-lifecycle" ──consume──▶ NotificationService
                                                                              │
                                                                         (логирует сейчас,
                                                                          в продакшне → email)
```

### OpenSearch: индексирование и поиск

```
ApartmentService ──create/update/delete──▶ ApartmentSearchServiceImpl ──▶ OpenSearch Index "apartments"
                                                                                    │
SearchController GET /api/search/apartments ◀──────── multi_match + filters ────────┘
```

При каждом создании/обновлении/удалении квартиры `ApartmentService` синхронно обновляет индекс.  
При старте `SearchInitializer` реиндексирует все квартиры из PostgreSQL.

---

## 4. Слоистая архитектура Spring Boot

Spring Boot-приложения принято разбивать на слои. Каждый слой отвечает за свою задачу и знает только о следующем слое вниз.

```
┌─────────────────────────────────────────────┐
│  Controller  (HTTP: принять запрос, вернуть ответ)
│  - Валидация входных данных (@Valid)
│  - Преобразование DTO ↔ HTTP
│  - Вызов сервисного слоя
└──────────────────┬──────────────────────────┘
                   │ вызывает
┌──────────────────▼──────────────────────────┐
│  Service  (бизнес-логика)
│  - Правила: кто может что делать
│  - Расчёты (цена, рейтинг)
│  - Транзакции (@Transactional)
│  - Публикация Kafka-событий
│  - Индексирование в OpenSearch
└──────────────────┬──────────────────────────┘
                   │ вызывает
┌──────────────────▼──────────────────────────┐
│  Repository  (доступ к данным)
│  - SQL-запросы через JPA/JPQL
│  - Кастомные @Query методы
│  - Пессимистические блокировки
└──────────────────┬──────────────────────────┘
                   │ работает с
┌──────────────────▼──────────────────────────┐
│  Entity  (JPA-сущности = таблицы БД)
│  - @Entity, @Table, @Column
│  - Связи: @ManyToOne, @OneToOne
│  - Аудит: @CreationTimestamp
└─────────────────────────────────────────────┘
```

**DTO (Data Transfer Object)** — отдельные классы для входящих запросов и ответов. Зачем:
- Контроллер не выставляет поля Entity наружу (нет пароля в ответе)
- Валидация (@NotBlank, @Email) — на DTO, а не на Entity
- Entity меняется независимо от API-контракта

---

## 5. Схема базы данных

### Таблицы и связи

```
users
├── id          BIGSERIAL PRIMARY KEY
├── email       VARCHAR UNIQUE NOT NULL      ← логин пользователя
├── password    VARCHAR NOT NULL             ← BCrypt-хэш
├── first_name  VARCHAR NOT NULL
├── last_name   VARCHAR NOT NULL
├── role        VARCHAR NOT NULL             ← CLIENT / LANDLORD / ADMIN
├── enabled     BOOLEAN DEFAULT true         ← false = заблокирован
├── created_at  TIMESTAMP
└── updated_at  TIMESTAMP

apartments
├── id              BIGSERIAL PRIMARY KEY
├── landlord_id     BIGINT → users.id        ← кто сдаёт
├── name            VARCHAR NOT NULL
├── description     TEXT
├── city            VARCHAR NOT NULL          ← индекс для поиска
├── street          VARCHAR
├── house_number    VARCHAR
├── price_per_night DECIMAL(10,2) NOT NULL
├── max_guests      INT NOT NULL
├── active          BOOLEAN DEFAULT true      ← false = снято с публикации
├── average_rating  DOUBLE DEFAULT 0.0        ← денормализован, пересчитывается при отзыве
├── created_at      TIMESTAMP
└── updated_at      TIMESTAMP

apartment_amenities                           ← удобства (Wi-Fi, парковка, ...)
├── apartment_id    BIGINT → apartments.id
└── amenity         VARCHAR

apartment_photos                              ← ссылки на фото
├── apartment_id    BIGINT → apartments.id
└── photo_url       VARCHAR

bookings
├── id              BIGSERIAL PRIMARY KEY
├── client_id       BIGINT → users.id         ← кто бронирует
├── apartment_id    BIGINT → apartments.id    ← что бронирует (индекс)
├── start_date      DATE NOT NULL
├── end_date        DATE NOT NULL
├── guests_count    INT NOT NULL
├── total_price     DECIMAL(10,2) NOT NULL    ← рассчитан при создании
├── status          VARCHAR NOT NULL          ← PENDING/CONFIRMED/...
├── created_at      TIMESTAMP                 ← индекс для планировщика
├── updated_at      TIMESTAMP
└── confirmed_at    TIMESTAMP                 ← NULL до подтверждения

reviews
├── id              BIGSERIAL PRIMARY KEY
├── booking_id      BIGINT → bookings.id UNIQUE   ← один отзыв = одна бронь
├── rating          INT NOT NULL              ← 1-5
├── comment         TEXT
└── created_at      TIMESTAMP
```

### Связи Entity

```
User ──(1:N)── Apartment   (один арендодатель — много квартир)
User ──(1:N)── Booking     (один клиент — много броней)
Apartment ──(1:N)── Booking   (одна квартира — много броней)
Booking ──(1:1)── Review   (одна бронь — один отзыв, UNIQUE constraint)
```

### Важные индексы

| Таблица | Индекс | Зачем |
|---------|--------|-------|
| `users` | `email` | Быстрый поиск при логине |
| `apartments` | `city` | Фильтр по городу в поиске |
| `bookings` | `(apartment_id, start_date, end_date)` | Проверка пересечения дат |
| `bookings` | `client_id` | «Мои брони» |
| `bookings` | `status` | Планировщик: ищет PENDING/CONFIRMED |

---

## 6. Как работает безопасность (JWT)

### Что такое JWT

**JWT (JSON Web Token)** — строка из трёх частей, разделённых точкой:
```
eyJhbGciOiJIUzI1NiJ9   ← header: алгоритм подписи (base64)
.
eyJzdWIiOiIxIiwiZW1haWwiOiJ1c2VyQHRlc3QuY29tIn0   ← payload: данные (base64)
.
SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c   ← signature: HMAC-SHA256 подпись
```

Сервер **не хранит** access-токен — он просто проверяет подпись при каждом запросе. Это делает архитектуру **stateless**.

### Схема аутентификации

```
Регистрация / Логин:
  Клиент                    Сервер
    │── POST /api/auth/login ──▶│
    │   {email, password}       │ 1. Проверить пароль (BCrypt)
    │                           │ 2. Сгенерировать access-токен (JWT, 15 мин)
    │                           │ 3. Сгенерировать refresh-токен (UUID, 30 дней)
    │                           │ 4. Сохранить refresh-токен в Redis
    │◀─ {accessToken, refresh} ─│

Обычный запрос:
  Клиент                    Сервер
    │── GET /api/bookings ──────▶│
    │   Authorization:           │ JwtAuthenticationFilter:
    │   Bearer eyJhbG...         │ 1. Извлечь токен из заголовка
    │                            │ 2. Проверить подпись и срок
    │                            │ 3. Положить User в SecurityContext
    │                            │ BookingService:
    │                            │ 4. securityUtils.getCurrentUser()
    │◀─ [{booking1}, ...]  ──────│

Обновление токена (раз в 15 мин):
  Клиент                    Сервер
    │── POST /api/auth/refresh ──▶│
    │   {refreshToken: "uuid"}    │ 1. Найти userId по токену в Redis
    │                             │ 2. Загрузить User из БД
    │                             │ 3. Выдать новый access-токен
    │◀─ {newAccessToken, refresh}─│  (refresh-токен тот же)

Выход:
  Клиент                    Сервер
    │── POST /api/auth/logout ───▶│
    │   {refreshToken: "uuid"}    │ Удалить refresh-токен из Redis
    │◀─ 204 No Content ───────────│
```

### Два токена — зачем?

| | Access Token | Refresh Token |
|--|--------------|---------------|
| Тип | JWT (подписанная строка) | UUID (случайный) |
| Срок | 15 минут | 30 дней |
| Где хранить на клиенте | В памяти приложения | В httpOnly cookie |
| Где хранится на сервере | Нигде (stateless) | В Redis с TTL |
| Для чего | Авторизация каждого запроса | Только для получения нового access |
| Отзыв | Невозможен до истечения | Мгновенный (удаляем из Redis) |

**Почему короткий access-токен?** Если токен украдут, он будет работать не более 15 минут. При выходе (`/logout`) refresh-токен удаляется из Redis — пользователь не сможет получить новый access, а значит следующий запрос через 15 минут упадёт с 401.

### Роли и @PreAuthorize

Spring Security хранит роль пользователя в `SecurityContext`. Аннотация `@PreAuthorize` проверяет её до вызова метода:

```java
@PreAuthorize("hasRole('LANDLORD')")   // только LANDLORD
@PreAuthorize("hasRole('ADMIN')")      // только ADMIN
// без аннотации — любой аутентифицированный
```

В `CustomUserDetailsService` роль оборачивается в префикс: `"CLIENT"` → `"ROLE_CLIENT"`.  
`hasRole('CLIENT')` проверяет наличие `"ROLE_CLIENT"` — это стандартное поведение Spring Security.

---

## 7. Жизненный цикл бронирования

### Диаграмма состояний

```
                    CLIENT создаёт бронь
                           │
                           ▼
                       ┌───────┐
                       │PENDING│  ← ожидает подтверждения клиентом
                       └───┬───┘
              ┌────────────┼────────────┐
              │            │            │
    CLIENT    │    прошло  │   CLIENT   │  LANDLORD
   confirm()  │   15 мин   │  cancel()  │  cancel()
              │            │            │
              ▼            ▼            ▼
        ┌─────────┐  ┌─────────┐  ┌──────────────────────┐
        │CONFIRMED│  │ EXPIRED │  │CANCELLED_BY_CLIENT или│
        └────┬────┘  └─────────┘  │CANCELLED_BY_LANDLORD  │
             │                    └──────────────────────┘
             │ дата выезда
             │ прошла (1:00 ночи)
             │
             ▼
        ┌─────────┐
        │COMPLETED│  ← клиент может оставить отзыв
        └─────────┘
```

### Правила переходов

| Переход | Кто может | Условие |
|---------|-----------|---------|
| `PENDING → CONFIRMED` | CLIENT (владелец брони) | Статус должен быть PENDING |
| `PENDING → EXPIRED` | Планировщик | Бронь не подтверждена за 15 минут |
| `PENDING/CONFIRMED → CANCELLED_BY_CLIENT` | CLIENT | Дата заезда ещё не наступила |
| `PENDING/CONFIRMED → CANCELLED_BY_LANDLORD` | LANDLORD (владелец квартиры) | Дата заезда ещё не наступила |
| `CONFIRMED → COMPLETED` | Планировщик | Дата выезда прошла |

### Защита от двойного бронирования

При создании брони `BookingRepository.findConflictingBookings()` использует **PESSIMISTIC_WRITE lock** — строки блокируются на уровне PostgreSQL до конца транзакции. Если два пользователя одновременно пытаются забронировать те же даты, второй будет ждать пока первый завершит транзакцию:

```
Transaction 1:             Transaction 2:
  findConflicting()          findConflicting() ← ЖДЁТ (lock)
  → пусто                                       ← lock снят
  save(booking1)             → находит booking1
  COMMIT                     → BookingConflictException (409)
```

Условие пересечения дат: `startA < endB AND endA > startB` — стандартный алгоритм проверки перекрытия отрезков.

---

## 8. Структура проекта

```
booking-service/
│
├── src/main/java/com/booking/
│   │
│   ├── BookingApplication.java          ← точка входа, запуск Spring Boot
│   │
│   ├── config/                          ← конфигурационные классы
│   │   ├── SecurityConfig.java          ← фильтры, роли, CORS, BCrypt
│   │   ├── KafkaConfig.java             ← KafkaTemplate, ProducerFactory
│   │   ├── RedisConfig.java             ← StringRedisTemplate (отключён в тестах)
│   │   ├── SchedulingConfig.java        ← включает @Scheduled
│   │   ├── OpenApiConfig.java           ← Swagger UI + Bearer Auth схема
│   │   └── OpenSearchConfig.java        ← Бин OpenSearchClient (@ConditionalOnProperty)
│   │
│   ├── controller/                      ← HTTP-слой: принимают запросы, возвращают ответы
│   │   ├── AuthController.java          ← /api/auth/** (регистрация, логин, токены)
│   │   ├── ApartmentController.java     ← /api/apartments/** (поиск и CRUD квартир)
│   │   ├── BookingController.java       ← /api/bookings/** (создание и управление бронями)
│   │   ├── ReviewController.java        ← /api/reviews, /api/apartments/{id}/reviews
│   │   ├── UserController.java          ← /api/users/** (профиль, блокировка)
│   │   ├── AdminController.java         ← /api/admin/** (статистика, принудительная смена статуса)
│   │   └── SearchController.java        ← /api/search/apartments (полнотекстовый поиск)
│   │
│   ├── dto/                             ← Data Transfer Objects (что приходит и уходит через API)
│   │   ├── request/                     ← входящие данные (с валидацией @Valid)
│   │   │   ├── RegisterRequest.java
│   │   │   ├── LoginRequest.java
│   │   │   ├── ApartmentRequest.java
│   │   │   ├── BookingRequest.java
│   │   │   ├── ReviewRequest.java
│   │   │   ├── UpdateProfileRequest.java
│   │   │   ├── RefreshTokenRequest.java
│   │   │   ├── LogoutRequest.java
│   │   │   └── ChangeStatusRequest.java
│   │   └── response/                    ← исходящие данные
│   │       ├── TokenResponse.java       ← {accessToken, refreshToken}
│   │       ├── ApartmentResponse.java
│   │       ├── BookingResponse.java
│   │       ├── ReviewResponse.java
│   │       ├── UserResponse.java
│   │       └── ErrorResponse.java       ← {status, message, timestamp}
│   │
│   ├── entity/                          ← JPA-сущности = таблицы в PostgreSQL
│   │   ├── User.java
│   │   ├── Apartment.java
│   │   ├── Booking.java
│   │   ├── Review.java
│   │   └── enums/
│   │       ├── Role.java                ← CLIENT, LANDLORD, ADMIN
│   │       └── BookingStatus.java       ← PENDING, CONFIRMED, CANCELLED_*, EXPIRED, COMPLETED
│   │
│   ├── event/                           ← Kafka-сообщения
│   │   ├── BookingEvent.java            ← событие изменения статуса брони
│   │   ├── NotificationEvent.java       ← запрос уведомления пользователю
│   │   └── BookingEventPublisher.java   ← отправляет события в Kafka (non-blocking)
│   │
│   ├── exception/                       ← кастомные исключения и их обработка
│   │   ├── GlobalExceptionHandler.java  ← @RestControllerAdvice: исключения → JSON-ответы
│   │   ├── ResourceNotFoundException.java  → HTTP 404
│   │   ├── BookingConflictException.java   → HTTP 409
│   │   ├── AccessDeniedException.java      → HTTP 403
│   │   └── InvalidOperationException.java  → HTTP 400
│   │
│   ├── repository/                      ← Spring Data JPA репозитории
│   │   ├── UserRepository.java
│   │   ├── ApartmentRepository.java     ← findAvailable() + findAllForReindex() с JOIN FETCH
│   │   ├── BookingRepository.java       ← findConflictingBookings() с PESSIMISTIC_WRITE lock
│   │   └── ReviewRepository.java        ← calculateAverageRating() — агрегатный запрос
│   │
│   ├── scheduler/
│   │   └── BookingExpirationScheduler.java  ← @Scheduled: истечение и завершение броней
│   │
│   ├── search/                          ← OpenSearch: полнотекстовый поиск
│   │   ├── ApartmentDocument.java       ← POJO-документ для индексирования
│   │   ├── ApartmentSearchService.java  ← интерфейс
│   │   ├── ApartmentSearchServiceImpl.java  ← реализация: index/search/delete (паттерны A+B)
│   │   └── SearchInitializer.java       ← @PostConstruct: реиндексация при старте
│   │
│   ├── security/
│   │   ├── JwtTokenProvider.java        ← генерация/валидация JWT (JJWT + HS256)
│   │   ├── JwtAuthenticationFilter.java ← OncePerRequestFilter: токен из заголовка → SecurityContext
│   │   ├── CustomUserDetailsService.java ← загрузка пользователя для Spring Security
│   │   └── SecurityUtils.java           ← getCurrentUser() — кто делает запрос
│   │
│   └── service/                         ← бизнес-логика
│       ├── AuthService.java             ← регистрация, логин, refresh, logout
│       ├── TokenService.java            ← JWT + refresh-токены (Redis + fallback)
│       ├── ApartmentService.java        ← CRUD квартир + индексирование в OpenSearch
│       ├── BookingService.java          ← жизненный цикл брони + планировщик
│       ├── ReviewService.java           ← создание отзывов + пересчёт рейтинга
│       ├── UserService.java             ← профиль, блокировка, удаление
│       └── NotificationService.java     ← Kafka consumer (заглушка под email)
│
├── src/main/resources/
│   ├── application.yml                  ← настройки приложения (включая opensearch.*)
│   └── logback-spring.xml               ← конфиг логирования: консоль + Loki
│
├── src/test/
│   ├── java/com/booking/
│   │   ├── BookingApplicationTest.java               ← smoke-тест: контекст поднимается
│   │   ├── security/
│   │   │   └── JwtTokenProviderTest.java             ← unit: генерация и валидация JWT
│   │   ├── service/
│   │   │   ├── AuthServiceTest.java                  ← unit: регистрация, логин
│   │   │   ├── BookingServiceTest.java               ← unit: жизненный цикл бронирования
│   │   │   ├── ApartmentServiceTest.java             ← unit: CRUD квартир
│   │   │   ├── ReviewServiceTest.java                ← unit: отзывы
│   │   │   ├── TokenServiceTest.java                 ← unit: Redis + fallback
│   │   │   └── UserServiceTest.java                  ← unit: управление пользователями
│   │   ├── scheduler/
│   │   │   └── BookingExpirationSchedulerTest.java   ← unit: планировщик
│   │   └── integration/
│   │       ├── TestConfig.java                       ← мок Redis для интеграционных тестов
│   │       ├── AuthControllerIntegrationTest.java    ← HTTP: регистрация, логин, токены
│   │       ├── ApartmentControllerIntegrationTest.java ← HTTP: CRUD квартир
│   │       ├── BookingControllerIntegrationTest.java ← HTTP: полный цикл бронирования
│   │       └── ReviewControllerIntegrationTest.java  ← HTTP: создание и удаление отзывов
│   └── resources/
│       ├── application-test.yml         ← H2 вместо PostgreSQL, отключены Redis/Kafka/OpenSearch
│       └── logback-test.xml             ← только консоль, без Loki (для тестов)
│
├── monitoring/
│   ├── loki-config.yml                  ← конфигурация Loki (tsdb, filesystem storage)
│   └── grafana/
│       └── provisioning/
│           └── datasources/
│               └── loki.yml             ← автоподключение Loki в Grafana при старте
│
├── rancher/                             ← Kubernetes-деплой для Rancher Desktop
│   ├── build-and-load.ps1               ← скрипт: сборка образа + загрузка в VM
│   └── k8s/                            ← K8s манифесты (применяются по порядку)
│       ├── 00-namespace.yaml            ← namespace: pet-booking
│       ├── 01-secrets.yaml             ← пароли (DB, JWT)
│       ├── 02-postgres.yaml            ← PostgreSQL 16: PVC + Deployment + Service
│       ├── 03-redis.yaml               ← Redis 7: Deployment + Service
│       ├── 04-zookeeper.yaml           ← ZooKeeper: Deployment + Service
│       ├── 05-kafka.yaml               ← Kafka: Deployment + Service
│       ├── 06-loki.yaml                ← Loki 3.1: ConfigMap + PVC + Deployment + Service
│       ├── 07-grafana.yaml             ← Grafana 11.1: 2×ConfigMap + PVC + Deployment + NodePort
│       ├── 08-opensearch.yaml          ← OpenSearch 2.17.0: PVC + Deployment + ClusterIP
│       ├── 09-app.yaml                 ← Spring Boot: ConfigMap + Deployment + NodePort (4 initContainers)
│       └── 10-dashboard.yaml           ← K8s Dashboard: RBAC + Deployment + NodePort
│
├── frontend/                            ← статичные HTML-страницы
├── postman_tests/                       ← коллекция Postman для ручного тестирования
├── Dockerfile                           ← сборка Docker-образа приложения
├── docker-compose.yml                   ← полный запуск: приложение + PostgreSQL + Redis + Kafka + OpenSearch + Loki + Grafana
├── docker-compose.prod.yml              ← продакшн деплой
├── rancher.md                           ← portable guide по K8s/Rancher Desktop (переиспользуется в других проектах)
├── OpenSearch.md                        ← подробная документация по интеграции OpenSearch
├── .gitlab-ci.yml                       ← CI/CD pipeline
├── build.gradle                         ← зависимости и настройки сборки Gradle
├── settings.gradle                      ← имя проекта (booking-service)
└── .env.example                         ← пример переменных окружения
```

---

## 9. Быстрый старт

### Что нужно установить

| Инструмент | Версия | Зачем |
|------------|--------|-------|
| Docker Desktop | Любая | Сборка образа и запуск всех контейнеров |
| Git | Любая | Клонирование репозитория |

> Java JDK устанавливать не нужно — приложение собирается внутри Docker.

### Шаг 1 — Клонировать проект

```bash
git clone https://gitlab.com/your-username/booking-service.git
cd booking-service
```

### Шаг 2 — Запустить всё одной командой

```bash
docker-compose up
```

Docker соберёт образ приложения и поднимет все контейнеры. Логи всех сервисов будут выводиться прямо в терминал — вы увидите строки Spring Boot вида `Started BookingApplication in X seconds`.

| Контейнер | Порт | Что это |
|-----------|------|---------|
| `booking-app` | **8555** | Spring Boot приложение |
| `booking-postgres` | 5432 | База данных PostgreSQL |
| `booking-redis` | 6379 | Кэш для refresh-токенов |
| `booking-zookeeper` | 2181 | Координатор кластера Kafka |
| `booking-kafka` | 9092 | Брокер сообщений |
| `booking-opensearch` | 9200 | Полнотекстовый поиск |
| `booking-loki` | 3100 | Сервер логов |
| `booking-grafana` | 3000 | Визуализация логов |

Первый запуск занимает больше времени — Docker скачивает базовые образы и собирает JAR (~2-5 минут).  
Hibernate автоматически создаст все таблицы в PostgreSQL при старте приложения (`ddl-auto: update`).

> **Запуск в фоне** (без логов в терминале): `docker-compose up -d`  
> После этого логи приложения: `docker-compose logs -f app`

### Шаг 3 — Открыть фронтенд

Фронтенд доступен прямо через браузер после запуска приложения. Начинайте с главной страницы — она сама перенаправит в нужный кабинет после входа:

| URL | Для кого | Что умеет |
|-----|----------|-----------|
| `http://localhost:8555/frontend/index.html` | Все | Главная страница: вход и регистрация |
| `http://localhost:8555/frontend/client.html` | `CLIENT` | Поиск квартир, создание броней, отзывы |
| `http://localhost:8555/frontend/landlord.html` | `LANDLORD` | Управление своими квартирами и бронями |
| `http://localhost:8555/frontend/admin.html` | `ADMIN` | Статистика и управление пользователями |

### Шаг 4 — Открыть Swagger UI

```
http://localhost:8555/swagger-ui.html
```

Здесь можно видеть все эндпоинты и тестировать их прямо в браузере.

**Как авторизоваться в Swagger:**
1. Нажать `POST /api/auth/register` → `Try it out` → ввести данные → `Execute`
2. Скопировать `accessToken` из ответа
3. Нажать кнопку **Authorize** вверху страницы
4. Вставить токен: `Bearer eyJhbGci...` → `Authorize`

Теперь все запросы будут с JWT-заголовком.

### Шаг 5 — Остановить

```bash
# Остановить контейнеры (данные сохранятся)
docker-compose down

# Остановить и удалить все данные (начать с нуля)
docker-compose down -v
```

---

## 10. API — все эндпоинты

### Аутентификация (`/api/auth`)

| Метод | Путь | Доступ | Описание | Код ответа |
|-------|------|--------|----------|------------|
| `POST` | `/api/auth/register` | Публичный | Регистрация (роль `CLIENT` или `LANDLORD`) | 201 Created |
| `POST` | `/api/auth/login` | Публичный | Вход по email+пароль | 200 OK |
| `POST` | `/api/auth/refresh` | Публичный | Обновить access-токен | 200 OK |
| `POST` | `/api/auth/logout` | JWT | Выйти (удалить refresh-токен) | 204 No Content |

### Квартиры (`/api/apartments`)

| Метод | Путь | Доступ | Описание | Код ответа |
|-------|------|--------|----------|------------|
| `GET` | `/api/apartments?city=...` | Публичный | Поиск свободных квартир с пагинацией | 200 OK |
| `GET` | `/api/apartments/{id}` | Публичный | Детали квартиры | 200 OK |
| `GET` | `/api/apartments/my` | `LANDLORD` | Мои квартиры | 200 OK |
| `POST` | `/api/apartments` | `LANDLORD` | Создать квартиру | 201 Created |
| `PUT` | `/api/apartments/{id}` | `LANDLORD` (владелец) | Обновить квартиру | 200 OK |
| `DELETE` | `/api/apartments/{id}` | `LANDLORD`/`ADMIN` | Деактивировать (soft delete) | 204 No Content |

**Параметры поиска (`GET /api/apartments`):**

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| `city` | String | Да | Город |
| `startDate` | LocalDate | Да | Дата заезда (формат: `2027-06-01`) |
| `endDate` | LocalDate | Да | Дата выезда |
| `guests` | int | Да | Количество гостей |
| `minPrice` | BigDecimal | Нет | Минимальная цена за ночь |
| `maxPrice` | BigDecimal | Нет | Максимальная цена за ночь |
| `page` | int | Нет | Номер страницы (по умолчанию 0) |
| `size` | int | Нет | Размер страницы (по умолчанию 20) |
| `sort` | String | Нет | Сортировка (например `pricePerNight,asc`) |

### Поиск (`/api/search`)

| Метод | Путь | Доступ | Описание | Код ответа |
|-------|------|--------|----------|------------|
| `GET` | `/api/search/apartments` | Публичный | Полнотекстовый поиск через OpenSearch | 200 OK |

**Параметры полнотекстового поиска (`GET /api/search/apartments`):**

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| `q` | String | Нет | Полнотекстовый запрос по названию и описанию |
| `city` | String | Нет | Фильтр по городу |
| `minPrice` | BigDecimal | Нет | Минимальная цена за ночь |
| `maxPrice` | BigDecimal | Нет | Максимальная цена за ночь |
| `page` | int | Нет | Номер страницы (по умолчанию 0) |
| `size` | int | Нет | Размер страницы (по умолчанию 20) |

Примеры:
```
GET /api/search/apartments?q=уютная+студия
GET /api/search/apartments?city=Москва&maxPrice=3000
GET /api/search/apartments?q=центр&city=Санкт-Петербург&minPrice=1000&maxPrice=5000&page=0&size=10
GET /api/search/apartments          ← match_all: все квартиры
```

> Поиск не требует авторизации (`permitAll()`). При недоступности OpenSearch возвращает пустую страницу (graceful degradation), не 500.

### Бронирования (`/api/bookings`)

| Метод | Путь | Доступ | Описание | Код ответа |
|-------|------|--------|----------|------------|
| `POST` | `/api/bookings` | JWT | Создать бронь → статус `PENDING` | 201 Created |
| `GET` | `/api/bookings` | JWT | Мои брони (CLIENT), брони квартир (LANDLORD), все (ADMIN) | 200 OK |
| `GET` | `/api/bookings/{id}` | JWT (участник) | Детали брони | 200 OK |
| `POST` | `/api/bookings/{id}/confirm` | `CLIENT` (владелец) | Подтвердить → `CONFIRMED` | 200 OK |
| `POST` | `/api/bookings/{id}/cancel` | `CLIENT` (владелец) | Отменить → `CANCELLED_BY_CLIENT` | 200 OK |
| `POST` | `/api/bookings/{id}/cancel-by-landlord` | `LANDLORD` (владелец квартиры) | Отменить → `CANCELLED_BY_LANDLORD` | 200 OK |

### Отзывы (`/api/reviews`)

| Метод | Путь | Доступ | Описание | Код ответа |
|-------|------|--------|----------|------------|
| `POST` | `/api/reviews` | `CLIENT` | Создать отзыв (только для `COMPLETED` брони) | 201 Created |
| `GET` | `/api/apartments/{id}/reviews` | Публичный | Отзывы квартиры | 200 OK |
| `DELETE` | `/api/reviews/{id}` | `ADMIN` | Удалить отзыв (модерация) | 204 No Content |

### Пользователи (`/api/users`)

| Метод | Путь | Доступ | Описание | Код ответа |
|-------|------|--------|----------|------------|
| `GET` | `/api/users/me` | JWT | Мой профиль | 200 OK |
| `PUT` | `/api/users/me` | JWT | Обновить имя/фамилию | 200 OK |
| `GET` | `/api/users` | `ADMIN` | Все пользователи | 200 OK |
| `PUT` | `/api/users/{id}/block` | `ADMIN` | Заблокировать пользователя | 200 OK |
| `DELETE` | `/api/users/{id}` | `ADMIN` | Удалить пользователя | 204 No Content |

### Администрирование (`/api/admin`)

| Метод | Путь | Доступ | Описание | Код ответа |
|-------|------|--------|----------|------------|
| `GET` | `/api/admin/stats` | `ADMIN` | Статистика: кол-во пользователей, квартир, броней | 200 OK |
| `PATCH` | `/api/admin/bookings/{id}/status` | `ADMIN` | Принудительно изменить статус брони | 200 OK |

> `PATCH /api/admin/bookings/{id}/status` принимает тело запроса: `{"status": "COMPLETED"}`

---

## 11. Примеры запросов (curl)

### Регистрация

```bash
curl -X POST http://localhost:8555/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "client@example.com",
    "password": "secret123",
    "firstName": "Иван",
    "lastName": "Петров",
    "role": "CLIENT"
  }'
```

Ответ:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer"
}
```

### Создать квартиру (от LANDLORD)

```bash
curl -X POST http://localhost:8555/api/apartments \
  -H "Authorization: Bearer eyJhbGci..." \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Уютная студия в центре",
    "city": "Москва",
    "street": "Тверская",
    "houseNumber": "1",
    "pricePerNight": 2500.00,
    "maxGuests": 2,
    "amenities": ["Wi-Fi", "Кондиционер"]
  }'
```

### Поиск квартир (PostgreSQL)

```bash
curl "http://localhost:8555/api/apartments?city=Москва&startDate=2027-07-01&endDate=2027-07-05&guests=2&page=0&size=10&sort=pricePerNight,asc"
```

### Полнотекстовый поиск (OpenSearch)

```bash
curl "http://localhost:8555/api/search/apartments?q=уютная+студия"
curl "http://localhost:8555/api/search/apartments?city=Москва&maxPrice=3000"
curl "http://localhost:8555/api/search/apartments?q=центр&minPrice=1000&page=0&size=5"
```

### Создать бронь

```bash
curl -X POST http://localhost:8555/api/bookings \
  -H "Authorization: Bearer eyJhbGci..." \
  -H "Content-Type: application/json" \
  -d '{
    "apartmentId": 1,
    "startDate": "2027-07-01",
    "endDate": "2027-07-05",
    "guestsCount": 2
  }'
```

### Подтвердить бронь

```bash
curl -X POST http://localhost:8555/api/bookings/1/confirm \
  -H "Authorization: Bearer eyJhbGci..."
```

### Оставить отзыв (после COMPLETED)

```bash
curl -X POST http://localhost:8555/api/reviews \
  -H "Authorization: Bearer eyJhbGci..." \
  -H "Content-Type: application/json" \
  -d '{
    "bookingId": 1,
    "rating": 5,
    "comment": "Отличная квартира, всё как на фото!"
  }'
```

### Принудительно изменить статус (ADMIN)

```bash
curl -X PATCH http://localhost:8555/api/admin/bookings/1/status \
  -H "Authorization: Bearer eyJhbGci..." \
  -H "Content-Type: application/json" \
  -d '{"status": "COMPLETED"}'
```

---

## 12. Тесты

В проекте **112+ тестов** двух видов: юнит-тесты и интеграционные.

### Запустить тесты

```bash
# Все тесты
./gradlew test

# HTML-отчёт (открыть в браузере)
# build/reports/tests/test/index.html

# Только юнит-тесты (быстро)
./gradlew test --tests "com.booking.service.*" --tests "com.booking.security.*"

# Только интеграционные
./gradlew test --tests "com.booking.integration.*"

# Конкретный класс
./gradlew test --tests "com.booking.service.BookingServiceTest"
```

### Что такое юнит-тесты

**Юнит-тест** проверяет один класс в изоляции. Все зависимости заменяются **моками** — объектами, которые притворяются реальными. Моки создаются через **Mockito**.

```java
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock BookingRepository bookingRepository;  // мок репозитория
    @Mock SecurityUtils securityUtils;          // мок утилиты безопасности

    @InjectMocks BookingService bookingService; // реальный объект с моками внутри

    @Test
    void create_конфликтДат_бросаетException() {
        // Arrange: настраиваем мок
        when(bookingRepository.findConflictingBookings(any(), any(), any()))
            .thenReturn(List.of(existingBooking)); // симулируем занятые даты

        // Act + Assert: вызываем метод и проверяем исключение
        assertThatThrownBy(() -> bookingService.create(request))
            .isInstanceOf(BookingConflictException.class);
    }
}
```

### Что такое интеграционные тесты

**Интеграционный тест** поднимает полный Spring-контекст и делает реальные HTTP-запросы через `MockMvc`. Вместо PostgreSQL используется **H2** (in-memory БД), Redis и Kafka заменяются моками.

### Все тестовые классы

#### Юнит-тесты (Mockito, без Spring)

| Класс | Тестов | Что проверяет |
|-------|--------|---------------|
| `JwtTokenProviderTest` | 7 | Генерация JWT, валидация, извлечение claims; истёкший и поддельный токен |
| `TokenServiceTest` | 8 | Сохранение в Redis, fallback на in-memory при недоступности Redis |
| `AuthServiceTest` | 7 | Регистрация, логин, refresh, logout; запрет роли ADMIN |
| `ApartmentServiceTest` | 9 | CRUD квартир; права: только владелец или ADMIN может удалить |
| `BookingServiceTest` | 19 | Весь жизненный цикл: создание, подтверждение, отмена, планировщик |
| `ReviewServiceTest` | 7 | Создание отзыва, запрет дублирования, удаление, пересчёт рейтинга |
| `UserServiceTest` | 9 | Профиль, блокировка (запрет для ADMIN), удаление |
| `BookingExpirationSchedulerTest` | 4 | Делегирование в сервис с правильным threshold и датой |

#### Интеграционные тесты (MockMvc + H2)

| Класс | Тестов | Что проверяет |
|-------|--------|---------------|
| `BookingApplicationTest` | 1 | Smoke-тест: Spring-контекст поднимается без ошибок |
| `AuthControllerIntegrationTest` | 7 | Регистрация, логин, обновление токена, logout через реальный HTTP |
| `ApartmentControllerIntegrationTest` | 8 | CRUD квартир: права доступа, мягкое удаление, поиск |
| `BookingControllerIntegrationTest` | 8 | Полный цикл бронирования, конфликт дат (HTTP 409) |
| `ReviewControllerIntegrationTest` | 7 | Отзывы: создание, дублирование, удаление только ADMIN |

### Как устроены тест-настройки

**`application-test.yml`** — переопределяет настройки для тестового профиля:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL  # H2 вместо PostgreSQL
  jpa:
    hibernate:
      ddl-auto: create-drop   # таблицы создаются перед тестами, удаляются после
  autoconfigure:
    exclude:
      - KafkaAutoConfiguration    # Kafka не нужна
      - RedisAutoConfiguration    # Redis не нужен
opensearch:
  enabled: false              # OpenSearch бин не создаётся → все тесты без OpenSearch
```

**`TestConfig.java`** — заменяет `StringRedisTemplate` моком, который бросает исключения → `TokenService` переключается на in-memory fallback.

**`logback-test.xml`** — только консольный вывод, без Loki (Loki не запущен во время тестов).

---

## 13. Мониторинг: Loki + Grafana

### Что такое Loki и Grafana

**Loki** — сервер для хранения и поиска логов (от создателей Prometheus).  
**Grafana** — веб-интерфейс для визуализации метрик и логов.

Схема работы:
```
Spring Boot приложение
   │
   │  Logback + Loki4j appender
   │  (отправляет логи через HTTP)
   ▼
Loki (порт 3100)
   │  хранит логи
   ▼
Grafana (порт 3000)
   │  читает из Loki, строит дашборды
   ▼
Вы в браузере
```

### Запуск

```bash
# Только Loki и Grafana
docker-compose up -d loki grafana

# Все сервисы включая мониторинг
docker-compose up -d
```

### Просмотр логов в Grafana

1. Открыть `http://localhost:3000` (логин/пароль по умолчанию: `admin`/`admin`)
2. Перейти в **Explore** (иконка компаса в меню слева)
3. Выбрать datasource **Loki** (добавляется автоматически при старте)
4. Ввести запрос на языке **LogQL**:

```logql
# Все логи приложения
{app="booking-service"}

# Только ошибки
{app="booking-service", level="ERROR"}

# Логи с упоминанием конкретного bookingId
{app="booking-service"} |= "bookingId=42"

# Фильтр по уровню через text filter
{app="booking-service"} | level="WARN"
```

---

## 14. CI/CD через GitLab

### Что такое CI/CD

**CI (Continuous Integration)** — каждый `git push` автоматически компилирует код и запускает тесты.  
**CD (Continuous Delivery)** — после успешного CI собирает Docker-образ и деплоит на сервер.

Файл `.gitlab-ci.yml` описывает весь pipeline.

### Схема pipeline

```
git push
  │
  ▼
┌──────────┐
│  build   │  ./gradlew compileJava
│  (30 с)  │  Проверяет что код компилируется
└────┬─────┘
     │ успех
     ▼
┌──────────┐
│   test   │  ./gradlew test
│  (2 мин) │  112+ тестов; при падении → pipeline красный, merge заблокирован
└────┬─────┘
     │ успех + ветка main/master
     ▼
┌──────────┐
│ package  │  ./gradlew bootJar
│  (1 мин) │  Собирает исполняемый JAR
└────┬─────┘
     │
     ▼
┌──────────┐
│  docker  │  docker build + push в GitLab Registry
│  (3 мин) │
└────┬─────┘
     │
     ▼
┌──────────┐
│  deploy  │  SSH на сервер → docker-compose pull + up
│ (ручной) │  Требует нажатия кнопки в GitLab UI
└──────────┘
```

### Настройка деплоя

**1. Создать SSH-ключ без пароля:**
```bash
ssh-keygen -t ed25519 -C "gitlab-deploy" -f ~/.ssh/deploy_key -N ""
cat ~/.ssh/deploy_key      # приватный → в GitLab Variables
cat ~/.ssh/deploy_key.pub  # публичный → на сервер в authorized_keys
```

**2. GitLab → Settings → CI/CD → Variables:**

| Переменная | Значение | Примечание |
|------------|----------|------------|
| `DEPLOY_HOST` | IP сервера | |
| `DEPLOY_USER` | `root` или `ubuntu` | |
| `SSH_PRIVATE_KEY` | содержимое `deploy_key` | Protected + Masked |
| `DB_PASSWORD` | пароль БД | Protected + Masked |
| `JWT_SECRET` | строка 64+ символа | Protected + Masked |

> **Protected + Masked** — переменная доступна только защищённым веткам и не отображается в логах.

---

## 15. Переменные окружения

Значения из `application.yml` подходят только для локальной разработки. В продакшне переопределяйте через переменные окружения.

| Переменная | Описание | Дефолт (dev) |
|-----------|----------|--------------|
| `JWT_SECRET` | Секрет для подписи JWT. **Минимум 32 символа**, случайная строка | `verySecretKey...` |
| `SPRING_DATASOURCE_URL` | Полный URL PostgreSQL | `jdbc:postgresql://localhost:5432/bookingdb` |
| `SPRING_DATASOURCE_USERNAME` | Пользователь БД | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Пароль БД | `1234` |
| `SPRING_DATA_REDIS_HOST` | Хост Redis | `localhost` |
| `SPRING_DATA_REDIS_PORT` | Порт Redis | `6379` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Адрес Kafka-брокера | `localhost:9092` |
| `OPENSEARCH_URL` | URL OpenSearch | `http://localhost:9200` |
| `LOKI_URL` | URL Loki для отправки логов | `http://localhost:3100` |
| `SERVER_PORT` | Порт HTTP-сервера | `8555` |

> Значения `verySecretKey...` и `1234` небезопасны для продакшна. Обязательно замените перед деплоем.

---

## 16. Запуск в Kubernetes (Rancher Desktop)

Помимо docker-compose, проект можно запустить в локальном Kubernetes-кластере через **Rancher Desktop** — это полноценный K8s на вашей машине.

> Подробный portable guide по K8s/Rancher Desktop с паттернами и решениями типичных проблем: **`rancher.md`** в корне проекта.

### Что нужно установить

| Инструмент | Где скачать | Зачем |
|------------|-------------|-------|
| **Rancher Desktop** | [rancherdesktop.io](https://rancherdesktop.io) | K8s + Docker в одном. При установке: Container Runtime = `dockerd (Moby)` |
| Git | — | Клонирование репозитория |

> Java, Gradle, kubectl, Docker — всё уже включено в Rancher Desktop. Устанавливать отдельно не нужно.

### Шаг 1 — Собрать и загрузить образ

```powershell
# Из корня проекта (где лежит Dockerfile):

# 1. Собрать образ (--provenance=false обязателен!)
docker build --provenance=false -t booking-service:1.0.0 .

# 2. Сохранить в файл
docker save booking-service:1.0.0 -o $env:TEMP\booking-service.tar

# 3. Загрузить в Docker внутри VM Rancher Desktop
rdctl shell -- sh -c "docker load < /mnt/c/Users/$env:USERNAME/AppData/Local/Temp/booking-service.tar"
```

> Скрипт `.\rancher\build-and-load.ps1` автоматизирует эти три шага, но при проблемах с кодировкой PowerShell 5.1 — выполняйте шаги вручную.

### Шаг 2 — Задеплоить весь стек

```powershell
kubectl apply -f rancher/k8s/
```

Файлы применяются в алфавитном порядке (поэтому нумерация 00–10 важна):

| Файл | Что создаёт | Namespace |
|------|-------------|-----------|
| `00-namespace.yaml` | namespace `pet-booking` | — |
| `01-secrets.yaml` | DB password, JWT secret | pet-booking |
| `02-postgres.yaml` | PostgreSQL 16 (PVC 1Gi + Deployment + ClusterIP) | pet-booking |
| `03-redis.yaml` | Redis 7 (Deployment + ClusterIP) | pet-booking |
| `04-zookeeper.yaml` | ZooKeeper (Deployment + ClusterIP) | pet-booking |
| `05-kafka.yaml` | Kafka (Deployment + ClusterIP, dual listeners) | pet-booking |
| `06-loki.yaml` | Loki 3.1 (ConfigMap + PVC 2Gi + Deployment + ClusterIP) | pet-booking |
| `07-grafana.yaml` | Grafana 11.1 (2×ConfigMap + PVC + Deployment + NodePort) | pet-booking |
| `08-opensearch.yaml` | OpenSearch 2.17.0 (PVC + Deployment + ClusterIP) | pet-booking |
| `09-app.yaml` | booking-service (ConfigMap + Deployment + NodePort) | pet-booking |
| `10-dashboard.yaml` | K8s Dashboard (RBAC + 2×Deployment + NodePort) | kubernetes-dashboard |

### Шаг 3 — Дождаться готовности

```powershell
# Следить за статусом
kubectl get all -n pet-booking

# Ждать пока приложение станет Ready
kubectl rollout status deployment/booking-app -n pet-booking --timeout=180s
```

Порядок запуска автоматически управляется через **initContainers** в поде приложения:
```
ZooKeeper → Kafka → (параллельно: PostgreSQL, Redis, OpenSearch)
                         ↓
               wait-for-postgres     [initContainer 1]
               wait-for-redis        [initContainer 2]
               wait-for-kafka        [initContainer 3]
               wait-for-opensearch   [initContainer 4]
                         ↓
               Spring Boot стартует (8-10 сек)
                         ↓
               readinessProbe /actuator/health → 200 UP
```

### Доступные URL после запуска

| Сервис | URL | Примечание |
|--------|-----|------------|
| **Приложение (API)** | http://localhost:30555 | REST API |
| **Swagger UI** | http://localhost:30555/swagger-ui.html | Документация и тестирование |
| **Фронтенд** | http://localhost:30555/frontend/index.html | Веб-интерфейс |
| **Grafana** | http://localhost:30303 | Логи (анонимный Admin) |
| **K8s Dashboard** | https://localhost:30443 | Управление кластером |

### Структура сервисов

| Сервис | Docker Compose порт | K8s NodePort |
|--------|---------------------|--------------|
| Приложение | 8555 | 30555 |
| PostgreSQL | 5432 | — (ClusterIP) |
| Redis | 6379 | — (ClusterIP) |
| Kafka | 9092 | — (ClusterIP) |
| OpenSearch | 9200 | — (ClusterIP) |
| Loki | 3100 | — (ClusterIP) |
| Grafana | 3000 | 30303 |
| K8s Dashboard | — | 30443 |

### Обновление приложения

```powershell
# 1. Пересобрать образ
docker build --provenance=false -t booking-service:1.0.0 .

# 2. Сохранить
docker save booking-service:1.0.0 -o $env:TEMP\booking-service.tar

# 3. Загрузить в VM
rdctl shell -- sh -c "docker load < /mnt/c/Users/$env:USERNAME/AppData/Local/Temp/booking-service.tar"

# 4. Rolling restart
kubectl rollout restart deployment/booking-app -n pet-booking

# 5. Следить за обновлением
kubectl rollout status deployment/booking-app -n pet-booking --timeout=180s
```

### Проверка OpenSearch в Docker Compose

```bash
# Статус кластера
curl http://localhost:9200/_cluster/health

# Количество документов в индексе
curl http://localhost:9200/apartments/_count

# Поиск через API приложения
curl "http://localhost:8555/api/search/apartments?q=уютная"
```

### Проверка OpenSearch в Rancher Desktop

```powershell
# Проброс порта
kubectl port-forward -n pet-booking svc/opensearch-service 9200:9200

# В другом терминале:
curl http://localhost:9200/_cluster/health
curl "http://localhost:30555/api/search/apartments?q=уютная"
```

### Полезные команды

```powershell
# Статус всего стека
kubectl get all -n pet-booking

# Логи приложения (live)
kubectl logs -n pet-booking deployment/booking-app -f

# Описание пода — события, probe failures, ошибки образа
kubectl describe pod -n pet-booking <pod-name>

# Прямой доступ без NodePort
kubectl port-forward -n pet-booking service/booking-service 8555:8555

# Полный сброс и пересоздание
kubectl delete namespace pet-booking
kubectl apply -f rancher/k8s/
```

### Диагностика типичных проблем

| Симптом | Причина | Решение |
|---------|---------|---------|
| `ErrImageNeverPull` | Образ не загружен в VM | Выполнить шаги 1-3 из раздела "Собрать и загрузить образ" |
| Pod не стартует, Exit Code 1 за 2 сек | Kafka получает `KAFKA_PORT` от K8s service links | `enableServiceLinks: false` в spec (уже есть) |
| `readinessProbe failed: 503` | Один из HealthIndicator DOWN | `kubectl exec ... wget -qO- http://localhost:8555/actuator/health` для диагностики |
| `CrashLoopBackOff` | Spring Boot не смог подключиться к БД/Kafka/OpenSearch при старте | initContainers wait-for-* (уже есть) |
| OpenSearch Exit 78 | `vm.max_map_count` слишком мал | privileged initContainer sysctl-fix (уже есть в 08-opensearch.yaml) |
| Pod рестартует при старте | `livenessProbe.initialDelaySeconds` меньше времени запуска | Увеличить `initialDelaySeconds` в 09-app.yaml |

### Остановить стек

```powershell
# Удалить только наш стек (данные в PVC тоже удалятся)
kubectl delete namespace pet-booking

# Удалить Dashboard
kubectl delete namespace kubernetes-dashboard
kubectl delete clusterrolebinding admin-user kubernetes-dashboard-metrics
kubectl delete clusterrole kubernetes-dashboard-metrics
```
