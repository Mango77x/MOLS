package com.mls.logistics.dto.request;

import com.mls.logistics.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the pagination/sort query-parameter parsing shared by all
 * list endpoints.
 */
class PageQueryTest {

    private static final Set<String> FIELDS = Set.of("id", "name");
    private static final Sort DEFAULT_SORT = Sort.by("id");

    @Test
    void nullParameters_UseDefaults() {
        Pageable pageable = PageQuery.toPageable(null, null, null, FIELDS, DEFAULT_SORT);

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(PageQuery.DEFAULT_SIZE);
        assertThat(pageable.getSort()).isEqualTo(DEFAULT_SORT);
    }

    @Test
    void explicitSort_IsParsedWithDirection() {
        Pageable pageable = PageQuery.toPageable(2, 50, "name,desc", FIELDS, DEFAULT_SORT);

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(50);
        Sort.Order order = pageable.getSort().getOrderFor("name");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void sortWithoutDirection_DefaultsToAscending() {
        Pageable pageable = PageQuery.toPageable(0, 10, "name", FIELDS, DEFAULT_SORT);

        Sort.Order order = pageable.getSort().getOrderFor("name");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void negativePage_IsRejected() {
        assertThatThrownBy(() -> PageQuery.toPageable(-1, null, null, FIELDS, DEFAULT_SORT))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("page");
    }

    @Test
    void sizeOutOfBounds_IsRejected() {
        assertThatThrownBy(() -> PageQuery.toPageable(0, 0, null, FIELDS, DEFAULT_SORT))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("size");

        assertThatThrownBy(() -> PageQuery.toPageable(0, PageQuery.MAX_SIZE + 1, null, FIELDS, DEFAULT_SORT))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("size");
    }

    @Test
    void nonWhitelistedSortField_IsRejected() {
        assertThatThrownBy(() -> PageQuery.toPageable(0, 10, "password", FIELDS, DEFAULT_SORT))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Unsupported sort field");
    }

    @Test
    void invalidSortDirection_IsRejected() {
        assertThatThrownBy(() -> PageQuery.toPageable(0, 10, "name,sideways", FIELDS, DEFAULT_SORT))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("direction");
    }
}
