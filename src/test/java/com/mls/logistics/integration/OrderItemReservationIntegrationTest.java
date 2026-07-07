package com.mls.logistics.integration;

import com.mls.logistics.security.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that order-item creation reserves stock instead of only checking
 * it, closing the gap where two orders (sequential or concurrent) could
 * each independently pass an availability check and together commit more
 * demand than physically exists — see {@code OrderItemService.reserve}.
 *
 * <p>Every order has a fixed origin warehouse, so all reservations in these
 * tests are scoped to a single {@code (resource, warehouse)} stock row.</p>
 */
class OrderItemReservationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void secondOrder_CannotOverCommitStockAlreadyClaimedByAFirstOpenOrder() {
        String token = createUserAndLogin("admin-reserve-sequential", Role.ADMIN);

        long warehouseId = postForId("/api/warehouses",
                "{\"name\":\"Depot\",\"location\":\"Burgos\"}", token);
        long unitId = postForId("/api/units",
                "{\"name\":\"Bravo Unit\",\"location\":\"Burgos\"}", token);
        long resourceId = postForId("/api/resources",
                "{\"name\":\"Fuel drum\",\"type\":\"SUPPLY\",\"criticality\":\"HIGH\"}", token);
        postForId("/api/stocks",
                "{\"resourceId\":" + resourceId + ",\"warehouseId\":" + warehouseId + ",\"quantity\":50}", token);

        // First order claims 40 of the 50 physical units — still just CREATED,
        // nothing physically deducted yet.
        long orderA = postForId("/api/orders",
                "{\"unitId\":" + unitId + ",\"warehouseId\":" + warehouseId +
                        ",\"dateCreated\":\"2026-07-01\",\"status\":\"CREATED\"}", token);
        postForId("/api/order-items",
                "{\"orderId\":" + orderA + ",\"resourceId\":" + resourceId + ",\"quantity\":40}", token);

        // A second, independent order from the same warehouse asking for 40
        // more would have passed under the old "check physical stock only"
        // rule (40 <= 50) even though only 10 units are actually unclaimed.
        long orderB = postForId("/api/orders",
                "{\"unitId\":" + unitId + ",\"warehouseId\":" + warehouseId +
                        ",\"dateCreated\":\"2026-07-01\",\"status\":\"CREATED\"}", token);
        var response = restTemplate.postForEntity("/api/order-items",
                jsonEntity("{\"orderId\":" + orderB + ",\"resourceId\":" + resourceId + ",\"quantity\":40}", token),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).contains("available in this warehouse (physical stock minus existing reservations): 10");

        // The 10 units still genuinely free remain claimable.
        var freeRoom = restTemplate.postForEntity("/api/order-items",
                jsonEntity("{\"orderId\":" + orderB + ",\"resourceId\":" + resourceId + ",\"quantity\":10}", token),
                String.class);
        assertThat(freeRoom.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void cancellingAnOrder_ReleasesItsReservedStockForOtherOrders() {
        String token = createUserAndLogin("admin-reserve-cancel", Role.ADMIN);

        long warehouseId = postForId("/api/warehouses",
                "{\"name\":\"Depot\",\"location\":\"Leon\"}", token);
        long unitId = postForId("/api/units",
                "{\"name\":\"Charlie Unit\",\"location\":\"Leon\"}", token);
        long resourceId = postForId("/api/resources",
                "{\"name\":\"Radio unit\",\"type\":\"EQUIPMENT\",\"criticality\":\"MEDIUM\"}", token);
        postForId("/api/stocks",
                "{\"resourceId\":" + resourceId + ",\"warehouseId\":" + warehouseId + ",\"quantity\":20}", token);

        long orderA = postForId("/api/orders",
                "{\"unitId\":" + unitId + ",\"warehouseId\":" + warehouseId +
                        ",\"dateCreated\":\"2026-07-01\",\"status\":\"CREATED\"}", token);
        postForId("/api/order-items",
                "{\"orderId\":" + orderA + ",\"resourceId\":" + resourceId + ",\"quantity\":20}", token);

        // With all 20 units reserved by order A, a second order can't claim any.
        long orderB = postForId("/api/orders",
                "{\"unitId\":" + unitId + ",\"warehouseId\":" + warehouseId +
                        ",\"dateCreated\":\"2026-07-01\",\"status\":\"CREATED\"}", token);
        var blocked = restTemplate.postForEntity("/api/order-items",
                jsonEntity("{\"orderId\":" + orderB + ",\"resourceId\":" + resourceId + ",\"quantity\":1}", token),
                String.class);
        assertThat(blocked.getStatusCode().value()).isEqualTo(409);

        // Cancelling order A must release its reservation...
        var cancel = restTemplate.exchange("/api/orders/" + orderA, HttpMethod.PUT,
                jsonEntity("{\"status\":\"CANCELLED\"}", token), String.class);
        assertThat(cancel.getStatusCode().value()).isEqualTo(200);

        // ...so order B can now claim the full 20 units.
        var afterCancel = restTemplate.postForEntity("/api/order-items",
                jsonEntity("{\"orderId\":" + orderB + ",\"resourceId\":" + resourceId + ",\"quantity\":20}", token),
                String.class);
        assertThat(afterCancel.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void concurrentOrderItemCreation_NeverCommitsMoreThanPhysicalStock() throws Exception {
        String token = createUserAndLogin("admin-reserve-concurrency", Role.ADMIN);

        int threads = 8;
        int quantityEach = 15;
        int initialStock = 100; // only enough for 6 of the 8 requests (90), not all 8 (120)

        long warehouseId = postForId("/api/warehouses",
                "{\"name\":\"Depot\",\"location\":\"Valladolid\"}", token);
        long unitId = postForId("/api/units",
                "{\"name\":\"Delta Unit\",\"location\":\"Valladolid\"}", token);
        long resourceId = postForId("/api/resources",
                "{\"name\":\"Ammo crate\",\"type\":\"SUPPLY\",\"criticality\":\"HIGH\"}", token);
        long stockId = postForId("/api/stocks",
                "{\"resourceId\":" + resourceId + ",\"warehouseId\":" + warehouseId + ",\"quantity\":" + initialStock + "}", token);

        long orderId = postForId("/api/orders",
                "{\"unitId\":" + unitId + ",\"warehouseId\":" + warehouseId +
                        ",\"dateCreated\":\"2026-07-01\",\"status\":\"CREATED\"}", token);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            Callable<Integer> createItem = () -> restTemplate.postForEntity("/api/order-items",
                    jsonEntity("{\"orderId\":" + orderId + ",\"resourceId\":" + resourceId +
                            ",\"quantity\":" + quantityEach + "}", token),
                    String.class).getStatusCode().value();

            List<Future<Integer>> results = pool.invokeAll(
                    java.util.Collections.nCopies(threads, createItem), 60, TimeUnit.SECONDS);

            int succeeded = 0;
            for (Future<Integer> result : results) {
                int status = result.get();
                // Every request either reserved successfully (201) or lost the race cleanly (409)
                assertThat(status).isIn(201, 409);
                if (status == 201) {
                    succeeded++;
                }
            }

            // At least one must go through, and never more than physical stock allows.
            assertThat(succeeded).isGreaterThan(0);
            assertThat(succeeded * quantityEach).isLessThanOrEqualTo(initialStock);

            // The stock row's committed total must exactly match what was
            // actually accepted — no lost updates, no phantom over-reservation.
            var stock = getJson("/api/stocks/" + stockId, token);
            assertThat(stock.get("reservedQuantity").asInt()).isEqualTo(succeeded * quantityEach);
        } finally {
            pool.shutdownNow();
        }
    }
}
