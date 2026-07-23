package com.mls.logistics.service;

import com.mls.logistics.domain.Unit;
import com.mls.logistics.dto.request.CreateUnitRequest;
import com.mls.logistics.dto.response.ImportPreviewResponse;
import com.mls.logistics.dto.response.ImportRowStatus;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.repository.OrderRepository;
import com.mls.logistics.repository.UnitRepository;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnitServiceImportTest {

    @Mock
    private UnitRepository unitRepository;
    @Mock
    private OrderRepository orderRepository;

    private UnitService unitService;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        unitService = new UnitService(unitRepository, orderRepository, validator);
        lenient().when(unitRepository.findAll()).thenReturn(List.of());
    }

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "units.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void previewImport_AllValidRows_MarksEveryRowValid() {
        var file = csv("name,location\n1st Battalion,Fort A\n2nd Battalion,Fort B\n");

        ImportPreviewResponse<CreateUnitRequest> result = unitService.previewImport(file);

        assertThat(result.getValidCount()).isEqualTo(2);
        verify(unitRepository, never()).save(any());
    }

    @Test
    void previewImport_TooShortName_IsMarkedError() {
        // @Size(min = 2, ...) on the name field.
        var file = csv("name,location\nA,Fort A\n");

        ImportPreviewResponse<CreateUnitRequest> result = unitService.previewImport(file);

        assertThat(result.getErrorCount()).isEqualTo(1);
        assertThat(result.getRows().get(0).getStatus()).isEqualTo(ImportRowStatus.ERROR);
    }

    @Test
    void previewImport_DuplicateName_IsMarkedDuplicateWarning() {
        Unit existing = new Unit();
        existing.setName("1st Battalion");
        when(unitRepository.findAll()).thenReturn(List.of(existing));
        var file = csv("name,location\n1st Battalion,Fort A\n");

        ImportPreviewResponse<CreateUnitRequest> result = unitService.previewImport(file);

        assertThat(result.getRows().get(0).getStatus()).isEqualTo(ImportRowStatus.DUPLICATE_WARNING);
    }

    @Test
    void previewImport_MissingRequiredColumn_ThrowsInvalidRequestException() {
        var file = csv("name\n1st Battalion\n");

        assertThatThrownBy(() -> unitService.previewImport(file))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("location");
    }

    @Test
    void commitImport_PersistsValidRows_SkipsErrorRows() {
        when(unitRepository.save(any(Unit.class))).thenAnswer(inv -> inv.getArgument(0));
        var file = csv("name,location\n1st Battalion,Fort A\nA,Fort B\n");

        ImportPreviewResponse<CreateUnitRequest> result = unitService.commitImport(file);

        assertThat(result.getValidCount()).isEqualTo(1);
        assertThat(result.getErrorCount()).isEqualTo(1);
        verify(unitRepository, times(1)).save(any(Unit.class));
    }
}
