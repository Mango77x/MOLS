package com.mls.logistics.service;

import com.mls.logistics.domain.Vehicle;
import com.mls.logistics.domain.VehicleStatus;
import com.mls.logistics.dto.request.CreateVehicleRequest;
import com.mls.logistics.dto.request.UpdateVehicleRequest;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.repository.ShipmentRepository;
import com.mls.logistics.repository.VehicleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for Vehicle-related business operations.
 * 
 * This class acts as an intermediary between controllers and repositories,
 * enforcing business rules and application logic.
 */
@Service
@Transactional(readOnly = true)
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final ShipmentRepository shipmentRepository;

    /**
     * Constructor-based dependency injection.
     * This is the recommended approach in Spring.
     */
    public VehicleService(VehicleRepository vehicleRepository, ShipmentRepository shipmentRepository) {
        this.vehicleRepository = vehicleRepository;
        this.shipmentRepository = shipmentRepository;
    }

    /**
     * Retrieves all registered vehicles.
     */
    public List<Vehicle> getAllVehicles() {
        return vehicleRepository.findAll();
    }

    public List<Vehicle> getAllVehicles(Sort sort) {
        return vehicleRepository.findAll(sort);
    }

    /**
     * Retrieves a page of vehicles.
     */
    public Page<Vehicle> getAllVehicles(Pageable pageable) {
        return vehicleRepository.findAll(pageable);
    }

    /**
     * Retrieves a page of vehicles matching the optional filters.
     *
     * @param type   exact type (case-insensitive); ignored when null/blank
     * @param status vehicle status; ignored when null/blank
     */
    public Page<Vehicle> searchVehicles(String type, String status, Pageable pageable) {
        List<Specification<Vehicle>> filters = new ArrayList<>();
        if (type != null && !type.isBlank()) {
            String value = type.trim().toLowerCase();
            filters.add((root, query, cb) -> cb.equal(cb.lower(root.get("type")), value));
        }
        if (status != null && !status.isBlank()) {
            VehicleStatus parsed = VehicleStatus.from(status);
            filters.add((root, query, cb) -> cb.equal(root.get("status"), parsed));
        }
        return vehicleRepository.findAll(Specification.allOf(filters), pageable);
    }

    /**
     * Retrieves a vehicle by its identifier.
     */
    public Optional<Vehicle> getVehicleById(Long id) {
        return vehicleRepository.findById(id);
    }

    /**
     * Creates a new vehicle from a DTO request.
     * 
     * This method separates API contracts from domain logic.
     *
     * @param request DTO containing vehicle data
     * @return created vehicle entity
     */
    @Transactional
    public Vehicle createVehicle(CreateVehicleRequest request) {
        Vehicle vehicle = new Vehicle();
        vehicle.setType(request.getType());
        vehicle.setCapacity(request.getCapacity());
        vehicle.setStatus(VehicleStatus.from(request.getStatus()));
        return vehicleRepository.save(vehicle);
    }

    /**
     * Updates an existing vehicle.
     * 
     * Only non-null fields from the request are updated.
     *
     * @param id vehicle identifier
     * @param request update data
     * @return updated vehicle
     * @throws ResourceNotFoundException if vehicle doesn't exist
     */
    @Transactional
    public Vehicle updateVehicle(Long id, UpdateVehicleRequest request) {
        Vehicle vehicle = vehicleRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", "id", id));

        if (request.getType() != null) {
            vehicle.setType(request.getType());
        }
        if (request.getCapacity() != null) {
            vehicle.setCapacity(request.getCapacity());
        }
        if (request.getStatus() != null) {
            vehicle.setStatus(VehicleStatus.from(request.getStatus()));
        }

        return vehicleRepository.save(vehicle);
    }

    /**
     * Deletes a vehicle by ID.
     *
     * @param id vehicle identifier
     * @throws ResourceNotFoundException if vehicle doesn't exist
     * @throws InvalidRequestException if the vehicle still has shipments referencing it
     */
    @Transactional
    public void deleteVehicle(Long id) {
        if (!vehicleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Vehicle", "id", id);
        }
        // Shipments reference their vehicle via a required FK; deleting a
        // vehicle that still has shipments (of any status) would either
        // violate that constraint or, if cascaded, silently destroy
        // audit-relevant shipment history.
        if (shipmentRepository.existsByVehicleId(id)) {
            throw new InvalidRequestException(
                "Cannot delete vehicle with existing shipments. Vehicle id: " + id);
        }
        vehicleRepository.deleteById(id);
    }
}
