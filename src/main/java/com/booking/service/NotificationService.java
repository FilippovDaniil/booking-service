package com.booking.service;

import com.booking.event.BookingEvent;
import com.booking.event.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Сервис обработки уведомлений — потребитель (consumer) Kafka-сообщений.
 *
 * Подписывается на два топика:
 *   "booking-lifecycle"    — события изменения статуса броней
 *   "notification-request" — запросы на отправку уведомлений пользователям
 *
 * @KafkaListener — Spring Kafka автоматически создаёт поток-слушатель,
 * который постоянно опрашивает Kafka-брокер и вызывает метод при появлении новых сообщений.
 *
 * groupId — идентификатор группы потребителей (consumer group).
 * Kafka гарантирует: каждое сообщение будет обработано только ОДНИМ экземпляром
 * приложения из группы. Это важно при горизонтальном масштабировании:
 * несколько инстансов сервиса с одним groupId не дублируют обработку.
 *
 * В текущей реализации методы только логируют — это заглушка.
 * В продакшне здесь будет отправка email (SMTP / SendGrid / AWS SES).
 */
@Service
@Slf4j
public class NotificationService {

    /**
     * Обрабатывает события жизненного цикла бронирования.
     * Пример применения: отправить email арендодателю при создании брони.
     *
     * Jackson автоматически десериализует JSON из Kafka в объект BookingEvent.
     */
    @KafkaListener(topics = "booking-lifecycle", groupId = "booking-service-lifecycle")
    public void handleBookingEvent(BookingEvent event) {
        log.info("[BOOKING EVENT] type={}, bookingId={}, newStatus={}",
                event.getEventType(), event.getBookingId(), event.getNewStatus());
        // TODO продакшн: уведомить арендодателя о новой брони, клиента о подтверждении и т.д.
    }

    /**
     * Обрабатывает запросы на уведомление конкретного пользователя.
     * Пример: "Ваша бронь #42 создана, подтвердите в течение 15 минут."
     */
    @KafkaListener(topics = "notification-request", groupId = "booking-service-notifications")
    public void handleNotification(NotificationEvent event) {
        log.info("[NOTIFICATION] userId={}, subject={}, body={}",
                event.getUserId(), event.getSubject(), event.getBody());
        // TODO продакшн: отправить email через SMTP / SendGrid / etc.
    }
}
