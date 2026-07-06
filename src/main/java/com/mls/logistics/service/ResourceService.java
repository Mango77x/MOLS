package com.mls.logistics.service;

import com.mls.logistics.domain.Resource;
import com.mls.logistics.dto.request.CreateResourceRequest;
import com.mls.logistics.dto.request.UpdateResourceRequest;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.repository.ResourceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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

    /**
     * Constructor-based dependency injection.
     * This is the recommended approach in Spring.
     */
    public ResourceService(ResourceRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
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
     * Retrieves a resource by its identifier.
     */
    public Optional<Resource> getResourceById(Long id) {
        return resourceRepository.findById(id);
    }

    /**
     * Creates a new resource.
     * 
     * Business rules can be added here in the future
     * (e.g. resource availability checks).
     */
    @Transactional
    public Resource createResource(Resource resource) {
        return resourceRepository.save(resource);
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
     */
    @Transactional
    public void deleteResource(Long id) {
        if (!resourceRepository.existsById(id)) {
            throw new ResourceNotFoundException("Resource", "id", id);
        }
        resourceRepository.deleteById(id);
    }
}
