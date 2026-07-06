package com.mls.logistics.dto.response;

import com.mls.logistics.domain.Unit;

/**
 * Data Transfer Object for Unit responses.
 * 
 * This class defines the structure of unit data returned by the API,
 * allowing control over exactly what fields are exposed to clients.
 */
public class UnitResponse {

    private Long id;
    private String name;
    private String location;
    private Double latitude;
    private Double longitude;

    /**
     * Default constructor for serialization.
     */
    public UnitResponse() {
    }

    /**
     * Constructs a UnitResponse with all fields.
     *
     * @param id unit identifier
     * @param name unit name
     * @param location unit location
     * @param latitude optional geographic latitude
     * @param longitude optional geographic longitude
     */
    public UnitResponse(Long id, String name, String location,
                        Double latitude, Double longitude) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * Creates a UnitResponse from a Unit entity.
     * 
     * This static factory method converts domain entities to DTOs,
     * decoupling the API from the persistence layer.
     *
     * @param unit the unit entity
     * @return UnitResponse DTO
     */
    public static UnitResponse from(Unit unit) {
        return new UnitResponse(
                unit.getId(),
                unit.getName(),
                unit.getLocation(),
                unit.getLatitude(),
                unit.getLongitude()
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
