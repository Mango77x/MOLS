package com.mls.logistics.dto.response;

import com.mls.logistics.domain.Resource;

/**
 * Data Transfer Object for Resource responses.
 *
 * This class defines the structure of resource data returned by the API,
 * allowing control over exactly what fields are exposed to clients.
 */
public class ResourceResponse {

    private Long id;
    private String name;
    private String type;
    private String criticality;

    /**
     * Default constructor for serialization.
     */
    public ResourceResponse() {
    }

    /**
     * Constructs a ResourceResponse with all fields.
     *
     * @param id resource identifier
     * @param name resource name
     * @param type resource type
     * @param criticality criticality level
     */
    public ResourceResponse(Long id, String name, String type, String criticality) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.criticality = criticality;
    }

    /**
     * Creates a ResourceResponse from a Resource entity.
     *
     * This static factory method converts domain entities to DTOs,
     * decoupling the API from the persistence layer.
     *
     * @param resource the resource entity
     * @return ResourceResponse DTO
     */
    public static ResourceResponse from(Resource resource) {
        return new ResourceResponse(
                resource.getId(),
                resource.getName(),
                resource.getType(),
                resource.getCriticality()
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCriticality() {
        return criticality;
    }

    public void setCriticality(String criticality) {
        this.criticality = criticality;
    }
}
