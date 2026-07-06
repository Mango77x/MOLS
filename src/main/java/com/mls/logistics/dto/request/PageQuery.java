package com.mls.logistics.dto.request;

import com.mls.logistics.exception.InvalidRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Translates the optional {@code page}/{@code size}/{@code sort} query
 * parameters of list endpoints into a Spring Data {@link Pageable}.
 *
 * <p>Sort fields are validated against a per-endpoint whitelist so clients
 * cannot sort by arbitrary entity property paths (which would leak the domain
 * model and produce 500s on typos); violations surface as HTTP 400.</p>
 */
public final class PageQuery {

    /** Page size applied when the client paginates without an explicit size. */
    public static final int DEFAULT_SIZE = 20;

    /** Upper bound protecting the API from unbounded page sizes. */
    public static final int MAX_SIZE = 100;

    private PageQuery() {
    }

    /**
     * Builds a {@link Pageable} from raw query parameters.
     *
     * @param page       zero-based page index, defaults to 0
     * @param size       page size, defaults to {@link #DEFAULT_SIZE}, capped at {@link #MAX_SIZE}
     * @param sort       {@code field} or {@code field,asc|desc}; must be whitelisted
     * @param sortFields whitelist of sortable fields for the endpoint
     * @param defaultSort ordering applied when no sort parameter is given
     */
    public static Pageable toPageable(Integer page, Integer size, String sort,
                                      Set<String> sortFields, Sort defaultSort) {
        int pageIndex = page != null ? page : 0;
        int pageSize = size != null ? size : DEFAULT_SIZE;

        if (pageIndex < 0) {
            throw new InvalidRequestException("Query parameter 'page' must be 0 or greater");
        }
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            throw new InvalidRequestException(
                    "Query parameter 'size' must be between 1 and " + MAX_SIZE);
        }

        return PageRequest.of(pageIndex, pageSize, parseSort(sort, sortFields, defaultSort));
    }

    private static Sort parseSort(String sort, Set<String> sortFields, Sort defaultSort) {
        if (sort == null || sort.isBlank()) {
            return defaultSort;
        }

        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!sortFields.contains(field)) {
            throw new InvalidRequestException(
                    "Unsupported sort field '" + field + "'. Sortable fields: "
                            + String.join(", ", sortFields.stream().sorted().toList()));
        }

        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length > 1) {
            String dir = parts[1].trim();
            if (dir.equalsIgnoreCase("desc")) {
                direction = Sort.Direction.DESC;
            } else if (!dir.equalsIgnoreCase("asc")) {
                throw new InvalidRequestException(
                        "Sort direction must be 'asc' or 'desc', got '" + dir + "'");
            }
        }
        return Sort.by(direction, field);
    }
}
