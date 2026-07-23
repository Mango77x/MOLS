package com.mls.logistics.service;

import com.mls.logistics.domain.Resource;
import com.mls.logistics.dto.request.CreateResourceRequest;
import com.mls.logistics.dto.response.ImportPreviewResponse;
import com.mls.logistics.dto.response.ImportRowStatus;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.repository.OrderItemRepository;
import com.mls.logistics.repository.ResourceRepository;
import com.mls.logistics.repository.StockRepository;
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
class ResourceServiceImportTest {

    @Mock
    private ResourceRepository resourceRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private OrderItemRepository orderItemRepository;

    private ResourceService resourceService;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        resourceService = new ResourceService(resourceRepository, stockRepository, orderItemRepository, validator);
        lenient().when(resourceRepository.findAll()).thenReturn(List.of());
    }

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "resources.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void previewImport_AllValidRows_MarksEveryRowValid() {
        var file = csv("name,type,criticality\nFuel,Material,HIGH\nRadio,Equipment,MEDIUM\n");

        ImportPreviewResponse<CreateResourceRequest> result = resourceService.previewImport(file);

        assertThat(result.getValidCount()).isEqualTo(2);
        assertThat(result.getErrorCount()).isZero();
        verify(resourceRepository, never()).save(any());
    }

    @Test
    void previewImport_MissingCriticality_IsMarkedError() {
        var file = csv("name,type,criticality\nFuel,Material,\n");

        ImportPreviewResponse<CreateResourceRequest> result = resourceService.previewImport(file);

        assertThat(result.getErrorCount()).isEqualTo(1);
        assertThat(result.getRows().get(0).getStatus()).isEqualTo(ImportRowStatus.ERROR);
    }

    @Test
    void previewImport_DuplicateName_IsMarkedDuplicateWarning() {
        Resource existing = new Resource();
        existing.setName("Fuel");
        when(resourceRepository.findAll()).thenReturn(List.of(existing));
        var file = csv("name,type,criticality\nFuel,Material,HIGH\n");

        ImportPreviewResponse<CreateResourceRequest> result = resourceService.previewImport(file);

        assertThat(result.getRows().get(0).getStatus()).isEqualTo(ImportRowStatus.DUPLICATE_WARNING);
    }

    @Test
    void previewImport_MissingRequiredColumn_ThrowsInvalidRequestException() {
        var file = csv("name,type\nFuel,Material\n");

        assertThatThrownBy(() -> resourceService.previewImport(file))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("criticality");
    }

    @Test
    void commitImport_PersistsValidRows_SkipsErrorRows() {
        when(resourceRepository.save(any(Resource.class))).thenAnswer(inv -> inv.getArgument(0));
        var file = csv("name,type,criticality\nFuel,Material,HIGH\nBad,Material,\n");

        ImportPreviewResponse<CreateResourceRequest> result = resourceService.commitImport(file);

        assertThat(result.getValidCount()).isEqualTo(1);
        assertThat(result.getErrorCount()).isEqualTo(1);
        verify(resourceRepository, times(1)).save(any(Resource.class));
    }
}
