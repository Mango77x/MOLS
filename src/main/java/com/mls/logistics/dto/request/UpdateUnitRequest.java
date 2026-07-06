package com.mls.logistics.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object for updating an existing Unit.
 * 
 * All fields are optional - only provided fields will be updated.
 */
public class UpdateUnitRequest {

    @Size(min = 2, max = 100, message = "Unit name must be between 2 and 100 characters")
    private String name;

    @Size(min = 2, max = 200, message = "Unit location must be between 2 and 200 characters")
    private String location;

    /** Optional geographic latitude for the logistics map. */
    @DecimalMin(value = "-90", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90", message = "Latitude must be between -90 and 90")
    private Double latitude;

    /** Optional geographic longitude for the logistics map. */
    @DecimalMin(value = "-180", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180", message = "Longitude must be between -180 and 180")
    private Double longitude;

    public UpdateUnitRequest() {
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