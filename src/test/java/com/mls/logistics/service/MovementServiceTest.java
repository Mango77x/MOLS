package com.mls.logistics.service;

import com.mls.logistics.domain.Movement;
import com.mls.logistics.domain.MovementType;
import com.mls.logistics.domain.Stock;
import com.mls.logistics.repository.MovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MovementService.
 *
 * The movement audit trail is append-only: this service is read-only
 * (movements are created internally by StockService), so only query
 * operations are tested here.
 */
@ExtendWith(MockitoExtension.class)
class MovementServiceTest {

    @Mock
    private MovementRepository movementRepository;

    @InjectMocks
    private MovementService movementService;

    private Movement testMovement;

    @BeforeEach
    void setUp() {
        Stock stock = new Stock();
        stock.setId(1L);

        testMovement = new Movement();
        testMovement.setId(1L);
        testMovement.setStock(stock);
        testMovement.setType(MovementType.ENTRY);
        testMovement.setQuantity(5);
        testMovement.setDateTime(LocalDateTime.of(2024, 1, 1, 10, 0));
    }

    @Test
    void getAllMovements_ShouldReturnAllMovements() {
        // Given
        List<Movement> movements = Arrays.asList(testMovement);
        when(movementRepository.findAll()).thenReturn(movements);

        // When
        List<Movement> result = movementService.getAllMovements();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo(MovementType.ENTRY);
        verify(movementRepository, times(1)).findAll();
    }

    @Test
    void getMovementById_WhenExists_ShouldReturnMovement() {
        // Given
        when(movementRepository.findById(1L)).thenReturn(Optional.of(testMovement));

        // When
        Optional<Movement> result = movementService.getMovementById(1L);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(MovementType.ENTRY);
        verify(movementRepository, times(1)).findById(1L);
    }

    @Test
    void getMovementById_WhenNotExists_ShouldReturnEmpty() {
        // Given
        when(movementRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        Optional<Movement> result = movementService.getMovementById(999L);

        // Then
        assertThat(result).isEmpty();
        verify(movementRepository, times(1)).findById(999L);
    }

    @Test
    void getMovementCountByType_ShouldGroupByEnumName() {
        // Given
        Movement exit = new Movement();
        exit.setType(MovementType.EXIT);
        LocalDateTime since = LocalDateTime.of(2024, 1, 1, 0, 0);
        when(movementRepository.findByDateTimeAfter(since))
                .thenReturn(List.of(testMovement, exit, testMovement));

        // When
        Map<String, Long> result = movementService.getMovementCountByType(since);

        // Then
        assertThat(result)
                .containsEntry("ENTRY", 2L)
                .containsEntry("EXIT", 1L);
    }
}
