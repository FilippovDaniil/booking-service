package com.booking.entity;

import com.booking.entity.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * JPA-сущность «Пользователь».
 * Хранится в таблице users.
 * Один пользователь может иметь только одну роль (CLIENT / LANDLORD / ADMIN).
 * Поле enabled используется для мягкой блокировки: false — аккаунт заблокирован.
 */
@Entity
@Table(name = "users", indexes = {
        // Индекс по email ускоряет поиск при логине и проверке уникальности
        @Index(name = "idx_user_email", columnList = "email")
})
@Getter
@Setter
@NoArgsConstructor   // нужен JPA для создания объекта через рефлексию
@AllArgsConstructor  // нужен @Builder для конструктора со всеми полями
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // автоинкремент в PostgreSQL (SERIAL)
    private Long id;

    @Column(unique = true, nullable = false) // email уникален — используется как логин
    private String email;

    @Column(nullable = false) // хранится хэш пароля (BCrypt), а не сам пароль
    private String password;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING) // сохраняем строку "CLIENT"/"LANDLORD"/"ADMIN", не число
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    @Builder.Default // без этой аннотации @Builder игнорирует дефолт и ставит false
    private boolean enabled = true;

    @CreationTimestamp // Hibernate сам выставляет при вставке строки
    private LocalDateTime createdAt;

    @UpdateTimestamp   // Hibernate сам обновляет при каждом UPDATE
    private LocalDateTime updatedAt;
}
