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
 * Concurrency test for the "stock never goes negative / no lost updates"
 * guarantee: fires simultaneous stock adjustments at the same row and checks
 * the invariant that every applied delta is accounted for exactly once.
 *
 * <p>Optimistic locking ({@code @Version} on Stock) makes losing requests
 * fail with 409 instead of silently overwriting each other; the audit trail
 * must contain exactly one movement per successful adjustment.</p>
 */
class StockConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final int THREADS = 8;
    private static final int INITIAL_QUANTITY = 1000;
    private static final int DELTA = -50;

    @Test
    void concurrentAdjustments_NeverLoseUpdates() throws Exception {
        String token = createUserAndLogin("admin-concurrency", Role.ADMIN);

        long warehouseId = postForId("/api/warehouses",
                "{\"name\":\"Contended\",\"location\":\"Valencia\"}", token);
        long resourceId = postForId("/api/resources",
                "{\"name\":\"Ammo crate\",\"type\":\"SUPPLY\",\"criticality\":\"HIGH\"}", token);
        long stockId = postForId("/api/stocks",
                "{\"resourceId\":" + resourceId + ",\"warehouseId\":" + warehouseId +
                        ",\"quantity\":" + INITIAL_QUANTITY + "}", token);

        // Fire THREADS simultaneous decrements against the same stock row
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            Callable<Integer> adjustment = () -> restTemplate.exchange(
                    "/api/stocks/" + stockId + "/adjust", HttpMethod.PATCH,
                    jsonEntity("{\"delta\":" + DELTA + ",\"reason\":\"Concurrent test\"}", token),
                    String.class).getStatusCode().value();

            List<Future<Integer>> results = pool.invokeAll(
                    java.util.Collections.nCopies(THREADS, adjustment), 60, TimeUnit.SECONDS);

            int succeeded = 0;
            for (Future<Integer> result : results) {
                int status = result.get();
                // Every request either applied (200) or lost the race cleanly (409)
                assertThat(status).isIn(200, 409);
                if (status == 200) {
                    succeeded++;
                }
            }

            // At least one adjustment must have gone through
            assertThat(succeeded).isGreaterThan(0);

            // Invariant: final quantity reflects EXACTLY the successful deltas —
            // no lost updates, no phantom deductions.
            var stock = restTemplate.exchange("/api/stocks/" + stockId, HttpMethod.GET,
                    jsonEntity(null, token), String.class);
            int finalQuantity = readJson(stock.getBody()).get("quantity").asInt();
            assertThat(finalQuantity).isEqualTo(INITIAL_QUANTITY + succeeded * DELTA);

            // Audit invariant: initial ENTRY + one EXIT per successful adjustment
            var movements = restTemplate.exchange("/api/movements", HttpMethod.GET,
                    jsonEntity(null, token), String.class);
            assertThat(readJson(movements.getBody()).size()).isEqualTo(1 + succeeded);
        } finally {
            pool.shutdownNow();
        }
    }

}
