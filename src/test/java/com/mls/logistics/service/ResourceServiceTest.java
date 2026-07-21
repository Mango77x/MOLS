package com.mls.logistics.service;

import com.mls.logistics.domain.Resource;
import com.mls.logistics.dto.request.CreateResourceRequest;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.repository.OrderItemRepository;
import com.mls.logistics.repository.ResourceRepository;
import com.mls.logistics.repository.StockRepository;
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
 * Unit tests for ResourceService.
 *
 * Tests business logic without requiring database or Spring context.
 * Uses Mockito to mock repository dependencies.
 */
@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private ResourceService resourceService;

    private Resource testResource;

    @BeforeEach
    void setUp() {
        testResource = new Resource();
        testResource.setId(1L);
        testResource.setName("Test Resource");
        testResource.setType("Material");
        testResource.setCriticality("HIGH");
    }

    @Test
    void getAllResources_ShouldReturnAllResources() {
        // Given
        List<Resource> resources = Arrays.asList(testResource);
        when(resourceRepository.findAll()).thenReturn(resources);

        // When
        List<Resource> result = resourceService.getAllResources();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Test Resource");
        verify(resourceRepository, times(1)).findAll();
    }

    @Test
    void getResourceById_WhenExists_ShouldReturnResource() {
        // Given
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(testResource));

        // When
        Optional<Resource> result = resourceService.getResourceById(1L);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Test Resource");
        verify(resourceRepository, times(1)).findById(1L);
    }

    @Test
    void getResourceById_WhenNotExists_ShouldReturnEmpty() {
        // Given
        when(resourceRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        Optional<Resource> result = resourceService.getResourceById(999L);

        // Then
        assertThat(result).isEmpty();
        verify(resourceRepository, times(1)).findById(999L);
    }

    @Test
    void createResource_WithValidRequest_ShouldReturnCreatedResource() {
        // Given
        CreateResourceRequest request = new CreateResourceRequest("New Resource", "Material", "HIGH");
        when(resourceRepository.save(any(Resource.class))).thenReturn(testResource);

        // When
        Resource result = resourceService.createResource(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Test Resource");
        verify(resourceRepository, times(1)).save(any(Resource.class));
    }

    @Test
    void deleteResource_WhenExists_ShouldDeleteSuccessfully() {
        // Given
        when(resourceRepository.existsById(1L)).thenReturn(true);
        doNothing().when(resourceRepository).deleteById(1L);

        // When
        resourceService.deleteResource(1L);

        // Then
        verify(resourceRepository, times(1)).existsById(1L);
        verify(resourceRepository, times(1)).deleteById(1L);
    }

    @Test
    void deleteResource_WhenNotExists_ShouldThrowException() {
        // Given
        when(resourceRepository.existsById(999L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> resourceService.deleteResource(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Resource not found with id: '999'");

        verify(resourceRepository, times(1)).existsById(999L);
        verify(resourceRepository, never()).deleteById(any());
    }

    @Test
    void deleteResource_WithExistingStock_ShouldThrowException() {
        // Given
        when(resourceRepository.existsById(1L)).thenReturn(true);
        when(stockRepository.existsByResourceId(1L)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> resourceService.deleteResource(1L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("existing stock");

        verify(resourceRepository, never()).deleteById(any());
    }

    @Test
    void deleteResource_WithExistingOrderItems_ShouldThrowException() {
        // Given
        when(resourceRepository.existsById(1L)).thenReturn(true);
        when(stockRepository.existsByResourceId(1L)).thenReturn(false);
        when(orderItemRepository.existsByResourceId(1L)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> resourceService.deleteResource(1L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("order items");

        verify(resourceRepository, never()).deleteById(any());
    }
}
