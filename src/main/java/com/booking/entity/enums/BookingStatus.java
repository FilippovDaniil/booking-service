package com.booking.entity.enums;

/**
 * Жизненный цикл бронирования:
 *
 *  PENDING             — создано клиентом, ожидает подтверждения (даётся 15 минут).
 *  CONFIRMED           — клиент подтвердил бронирование.
 *  CANCELLED_BY_CLIENT — клиент отменил до начала заезда.
 *  CANCELLED_BY_LANDLORD — владелец отменил до начала заезда.
 *  EXPIRED             — клиент не подтвердил за 15 минут (переводит планировщик).
 *  COMPLETED           — дата выезда прошла, бронь завершена (переводит планировщик).
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED_BY_CLIENT,
    CANCELLED_BY_LANDLORD,
    EXPIRED,
    COMPLETED
}
