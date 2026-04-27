package com.booking.service;

import com.booking.dto.request.ApartmentRequest;
import com.booking.dto.response.ApartmentResponse;
import com.booking.entity.Apartment;
import com.booking.entity.User;
import com.booking.entity.enums.Role;
import com.booking.exception.AccessDeniedException;
import com.booking.exception.ResourceNotFoundException;
import com.booking.repository.ApartmentRepository;
import com.booking.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApartmentServiceTest {

    @Mock private ApartmentRepository apartmentRepository;
    @Mock private SecurityUtils securityUtils;

    @InjectMocks
    private ApartmentService apartmentService;

    private User landlord;
    private User anotherLandlord;
    private User admin;
    private Apartment apartment;
    private ApartmentRequest apartmentRequest;

    @BeforeEach
    void setUp() {
        landlord = User.builder().id(1L).email("landlord@test.com")
                .firstName("Anna").lastName("Smith").role(Role.LANDLORD).enabled(true).build();
        anotherLandlord = User.builder().id(2L).role(Role.LANDLORD).enabled(true).build();
        admin = User.builder().id(99L).role(Role.ADMIN).enabled(true).build();

        apartment = Apartment.builder()
                .id(10L)
                .landlord(landlord)
                .name("Cozy Studio")
                .city("Moscow")
                .pricePerNight(new BigDecimal("2000.00"))
                .maxGuests(2)
                .active(true)
                .build();

        apartmentRequest = new ApartmentRequest();
        apartmentRequest.setName("Updated Name");
        apartmentRequest.setCity("Moscow");
        apartmentRequest.setPricePerNight(new BigDecimal("2500.00"));
        apartmentRequest.setMaxGuests(3);
    }

    // ==================== create ====================

    @Test
    void create_успешноеСоздание() {
        when(securityUtils.getCurrentUser()).thenReturn(landlord);
        when(apartmentRepository.save(any())).thenReturn(apartment);

        ApartmentResponse response = apartmentService.create(apartmentRequest);

        assertThat(response).isNotNull();
        verify(apartmentRepository).save(any(Apartment.class));
    }

    // ==================== update ====================

    @Test
    void update_владелецОбновляетСвоюКвартиру() {
        when(apartmentRepository.findById(10L)).thenReturn(Optional.of(apartment));
        when(securityUtils.getCurrentUser()).thenReturn(landlord);
        when(apartmentRepository.save(any())).thenReturn(apartment);

        ApartmentResponse response = apartmentService.update(10L, apartmentRequest);

        assertThat(response).isNotNull();
        assertThat(apartment.getName()).isEqualTo("Updated Name");
        assertThat(apartment.getMaxGuests()).isEqualTo(3);
    }

    @Test
    void update_чужойАрендодатель_бросаетAccessDeniedException() {
        when(apartmentRepository.findById(10L)).thenReturn(Optional.of(apartment));
        when(securityUtils.getCurrentUser()).thenReturn(anotherLandlord);

        assertThatThrownBy(() -> apartmentService.update(10L, apartmentRequest))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("don't own");
    }

    // ==================== delete ====================

    @Test
    void delete_владелецДеактивируетСвоюКвартиру() {
        when(apartmentRepository.findById(10L)).thenReturn(Optional.of(apartment));
        when(securityUtils.getCurrentUser()).thenReturn(landlord);

        apartmentService.delete(10L);

        assertThat(apartment.isActive()).isFalse(); // мягкое удаление
        verify(apartmentRepository).save(apartment);
    }

    @Test
    void delete_adminМожетДеактивироватьЛюбуюКвартиру() {
        when(apartmentRepository.findById(10L)).thenReturn(Optional.of(apartment));
        when(securityUtils.getCurrentUser()).thenReturn(admin);

        apartmentService.delete(10L);

        assertThat(apartment.isActive()).isFalse();
    }

    @Test
    void delete_чужойАрендодатель_бросаетAccessDeniedException() {
        when(apartmentRepository.findById(10L)).thenReturn(Optional.of(apartment));
        when(securityUtils.getCurrentUser()).thenReturn(anotherLandlord);

        assertThatThrownBy(() -> apartmentService.delete(10L))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ==================== getById ====================

    @Test
    void getById_существующаяКвартира_возвращаетResponse() {
        when(apartmentRepository.findById(10L)).thenReturn(Optional.of(apartment));

        ApartmentResponse response = apartmentService.getById(10L);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getName()).isEqualTo("Cozy Studio");
    }

    @Test
    void getById_несуществующаяКвартира_бросаетResourceNotFoundException() {
        when(apartmentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apartmentService.getById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ==================== getMyApartments ====================

    @Test
    void getMyApartments_возвращаетКвартирыТекущегоАрендодателя() {
        when(securityUtils.getCurrentUser()).thenReturn(landlord);
        when(apartmentRepository.findByLandlord(landlord)).thenReturn(List.of(apartment));

        List<ApartmentResponse> list = apartmentService.getMyApartments();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getLandlordId()).isEqualTo(1L);
    }
}
