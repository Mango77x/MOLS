package com.mls.logistics.dto.response;

import com.mls.logistics.domain.Warehouse;

/**
 * Data Transfer Object for Warehouse responses.
 * 
 * This class defines the structure of warehouse data returned by the API,
 * allowing control over exactly what fields are exposed to clients.
 */
public class WarehouseResponse {

    private Long id;
    private String name;
    private String location;
    private Double latitude;
    private Double longitude;

    /**
     * Default constructor for serialization.
     */
    public WarehouseResponse() {
    }

    /**
     * Constructs a WarehouseResponse with all fields.
     *
     * @param id warehouse identifier
     * @param name warehouse name
     * @param location warehouse location
     * @param latitude optional geographic latitude
     * @param longitude optional geographic longitude
     */
    public WarehouseResponse(Long id, String name, String location,
                             Double latitude, Double longitude) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * Creates a WarehouseResponse from a Warehouse entity.
     * 
     * This static factory method converts domain entities to DTOs,
     * decoupling the API from the persistence layer.
     *
     * @param warehouse the warehouse entity
     * @return WarehouseResponse DTO
     */
    public static WarehouseResponse from(Warehouse warehouse) {
        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getName(),
                warehouse.getLocation(),
                warehouse.getLatitude(),
                warehouse.getLongitude()
        );
    }

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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