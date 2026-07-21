package com.mls.logistics.service;

import com.mls.logistics.domain.Vehicle;
import com.mls.logistics.domain.VehicleStatus;
import com.mls.logistics.dto.request.CreateVehicleRequest;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.repository.ShipmentRepository;
import com.mls.logistics.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for VehicleService.
 *
 * Tests business logic without requiring database or Spring context.
 * Uses Mockito to mock repository dependencies.
 */
@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private ShipmentRepository shipmentRepository;

    @InjectMocks
    private VehicleService vehicleService;

    private Vehicle testVehicle;

    @BeforeEach
    void setUp() {
        testVehicle = new Vehicle();
        testVehicle.setId(1L);
        testVehicle.setType("Truck");
        testVehicle.setCapacity(1000);
        testVehicle.setStatus(VehicleStatus.AVAILABLE);
    }

    @Test
    void getAllVehicles_ShouldReturnAllVehicles() {
        // Given
        List<Vehicle> vehicles = Arrays.asList(testVehicle);
        when(vehicleRepository.findAll()).thenReturn(vehicles);

        // When
        List<Vehicle> result = vehicleService.getAllVehicles();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo("Truck");
        verify(vehicleRepository, times(1)).findAll();
    }

    @Test
    void getVehicleById_WhenExists_ShouldReturnVehicle() {
        // Given
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(testVehicle));

        // When
        Optional<Vehicle> result = vehicleService.getVehicleById(1L);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo("Truck");
        verify(vehicleRepository, times(1)).findById(1L);
    }

    @Test
    void getVehicleById_WhenNotExists_ShouldReturnEmpty() {
        // Given
        when(vehicleRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        Optional<Vehicle> result = vehicleService.getVehicleById(999L);

        // Then
        assertThat(result).isEmpty();
        verify(vehicleRepository, times(1)).findById(999L);
    }

    @Test
    void createVehicle_WithValidRequest_ShouldReturnCreatedVehicle() {
        // Given
        CreateVehicleRequest request = new CreateVehicleRequest("Truck", 1000, "AVAILABLE");
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(testVehicle);

        // When
        Vehicle result = vehicleService.createVehicle(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo("Truck");
        verify(vehicleRepository, times(1)).save(any(Vehicle.class));
    }

    @Test
    void deleteVehicle_WhenExists_ShouldDeleteSuccessfully() {
        // Given
        when(vehicleRepository.existsById(1L)).thenReturn(true);
        doNothing().when(vehicleRepository).deleteById(1L);

        // When
        vehicleService.deleteVehicle(1L);

        // Then
        verify(vehicleRepository, times(1)).existsById(1L);
        verify(vehicleRepository, times(1)).deleteById(1L);
    }

    @Test
    void deleteVehicle_WhenNotExists_ShouldThrowException() {
        // Given
        when(vehicleRepository.existsById(999L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> vehicleService.deleteVehicle(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Vehicle not found with id: '999'");

        verify(vehicleRepository, times(1)).existsById(999L);
        verify(vehicleRepository, never()).deleteById(any());
    }

    @Test
    void deleteVehicle_WithExistingShipments_ShouldThrowException() {
        // Given
        when(vehicleRepository.existsById(1L)).thenReturn(true);
        when(shipmentRepository.existsByVehicleId(1L)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> vehicleService.deleteVehicle(1L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("existing shipments");

        verify(vehicleRepository, never()).deleteById(any());
    }
}
