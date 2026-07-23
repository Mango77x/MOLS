package com.mls.logistics.service;

import com.mls.logistics.domain.Resource;
import com.mls.logistics.dto.request.CreateResourceRequest;
import com.mls.logistics.dto.request.UpdateResourceRequest;
import com.mls.logistics.dto.response.ImportPreviewResponse;
import com.mls.logistics.dto.response.ImportRowResult;
import com.mls.logistics.dto.response.ImportRowStatus;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.repository.OrderItemRepository;
import com.mls.logistics.repository.ResourceRepository;
import com.mls.logistics.repository.StockRepository;
import com.mls.logistics.service.imports.CsvImportSupport;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service layer for Resource-related business operations.
 * 
 * This class acts as an intermediary between controllers and repositories,
 * enforcing business rules and application logic.
 */
@Service
@Transactional(readOnly = true)
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final StockRepository stockRepository;
    private final OrderItemRepository orderItemRepository;
    private final Validator validator;

    /**
     * Constructor-based dependency injection.
     * This is the recommended approach in Spring.
     */
    public ResourceService(
            ResourceRepository resourceRepository,
            StockRepository stockRepository,
            OrderItemRepository orderItemRepository,
            Validator validator) {
        this.resourceRepository = resourceRepository;
        this.stockRepository = stockRepository;
        this.orderItemRepository = orderItemRepository;
        this.validator = validator;
    }

    /**
     * Retrieves all registered resources.
     */
    public List<Resource> getAllResources() {
        return resourceRepository.findAll();
    }

    public List<Resource> getAllResources(Sort sort) {
        return resourceRepository.findAll(sort);
    }

    /**
     * Retrieves a page of resources.
     */
    public Page<Resource> getAllResources(Pageable pageable) {
        return resourceRepository.findAll(pageable);
    }

    /**
     * Retrieves a page of resources matching the optional filters.
     *
     * @param name case-insensitive name fragment; ignored when null/blank
     * @param type exact type (case-insensitive); ignored when null/blank
     */
    public Page<Resource> searchResources(String name, String type, Pageable pageable) {
        List<Specification<Resource>> filters = new ArrayList<>();
        if (name != null && !name.isBlank()) {
            String pattern = "%" + name.trim().toLowerCase() + "%";
            filters.add((root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern));
        }
        if (type != null && !type.isBlank()) {
            String value = type.trim().toLowerCase();
            filters.add((root, query, cb) -> cb.equal(cb.lower(root.get("type")), value));
        }
        return resourceRepository.findAll(Specification.allOf(filters), pageable);
    }

    /**
     * Retrieves a resource by its identifier.
     */
    public Optional<Resource> getResourceById(Long id) {
        return resourceRepository.findById(id);
    }

    /**
     * Creates a new resource from a DTO request.
     * 
     * This method separates API contracts from domain logic.
     *
     * @param request DTO containing resource data
     * @return created resource entity
     */
    @Transactional
    public Resource createResource(CreateResourceRequest request) {
        Resource resource = new Resource();
        resource.setName(request.getName());
        resource.setType(request.getType());
        resource.setCriticality(request.getCriticality());
        return resourceRepository.save(resource);
    }

    /**
     * Updates an existing resource.
     * 
     * Only non-null fields from the request are updated.
     *
     * @param id resource identifier
     * @param request update data
     * @return updated resource
     * @throws ResourceNotFoundException if resource doesn't exist
     */
    @Transactional
    public Resource updateResource(Long id, UpdateResourceRequest request) {
        Resource resource = resourceRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource", "id", id));

        if (request.getName() != null) {
            resource.setName(request.getName());
        }
        if (request.getType() != null) {
            resource.setType(request.getType());
        }
        if (request.getCriticality() != null) {
            resource.setCriticality(request.getCriticality());
        }

        return resourceRepository.save(resource);
    }

    /**
     * Deletes a resource by ID.
     *
     * @param id resource identifier
     * @throws ResourceNotFoundException if resource doesn't exist
     * @throws InvalidRequestException if the resource still has stock or order items referencing it
     */
    @Transactional
    public void deleteResource(Long id) {
        if (!resourceRepository.existsById(id)) {
            throw new ResourceNotFoundException("Resource", "id", id);
        }
        if (stockRepository.existsByResourceId(id)) {
            throw new InvalidRequestException(
                "RESOURCE_DELETE_HAS_STOCK",
                Map.of("resourceId", id),
                "Cannot delete resource with existing stock. Resource id: " + id);
        }
        // Order items reference their resource via a required FK, independently
        // of whether stock for it still exists in any warehouse.
        if (orderItemRepository.existsByResourceId(id)) {
            throw new InvalidRequestException(
                "RESOURCE_DELETE_REFERENCED",
                Map.of("resourceId", id),
                "Cannot delete resource referenced by order items. Resource id: " + id);
        }
        resourceRepository.deleteById(id);
    }

    private static final List<String> IMPORT_REQUIRED_COLUMNS = List.of("name", "type", "criticality");

    /**
     * Parses and validates a CSV file of resources without persisting
     * anything.
     */
    public ImportPreviewResponse<CreateResourceRequest> previewImport(MultipartFile file) {
        return ImportPreviewResponse.from(buildImportRows(file));
    }

    /**
     * Re-parses and re-validates the same file, then persists every row
     * that isn't an ERROR. One transaction for the whole file.
     */
    @Transactional
    public ImportPreviewResponse<CreateResourceRequest> commitImport(MultipartFile file) {
        List<ImportRowResult<CreateResourceRequest>> rows = buildImportRows(file);
        for (ImportRowResult<CreateResourceRequest> row : rows) {
            if (row.getStatus() != ImportRowStatus.ERROR) {
                createResource(row.getData());
            }
        }
        return ImportPreviewResponse.from(rows);
    }

    private List<ImportRowResult<CreateResourceRequest>> buildImportRows(MultipartFile file) {
        List<CSVRecord> records = CsvImportSupport.parseRecords(file, IMPORT_REQUIRED_COLUMNS);
        Set<String> existingNames = resourceRepository.findAll().stream()
                .map(r -> r.getName().toLowerCase())
                .collect(Collectors.toSet());
        Set<String> seenInFile = new HashSet<>();

        List<ImportRowResult<CreateResourceRequest>> rows = new ArrayList<>();
        int rowNumber = 0;
        for (CSVRecord record : records) {
            rowNumber++;
            List<String> errors = new ArrayList<>();
            try {
                CreateResourceRequest dto = new CreateResourceRequest();
                dto.setName(CsvImportSupport.get(record, "name"));
                dto.setType(CsvImportSupport.get(record, "type"));
                dto.setCriticality(CsvImportSupport.get(record, "criticality"));

                for (ConstraintViolation<CreateResourceRequest> violation : validator.validate(dto)) {
                    errors.add(violation.getMessage());
                }

                if (!errors.isEmpty()) {
                    rows.add(new ImportRowResult<>(rowNumber, ImportRowStatus.ERROR, errors, dto));
                    continue;
                }

                String normalizedName = dto.getName().trim().toLowerCase();
                boolean duplicate = existingNames.contains(normalizedName) || !seenInFile.add(normalizedName);
                rows.add(new ImportRowResult<>(rowNumber,
                        duplicate ? ImportRowStatus.DUPLICATE_WARNING : ImportRowStatus.VALID,
                        List.of(), dto));
            } catch (RuntimeException ex) {
                rows.add(new ImportRowResult<>(rowNumber, ImportRowStatus.ERROR,
                        List.of("Could not read this row: " + ex.getMessage()), null));
            }
        }
        return rows;
    }
}
