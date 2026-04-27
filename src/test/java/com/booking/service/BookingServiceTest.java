package com.booking.service;

import com.booking.dto.request.BookingRequest;
import com.booking.dto.response.BookingResponse;
import com.booking.entity.Apartment;
import com.booking.entity.Booking;
import com.booking.entity.User;
import com.booking.entity.enums.BookingStatus;
import com.booking.entity.enums.Role;
import com.booking.event.BookingEventPublisher;
import com.booking.exception.AccessDeniedException;
import com.booking.exception.BookingConflictException;
import com.booking.exception.InvalidOperationException;
import com.booking.repository.BookingRepository;
import com.booking.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ===== Unit-тесты BookingService =====
 *
 * Что проверяем:
 *   - Корректный расчёт цены (ночи × тариф)
 *   - Бизнес-правила: нельзя забронировать свою квартиру, превысить лимит гостей и т.д.
 *   - Конфликт дат возвращает BookingConflictException
 *   - Проверку прав: только владелец брони может подтвердить/отменить
 *   - Работу планировщика: expirePendingBookings и completeFinishedBookings
 *
 * ХИТРОСТЬ С МОКОМ РЕПОЗИТОРИЯ:
 *   when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0))
 *   → «когда вызван save() с любым аргументом — вернуть тот же объект обратно»
 *   Это полезно когда нам нужно, чтобы save() "применил" изменения статуса.
 *
 * ПОЧЕМУ ТЕСТЫ НЕ ЗАВИСЯТ ОТ БД:
 *   BookingService принимает зависимости через конструктор.
 *   @InjectMocks подменяет их моками.
 *   Нет реальных SQL-запросов → тесты быстрые (< 1 сек) и стабильные.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private ApartmentService apartmentService;
    @Mock private SecurityUtils securityUtils;
    @Mock private BookingEventPublisher eventPublisher;

    @InjectMocks
    private BookingService bookingService;

    private User client;
    private User landlord;
    private Apartment apartment;
    private BookingRequest bookingRequest;
    private Booking pendingBooking;

    @BeforeEach
    void setUp() {
        client   = User.builder().id(1L).email("client@test.com").role(Role.CLIENT).enabled(true).build();
        landlord = User.builder().id(2L).email("landlord@test.com").role(Role.LANDLORD).enabled(true).build();

        apartment = Apartment.builder()
                .id(10L)
                .landlord(landlord)
                .name("Test Apt")
                .pricePerNight(new BigDecimal("1000.00"))
                .maxGuests(4)
                .active(true)
                .build();

        bookingRequest = new BookingRequest();
        bookingRequest.setApartmentId(10L);
        bookingRequest.setStartDate(LocalDate.now().plusDays(1));
        bookingRequest.setEndDate(LocalDate.now().plusDays(4)); // 3 ночи
        bookingRequest.setGuestsCount(2);

        pendingBooking = Booking.builder()
                .id(100L)
                .client(client)
                .apartment(apartment)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(4))
                .guestsCount(2)
                .totalPrice(new BigDecimal("3000.00"))
                .status(BookingStatus.PENDING)
                .build();
    }

    // ==================== create ====================

    @Test
    void create_успешноеСозданиеБрони() {
        // Arrange
        when(securityUtils.getCurrentUser()).thenReturn(client);
        when(apartmentService.findById(10L)).thenReturn(apartment);
        when(bookingRepository.findConflictingBookings(any(), any(), any()))
                .thenReturn(Collections.emptyList()); // квартира свободна
        when(bookingRepository.save(any())).thenReturn(pendingBooking);

        // Act
        BookingResponse response = bookingService.create(bookingRequest);

        // Assert
        assertThat(response.getStatus()).isEqualTo(BookingStatus.PENDING);
        // 3 ночи × 1000 = 3000
        assertThat(response.getTotalPrice()).isEqualByComparingTo("3000.00");
        // Проверяем что события были опубликованы в Kafka
        verify(eventPublisher).publishBookingEvent(any());
        verify(eventPublisher).publishNotification(any());
    }

    @Test
    void create_арендодательПытаетсяЗабронироватьСвоюКвартиру_бросаетException() {
        // landlord является владельцем apartment, не может сам у себя бронировать
        when(securityUtils.getCurrentUser()).thenReturn(landlord);
        when(apartmentService.findById(10L)).thenReturn(apartment);

        assertThatThrownBy(() -> bookingService.create(bookingRequest))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Cannot book your own apartment");
    }

    @Test
    void create_превышениеКоличестваГостей_бросаетException() {
        bookingRequest.setGuestsCount(10); // maxGuests = 4
        when(securityUtils.getCurrentUser()).thenReturn(client);
        when(apartmentService.findById(10L)).thenReturn(apartment);

        assertThatThrownBy(() -> bookingService.create(bookingRequest))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Guests count exceeds apartment limit");
    }

    @Test
    void create_датаВыездаНеПослеЗаезда_бросаетException() {
        // endDate == startDate — невалидный диапазон
        bookingRequest.setEndDate(bookingRequest.getStartDate());
        when(securityUtils.getCurrentUser()).thenReturn(client);
        when(apartmentService.findById(10L)).thenReturn(apartment);

        assertThatThrownBy(() -> bookingService.create(bookingRequest))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("End date must be after start date");
    }

    @Test
    void create_конфликтДат_бросаетBookingConflictException() {
        when(securityUtils.getCurrentUser()).thenReturn(client);
        when(apartmentService.findById(10L)).thenReturn(apartment);
        // findConflictingBookings вернул непустой список → квартира занята
        when(bookingRepository.findConflictingBookings(any(), any(), any()))
                .thenReturn(List.of(pendingBooking));

        assertThatThrownBy(() -> bookingService.create(bookingRequest))
                .isInstanceOf(BookingConflictException.class);
    }

    @Test
    void create_неактивнаяКвартира_бросаетException() {
        apartment.setActive(false); // квартира деактивирована
        when(securityUtils.getCurrentUser()).thenReturn(client);
        when(apartmentService.findById(10L)).thenReturn(apartment);

        assertThatThrownBy(() -> bookingService.create(bookingRequest))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Apartment is not available");
    }

    // ==================== confirm ====================

    @Test
    void confirm_клиентПодтверждаетСвоюБронь_статусMeняетсяНаCONFIRMED() {
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(pendingBooking));
        when(securityUtils.getCurrentUser()).thenReturn(client);
        // thenAnswer(inv -> inv.getArgument(0)) — вернуть тот же объект что передан в save()
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = bookingService.confirm(100L);

        assertThat(response.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void confirm_чужаяБронь_бросаетAccessDeniedException() {
        // Другой пользователь пытается подтвердить не свою бронь
        User otherClient = User.builder().id(99L).role(Role.CLIENT).build();
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(pendingBooking));
        when(securityUtils.getCurrentUser()).thenReturn(otherClient);

        assertThatThrownBy(() -> bookingService.confirm(100L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void confirm_бронь_не_PENDING_бросаетException() {
        pendingBooking.setStatus(BookingStatus.CONFIRMED); // уже подтверждена — нельзя подтвердить повторно
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(pendingBooking));
        when(securityUtils.getCurrentUser()).thenReturn(client);

        assertThatThrownBy(() -> bookingService.confirm(100L))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("not in PENDING status");
    }

    // ==================== cancelByClient ====================

    @Test
    void cancelByClient_успешнаяОтмена() {
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(pendingBooking));
        when(securityUtils.getCurrentUser()).thenReturn(client);
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = bookingService.cancelByClient(100L);

        assertThat(response.getStatus()).isEqualTo(BookingStatus.CANCELLED_BY_CLIENT);
    }

    @Test
    void cancelByClient_бронь_уже_началась_бросаетException() {
        // Дата заезда в прошлом — отменять нельзя
        pendingBooking.setStartDate(LocalDate.now().minusDays(1));
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(pendingBooking));
        when(securityUtils.getCurrentUser()).thenReturn(client);

        assertThatThrownBy(() -> bookingService.cancelByClient(100L))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("already started");
    }

    // ==================== cancelByLandlord ====================

    @Test
    void cancelByLandlord_успешнаяОтмена_уведомлениеОтправлено() {
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(pendingBooking));
        when(securityUtils.getCurrentUser()).thenReturn(landlord);
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = bookingService.cancelByLandlord(100L);

        assertThat(response.getStatus()).isEqualTo(BookingStatus.CANCELLED_BY_LANDLORD);
        // Клиент должен получить уведомление об отмене арендодателем
        verify(eventPublisher).publishNotification(any());
    }

    @Test
    void cancelByLandlord_чужаяКвартира_бросаетAccessDeniedException() {
        User anotherLandlord = User.builder().id(99L).role(Role.LANDLORD).build();
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(pendingBooking));
        when(securityUtils.getCurrentUser()).thenReturn(anotherLandlord);

        assertThatThrownBy(() -> bookingService.cancelByLandlord(100L))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ==================== expirePendingBookings (планировщик) ====================

    @Test
    void expirePendingBookings_переводитВStatusEXPIRED() {
        // Репозиторий "нашёл" просроченные брони
        when(bookingRepository.findExpiredPendingBookings(any())).thenReturn(List.of(pendingBooking));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        bookingService.expirePendingBookings(LocalDate.now().atStartOfDay());

        // Статус изменился
        assertThat(pendingBooking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        // Событие и уведомление опубликованы
        verify(eventPublisher).publishBookingEvent(any());
        verify(eventPublisher).publishNotification(any());
    }

    // ==================== completeFinishedBookings (планировщик) ====================

    @Test
    void completeFinishedBookings_переводитВStatusCOMPLETED() {
        pendingBooking.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findCompletedBookings(any())).thenReturn(List.of(pendingBooking));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        bookingService.completeFinishedBookings(LocalDate.now());

        assertThat(pendingBooking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        verify(eventPublisher).publishBookingEvent(any());
    }
}
