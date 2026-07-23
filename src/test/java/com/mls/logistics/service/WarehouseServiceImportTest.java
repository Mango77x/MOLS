package com.mls.logistics.service;

import com.mls.logistics.domain.Warehouse;
import com.mls.logistics.dto.request.CreateWarehouseRequest;
import com.mls.logistics.dto.response.ImportPreviewResponse;
import com.mls.logistics.dto.response.ImportRowResult;
import com.mls.logistics.dto.response.ImportRowStatus;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.repository.OrderRepository;
import com.mls.logistics.repository.ShipmentRepository;
import com.mls.logistics.repository.StockRepository;
import com.mls.logistics.repository.WarehouseRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for WarehouseService's Sprint 20 bulk-import preview/commit,
 * using a real {@link Validator} (Hibernate Validator, already on the
 * classpath via spring-boot-starter-validation) so the same constraint
 * annotations CreateWarehouseRequest already carries are actually
 * exercised, rather than re-declaring the validation rules in the test.
 */
@ExtendWith(MockitoExtension.class)
class WarehouseServiceImportTest {

    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ShipmentRepository shipmentRepository;

    private WarehouseService warehouseService;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        warehouseService = new WarehouseService(
                warehouseRepository, stockRepository, orderRepository, shipmentRepository, validator);
        // Not every test reaches the duplicate-name lookup (some fail before
        // it, at file-parsing time) — lenient so those aren't flagged as
        // unnecessary stubbing.
        lenient().when(warehouseRepository.findAll()).thenReturn(List.of());
    }

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "warehouses.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void previewImport_AllValidRows_MarksEveryRowValid_AndPersistsNothing() {
        var file = csv("name,location,latitude,longitude\nAlpha,Base A,10.5,20.5\nBeta,Base B,,\n");

        ImportPreviewResponse<CreateWarehouseRequest> result = warehouseService.previewImport(file);

        assertThat(result.getValidCount()).isEqualTo(2);
        assertThat(result.getErrorCount()).isZero();
        assertThat(result.getDuplicateWarningCount()).isZero();
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    void previewImport_RowFailingFieldValidation_IsMarkedError() {
        // Location is required (@NotBlank) — this row leaves it blank.
        var file = csv("name,location\nAlpha,\n");

        ImportPreviewResponse<CreateWarehouseRequest> result = warehouseService.previewImport(file);

        assertThat(result.getErrorCount()).isEqualTo(1);
        ImportRowResult<CreateWarehouseRequest> row = result.getRows().get(0);
        assertThat(row.getStatus()).isEqualTo(ImportRowStatus.ERROR);
        assertThat(row.getErrors()).isNotEmpty();
    }

    @Test
    void previewImport_RowCollidingWithAnExistingName_IsMarkedDuplicateWarning() {
        Warehouse existing = new Warehouse();
        existing.setName("Alpha");
        when(warehouseRepository.findAll()).thenReturn(List.of(existing));
        var file = csv("name,location\nAlpha,Base A\n");

        ImportPreviewResponse<CreateWarehouseRequest> result = warehouseService.previewImport(file);

        assertThat(result.getDuplicateWarningCount()).isEqualTo(1);
        assertThat(result.getRows().get(0).getStatus()).isEqualTo(ImportRowStatus.DUPLICATE_WARNING);
    }

    @Test
    void previewImport_TwoRowsWithTheSameNameInTheFile_SecondIsMarkedDuplicateWarning() {
        var file = csv("name,location\nAlpha,Base A\nAlpha,Base B\n");

        ImportPreviewResponse<CreateWarehouseRequest> result = warehouseService.previewImport(file);

        assertThat(result.getRows().get(0).getStatus()).isEqualTo(ImportRowStatus.VALID);
        assertThat(result.getRows().get(1).getStatus()).isEqualTo(ImportRowStatus.DUPLICATE_WARNING);
    }

    @Test
    void previewImport_NonNumericLatitude_IsMarkedErrorRatherThanThrowing() {
        var file = csv("name,location,latitude\nAlpha,Base A,not-a-number\n");

        ImportPreviewResponse<CreateWarehouseRequest> result = warehouseService.previewImport(file);

        assertThat(result.getErrorCount()).isEqualTo(1);
        assertThat(result.getRows().get(0).getErrors()).anyMatch(msg -> msg.contains("latitude"));
    }

    @Test
    void previewImport_MissingRequiredColumn_ThrowsInvalidRequestException() {
        var file = csv("name\nAlpha\n");

        assertThatThrownBy(() -> warehouseService.previewImport(file))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("location");
    }

    @Test
    void previewImport_EmptyFile_ThrowsInvalidRequestException() {
        var file = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);

        assertThatThrownBy(() -> warehouseService.previewImport(file))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void commitImport_PersistsValidAndDuplicateRows_ButNotErrorRows() {
        Warehouse existing = new Warehouse();
        existing.setName("Beta");
        when(warehouseRepository.findAll()).thenReturn(List.of(existing));
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(inv -> inv.getArgument(0));
        // Row 1: valid. Row 2: duplicate of existing "Beta" (still committed).
        // Row 3: blank location, fails validation (skipped).
        var file = csv("name,location\nAlpha,Base A\nBeta,Base B\nGamma,\n");

        ImportPreviewResponse<CreateWarehouseRequest> result = warehouseService.commitImport(file);

        assertThat(result.getValidCount()).isEqualTo(1);
        assertThat(result.getDuplicateWarningCount()).isEqualTo(1);
        assertThat(result.getErrorCount()).isEqualTo(1);
        verify(warehouseRepository, org.mockito.Mockito.times(2)).save(any(Warehouse.class));
    }

    @Test
    void commitImport_AllRowsInvalid_PersistsNothing() {
        var file = csv("name,location\n,\n");

        ImportPreviewResponse<CreateWarehouseRequest> result = warehouseService.commitImport(file);

        assertThat(result.getErrorCount()).isEqualTo(1);
        verify(warehouseRepository, never()).save(any());
    }
}
