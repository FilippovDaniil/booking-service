# ============================================================
#  Dockerfile — инструкция для сборки Docker-образа приложения
#
#  MULTI-STAGE BUILD (многоэтапная сборка):
#    Используем два образа:
#      1. "builder" — тяжёлый образ с JDK + Gradle для сборки JAR
#      2. финальный — лёгкий образ только с JRE для запуска
#    Итоговый образ ~200 МБ вместо ~800 МБ если бы использовали один этап.
#
#  КАК РАБОТАЕТ:
#    docker build -t booking-service .    → собрать образ
#    docker run -p 8555:8555 booking-service  → запустить контейнер
# ============================================================

# ---- ЭТАП 1: сборка ----
# FROM — базовый образ. gradle:8.7-jdk17 содержит Java 17 + Gradle 8.7
FROM gradle:8.7-jdk17 AS builder

# WORKDIR — рабочая директория внутри контейнера
WORKDIR /app

# Сначала копируем только файлы сборки (без исходников).
# Docker кэширует слои — если build.gradle не изменился,
# зависимости не будут скачиваться заново при следующей сборке.
COPY build.gradle settings.gradle ./
COPY gradle/ gradle/
COPY gradlew ./

# Скачиваем зависимости (этот слой будет закэширован)
RUN ./gradlew dependencies --no-daemon || true

# Теперь копируем исходный код
COPY src/ src/

# Собираем JAR, пропуская тесты (они уже прогнаны в CI)
# -x test — исключить task "test"
RUN ./gradlew bootJar -x test --no-daemon

# ---- ЭТАП 2: финальный образ ----
# eclipse-temurin:17-jre — только JRE (не JDK), значительно легче
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Создаём непривилегированного пользователя — запускать приложение от root небезопасно
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Копируем только JAR из этапа builder (не тащим Gradle, исходники и т.д.)
COPY --from=builder /app/build/libs/*.jar app.jar

# Меняем владельца файла
RUN chown appuser:appgroup app.jar

# Переключаемся на непривилегированного пользователя
USER appuser

# Документируем порт (не открывает его — это делает docker run -p или docker-compose)
EXPOSE 8555

# Переменные окружения с дефолтными значениями.
# Реальные значения передаются через docker-compose.yml или GitLab Variables.
ENV SPRING_PROFILES_ACTIVE=prod
ENV JWT_SECRET=changeMeInProduction

# Точка входа — команда запуска при старте контейнера.
# -Djava.security.egd — ускоряет генерацию случайных чисел в Docker (для JWT)
ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", \
  "app.jar"]
