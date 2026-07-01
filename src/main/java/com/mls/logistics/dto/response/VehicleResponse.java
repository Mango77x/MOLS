package com.mls.logistics.dto.response;

import com.mls.logistics.domain.Vehicle;

/**
 * Data Transfer Object for Vehicle responses.
 * 
 * This class defines the structure of vehicle data returned by the API,
 * allowing control over exactly what fields are exposed to clients.
 */
public class VehicleResponse {

    private Long id;
    private String type;
    private int capacity;
    private String status;

    /**
     * Default constructor for serialization.
     */
    public VehicleResponse() {
    }

    /**
     * Constructs a VehicleResponse with all fields.
     *
     * @param id vehicle identifier
     * @param type vehicle type
     * @param capacity vehicle capacity
     * @param status vehicle status
     */
    public VehicleResponse(Long id, String type, int capacity, String status) {
        this.id = id;
        this.type = type;
        this.capacity = capacity;
        this.status = status;
    }

    /**
     * Creates a VehicleResponse from a Vehicle entity.
     * 
     * This static factory method converts domain entities to DTOs,
     * decoupling the API from the persistence layer.
     *
     * @param vehicle the vehicle entity
     * @return VehicleResponse DTO
     */
    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getType(),
                vehicle.getCapacity(),
                vehicle.getStatus() != null ? vehicle.getStatus().name() : null
        );
    }

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}