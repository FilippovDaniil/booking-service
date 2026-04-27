# Booking Service

Учебный проект — REST API для платформы бронирования жилья (аналог Airbnb).  
Написан на **Java 17 + Spring Boot 3.4.5**.

---

## Содержание

1. [Что умеет приложение](#что-умеет-приложение)
2. [Архитектура и технологии](#архитектура-и-технологии)
3. [Структура проекта](#структура-проекта)
4. [Быстрый старт (локально)](#быстрый-старт-локально)
5. [API документация](#api-документация)
6. [Тесты](#тесты)
7. [Запуск через GitLab CI/CD](#запуск-через-gitlab-cicd)
8. [Переменные окружения](#переменные-окружения)

---

## Что умеет приложение

| Функция | Описание |
|---------|----------|
| **Регистрация и авторизация** | JWT-токены (access 15 мин + refresh 30 дней), BCrypt |
| **Роли** | `CLIENT` — арендует, `LANDLORD` — сдаёт, `ADMIN` — управляет |
| **Квартиры** | CRUD для арендодателей, публичный поиск с фильтрами |
| **Бронирования** | Создание → Подтверждение → Отмена / Истечение / Завершение |
| **Отзывы** | Клиент оставляет отзыв после завершённого бронирования |
| **Уведомления** | Через Kafka: события жизненного цикла бронирования |
| **Автоматика** | Планировщик истекает PENDING брони и завершает CONFIRMED |

---

## Архитектура и технологии

```
┌─────────────────────────────────────────────────┐
│                  HTTP клиент                     │
│         (браузер, Postman, мобильное приложение) │
└────────────────────┬────────────────────────────┘
                     │ HTTP / JSON
┌────────────────────▼────────────────────────────┐
│              Spring Boot 3.4.5                   │
│                                                  │
│  ┌─────────────┐  ┌──────────┐  ┌────────────┐  │
│  │ Controllers │→ │ Services │→ │Repositories│  │
│  └─────────────┘  └──────────┘  └─────┬──────┘  │
│                                        │         │
│  ┌──────────────────────────────────┐  │         │
│  │   Spring Security + JWT Filter   │  │         │
│  └──────────────────────────────────┘  │         │
└────────────────────────────────────────┼─────────┘
                     ┌──────────────────┬┘
          ┌──────────▼──┐  ┌────────┐  ┌──────────┐
          │ PostgreSQL  │  │ Redis  │  │  Kafka   │
          │ (основные   │  │(refresh│  │(события  │
          │   данные)   │  │токены) │  │ брони)   │
          └─────────────┘  └────────┘  └──────────┘
```

**Стек:**

| Слой | Технология |
|------|-----------|
| Язык | Java 17 |
| Фреймворк | Spring Boot 3.4.5 |
| Безопасность | Spring Security + JJWT 0.12.6 |
| База данных | PostgreSQL 16 + Spring Data JPA / Hibernate |
| Кэш токенов | Redis 7 |
| Очередь событий | Apache Kafka 7.6.0 |
| Сборка | Gradle 8.7 |
| Документация API | SpringDoc OpenAPI (Swagger UI) |
| Тесты | JUnit 5 + Mockito + AssertJ + H2 |

---

## Структура проекта

```
booking-service/
├── src/
│   ├── main/java/com/booking/
│   │   ├── config/          # SecurityConfig, KafkaConfig, RedisConfig, ...
│   │   ├── controller/      # REST-контроллеры (AuthController, BookingController, ...)
│   │   ├── dto/
│   │   │   ├── request/     # Тела входящих запросов (RegisterRequest, BookingRequest, ...)
│   │   │   └── response/    # Тела ответов (TokenResponse, BookingResponse, ...)
│   │   ├── entity/          # JPA-сущности (User, Apartment, Booking, Review)
│   │   ├── event/           # Kafka-события (BookingEvent, NotificationEvent)
│   │   ├── exception/       # Кастомные исключения + GlobalExceptionHandler
│   │   ├── repository/      # Spring Data репозитории
│   │   ├── scheduler/       # @Scheduled-задачи (истечение броней)
│   │   ├── security/        # JWT-фильтр, UserDetailsService, SecurityUtils
│   │   └── service/         # Бизнес-логика (AuthService, BookingService, ...)
│   ├── main/resources/
│   │   └── application.yml  # Настройки приложения
│   └── test/                # Юнит и интеграционные тесты (82 теста)
├── frontend/                # Статичные HTML-страницы
├── postman_tests/           # Коллекция Postman
├── Dockerfile               # Инструкция сборки Docker-образа
├── docker-compose.yml       # Локальная разработка
├── docker-compose.prod.yml  # Продакшн деплой
├── .gitlab-ci.yml           # CI/CD pipeline
└── .env.example             # Пример файла переменных окружения
```

---

## Быстрый старт (локально)

### Предварительные требования

- **Java 17+** — [скачать](https://adoptium.net/)
- **Docker Desktop** — [скачать](https://www.docker.com/products/docker-desktop/)
- Git

### Шаг 1 — Клонировать репозиторий

```bash
git clone https://gitlab.com/your-username/booking-service.git
cd booking-service
```

### Шаг 2 — Запустить инфраструктуру (PostgreSQL + Redis + Kafka)

```bash
docker-compose up -d
```

Это поднимет 4 контейнера: PostgreSQL, Redis, Zookeeper, Kafka.  
Первый запуск занимает 1–2 минуты (скачиваются образы).

Проверить что всё запустилось:
```bash
docker-compose ps
```

Ожидаемый результат — все сервисы в статусе `Up`:
```
NAME                STATUS
booking-postgres    Up
booking-redis       Up
booking-zookeeper   Up
booking-kafka       Up
```

### Шаг 3 — Запустить приложение

**Вариант А — через Gradle (для разработки):**
```bash
# Linux / Mac
./gradlew bootRun

# Windows
gradlew.bat bootRun
```

**Вариант Б — через IntelliJ IDEA:**  
Открыть `BookingApplication.java` → нажать зелёный треугольник ▶ рядом с `main`.

### Шаг 4 — Проверить работу

Приложение запустится на порту **8555**.

Открыть Swagger UI в браузере:
```
http://localhost:8555/swagger-ui.html
```

Проверить доступность API:
```bash
curl http://localhost:8555/api/apartments?city=Moscow&startDate=2027-06-01&endDate=2027-06-05&guests=2
```

Ожидаемый ответ:
```json
{"content": [], "totalElements": 0, ...}
```

### Шаг 5 — Остановить инфраструктуру

```bash
docker-compose down
# или с удалением данных:
docker-compose down -v
```

---

## API документация

После запуска Swagger UI доступен по адресу:  
**http://localhost:8555/swagger-ui.html**

### Основные эндпоинты

#### Аутентификация

| Метод | Путь | Доступ | Описание |
|-------|------|--------|----------|
| `POST` | `/api/auth/register` | Публичный | Регистрация (`CLIENT` или `LANDLORD`) |
| `POST` | `/api/auth/login` | Публичный | Вход, получение токенов |
| `POST` | `/api/auth/refresh` | Публичный | Обновить access-токен |
| `POST` | `/api/auth/logout` | Авторизован | Выйти (инвалидировать refresh-токен) |

#### Квартиры

| Метод | Путь | Доступ | Описание |
|-------|------|--------|----------|
| `GET` | `/api/apartments` | Публичный | Поиск с фильтрами (city, dates, guests) |
| `GET` | `/api/apartments/{id}` | Публичный | Квартира по ID |
| `GET` | `/api/apartments/my` | `LANDLORD` | Мои квартиры |
| `POST` | `/api/apartments` | `LANDLORD` | Добавить квартиру |
| `PUT` | `/api/apartments/{id}` | `LANDLORD` (владелец) | Обновить квартиру |
| `DELETE` | `/api/apartments/{id}` | `LANDLORD` (владелец) | Деактивировать |

#### Бронирования

| Метод | Путь | Доступ | Описание |
|-------|------|--------|----------|
| `POST` | `/api/bookings` | `CLIENT` | Создать бронь (`PENDING`) |
| `GET` | `/api/bookings` | Авторизован | Мои брони (или брони моих квартир) |
| `POST` | `/api/bookings/{id}/confirm` | `CLIENT` (владелец) | Подтвердить → `CONFIRMED` |
| `POST` | `/api/bookings/{id}/cancel` | `CLIENT` (владелец) | Отменить → `CANCELLED_BY_CLIENT` |
| `POST` | `/api/bookings/{id}/cancel-by-landlord` | `LANDLORD` | Отменить → `CANCELLED_BY_LANDLORD` |

#### Отзывы

| Метод | Путь | Доступ | Описание |
|-------|------|--------|----------|
| `POST` | `/api/bookings/{id}/review` | `CLIENT` | Оставить отзыв (только `COMPLETED`) |
| `GET` | `/api/apartments/{id}/reviews` | Публичный | Отзывы квартиры |

### Жизненный цикл бронирования

```
          CLIENT создаёт
               │
               ▼
           [PENDING]
          /    │    \
     клиент  истёк  клиент/арендодатель
    confirm  (15м)  cancel
         │    │    │
         ▼    ▼    ▼
    [CONFIRMED] [EXPIRED] [CANCELLED_BY_*]
         │
    дата выезда прошла
         │
         ▼
    [COMPLETED]  ← клиент может оставить отзыв
```

### Как использовать авторизацию в Swagger UI

1. Нажать кнопку **Authorize** (🔒) в правом верхнем углу
2. В поле `bearerAuth` ввести токен: `Bearer eyJhbGci...`
3. Нажать **Authorize**

Все последующие запросы из Swagger UI будут отправляться с этим токеном.

---

## Тесты

В проекте **82 теста** двух видов.

### Юнит-тесты (быстрые, без БД)

Тестируют отдельные классы с Mockito-моками вместо реальных зависимостей.

```
src/test/java/com/booking/
├── service/
│   ├── AuthServiceTest.java       (8 тестов)
│   ├── BookingServiceTest.java    (13 тестов)
│   ├── ApartmentServiceTest.java  (9 тестов)
│   ├── ReviewServiceTest.java     (8 тестов)
│   └── TokenServiceTest.java      (7 тестов)
└── security/
    └── JwtTokenProviderTest.java  (7 тестов)
```

### Интеграционные тесты (реальный HTTP + H2)

Поднимают полный Spring-контекст с H2 in-memory вместо PostgreSQL.  
Redis и Kafka заменяются моками.

```
src/test/java/com/booking/integration/
├── AuthControllerIntegrationTest.java     (7 тестов)
├── BookingControllerIntegrationTest.java  (13 тестов)
└── ApartmentControllerIntegrationTest.java (9 тестов)
```

### Запустить все тесты

```bash
./gradlew test
```

HTML-отчёт появится в:
```
build/reports/tests/test/index.html
```

Открыть в браузере — увидите все тесты с зелёными/красными статусами.

---

## Запуск через GitLab CI/CD

Этот раздел объясняет как настроить автоматическую сборку, тестирование и деплой через GitLab.

### Что такое CI/CD

**CI (Continuous Integration)** — каждый `git push` автоматически:
1. Компилирует код
2. Запускает тесты
3. Если тесты упали — уведомляет и блокирует merge

**CD (Continuous Delivery)** — после успешного CI:
1. Собирает Docker-образ
2. Публикует в Container Registry
3. Деплоит на сервер

### Как работает pipeline

Файл `.gitlab-ci.yml` в корне репозитория описывает pipeline.  
GitLab запускает его автоматически при каждом push.

```
git push
    │
    ▼
┌────────┐    ┌──────┐    ┌─────────┐    ┌────────┐    ┌────────┐
│ build  │───▶│ test │───▶│ package │───▶│ docker │───▶│ deploy │
│(compile│    │(82   │    │(bootJar)│    │(build  │    │(SSH на │
│  Java) │    │tests)│    │         │    │& push) │    │сервер) │
└────────┘    └──────┘    └─────────┘    └────────┘    └────────┘
                                                             ▲
                                                        РУЧНОЙ ЗАПУСК
```

Stages `package`, `docker`, `deploy` выполняются только на ветке `main`/`master`.  
`deploy` требует ручного подтверждения (защита от случайного деплоя).

### Шаг 1 — Создать репозиторий на GitLab

1. Зайти на [gitlab.com](https://gitlab.com) → **New project** → **Create blank project**
2. Дать имя: `booking-service`
3. Нажать **Create project**

### Шаг 2 — Подключить локальный проект

```bash
# Добавить GitLab как remote-репозиторий
git remote add origin https://gitlab.com/your-username/booking-service.git

# Первый push
git push -u origin master
```

### Шаг 3 — Посмотреть pipeline

После push перейти в GitLab: **CI/CD → Pipelines**.

Вы увидите запущенный pipeline. Нажать на него → увидите stages и их статусы.  
Нажать на stage → увидите логи выполнения команд.

При успешном прохождении тестов появится зелёная галочка ✅.

### Шаг 4 — Настроить деплой на сервер (опционально)

Для деплоя нужен сервер с Docker. Это может быть VPS (DigitalOcean, Hetzner, Selectel и др.).

#### 4.1. Настроить сервер

Подключиться к серверу по SSH и выполнить:

```bash
# Установить Docker
curl -fsSL https://get.docker.com | sh

# Установить docker-compose
apt install -y docker-compose

# Создать директорию приложения
mkdir -p /opt/booking-service

# Скопировать файлы конфигурации
# (docker-compose.prod.yml и .env)
```

#### 4.2. Создать SSH-ключ для деплоя

На вашей локальной машине:

```bash
# Генерируем ключ без пароля (нужен для автоматического SSH в CI)
ssh-keygen -t ed25519 -C "gitlab-ci-deploy" -f ~/.ssh/deploy_key -N ""

# Просмотр публичного ключа (скопировать)
cat ~/.ssh/deploy_key.pub

# Просмотр приватного ключа (скопировать в GitLab)
cat ~/.ssh/deploy_key
```

На сервере добавить публичный ключ:

```bash
echo "ВСТАВИТЬ_ПУБЛИЧНЫЙ_КЛЮЧ_СЮДА" >> ~/.ssh/authorized_keys
```

#### 4.3. Добавить Variables в GitLab

Перейти: **Settings → CI/CD → Variables → Add variable**

| Ключ | Значение | Тип |
|------|----------|-----|
| `DEPLOY_HOST` | IP-адрес вашего сервера | Variable |
| `DEPLOY_USER` | Пользователь SSH (например `root` или `ubuntu`) | Variable |
| `SSH_PRIVATE_KEY` | Содержимое файла `~/.ssh/deploy_key` | Variable (Protected, Masked) |
| `DB_PASSWORD` | Пароль БД | Variable (Protected, Masked) |
| `JWT_SECRET` | Случайная строка 64+ символа | Variable (Protected, Masked) |

> **Почему Variables, а не прямо в коде?**  
> Секреты (пароли, ключи) нельзя хранить в git — это угроза безопасности.  
> GitLab Variables шифруются и не видны в логах (опция Masked).

#### 4.4. Настроить .env на сервере

На сервере в `/opt/booking-service/.env`:

```bash
DB_PASSWORD=ваш_пароль
JWT_SECRET=ваш_секрет_64_символа
CI_REGISTRY_IMAGE=registry.gitlab.com/your-username/booking-service
```

#### 4.5. Запустить деплой

После успешного прохождения `docker-build` появится stage `deploy` со значком ▶ (ручной запуск).  
Нажать ▶ → подтвердить → GitLab подключится к серверу по SSH и задеплоит.

### Как выглядит успешный pipeline

```
✅ build      (30 сек) — компиляция OK
✅ test       (2 мин)  — 82/82 тестов прошло
✅ package    (1 мин)  — JAR собран
✅ docker     (3 мин)  — образ опубликован в registry
▶  deploy     (ручной) — ожидает подтверждения
```

---

## Переменные окружения

Приложение читает настройки из переменных окружения (они переопределяют `application.yml`).

| Переменная | Описание | Дефолт в `application.yml` |
|-----------|----------|---------------------------|
| `JWT_SECRET` | Секрет для подписи JWT | `verySecretKey...` (только для dev!) |
| `SPRING_DATASOURCE_URL` | URL PostgreSQL | `jdbc:postgresql://localhost:5432/bookingdb` |
| `SPRING_DATASOURCE_PASSWORD` | Пароль PostgreSQL | `1234` |
| `SPRING_REDIS_HOST` | Хост Redis | `localhost` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Адрес Kafka | `localhost:9092` |
| `SPRING_PROFILES_ACTIVE` | Профиль Spring | — |
| `SERVER_PORT` | Порт сервера | `8555` |

> **Важно:** Значения из `application.yml` подходят только для локальной разработки.  
> В production всегда используйте переменные окружения с уникальными секретами.
