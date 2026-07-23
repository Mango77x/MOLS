package com.mls.logistics.service;

import com.mls.logistics.domain.Unit;
import com.mls.logistics.dto.request.CreateUnitRequest;
import com.mls.logistics.dto.request.UpdateUnitRequest;
import com.mls.logistics.dto.response.ImportPreviewResponse;
import com.mls.logistics.dto.response.ImportRowResult;
import com.mls.logistics.dto.response.ImportRowStatus;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.repository.OrderRepository;
import com.mls.logistics.repository.UnitRepository;
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
 * Service layer for Unit-related business operations.
 * 
 * This class acts as an intermediary between controllers and repositories,
 * enforcing business rules and application logic.
 */
@Service
@Transactional(readOnly = true)
public class UnitService {

    private final UnitRepository unitRepository;
    private final OrderRepository orderRepository;
    private final Validator validator;

    /**
     * Constructor-based dependency injection.
     * This is the recommended approach in Spring.
     */
    public UnitService(UnitRepository unitRepository, OrderRepository orderRepository, Validator validator) {
        this.unitRepository = unitRepository;
        this.orderRepository = orderRepository;
        this.validator = validator;
    }

    /**
     * Retrieves all registered units.
     */
    public List<Unit> getAllUnits() {
        return unitRepository.findAll();
    }

    public List<Unit> getAllUnits(Sort sort) {
        return unitRepository.findAll(sort);
    }

    /**
     * Retrieves a page of units.
     */
    public Page<Unit> getAllUnits(Pageable pageable) {
        return unitRepository.findAll(pageable);
    }

    /**
     * Retrieves a page of units matching the optional filters.
     *
     * @param name case-insensitive name fragment; ignored when null/blank
     */
    public Page<Unit> searchUnits(String name, Pageable pageable) {
        List<Specification<Unit>> filters = new ArrayList<>();
        if (name != null && !name.isBlank()) {
            String pattern = "%" + name.trim().toLowerCase() + "%";
            filters.add((root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern));
        }
        return unitRepository.findAll(Specification.allOf(filters), pageable);
    }

    /**
     * Retrieves a unit by its identifier.
     */
    public Optional<Unit> getUnitById(Long id) {
        return unitRepository.findById(id);
    }

    /**
     * Creates a new unit from a DTO request.
     * 
     * This method separates API contracts from domain logic.
     *
     * @param request DTO containing unit data
     * @return created unit entity
     */
    @Transactional
    public Unit createUnit(CreateUnitRequest request) {
        Unit unit = new Unit();
        unit.setName(request.getName());
        unit.setLocation(request.getLocation());
        unit.setLatitude(request.getLatitude());
        unit.setLongitude(request.getLongitude());
        return unitRepository.save(unit);
    }

    /**
     * Updates an existing unit.
     * 
     * Only non-null fields from the request are updated.
     *
     * @param id unit identifier
     * @param request update data
     * @return updated unit
     * @throws ResourceNotFoundException if unit doesn't exist
     */
    @Transactional
    public Unit updateUnit(Long id, UpdateUnitRequest request) {
        Unit unit = unitRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "id", id));

        if (request.getName() != null) {
            unit.setName(request.getName());
        }
        if (request.getLocation() != null) {
            unit.setLocation(request.getLocation());
        }
        if (request.getLatitude() != null) {
            unit.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            unit.setLongitude(request.getLongitude());
        }

        return unitRepository.save(unit);
    }

    /**
     * Deletes a unit by ID.
     *
     * @param id unit identifier
     * @throws ResourceNotFoundException if unit doesn't exist
     * @throws InvalidRequestException if the unit still has orders referencing it
     */
    @Transactional
    public void deleteUnit(Long id) {
        if (!unitRepository.existsById(id)) {
            throw new ResourceNotFoundException("Unit", "id", id);
        }
        // Orders reference their unit via a required FK; deleting a unit that
        // still has orders (of any status) would either violate that
        // constraint or, if cascaded, silently destroy audit-relevant order
        // history. Reassign or delete the orders first.
        if (orderRepository.existsByUnitId(id)) {
            throw new InvalidRequestException(
                "UNIT_DELETE_HAS_ORDERS",
                Map.of("unitId", id),
                "Cannot delete unit with existing orders. Unit id: " + id);
        }
        unitRepository.deleteById(id);
    }

    private static final List<String> IMPORT_REQUIRED_COLUMNS = List.of("name", "location");

    /**
     * Parses and validates a CSV file of units without persisting
     * anything.
     */
    public ImportPreviewResponse<CreateUnitRequest> previewImport(MultipartFile file) {
        return ImportPreviewResponse.from(buildImportRows(file));
    }

    /**
     * Re-parses and re-validates the same file, then persists every row
     * that isn't an ERROR. One transaction for the whole file.
     */
    @Transactional
    public ImportPreviewResponse<CreateUnitRequest> commitImport(MultipartFile file) {
        List<ImportRowResult<CreateUnitRequest>> rows = buildImportRows(file);
        for (ImportRowResult<CreateUnitRequest> row : rows) {
            if (row.getStatus() != ImportRowStatus.ERROR) {
                createUnit(row.getData());
            }
        }
        return ImportPreviewResponse.from(rows);
    }

    private List<ImportRowResult<CreateUnitRequest>> buildImportRows(MultipartFile file) {
        List<CSVRecord> records = CsvImportSupport.parseRecords(file, IMPORT_REQUIRED_COLUMNS);
        Set<String> existingNames = unitRepository.findAll().stream()
                .map(u -> u.getName().toLowerCase())
                .collect(Collectors.toSet());
        Set<String> seenInFile = new HashSet<>();

        List<ImportRowResult<CreateUnitRequest>> rows = new ArrayList<>();
        int rowNumber = 0;
        for (CSVRecord record : records) {
            rowNumber++;
            List<String> errors = new ArrayList<>();
            try {
                CreateUnitRequest dto = new CreateUnitRequest();
                dto.setName(CsvImportSupport.get(record, "name"));
                dto.setLocation(CsvImportSupport.get(record, "location"));
                dto.setLatitude(CsvImportSupport.getOptionalDouble(record, "latitude", errors));
                dto.setLongitude(CsvImportSupport.getOptionalDouble(record, "longitude", errors));

                for (ConstraintViolation<CreateUnitRequest> violation : validator.validate(dto)) {
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
