package com.booking.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Конфигурация Kafka.
 *
 * Kafka — брокер сообщений. В этом приложении используется для:
 *  - Публикации событий бронирования ("booking-lifecycle")
 *  - Отправки уведомлений пользователям ("notification-request")
 *
 * Kafka работает по схеме «producer → topic → consumer»:
 *  Producer (BookingEventPublisher) отправляет сообщение в топик.
 *  Consumer (NotificationService) читает из топика.
 *
 * Если Kafka недоступна — приложение продолжает работу (setFatalIfBrokerNotAvailable = false),
 * события просто не публикуются (логируется предупреждение).
 */
@Configuration
public class KafkaConfig {

    // Адрес Kafka-брокера из application.yml (например, localhost:9092)
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * KafkaAdmin — административный клиент Kafka.
     * Используется Spring Kafka для автоматического создания топиков при старте приложения.
     * setFatalIfBrokerNotAvailable(false) — не падаем при недоступном Kafka при старте.
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "3000");      // таймаут запроса 3 сек
        configs.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "5000");  // общий таймаут 5 сек
        KafkaAdmin admin = new KafkaAdmin(configs);
        admin.setFatalIfBrokerNotAvailable(false); // не падаем при старте без Kafka
        return admin;
    }

    /**
     * ProducerFactory — фабрика, создающая Kafka-продюсеров.
     *
     * Ключевые настройки:
     *  KEY_SERIALIZER   — ключ партиционирования сериализуется как строка (bookingId или userId)
     *  VALUE_SERIALIZER — значение (наш объект BookingEvent / NotificationEvent) сериализуется в JSON
     *  ADD_TYPE_INFO_HEADERS = false — не добавляем заголовок с именем Java-класса в сообщение
     *    (без этого потребители из других языков/сервисов не смогут прочитать сообщение)
     *  REQUEST_TIMEOUT_MS   — сколько ждать ответа от брокера (3 сек)
     *  DELIVERY_TIMEOUT_MS  — полный таймаут доставки сообщения (5 сек)
     *  MAX_BLOCK_MS         — сколько блокировать поток при недоступном брокере (3 сек)
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 5000);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000); // не блокируем надолго если нет Kafka
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * KafkaTemplate — основной инструмент для отправки сообщений в Kafka.
     * Используется в BookingEventPublisher: kafkaTemplate.send(topic, key, value).
     * Параметры <String, Object>: ключ — строка, значение — любой объект (сериализуется в JSON).
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> pf) {
        return new KafkaTemplate<>(pf);
    }
}
