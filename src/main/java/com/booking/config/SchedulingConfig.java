package com.booking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Конфигурация планировщика задач.
 *
 * @EnableScheduling активирует поддержку аннотации @Scheduled в Spring.
 * Без этого класса методы с @Scheduled в BookingExpirationScheduler
 * не будут вызываться автоматически.
 *
 * Spring создаёт пул потоков для запуска задач по расписанию.
 * По умолчанию используется однопоточный пул (TaskScheduler),
 * то есть задачи выполняются последовательно, не параллельно.
 *
 * Конфигурация вынесена в отдельный класс (а не в BookingApplication),
 * чтобы можно было легко отключить планировщик в тестах —
 * для этого достаточно не создавать этот бин (например, через @Profile("!test")).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
