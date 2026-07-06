package com.mls.logistics.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Stable pagination envelope for list endpoints.
 *
 * <p>Defines the API's own page contract instead of serializing Spring Data's
 * {@code Page} directly, whose JSON shape is not guaranteed across framework
 * versions.</p>
 *
 * @param content       the page of mapped response items
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements total number of matching elements
 * @param totalPages    total number of pages
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    /**
     * Builds a response from a Spring Data page, mapping each entity to its DTO.
     */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
