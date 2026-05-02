package com.booking.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Событие запроса на уведомление пользователя.
 * Публикуется в Kafka-топик "notification-request" когда нужно оповестить клиента:
 *  - Бронь создана → "подтвердите в течение 15 минут"
 *  - Бронь отменена арендодателем → "ваша бронь #42 отменена"
 *  - Бронь истекла → "время на подтверждение вышло"
 *
 * Потребляется NotificationService — в реальном продакшне там будет отправка
 * email/push-уведомления через SMTP / Firebase / AWS SES.
 *
 * @NoArgsConstructor нужен Jackson для десериализации входящих сообщений из Kafka.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {

    private Long userId;     // кому отправить уведомление
    private String subject;  // тема: заголовок письма или push-уведомления
    private String body;     // тело: текст сообщения пользователю
}
