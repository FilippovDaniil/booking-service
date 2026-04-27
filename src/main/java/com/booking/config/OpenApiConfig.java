package com.booking.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация Swagger / OpenAPI 3.
 *
 * Swagger — инструмент для автоматической генерации интерактивной документации REST API.
 * После запуска приложения документация доступна по адресам:
 *   http://localhost:8555/swagger-ui.html  — веб-интерфейс для ручного тестирования API
 *   http://localhost:8555/api-docs         — JSON-схема OpenAPI 3 (можно импортировать в Postman)
 *
 * Аннотации в контроллерах:
 *   @Tag(name = "...")              — группирует эндпоинты в разделы
 *   @Operation(summary = "...")     — описание конкретного эндпоинта
 *   @SecurityRequirement(name = "bearerAuth") — помечает эндпоинт как требующий JWT
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Booking Service API")
                        .description("Airbnb-like apartment booking backend")
                        .version("1.0"))
                .components(new Components()
                        // Регистрируем схему аутентификации "bearerAuth"
                        // Это позволяет в Swagger UI нажать "Authorize" и вставить JWT-токен,
                        // после чего он автоматически подставляется в заголовок Authorization: Bearer <token>
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP) // тип: HTTP-аутентификация
                                        .scheme("bearer")               // схема: Bearer token
                                        .bearerFormat("JWT")));         // подсказка для UI
    }
}
