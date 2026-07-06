package com.mls.logistics.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object for creating a new Warehouse.
 * 
 * This class defines the structure of data accepted by the API
 * when creating a warehouse, separating API contracts from
 * domain entities.
 */
public class CreateWarehouseRequest {

    @NotBlank(message = "Warehouse name is required")
    @Size(min = 2, max = 100, message = "Warehouse name must be between 2 and 100 characters")
    private String name;
    @NotBlank(message = "Warehouse location is required")
    @Size(min = 2, max = 200, message = "Warehouse location must be between 2 and 200 characters")
    private String location;

    /** Optional geographic latitude for the logistics map. */
    @DecimalMin(value = "-90", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90", message = "Latitude must be between -90 and 90")
    private Double latitude;

    /** Optional geographic longitude for the logistics map. */
    @DecimalMin(value = "-180", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180", message = "Longitude must be between -180 and 180")
    private Double longitude;

    /**
     * Default constructor for deserialization.
     */
    public CreateWarehouseRequest() {
    }

    /**
     * Constructs a CreateWarehouseRequest with all fields.
     *
     * @param name warehouse name
     * @param location warehouse location
     */
    public CreateWarehouseRequest(String name, String location) {
        this.name = name;
        this.location = location;
    }

    // Getters and setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
}