package com.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входа в приложение.
 *
 * @SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan
 *   - @Configuration     — этот класс может содержать @Bean-определения
 *   - @EnableAutoConfiguration — Spring Boot сам настраивает компоненты (DataSource, Redis, Kafka и т.д.)
 *     на основе зависимостей в classpath и настроек application.yml
 *   - @ComponentScan     — сканирует пакет com.booking и все его подпакеты,
 *     находит @Component, @Service, @Repository, @Controller и регистрирует их как бины
 *
 * Планировщик (@Scheduled) включается через @EnableScheduling в SchedulingConfig.
 */
@SpringBootApplication
public class BookingApplication {

    public static void main(String[] args) {
        // SpringApplication.run запускает embedded Tomcat, поднимает ApplicationContext
        // и делает приложение доступным на порту, указанном в application.yml (8555)
        SpringApplication.run(BookingApplication.class, args);
    }
}
